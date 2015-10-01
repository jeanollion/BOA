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
import boa.gui.imageInteraction.ImageDisplayer;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObjectProcessing;
import ij.process.AutoThresholder;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageOperations;
import java.util.ArrayList;
import plugins.Segmenter;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.Filters;
import processing.ImageFeatures;
import processing.WatershedTransform;
import utils.ArrayUtil;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class BacteriesFluo2D implements Segmenter {
    NumberParameter split = new BoundedNumberParameter("Split Threshold", 2, 0.3, 0, 0);
    NumberParameter size = new BoundedNumberParameter("Minimal Object Dimension", 0, 15, 1, null);
    Parameter[] parameters = new Parameter[]{split};
    final static double gradientScale = 1;
    final static double medianScale = 2; // a mettre dans les prefilters
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        
        return run(input, parent.getMask(), size.getValue().doubleValue(), split.getValue().doubleValue());
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask, double minObjectDimension, double splitThld) {
        //splitThld = 0.3;
        int derScale = 2;
        ImageDisplayer disp = new IJImageDisplayer();
        //ImageFloat filtered = ImageFeatures.differenceOfGaussians(input, 2, 4, 1, false, false).setName("filtered");
        ImageFloat filtered = ImageFeatures.LoG(input, 4, 4);
        // get precise X bounds to get Y-projection values reproductibles
        float[] projX = ImageOperations.meanProjection(filtered, ImageOperations.Axis.X, null);
        int xMax = ArrayUtil.max(projX);
        double thld = 0.05 * projX[xMax];
        // limit1 = lastValue>thld
        int xLeft = xMax;
        while (xLeft>0 && projX[xLeft-1]>=thld){--xLeft;}
        // limit2 = lastValue>thld
        int xRight = xMax;
        while (xRight<projX.length-1 && projX[xRight+1]>=thld){++xRight;}
        logger.debug("find xbounds: xMax: {}, right: {}, left: {}", xMax, xLeft, xRight);
        BoundingBox projBounds = new BoundingBox(xLeft, xRight, 0, input.getSizeY()-1, 0, input.getSizeZ()-1);
        
        // robustesse du paramètre de segmentation: normalisation
        // idée 1: normalisation des valeurs avant dérivation
        //normalisation par l'écart-type
        //ImageOperations.normalize(filtered, mask, filtered); // normalisation par la valeur moyenne
        //idée 2: normalisation par la valeur maximale (ou % de pixels brillant) de la derivée en X (car en Y on n'est pas sur d'avoir un max) 
        Image diffX = ImageFeatures.getDerivative(filtered, derScale, 1, 0, 0, false).setName("diffX").crop(projBounds); 
        double norm = ImageOperations.getPercentile(diffX, 0.01d, null);
        logger.debug("diffX max: {}, percentile: {}", diffX.getMinAndMax(null)[1], norm);
        ImageFloat diff = ImageFeatures.getDerivative(filtered, derScale, 0, 1, 0, false).setName("diff2"); 
        ImageOperations.multiply(diff, diff, 1d/norm);
        disp.showImage(filtered);
        //disp.showImage(diff);
        //disp.showImage(diffX);
        
        
        float[] projValues = ImageOperations.meanProjection(filtered, ImageOperations.Axis.Y, projBounds);
        float[] projDiff = ImageOperations.meanProjection(diff, ImageOperations.Axis.Y, projBounds);
        Utils.plotProfile("values", projValues);
        Utils.plotProfile("diff", projDiff);
        int[] regMax = ArrayUtil.getRegionalExtrema(projDiff, 3, true);
        logger.debug("reg max diff2: {}", regMax);
        int[] min = new int[regMax.length-1];
        for (int i = 1; i<regMax.length; ++i) min[i-1] = ArrayUtil.min(projDiff, regMax[i-1], regMax[i]);
        logger.debug("loc min diff2: {}", min);
        
        //first max index: values & diff > 0 
        int firstMaxIdx = 0; 
        while(firstMaxIdx<regMax.length-1 && projDiff[regMax[firstMaxIdx]]==0 || projValues[regMax[firstMaxIdx]]==0) ++firstMaxIdx;
        
        //last min index: last max >0 & 
        //int lastMinIdx = min.length-1;
        //while (lastMinIdx>0 && !(projDiff[min[lastMinIdx]]<0 && projDiff[regMax[lastMinIdx]]>0 && projValues[regMax[lastMinIdx]]>0)) lastMinIdx--;
        //logger.debug("firstMax idx: {}, last min idx: {}", firstMaxIdx, lastMinIdx);
        ArrayList<int[]> yBounds = new ArrayList<int[]>(min.length);
        int lastMaxIdx = firstMaxIdx;
        for (int i = firstMaxIdx; i<min.length; ++i) {
            if (projDiff[min[i]]*projDiff[regMax[i+1]]<0 // changement de signe
                    && (projDiff[regMax[i+1]]-projDiff[min[i]])>=splitThld // critère de séparation
                    ) {
                int lower;
                if (yBounds.isEmpty()) lower = ArrayUtil.getFirstOccurence(projDiff, regMax[lastMaxIdx], lastMaxIdx>0?min[lastMaxIdx-1]:0, 0, false, true);
                //else lower = min[lastMaxIdx-1];
                else lower = ArrayUtil.min(projValues, min[lastMaxIdx-1], regMax[lastMaxIdx]);
                //int higher = regMax[i+1];
                int higher = ArrayUtil.min(projValues, min[i], regMax[i+1]);
                //int higher = ArrayUtil.getFirstOccurence(projDiff, min[i], regMax[i+1], 0, true, true);
                yBounds.add(new int[]{lower, higher});
                logger.debug("add separation: min: {}, max: {}", lower, higher);
                lastMaxIdx=i+1;
            }
        }
        int end = ArrayUtil.min(projDiff, regMax[lastMaxIdx]+1, projDiff.length);
        if (end>regMax[lastMaxIdx]) {
            //look for last local max < min
            int lastLocalMax=lastMaxIdx;
            while(lastLocalMax<regMax.length-1 && regMax[lastLocalMax+1]<end) lastLocalMax++;
            if ((projDiff[regMax[lastLocalMax]]-projDiff[end])>splitThld) {
                //int lower = min[lastMaxIdx-1];
                int lower = ArrayUtil.min(projValues, min[lastMaxIdx-1], regMax[lastMaxIdx]);
                int higher = ArrayUtil.getFirstOccurence(projDiff, end, projDiff.length, 0, true, true);
                yBounds.add(new int[]{lower, higher});
                logger.debug("add last bactery: min: {}, max: {}", lower, higher);
            }
        }
        //a faire: dans chaque sous masque: fit aux donnée (depuis le max des intensités dans le masque)
        //Image structure = ImageFeatures.structureTransform(input, 2, 1, false)[0];
        ArrayList<Object3D> objects = new ArrayList<Object3D>(yBounds.size());
        boolean display = false;
        int count=1;
        for (int[] yb : yBounds) {
            BoundingBox b = new BoundingBox(0, input.getSizeX()-1, yb[0], yb[1], 0, input.getSizeZ()-1);
            Image subImage = filtered.crop(b);
            if (display) {
                logger.debug("crop bounds : {}", b);
                //disp.showImage(subImage.setName("sub image"));
                //disp.showImage(subImageFit.setName("fit image"));
                display=false;
            }
            ImageInteger bin = ImageOperations.threshold(subImage, IJAutoThresholder.runThresholder(subImage, null, AutoThresholder.Method.Otsu, 1), true, false);
            Object3D[] obs =  ImageLabeller.labelImage(bin);
            if (obs.length>0) {
                int idx = 0;
                if (obs.length>1) { //get object of maximal size  
                    for (int i = 1; i<obs.length; ++i) if (obs[i].getVoxels().size()>obs[idx].getVoxels().size()) idx=i;
                    logger.warn("Bacteries Fluo Adjust Segmentation: {} objects found in {}th position", obs.length, count-1);
                }
                objects.add(obs[idx].setLabel(count++).addOffset(b));
            } else logger.warn("Bacteries Fluo Adjust Segmentation: no object found in {}th position", count-1);
            //objects.add(FitEdges.run(subImage, subImageFit, null, IJAutoThresholder.runThresholder(subImage, null, AutoThresholder.Method.Otsu)).addOffset(b));
        }
        ObjectPopulation pop = new ObjectPopulation(objects, input);
        //disp.showImage(pop.getLabelImage().setName("labels"));        
        return pop;        
    }

    public static ObjectPopulation run(Image input, ImageMask mask, double logScale, int minSize) {
        ImageFloat log = ImageFeatures.LoG(input, logScale, logScale);
        ImageByte seeds = Filters.localExtrema(log, null, true, Filters.getNeighborhood(minSize, minSize, input));
        ObjectPopulation pop = WatershedTransform.watershed(log, mask, seeds, true);
        return pop;
    }
    
    
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
