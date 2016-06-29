/*
 * Copyright (C) 2016 jollion
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package plugins.plugins.segmenters;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageMask;
import image.ObjectFactory;
import java.util.ArrayList;
import java.util.List;
import plugins.plugins.preFilter.IJSubtractBackground;
import static plugins.plugins.segmenters.MutationSegmenterScaleSpace.getScaleSpace;
import processing.Filters;
import processing.ImageFeatures;
import processing.MultiScaleWatershedTransform;
import processing.WatershedTransform;
import processing.neighborhood.EllipsoidalNeighborhoodCompartment;
import processing.neighborhood.Neighborhood;

/**
 *
 * @author jollion
 */
public class MutationSegmenterScaleSpace2 extends MutationSegmenterScaleSpace {
    NumberParameter subtractBackgroundRadius = new BoundedNumberParameter("Subtract Background Radius", 1, 4, 2, null);
    
    public MutationSegmenterScaleSpace2() {
        parameters = new Parameter[]{minSpotSize, subtractBackgroundRadius, thresholdHigh,  thresholdLow, intensityThreshold, postFilters};
        thresholdHigh.setValue(7);
        thresholdLow.setValue(5);
    }
    @Override 
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        ObjectPopulation res= runPlane(input, parent.getMask(), ((StructureObject)parent).getObjectPopulation(1).getLabelMap());
        return postFilters.filter(res, structureIdx, (StructureObject)parent);
    }
    ImageInteger compartimentMap;
    public MutationSegmenterScaleSpace2 setCompartimentMap(ImageInteger compartimentMap) {
        this.compartimentMap=compartimentMap;
        return this;
    }
    public ObjectPopulation runPlane(Image input, ImageMask mask, ImageInteger compartimentMap) {
        if (input.getSizeZ()>1) throw new Error("MutationSegmenter: should be run on a 2D image");
        double[] radii = new double[]{2, 2.5, 3, 3.5}; 
        int maxScaleWSIdx=1;
        Image smooth = ImageFeatures.gaussianSmooth(input, 2, 2, false);
        Image sb = IJSubtractBackground.filter(input, subtractBackgroundRadius.getValue().doubleValue(), false, false, true, false).setName("sub");
        //Image median = Filters.median(input, new ImageFloat("", input), new EllipsoidalNeighborhoodCompartment(1.5, false, compartimentMap));
        //Image sb = Filters.tophat(input, median, new ImageFloat("", input), new EllipsoidalNeighborhoodCompartment(subtractBackgroundRadius.getValue().doubleValue(), false, compartimentMap)); // TODO : new filter: tophat restreint @ la bactérie
        Image scaleSpace = ImageFeatures.getScaleSpaceLaplacian(sb, radii); //getScaleSpace(sb, smooth, radii); 
        ImageByte seedsSP = getSeedsScaleSpace(scaleSpace, thresholdHigh.getValue().doubleValue(), 1.5);
        
        for (int z = 0; z<seedsSP.getSizeZ(); ++z) {
            for (int xy = 0; xy<seedsSP.getSizeXY(); ++xy) {
                if (seedsSP.insideMask(xy, z) && smooth.getPixel(xy, 0)<intensityThreshold.getValue().doubleValue()) seedsSP.setPixel(xy, z, 0);
            }
        }
        Image[] wsMaps = scaleSpace.splitZPlanes(0, maxScaleWSIdx).toArray(new Image[0]);
        ImageByte[] seedMaps = seedsSP.splitZPlanes(0, maxScaleWSIdx).toArray(new ImageByte[0]); //remove seeds from 2 last radii
        
        // combine seeds: project seeds from higher radii to radius @maxScaleWSIdx  
        for (int i = maxScaleWSIdx+1; i<radii.length; ++i) combineSeeds(seedsSP.getZPlane(i), seedMaps[maxScaleWSIdx], wsMaps[maxScaleWSIdx], radii[i]);
        //for (int i = 0; i<maxScaleWSIdx; ++i) removeSeeds(seedMaps[maxScaleWSIdx], seedMaps[i], 1.5);
        if (intermediateImages!=null) {
            //intermediateImages.add(input);
            intermediateImages.add(scaleSpace);
            intermediateImages.add(seedsSP);
            //intermediateImages.add(median.setName("median"));
            intermediateImages.add(sb);
            intermediateImages.add(compartimentMap.setName("comp map"));
        }
        
        
        ObjectPopulation pop =  MultiScaleWatershedTransform.watershed(wsMaps, mask, seedMaps, true, new MultiScaleWatershedTransform.MultiplePropagationCriteria(new MultiScaleWatershedTransform.ThresholdPropagationOnWatershedMap(thresholdLow.getValue().doubleValue())), new MultiScaleWatershedTransform.SizeFusionCriterion(minSpotSize.getValue().intValue()));// minSpotSize->1 //, new MultiScaleWatershedTransform.MonotonalPropagation()
        
        pop.filter(new ObjectPopulation.RemoveFlatObjects(input));
        pop.filter(new ObjectPopulation.Size().setMin(minSpotSize.getValue().intValue()));
        return pop;
    }
    @Override 
    public ObjectPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        Image scaleSpace = ImageFeatures.getLaplacian(input, 2.5, true, false).setName("WatershedMap from: "+input.getName());
        List<Object3D> seedObjects = ObjectFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getScaleXY(), input.getScaleZ());
        ObjectPopulation pop =  WatershedTransform.watershed(scaleSpace, segmentationMask, seedObjects, true, new WatershedTransform.ThresholdPropagationOnWatershedMap(thresholdLow.getValue().doubleValue()), new WatershedTransform.SizeFusionCriterion(minSpotSize.getValue().intValue()));
        
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixelWithOffset(seed[0], seed[1], seed[2], 1);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(seedMap);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(scaleSpace);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pop.getLabelMap().setName("segmented from: "+input.getName()));
        }
        
        return pop;
    }
}
