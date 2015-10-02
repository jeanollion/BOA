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
import dataStructure.objects.Voxel;
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
import java.util.Iterator;
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
    NumberParameter split = new BoundedNumberParameter("Split Threshold", 2, 0.3, 0, 1);
    NumberParameter bcg = new BoundedNumberParameter("Background Threshold", 4, 0.0001, 0.00005, 0.005);
    NumberParameter size = new BoundedNumberParameter("Minimal Object Dimension", 0, 15, 1, null);
    Parameter[] parameters = new Parameter[]{split};
    final static double gradientScale = 1;
    final static double medianScale = 2; // a mettre dans les prefilters
    public static boolean debug=false;
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        
        return run(input, parent.getMask(), size.getValue().doubleValue(), split.getValue().doubleValue(), bcg.getValue().doubleValue());
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask, double minObjectDimension, double splitThld, double backgroundThld) {
        int derScale = 2;
        int logScale = 4;
        
        //ImageFloat filtered = ImageFeatures.differenceOfGaussians(input, 2, 4, 1, false, false).setName("filtered");
        ImageFloat filtered = ImageFeatures.LoG(input, logScale, logScale*input.getScaleXY()/input.getScaleZ());
        Image wsMap1 = ImageFeatures.getGradientMagnitude(filtered, 1, false);
        
        // get precise X bounds to get Y-projection values reproductibles
        BoundingBox projBounds = getBounds(input, filtered, wsMap1, logScale+1);
        ArrayList<int[]> yBounds = analyseYDiffProfile(filtered, derScale, wsMap1, projBounds, splitThld, backgroundThld);
        
        // Get foreground & background seeds
        ImageByte seeds = new ImageByte("seeds", input);
        ArrayList<Voxel> seedList = new ArrayList<Voxel>(yBounds.size());
        ArrayList<Voxel> bcgSeedList = new ArrayList<Voxel>(yBounds.size()*2);
        for (int[] yb : yBounds) {
            // get global intensity maximum within the area
            Voxel seedMax = ImageOperations.getGlobalExtremum(filtered, new BoundingBox(0, input.getSizeX()-1, yb[0]+1, yb[1], 0, input.getSizeZ()-1), true);
            seeds.setPixel(seedMax.x, seedMax.y, seedMax.z, 1);
            seedList.add(seedMax);
            
            // get background seeds on each side of the area (min of wsMap)
            Voxel seedBg1 = ImageOperations.getGlobalExtremum(wsMap1, new BoundingBox(0, projBounds.getxMin(), yb[0]+1, yb[1], 0, input.getSizeZ()-1), false);
            bcgSeedList.add(seedBg1);
            seeds.setPixel(seedBg1.x, seedBg1.y, seedBg1.z, 1);
            Voxel seedBg2 = ImageOperations.getGlobalExtremum(wsMap1, new BoundingBox(projBounds.getxMax(), input.getSizeX()-1, yb[0]+1, yb[1], 0, input.getSizeZ()-1), false);
            seeds.setPixel(seedBg2.x, seedBg2.y, seedBg2.z, 1); 
            bcgSeedList.add(seedBg2);
        }
        
        if (debug) {
            ImageDisplayer disp = new IJImageDisplayer();
            disp.showImage(wsMap1);
            disp.showImage(filtered);
            disp.showImage(seeds.duplicate("seeds"));
        }
        
        // 1st Watershed to discriminate background & foreground
        ObjectPopulation pop = WatershedTransform.watershed(wsMap1, mask, seeds, false);
        // keep only objects that contains the seeds
        Iterator<Object3D> it = pop.getObjects().iterator();
        while(it.hasNext()) {
            Object3D o = it.next();
            Voxel seed=null;
            for (Voxel s : seedList) {
                if (o.getVoxels().contains(s)) {
                    seed = s;
                    break;
                }
            }
            if (seed!=null) seedList.remove(seed); // seeds present in one and only one foreground object
            else it.remove(); // if no seed in object -> background
        }
        pop.relabel();
        
        // re-run watershed with intensities, within the previous watershed mask, only with foreground seeds
        for (Voxel v : bcgSeedList) seeds.setPixel(v.x, v.y, v.z, 0);
        Image wsMap2 = filtered;
        pop = WatershedTransform.watershed(wsMap2, pop.getLabelImage(), seeds, true);
        return pop;
           
    }
    
    protected static BoundingBox getBounds(Image input, Image filtered, Image gradient, int margin) {
        
        float[] projX = ImageOperations.meanProjection(filtered, ImageOperations.Axis.X, null);
        int xMax = ArrayUtil.max(projX);
        
        float[] projXGrad = ImageOperations.meanProjection(gradient, ImageOperations.Axis.X, null);
        int maxLeft = ArrayUtil.max(projXGrad, 0, xMax);
        int maxRight = ArrayUtil.max(projXGrad, xMax+1, projXGrad.length);
        int xLeft = Math.max(0, maxLeft-margin);
        int xRight = Math.min(projX.length-1, maxRight+margin);
        /*
        // get Y bounds using difference of gaussian & threshold to 0
        Image dog = ImageFeatures.differenceOfGaussians(input, 4, (xRight-xLeft), input.getScaleZ()/input.getScaleXY(), false, false);
        new IJImageDisplayer().showImage(dog.setName("dog"));
        float[] projYDoG = ImageOperations.maxProjection(dog, ImageOperations.Axis.Y, null);
        Utils.plotProfile("projDOG", projYDoG);
        // first non negative value
        int yStart = ArrayUtil.getFirstOccurence(projYDoG, 0, projYDoG.length, 0, false, true);
        int yStop = ArrayUtil.getFirstOccurence(projYDoG, projYDoG.length-1, 0, 0, false, true); */
        int yStart=0;
        int yStop = input.getSizeY()-1;
        /*double thld = signalProportion * projX[xMax];
        // limit1 = lastValue>thld
        int xLeft = xMax;
        while (xLeft>0 && projX[xLeft-1]>=thld){--xLeft;}
        // limit2 = lastValue>thld
        int xRight = xMax;
        while (xRight<projX.length-1 && projX[xRight+1]>=thld){++xRight;}*/
        
        if (debug) logger.debug("find bounds: xMax: {}, right: {}, left: {}, yStart: {}, yStop: {}", xMax, xLeft, xRight, yStart, yStop);
        return new BoundingBox(xLeft, xRight, yStart, yStop, 0, filtered.getSizeZ()-1);
    }
    
    protected static ArrayList<int[]> analyseYDiffProfile(Image intensities, double derScale, Image grad, BoundingBox projBounds, double splitThld, double backgroundThld) {
        int yOffset = projBounds.getyMin();
        
        //Image grad = ImageFeatures.getGradientMagnitude(intensities, derScale, false); // utiliser le gradient calculé précédement?
        ImageFloat norm=new ImageFloat("grad max", projBounds.getSizeY(), ImageOperations.maxProjection(grad, ImageOperations.Axis.Y, projBounds));
        norm=ImageFeatures.gaussianSmooth(norm, projBounds.getSizeX()/2, projBounds.getSizeX()/2, true);
        
        float[] projValues = ImageOperations.meanProjection(intensities, ImageOperations.Axis.Y, projBounds);
        float maxValue = ArrayUtil.max(projValues);
        
        ImageFloat diffY = ImageFeatures.getDerivative(intensities, derScale, 0, 1, 0, false).setName("diffY"); 
        float[] projDiff = ImageOperations.meanProjection(diffY, ImageOperations.Axis.Y, projBounds);
        
        float[] projDiffNorm=new float[projDiff.length];   
        for (int y = 0; y<projDiff.length; ++y) if (norm.getPixel(y, 0, 0)>0) projDiffNorm[y]=projDiff[y]/norm.getPixel(y, 0, 0);
        if (debug) {
            Utils.plotProfile("values", projValues);
            Utils.plotProfile("diffNorm", projDiffNorm);
            Utils.plotProfile("norm", norm.getPixelArray()[0]);
            float[] projDiffNorm2=new float[projDiff.length]; 
            if (maxValue>0) for (int y = 0; y<projDiff.length; ++y) projDiffNorm2[y]=projDiff[y]/maxValue;
            Utils.plotProfile("diff", projDiffNorm2);
        }
        int[] regMax = ArrayUtil.getRegionalExtrema(projDiff, 3, true);
        logger.trace("reg max diff2: {}, offset: {}", regMax, yOffset);
        int[] min = new int[regMax.length-1];
        for (int i = 1; i<regMax.length; ++i) min[i-1] = ArrayUtil.min(projDiff, regMax[i-1], regMax[i]);
        logger.trace("loc min diff2: {}, offset: {}", min, yOffset);
        
        //first max index: values & diff > 0 
        int firstMaxIdx = 0; 
        while(firstMaxIdx<regMax.length-1 && projDiff[regMax[firstMaxIdx]]==0 || projValues[regMax[firstMaxIdx]]==0) ++firstMaxIdx;
        
        ArrayList<int[]> yBounds = new ArrayList<int[]>(min.length);
        int lastMaxIdx = firstMaxIdx;
        for (int i = firstMaxIdx; i<min.length; ++i) {
            if (projDiff[min[i]]*projDiff[regMax[i+1]]<0 // changement de signe
                    && (projDiff[regMax[i+1]]-projDiff[min[i]])>=backgroundThld*maxValue // critère d'élimination du bruit
                    && (projDiffNorm[regMax[i+1]]-projDiffNorm[min[i]])>=splitThld // critère de séparation
                    ) {
                int lower;
                if (yBounds.isEmpty()) lower = ArrayUtil.getFirstOccurence(projDiff, regMax[lastMaxIdx], lastMaxIdx>0?min[lastMaxIdx-1]:0, 0, false, true);
                //else lower = min[lastMaxIdx-1];
                else lower = ArrayUtil.min(projValues, min[lastMaxIdx-1], regMax[lastMaxIdx]);
                //int higher = regMax[i+1];
                int higher = ArrayUtil.min(projValues, min[i], regMax[i+1]);
                //int higher = ArrayUtil.getFirstOccurence(projDiff, min[i], regMax[i+1], 0, true, true);
                yBounds.add(new int[]{lower+yOffset, higher+yOffset});
                logger.trace("add separation: min: {}, max: {}", lower+yOffset, higher+yOffset);
                lastMaxIdx=i+1;
            }
        }
        /*
        // no need to add the end of last bacteria -> LoG creates local max at the end of last bacteria 
        int end = ArrayUtil.min(projDiff, regMax[lastMaxIdx]+1, projDiff.length);
        if (end>regMax[lastMaxIdx]) {
            //look for last local max < min
            int lastLocalMax=lastMaxIdx;
            while(lastLocalMax<regMax.length-1 && regMax[lastLocalMax+1]<end) lastLocalMax++;
            if ((projDiffNorm[regMax[lastLocalMax]]-projDiffNorm[end])>splitThld // critère de séparation
                    && (projDiff[regMax[lastLocalMax]]-projDiff[end])>=backgroundThld*maxValue // critère d'élimination du bruit
                    ) {
                //int lower = min[lastMaxIdx-1];
                int lower = ArrayUtil.min(projValues, min[lastMaxIdx-1], regMax[lastMaxIdx]);
                int higher = ArrayUtil.getFirstOccurence(projDiff, end, projDiff.length, 0, true, true);
                yBounds.add(new int[]{lower+yOffset, higher+yOffset});
                logger.trace("add last bacteria: min: {}, max: {}", lower+yOffset, higher+yOffset);
            }
        }*/
        return yBounds;
    }

    /*public static ObjectPopulation run(Image input, ImageMask mask, double logScale, int minSize) {
        ImageFloat log = ImageFeatures.LoG(input, logScale, logScale);
        log=ImageFeatures.getGradientMagnitude(log, 1, true);
        ImageByte seeds = Filters.localExtrema(log, null, true, Filters.getNeighborhood(minSize, minSize, input));
        ObjectPopulation pop = WatershedTransform.watershed(log, mask, seeds, false);
        return pop;
    }*/
    
    
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
