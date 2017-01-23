/*
 * Copyright (C) 2015 jollion
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

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.ObjectPopulation.MeanIntensity;
import dataStructure.objects.ObjectPopulation.Or;
import dataStructure.objects.ObjectPopulation.Overlap;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.Voxel;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageOperations;
import image.ImageShort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import jj2000.j2k.util.ArrayUtil;
import plugins.Segmenter;
import plugins.Thresholder;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.thresholders.BackgroundFit;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.KappaSigma;
import plugins.plugins.thresholders.ObjectCountThresholder;
import processing.Filters;
import processing.gaussianFit.GaussianFit;
import processing.IJFFTBandPass;
import processing.ImageFeatures;
import processing.LoG;
import processing.WatershedTransform;
import processing.WatershedTransform.MonotonalPropagation;
import processing.WatershedTransform.MultiplePropagationCriteria;
import processing.WatershedTransform.SizeFusionCriterion;
import processing.WatershedTransform.ThresholdPropagation;
import processing.WatershedTransform.ThresholdPropagationOnWatershedMap;
import static processing.WatershedTransform.watershed;
import processing.neighborhood.EllipsoidalSubVoxNeighborhood;

/**
 *
 * @author jollion
 */
public class MutationSegmenter implements Segmenter {
    public List<Image> intermediateImages;
    public static boolean debug = false;
    public static boolean displayImages = false;
    NumberParameter scale = new BoundedNumberParameter("Scale", 1, 2, 1.5, 5);
    NumberParameter subtractBackgroundScale = new BoundedNumberParameter("Subtract Background Scale", 1, 6, 3, 20);
    NumberParameter minSpotSize = new BoundedNumberParameter("Min. Spot Size (Voxels)", 0, 5, 1, null);
    NumberParameter thresholdHigh = new BoundedNumberParameter("Threshold for Seeds", 2, 3, 1, null);
    PluginParameter<Thresholder> thresholdLow = new PluginParameter<Thresholder>("Threshold for propagation", Thresholder.class, new ObjectCountThresholder(20), false);
    //NumberParameter thresholdLow = new BoundedNumberParameter("Threshold for propagation", 2, 2, 0, null);
    NumberParameter intensityThreshold = new BoundedNumberParameter("Intensity Threshold for Seeds", 2, 2, 0, null);
    Parameter[] parameters = new Parameter[]{scale, subtractBackgroundScale, minSpotSize, thresholdHigh,  thresholdLow, intensityThreshold};
    
    public MutationSegmenter setThresholdSeeds(double threshold) {
        this.thresholdHigh.setValue(threshold);
        return this;
    }
    
    public MutationSegmenter setThresholdPropagation(double threshold) {
        this.thresholdLow.setPlugin(new ConstantValue(threshold));
        return this;
    }
    
    public MutationSegmenter setIntensityThreshold(double threshold) {
        this.intensityThreshold.setValue(threshold);
        return this;
    }
    
    public MutationSegmenter setScale(double scale) {
        this.scale.setValue(scale);
        return this;
    }
    
    public MutationSegmenter setSubtractBackgroundScale(double subtractBackgroundScale) {
        this.subtractBackgroundScale.setValue(subtractBackgroundScale);
        return this;
    }
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent, scale.getValue().doubleValue(), subtractBackgroundScale.getValue().doubleValue(), minSpotSize.getValue().intValue(), thresholdHigh.getValue().doubleValue(), thresholdLow.instanciatePlugin(), intensityThreshold.getValue().doubleValue(), intermediateImages);
    }
    
    public static ObjectPopulation run(Image input, StructureObjectProcessing parent, double scale, double subtractBackgroundScale, int minSpotSize, double thresholdHigh , Thresholder thresholdLow, double intensityThreshold, List<Image> intermediateImages) {
        if (input.getSizeZ()>1) {
            // tester sur average, max, ou plan par plan
            ArrayList<Image> planes = input.splitZPlanes();
            ArrayList<ObjectPopulation> populations = new ArrayList<ObjectPopulation>(planes.size());
            for (Image plane : planes) {
                ObjectPopulation obj = runPlane(plane, parent, scale, subtractBackgroundScale, minSpotSize, thresholdHigh, thresholdLow, intensityThreshold, intermediateImages);
                //if (true) return obj;
                if (obj!=null && !obj.getObjects().isEmpty()) populations.add(obj);
            }
            if (populations.isEmpty()) return new ObjectPopulation(new ArrayList<Object3D>(0), planes.get(0));
            // combine: 
            ObjectPopulation pop = populations.remove(populations.size()-1);
            pop.combine(populations);
            return pop;
        } else return runPlane(input, parent, scale, subtractBackgroundScale, minSpotSize, thresholdHigh, thresholdLow, intensityThreshold, intermediateImages);
    }
    
    public static ObjectPopulation runPlane(Image input, StructureObjectProcessing parent, double scale, double subtractBackgroundScale, int minSpotSize, double thresholdSeeds, Thresholder segThresholdPropagation, double intensityThreshold, List<Image> intermediateImages) {
        if (input.getSizeZ()>1) throw new Error("MutationSegmenter: should be run on a 2D image");
        Image sub  = IJSubtractBackground.filter(input, subtractBackgroundScale, true, false, true, false);
        //Image sub = input;
        Image smooth = ImageFeatures.gaussianSmooth(sub, scale, scale, false);
        Image lap = ImageFeatures.getLaplacian(sub, scale, true, false).setName("laplacian: "+scale);
        //ImageOperations.divide(lap, smooth, lap);
        double thresholdPropagation = segThresholdPropagation.runThresholder(lap, parent);
        if (debug) logger.debug("mutation segmenter: propagation thld: {}", thresholdPropagation);
        //new ObjectCountThresholder().setMaxObjectNumber(10).runThresholder(smooth, null);
        //new ObjectCountThresholder().setMaxObjectNumber(10).runThresholder(lap, null); //-> seuil propagation ? 
        if (intermediateImages!=null) {
            intermediateImages.add(input.setName("input"));
            intermediateImages.add(smooth.setName("smooth"));
            intermediateImages.add(sub.setName("sub"));
            intermediateImages.add(lap);
        }
        ImageByte seeds = Filters.localExtrema(lap, null, true, thresholdSeeds, Filters.getNeighborhood(scale, scale, input));
        for (int z = 0; z<seeds.getSizeZ(); ++z) {
            for (int xy = 0; xy<seeds.getSizeXY(); ++xy) {
                if (seeds.insideMask(xy, z) && smooth.getPixel(xy, 0)<intensityThreshold) seeds.setPixel(xy, z, 0);
            }
        }
        ObjectPopulation seedPop = new ObjectPopulation(seeds, false);
        //seedPop.filter(new Overlap(seedsHess, 1.5));
        //seedPop.filter(new Or(new ObjectPopulation.GaussianFit(norm, 3, 3, 5, 0.2, 0.010, 6), new MeanIntensity(-0.2, false, hess)));
        ObjectPopulation pop =  watershed(lap, parent.getMask(), seedPop.getObjects(), true, new ThresholdPropagationOnWatershedMap(thresholdPropagation), new SizeFusionCriterion(minSpotSize), false);
        pop.filter(new ObjectPopulation.RemoveFlatObjects(input));
        pop.filter(new ObjectPopulation.Size().setMin(minSpotSize));
        return pop;
    }
    
    

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
