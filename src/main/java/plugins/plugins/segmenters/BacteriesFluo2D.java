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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import measurement.BasicMeasurements;
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
    NumberParameter split = new BoundedNumberParameter("Split Threshold", 2, 1, 0, 1);
    NumberParameter size = new BoundedNumberParameter("Minimal Object Size (voxels)", 0, 100, 1, null);
    Parameter[] parameters = new Parameter[]{split};
    final static double gradientScale = 1;
    final static double medianScale = 2; // a mettre dans les prefilters
    public static boolean debug=false;
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        
        return run(input, parent.getMask(), split.getValue().doubleValue(), null);
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask, double splitThreshold, ArrayList<Double[]> debugValues) {
        Double[] dv=null;
        if (debugValues!=null) {
            dv = new Double[6];
            debugValues.add(dv);
        }
        int smoothScale = 2;
        int objectScale = 15;
        int logScale = 1;
        // filtrage: 
        ImageFloat smoothed = ImageFeatures.gaussianSmooth(input, smoothScale, smoothScale, false).setName("smoothed");
        ImageFloat dog = ImageFeatures.differenceOfGaussians(smoothed, 0, objectScale, 1, false, false).setName("DoG");
        ImageFloat log = ImageFeatures.getLaplacian(dog, logScale, true, false).setName("LoG");
        if (debug) {
            ImageDisplayer disp = new IJImageDisplayer();
            //disp.showImage(smoothed);
            //disp.showImage(dog);
            //disp.showImage(log3);
        }
        double t0 = IJAutoThresholder.runThresholder(dog, mask, AutoThresholder.Method.Otsu, 0);
        if (dv!=null) dv[2]= t0;
        logger.trace("threshold 0: {}", t0);
        ObjectPopulation pop1 = SimpleThresholder.run(dog, t0);
        pop1.filter(null, new ObjectPopulation.Thickness().setX(2).setY(2));
        pop1.filter(null, new ObjectPopulation.Size().setMin(50));
        
        // split each object
        ImageByte seedMap = new ImageByte("seeds", input);
        ImageByte labelMap = new ImageByte("masks", input);
        ArrayList<Object3D> objects = new ArrayList<Object3D>();
        pop1.keepOnlyLargestObject(); // for testing purpose
        for (Object3D o : pop1.getObjects()) {
            double normValue = BasicMeasurements.getPercentileValue(o, 0.5, dog);
            if (dv!=null)dv[3]=normValue;
            if (dv!=null)dv[4] = BasicMeasurements.getPercentileValue(o, 0.5, log);
            if (dv!=null) dv[5] = BasicMeasurements.getPercentileValue(o, 0.5, smoothed);
            o.draw(labelMap, 1);
            objects.addAll(split(log, labelMap, o, splitThreshold, seedMap, normValue, dv)); // ajouter filtrage de longueur?
            o.draw(labelMap, 0);
        }
        return new ObjectPopulation(objects, input);
    }
    
    public static ArrayList<Object3D> split(Image input, ImageMask mask, final Object3D object, double splitThreshold, ImageByte seedMap, double normFactor, Double[] debugValues) {
        BoundingBox bounds = object.getBounds();
        ArrayList<int[]> yBounds = analyseYDiffProfile(input, 2, null,  bounds, splitThreshold, normFactor, debugValues);
        if (yBounds.isEmpty()) return new ArrayList<Object3D>(){{add(object);}};
        yBounds.add(new int[]{yBounds.get(yBounds.size()-1)[1], bounds.getyMax()}); // add last element
        ArrayList<Voxel> seedList = new ArrayList<Voxel>(yBounds.size());
        getSeeds(input, true, yBounds, bounds, seedList, seedMap);
        ObjectPopulation pop = WatershedTransform.watershed(input, mask, seedMap, true);
        return pop.getObjects();
    }
    
    public static void getSeeds(Image image, boolean maximum, ArrayList<int[]> yBounds, BoundingBox globalBounds, ArrayList<Voxel> seedsList, ImageByte seedMap) {
        for (int[] yb : yBounds) {
            // get global intensity maximum within the area
            Voxel seedMax = ImageOperations.getGlobalExtremum(image, new BoundingBox(globalBounds.getxMin(), globalBounds.getxMax(), yb[0]+1, yb[1], globalBounds.getzMin(), globalBounds.getzMax()), maximum);
            if (seedMap!=null) seedMap.setPixel(seedMax.x, seedMax.y, seedMax.z, 1);
            if (seedsList!=null) seedsList.add(seedMax);
        }
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
    
    protected static ArrayList<int[]> analyseYDiffProfile(Image intensities, double derScale, Image grad, BoundingBox projBounds, double splitThld, double normFactor, Double[] debugValues) {
        int yOffset = projBounds.getyMin();
        ImageFloat diffY = ImageFeatures.getDerivative(intensities, derScale, 0, 1, 0, false).setName("diffY"); 
        if (grad==null) {
            //grad = ImageFeatures.getGradientMagnitude(intensities, derScale, false).setName("grad");
            //grad = ImageFeatures.getDerivative(intensities, derScale, 1, 0, 0, false).setName("diffX"); 
        } // utiliser le gradient calculé précédement?
        //ImageFloat norm=new ImageFloat("grad proj", projBounds.getSizeY(), ImageOperations.meanProjection(grad, ImageOperations.Axis.Y, projBounds));
        //norm=ImageFeatures.gaussianSmooth(norm, projBounds.getSizeX(), projBounds.getSizeX(), true);
        
        //ImageDisplayer disp = new IJImageDisplayer();
        //disp.showImage(grad);
        
        float[] projValues = ImageOperations.meanProjection(intensities, ImageOperations.Axis.Y, projBounds);
        float maxValue = ArrayUtil.max(projValues);
        
        
        float[] projDiff = ImageOperations.meanProjection(diffY, ImageOperations.Axis.Y, projBounds);
        
        float[] projDiffNorm=new float[projDiff.length];
        for (int y = 0; y<projDiff.length; ++y) projDiffNorm[y]=(float)(projDiff[y]/normFactor);
        //for (int y = 0; y<projDiff.length; ++y) if (norm.getPixel(y, 0, 0)>0) projDiffNorm[y]=projDiff[y]/norm.getPixel(y, 0, 0);
        if (debug) {
            Utils.plotProfile("values", projValues);
            Utils.plotProfile("diffNorm", projDiffNorm);
            //Utils.plotProfile("norm", norm.getPixelArray()[0]);
            //float[] projDiffNorm2=new float[projDiff.length]; 
            //if (maxValue>0) for (int y = 0; y<projDiff.length; ++y) projDiffNorm2[y]=projDiff[y]/maxValue;
            //Utils.plotProfile("diff norm by value", projDiffNorm2);
            Utils.plotProfile("diff", projDiff);
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
        List<Float> amplitudes= new ArrayList<Float>();
        List<Float> amplitudesNonNorm= new ArrayList<Float>();
        int lastMaxIdx = firstMaxIdx;
        for (int i = firstMaxIdx; i<min.length; ++i) {
            amplitudes.add(projDiffNorm[regMax[i+1]]-projDiffNorm[min[i]]);
            amplitudesNonNorm.add(projDiff[regMax[i+1]]-projDiff[min[i]]);
            if (projDiff[min[i]]*projDiff[regMax[i+1]]<0 // changement de signe
                    //&& (projDiff[regMax[i+1]]-projDiff[min[i]])>=backgroundThld*maxValue // critère d'élimination du bruit
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
                logger.trace("add separation: min: {}, max: {}, min+offset: {}, max+offset:{}, amplitude: {}",lower, higher, lower+yOffset, higher+yOffset, projDiffNorm[regMax[i+1]]-projDiffNorm[min[i]]);
                lastMaxIdx=i+1;
            }
        }
        if (debugValues!=null) {
            Collections.sort(amplitudes);
            Collections.sort(amplitudesNonNorm);
            double amp = 0;
            double count = 0;
            for (int i = amplitudesNonNorm.size()-1; i>=0 && i>=amplitudesNonNorm.size()-5; --i) {
                amp+=amplitudesNonNorm.get(i);
                count++;
            }
            if (count!=0) debugValues[0] = amp/count;
            else debugValues[0] = 0d;
            amp = 0;
            count = 0;
            for (int i = 0; i<amplitudesNonNorm.size() && i<=Math.max(4, amplitudesNonNorm.size()-5); ++i) {
                amp+=amplitudesNonNorm.get(i);
                count++;
            }
            if (count!=0) debugValues[1] = amp/count;
            else debugValues[1] = 0d;
        }
        
        //amplitudes = amplitudes.subList(Math.max(0, amplitudes.size()-5), amplitudes.size());
        //amplitudesNonNorm = amplitudesNonNorm.subList(Math.max(0, amplitudes.size()-6), amplitudes.size());
        //logger.debug("amplitudes normalisée par {}: {}", normFactor, amplitudes);
        //logger.debug("amplitudes: {}", amplitudesNonNorm);
        
        
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
