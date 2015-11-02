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
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObjectProcessing;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageMask;
import image.ImageOperations;
import image.ImageShort;
import java.util.ArrayList;
import plugins.Segmenter;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.thresholders.KappaSigma;
import processing.Filters;
import processing.ImageFeatures;
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
    NumberParameter smoothRadius = new BoundedNumberParameter("Smooth Radius", 1, 2, 0, 10);
    NumberParameter laplacianRadius = new BoundedNumberParameter("Laplacian Radius", 1, 2, 1, 5);
    NumberParameter minSpotSize = new BoundedNumberParameter("Min. Spot Size (Voxel)", 0, 5, 1, null);
    NumberParameter thresholdHigh = new BoundedNumberParameter("Threshold for Seeds", 1, 6, 1, null);
    NumberParameter thresholdLow = new BoundedNumberParameter("Threshold for propagation", 1, 4.5, 1, null);
    Parameter[] parameters = new Parameter[]{smoothRadius, laplacianRadius,minSpotSize, thresholdHigh, thresholdLow };
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent.getMask(), smoothRadius.getValue().doubleValue(), laplacianRadius.getValue().doubleValue(), minSpotSize.getValue().intValue(), thresholdHigh.getValue().doubleValue(), thresholdLow.getValue().doubleValue(), null);
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask, double smoothRadius, double laplacianRadius, int minSpotSize, double thresholdHigh, double thresholdLow, ArrayList<Image> intermediateImages) {
        // tester sur average, max, ou plan par plan
        /*ArrayList<Image> planes = input.splitZPlanes();
        for (Image plane : planes) {
            ObjectPopulation obj = runPlane(plane, mask);
        }
        */
        // autre stratégies: plan par plan puis choix entre les spots qui se recouvrent
        // smooth puis projection maximale
        Image avg = ImageOperations.meanZProjection(input);
        return runPlane(avg, mask, smoothRadius, laplacianRadius, minSpotSize, thresholdHigh, thresholdLow, intermediateImages);
    }
    
    public static ObjectPopulation runPlane(Image input, ImageMask mask, double smoothRadius, double laplacianRadius, int minSpotSize, double thresholdSeeds, double thresholdLow, ArrayList<Image> intermediateImages) {
        IJImageDisplayer disp = debug?new IJImageDisplayer():null;
        double hessianRadius = 2;
        // problem des pixels aveugles: appliquer un median 1 (ou 2) avant translation & rotation ? 
        //IJSubtractBackground.filter(input, 5, false, false, false, false);
        Image smoothed = Filters.median(input, null, Filters.getNeighborhood(smoothRadius, smoothRadius, input));
        //Image smoothed = ImageFeatures.gaussianSmooth(input, smoothRadius, smoothRadius, false);
        ImageFloat bckg = ImageFeatures.gaussianSmooth(input, 10, 10, false);
        smoothed = ImageOperations.addImage(smoothed, bckg, bckg, -1).setName("smoothed");
        
        //Image contrasted = ImageFeatures.getLaplacian(smoothed, laplacianRadius, true, false);
        bckg = ImageFeatures.gaussianSmooth(smoothed, 4, 1, false);
        Image contrasted = ImageOperations.addImage(smoothed, bckg, bckg, -1).setName("DoG");
        Image topHat = Filters.tophat(smoothed, null, Filters.getNeighborhood(4, 3, input));
        //contrasted = ImageFeatures.getLaplacian(contrasted, laplacianRadius, true, true);
        // scale lom according to background noise
        double[] meanSigma = new double[2];
        KappaSigma.kappaSigmaThreshold(contrasted, mask, 2, 3, meanSigma);
        ImageOperations.affineOperation(contrasted, contrasted, 1d/meanSigma[1], -meanSigma[0]);
        KappaSigma.kappaSigmaThreshold(smoothed, mask, 2, 3, meanSigma);
        ImageOperations.affineOperation(smoothed, smoothed, 1d/meanSigma[1], -meanSigma[0]);
        KappaSigma.kappaSigmaThreshold(topHat, mask, 2, 3, meanSigma);
        ImageOperations.affineOperation(topHat, topHat, 1d/meanSigma[1], -meanSigma[0]);
        
        //logger.debug("kappaSigma: mean: {}, sigma: {}", meanSigma[0], meanSigma[1]);
        Image[] hessMaxDet = ImageFeatures.getHessianMaxAndDeterminant(smoothed, hessianRadius, false); hessMaxDet[0].setName("hessian"); hessMaxDet[1].setName("hessianDet");
        Image detPerIntensity = ImageOperations.multiply(contrasted, hessMaxDet[1], null).setName("det x int");
        //Image grad = ImageFeatures.getGradientMagnitude(lom, hessianRadius, false);
        if (displayImages) {
            disp.showImage(input.setName("input"));
            //disp.showImage(smoothed.setName("smoothed"));
            disp.showImage(contrasted.setName("topHat"));
            //disp.showImage(lom.setName("laplacian"));
            disp.showImage(hessMaxDet[0]);
            disp.showImage(hessMaxDet[1]);
            disp.showImage(detPerIntensity);
            //disp.showImage(grad);
        }
        if (intermediateImages!=null) {
            intermediateImages.add(smoothed);
            intermediateImages.add(contrasted);
            intermediateImages.add(topHat);
            intermediateImages.add(hessMaxDet[0]);
            intermediateImages.add(hessMaxDet[1]);
            
            //intermediateImages.add(detPerIntensity);
        }
        ImageByte seeds = Filters.localExtrema(detPerIntensity, null, true, thresholdSeeds, Filters.getNeighborhood(1, 1, input));
        
        ObjectPopulation pop =  watershed(hessMaxDet[0], mask, seeds, false, new MultiplePropagationCriteria(new ThresholdPropagationOnWatershedMap(0), new ThresholdPropagation(contrasted, thresholdLow, true)), new SizeFusionCriterion(minSpotSize));
        //ObjectPopulation pop =  WatershedTransform.watershed(grad, mask, seeds, false, new WatershedTransform.MonotonalPropagation(), new WatershedTransform.SizeFusionCriterion(minSpotSize));
        
        pop.filter(new ObjectPopulation.RemoveFlatObjects(input));
        pop.filter(new ObjectPopulation.Size().setMin(minSpotSize));
        return pop;
    }
    
    

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
