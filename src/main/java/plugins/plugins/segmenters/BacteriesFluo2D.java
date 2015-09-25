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
import image.ImageMask;
import image.ImageOperations;
import image.ImageShort;
import java.util.ArrayList;
import java.util.Iterator;
import measurement.BasicMeasurements;
import plugins.Segmenter;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.Filters;
import processing.ImageFeatures;
import processing.WatershedTransform;
import processing.neighborhood.EllipsoidalNeighborhood;
import utils.ArrayUtil;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class BacteriesFluo2D implements Segmenter {
    NumberParameter size = new BoundedNumberParameter("Minimal Object Dimension", 0, 15, 1, null);
    final static double gradientScale = 1;
    final static double medianScale = 2; // a mettre dans les prefilters
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        double thld = IJAutoThresholder.runThresholder(parent.getParent().getRawImage(structureIdx), parent.getParent().getMask(), AutoThresholder.Method.Otsu);
        
        return run(input, parent.getMask(), size.getValue().doubleValue(), thld);
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask, double minObjectDimension, double splitThld) {
        splitThld = 0.03;
        ImageDisplayer disp = new IJImageDisplayer();
        //Image filtered = Filters.median(input, new ImageFloat("", 0, 0, 0), Filters.getNeighborhood(2, 2, input));
        ImageFloat filtered = ImageFeatures.differenceOfGaussians(input, 2, 15, 1, true, false).setName("filtered");
        ImageOperations.normalize(filtered, mask, filtered);
        Image diff = ImageFeatures.getDerivative(filtered, 2, 0, 1, 0, false).setName("diff");
        disp.showImage(filtered);
        disp.showImage(diff);
        
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
        ArrayList<int[]> xBounds = new ArrayList<int[]>(min.length);
        int lastMaxIdx = firstMaxIdx;
        for (int i = firstMaxIdx; i<min.length; ++i) {
            if (projDiff[min[i]]*projDiff[regMax[i+1]]<0 // changement de signe
                    && (projDiff[regMax[i+1]]-projDiff[min[i]])>=splitThld // critère de séparation
                    ) {
                xBounds.add(new int[]{regMax[lastMaxIdx], min[i]});
                logger.debug("add separation: min: {}, max: {}", regMax[lastMaxIdx], min[i]);
                lastMaxIdx=i+1;
            }
        }
        int end = ArrayUtil.min(projDiff, regMax[lastMaxIdx]+1, projDiff.length);
        if (end>regMax[lastMaxIdx]) {
            //look for last local max < min
            int lastLocalMax=lastMaxIdx;
            while(lastLocalMax<regMax.length-1 && regMax[lastLocalMax+1]<end) lastLocalMax++;
            if ((projDiff[regMax[lastLocalMax]]-projDiff[end])>splitThld) {
                xBounds.add(new int[]{regMax[lastLocalMax], end});
                logger.debug("add last bactery: min: {}, max: {}", regMax[lastLocalMax], end);
            }
        }
        a faire: dans chaque sous masque: fit aux donnée (depuis le max des intensités dans le masque)
        
        
        return null;
        /*Image[] structure = ImageFeatures.structureTransform(sub, medianScale, gradientScale);
        Image gradient = structure[0];
        //Image gradient = ImageFeatures.getGradientMagnitude(filtered, gradientScale, false);
        ImageByte seeds = Filters.localExtrema(gradient, null, false, Filters.getNeighborhood(minObjectDimension/2.0, 1, input));
        //ImageByte seeds = Filters.localExtrema(filtered, null, false, Filters.getNeighborhood(minObjectDimension/2.0, 1, input));
        
        disp.showImage(filtered.setName("filtered"));
        disp.showImage(structure[0].setName("structure 0"));
        disp.showImage(seeds.setName("seeds"));
        ObjectPopulation pop = WatershedTransform.watershed(gradient, mask, seeds, false);
        Iterator<Object3D> it = pop.getObjects().iterator();
        while(it.hasNext()) {
            Object3D o = it.next();
            if (BasicMeasurements.getMeanValue(o, input)<thld) it.remove();
        }
        pop.relabel();
        return pop;*/
        
    }

    public Parameter[] getParameters() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public boolean does3D() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
