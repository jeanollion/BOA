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
import plugins.plugins.thresholders.KappaSigma;
import processing.Filters;
import processing.ImageFeatures;
import processing.WatershedTransform;

/**
 *
 * @author jollion
 */
public class SpotFluo2D5 implements Segmenter {
    public static boolean debug = false;
    NumberParameter smoothRadius = new BoundedNumberParameter("Smooth Radius", 1, 2, 0, 10);
    NumberParameter laplacianRadius = new BoundedNumberParameter("Laplacian Radius", 1, 2, 1, 5);
    NumberParameter minSpotSize = new BoundedNumberParameter("Min. Spot Size (Voxel)", 0, 5, 1, null);
    NumberParameter thresholdSigma = new BoundedNumberParameter("Sigma Threshold Factor for Seeds", 1, 6, 1, null);
    NumberParameter thresholdSigmaLow = new BoundedNumberParameter("Sigma Threshold Factor for propagation", 1, 4.5, 1, null);
    Parameter[] parameters = new Parameter[]{smoothRadius, laplacianRadius,minSpotSize, thresholdSigma, thresholdSigmaLow };
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent.getMask(), smoothRadius.getValue().doubleValue(), laplacianRadius.getValue().doubleValue(), minSpotSize.getValue().intValue(), thresholdSigma.getValue().doubleValue(), thresholdSigmaLow.getValue().doubleValue());
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask, double smoothRadius, double laplacianRadius, int minSpotSize, double thresholdSigma, double thresholdSigmaLow) {
        // tester sur average, max, ou plan par plan
        /*ArrayList<Image> planes = input.splitZPlanes();
        for (Image plane : planes) {
            ObjectPopulation obj = runPlane(plane, mask);
        }
        */
        Image avg = ImageOperations.meanZProjection(input);
        return runPlane(avg, mask, smoothRadius, laplacianRadius, minSpotSize, thresholdSigma, thresholdSigmaLow);
    }
    
    public static ObjectPopulation runPlane(Image input, ImageMask mask, double smoothRadius, double laplacianRadius, int minSpotSize, double thresholdSigma, double thresholdSigmaLow) {
        IJImageDisplayer disp = debug?new IJImageDisplayer():null;
        double hessianRadius = 1;
        Image smoothed = Filters.median(input, null, Filters.getNeighborhood(smoothRadius, smoothRadius, input));
        Image lom = ImageFeatures.getLaplacian(smoothed, laplacianRadius, true, false);
        // scale lom according to background noise
        double[] meanSigma = new double[2];
        KappaSigma.kappaSigmaThreshold(lom, mask, 3, 2, meanSigma);
        ImageOperations.affineOperation(lom, lom, 1d/meanSigma[1], -meanSigma[0]);
        logger.debug("kappaSigma: mean: {}, sigma: {}", meanSigma[0], meanSigma[1]);
        //Image[] hessMaxDet = ImageFeatures.getHessianMaxAndDeterminant(lom, hessianRadius, false);
        if (debug) {
            disp.showImage(input.setName("input"));
            disp.showImage(smoothed.setName("smoothed"));
            disp.showImage(lom.setName("laplacian"));
            //disp.showImage(hessMaxDet[0].setName("hessian"));
            //disp.showImage(hessMaxDet[1].setName("hessianDet"));
        }
        ImageByte seeds = Filters.localExtrema(lom, null, true, thresholdSigma, Filters.getNeighborhood(1, 1, input));
        ObjectPopulation pop =  WatershedTransform.watershed(lom, mask, seeds, true, new WatershedTransform.ThresholdPropagation(lom, thresholdSigmaLow, true), new WatershedTransform.SizeFusionCriterion(minSpotSize));
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
