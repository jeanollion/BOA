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
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.ObjectPopulation.MeanIntensity;
import dataStructure.objects.ObjectPopulation.Or;
import dataStructure.objects.ObjectPopulation.Overlap;
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
import java.util.Map;
import java.util.Map.Entry;
import jj2000.j2k.util.ArrayUtil;
import plugins.Segmenter;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.thresholders.BackgroundFit;
import plugins.plugins.thresholders.KappaSigma;
import processing.Filters;
import processing.GaussianFit;
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
    public static boolean debug = false;
    public static boolean displayImages = false;
    NumberParameter scale = new BoundedNumberParameter("Scale", 1, 2.5, 1.5, 5);
    NumberParameter subtractBackgroundScale = new BoundedNumberParameter("Subtract Background Scale", 1, 4, 3, 20);
    NumberParameter minSpotSize = new BoundedNumberParameter("Min. Spot Size (Voxels)", 0, 5, 1, null);
    NumberParameter thresholdHigh = new BoundedNumberParameter("Threshold for Seeds", 2, 10, 1, null);
    NumberParameter thresholdLow = new BoundedNumberParameter("Threshold for propagation", 2, 5, 0, null);
    
    Parameter[] parameters = new Parameter[]{scale, subtractBackgroundScale, minSpotSize, thresholdHigh,  thresholdLow};
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent.getMask(), scale.getValue().doubleValue(), subtractBackgroundScale.getValue().doubleValue(), minSpotSize.getValue().intValue(), thresholdHigh.getValue().doubleValue(), thresholdLow.getValue().doubleValue(), null);
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask, double scale, double subtractBackgroundScale, int minSpotSize, double thresholdHigh , double thresholdLow, ArrayList<Image> intermediateImages) {
        if (input.getSizeZ()>1) {
            // tester sur average, max, ou plan par plan
            ArrayList<Image> planes = input.splitZPlanes();
            ArrayList<ObjectPopulation> populations = new ArrayList<ObjectPopulation>(planes.size());
            for (Image plane : planes) {
                ObjectPopulation obj = runPlane(plane, mask, scale, subtractBackgroundScale, minSpotSize, thresholdHigh, thresholdLow, intermediateImages);
                //if (true) return obj;
                if (obj!=null && !obj.getObjects().isEmpty()) populations.add(obj);
            }
            if (populations.isEmpty()) return new ObjectPopulation(new ArrayList<Object3D>(0), planes.get(0));
            // combine: 
            ObjectPopulation pop = populations.remove(populations.size()-1);
            pop.combine(populations);
            return pop;
        } else return runPlane(input, mask, scale, subtractBackgroundScale, minSpotSize, thresholdHigh, thresholdLow, intermediateImages);
    }
    
    public static ObjectPopulation runPlane(Image input, ImageMask mask, double scale, double subtractBackgroundScale, int minSpotSize, double thresholdSeeds, double thresholdPropagation, ArrayList<Image> intermediateImages) {
        if (input.getSizeZ()>1) throw new Error("MutationSegmenter: should be run on a 2D image");
        IJImageDisplayer disp = debug?new IJImageDisplayer():null;
        input = IJSubtractBackground.filter(input, subtractBackgroundScale, false, false, true, false);
        //Image lap = ImageFeatures.getLaplacian(input, scale, true, false).setName("laplacian: "+scale);
        //Image lapSP = ImageFeatures.getScaleSpaceLaplacian(input, new double[]{2, 4, 6, 8, 10});
        //Image hessSP = ImageFeatures.getScaleSpaceHessianDet(input, new double[]{2, 4, 6, 8, 10});
        Image[] hess = ImageFeatures.getHessianMaxAndDeterminant(input, scale, false);
        Image det = hess[1].setName("hessian det");
        Image hessMax = hess[0].setName("hessian max");
        if (displayImages) {
            disp.showImage(input.setName("input"));
            //disp.showImage(lom.setName("laplacian"));
            disp.showImage(det);
            disp.showImage(hessMax);
        }
        ImageByte seeds = Filters.localExtrema(det, null, true, thresholdSeeds, Filters.getNeighborhood(scale, scale, input));
        
        if (intermediateImages!=null) {
            intermediateImages.add(det);
            intermediateImages.add(hessMax);
            //intermediateImages.add(lap);
            intermediateImages.add(input);
            //intermediateImages.add(ImageFeatures.getScaleSpaceHessianDet(input, new double[]{2.5, 3, 3.5, 4, 4.5, 5, 5.5}).setName("scale space hess det"));
            intermediateImages.add(ImageFeatures.getScaleSpaceLaplacian(input, new double[]{2, 2.5, 3, 4, 5, 6}).setName("scale space laplacian"));
        }
        
        ObjectPopulation seedPop = new ObjectPopulation(seeds, false);
        //seedPop.filter(new Overlap(seedsHess, 1.5));
        //seedPop.filter(new Or(new ObjectPopulation.GaussianFit(norm, 3, 3, 5, 0.2, 0.010, 6), new MeanIntensity(-0.2, false, hess)));
        ObjectPopulation pop =  watershed(det, mask, seedPop.getObjects(), true, new MultiplePropagationCriteria(new ThresholdPropagationOnWatershedMap(thresholdPropagation), new MonotonalPropagation(), new ThresholdPropagation(hessMax, 0, false)), new SizeFusionCriterion(minSpotSize));
        pop.filter(new ObjectPopulation.RemoveFlatObjects(input));
        pop.filter(new ObjectPopulation.Size().setMin(minSpotSize));
        return pop;
    }
    
    

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
