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
public class SpotFluo2D5 implements Segmenter {
    public static boolean debug = false;
    public static boolean displayImages = false;
    NumberParameter smoothRadius = new BoundedNumberParameter("Smooth Radius", 1, 1.5, 0, 10);
    NumberParameter laplacianRadius = new BoundedNumberParameter("Laplacian Radius", 1, 1.5, 1, 5);
    NumberParameter minSpotSize = new BoundedNumberParameter("Min. Spot Size (Voxel)", 0, 8, 1, null);
    NumberParameter thresholdHigh = new BoundedNumberParameter("Threshold for Seeds", 1, 7, 1, null);
    NumberParameter thresholdSeedsHess = new BoundedNumberParameter("Threshold for Seeds (hessian", 2, -0.2, -3, 0);
    NumberParameter thresholdLow = new BoundedNumberParameter("Threshold for propagation", 1, 5, 1, null);
    Parameter[] parameters = new Parameter[]{smoothRadius, laplacianRadius,minSpotSize, thresholdHigh, thresholdSeedsHess, thresholdLow };
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent.getMask(), smoothRadius.getValue().doubleValue(), laplacianRadius.getValue().doubleValue(), minSpotSize.getValue().intValue(), thresholdHigh.getValue().doubleValue(), thresholdSeedsHess.getValue().doubleValue(), thresholdLow.getValue().doubleValue(), null);
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask, double smoothRadius, double laplacianRadius, int minSpotSize, double thresholdHigh, double thresholdSeedHess, double thresholdLow, ArrayList<Image> intermediateImages) {
        if (input.getSizeZ()>1) {
            // tester sur average, max, ou plan par plan
            ArrayList<Image> planes = input.splitZPlanes();
            ArrayList<ObjectPopulation> populations = new ArrayList<ObjectPopulation>(planes.size());
            for (Image plane : planes) {
                ObjectPopulation obj = runPlane(plane, mask, smoothRadius, laplacianRadius, minSpotSize, thresholdHigh, thresholdSeedHess, thresholdLow, intermediateImages);
                //if (true) return obj;
                if (obj!=null && !obj.getObjects().isEmpty()) populations.add(obj);
            }
            if (populations.isEmpty()) return new ObjectPopulation(new ArrayList<Object3D>(0), planes.get(0));
            // combine: 
            ObjectPopulation pop = populations.remove(populations.size()-1);
            pop.combine(populations);
            return pop;
        } else return runPlane(input, mask, smoothRadius, laplacianRadius, minSpotSize, thresholdHigh, thresholdSeedHess, thresholdLow, intermediateImages);
        
    }
    
    public static ObjectPopulation runPlane(Image input, ImageMask mask, double smoothRadius, double laplacianRadius, int minSpotSize, double thresholdSeeds, double thresholdSeedsHess, double thresholdLow, ArrayList<Image> intermediateImages) {
        IJImageDisplayer disp = debug?new IJImageDisplayer():null;
        double hessianRadius = 2;
        
        //Image smoothed = ImageFeatures.gaussianSmooth(input, smoothRadius, smoothRadius, false).setName("smoothed");
        Image smoothed = Filters.median(input, new ImageFloat("", 0, 0, 0), Filters.getNeighborhood(smoothRadius, smoothRadius, input));
        ImageFloat bckg = ImageFeatures.gaussianSmooth(input, 10, 10, false);
        //Image norm = ImageOperations.addImage(input, bckg, null, -1).setName("normalized");
        
        //Image norm = IJFFTBandPass.bandPass(input.duplicate(""), 1, 10).setName("bandPass filter");
        //Image norm = Filters.tophat(smoothed, null, Filters.getNeighborhood(5, 5, input));
        
        
        smoothed = ImageOperations.addImage(smoothed, bckg, bckg, -1).setName("smoothed");
        
        Image contrasted = ImageFeatures.getLaplacian(smoothed, laplacianRadius, true, false);
        //contrasted = ImageFeatures.getLaplacian(contrasted, laplacianRadius, true, true);
        // scale lom according to background noise
        double[] meanSigma = new double[2];
        KappaSigma.kappaSigmaThreshold(smoothed, mask, 2, 3, meanSigma);
        //BackgroundFit.backgroundFit(smoothed, mask, 3, meanSigma);
        ImageOperations.affineOperation(smoothed, smoothed, 1d/meanSigma[1], -meanSigma[0]);
        
        KappaSigma.kappaSigmaThreshold(contrasted, mask, 2, 3, meanSigma);
        //BackgroundFit.backgroundFit(contrasted, mask, 3, meanSigma);
        ImageOperations.affineOperation(contrasted, contrasted, 1d/meanSigma[1], -meanSigma[0]);
        
        
        
        //KappaSigma.kappaSigmaThreshold(norm, mask, 2, 3, meanSigma);
        //ImageOperations.affineOperation(norm, norm, 1d/meanSigma[1], -meanSigma[0]);
        //
        //Image[] hessMaxDet = ImageFeatures.getHessianMaxAndDeterminant(smoothed, hessianRadius, false); hessMaxDet[0].setName("hessian"); hessMaxDet[1].setName("hessianDet");
        Image hess = ImageFeatures.getHessian(smoothed, hessianRadius, false)[0].setName("hessian max");
        //Image grad = ImageFeatures.getGradientMagnitude(lom, hessianRadius, false);
        if (displayImages) {
            disp.showImage(input.setName("input"));
            //disp.showImage(lom.setName("laplacian"));
            disp.showImage(hess);
            disp.showImage(smoothed);
        }
        ImageByte seeds = Filters.localExtrema(contrasted, null, true, thresholdSeeds, Filters.getNeighborhood(1, 1, input));
        ImageByte seedsHess = Filters.localExtrema(hess, null, false, thresholdSeedsHess, Filters.getNeighborhood(1, 1, input));
        if (intermediateImages!=null) {
            intermediateImages.add(smoothed);
            intermediateImages.add(contrasted);
            intermediateImages.add(hess);
            intermediateImages.add(seeds);
            intermediateImages.add(seedsHess);
        }
        
        ObjectPopulation seedPop = new ObjectPopulation(seeds, false);
        seedPop.filter(new Overlap(seedsHess, 1.5));
        //seedPop.filter(new Or(new ObjectPopulation.GaussianFit(norm, 3, 3, 5, 0.2, 0.010, 6), new MeanIntensity(-0.2, false, hess)));
        
        ObjectPopulation pop =  watershed(hess, mask, seedPop.getObjects(), false, new MultiplePropagationCriteria(new ThresholdPropagationOnWatershedMap(0), new ThresholdPropagation(contrasted, thresholdLow, true)), new SizeFusionCriterion(minSpotSize));
        pop.filter(new ObjectPopulation.RemoveFlatObjects(input));
        pop.filter(new ObjectPopulation.Size().setMin(minSpotSize));
        return pop;
    }
    
    

    public Parameter[] getParameters() {
        return parameters;
    }
    
}