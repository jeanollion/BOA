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
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.Filters;
import processing.IJFFTBandPass;
import processing.ImageFeatures;
import processing.WatershedTransform;
import utils.ArrayUtil;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class BacteriesFluo2D implements Segmenter {
    NumberParameter split = new BoundedNumberParameter("Split Threshold", 4, 0.2, 0, 1);
    NumberParameter size = new BoundedNumberParameter("Minimal Object Size (voxels)", 0, 100, 1, null);
    Parameter[] parameters = new Parameter[]{split};
    public static double[] optimizationParameters;
    public static boolean debug=false;
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        double smoothScale = 2;
        double dogScale = 15;
        double logScale = 2;
        double splitValue = split.getValue().doubleValue();
        splitValue/=10d; // FOR DEBUG
        return run(input, parent.getMask(), splitValue, smoothScale, dogScale, logScale);
    }
    
    
    
    public static ObjectPopulation run(Image input, ImageMask mask, double splitThreshold, double smoothScale, double dogScale, double logScale) {
        
        // filtrage: 
        // fft 
        // 
        //Image filtered = IJFFTBandPass.bandPass(input, 3, 20).setName("fft");
        ImageFloat smoothed = ImageFeatures.gaussianSmooth(input, smoothScale, smoothScale, false).setName("smoothed");
        //Image filtered = smoothed;
        ImageFloat dog = ImageFeatures.differenceOfGaussians(smoothed, 0, dogScale, 1, false, false).setName("DoG");
        //Image dog = fft;
        ImageFloat log = ImageFeatures.getLaplacian(dog, logScale, true, false).setName("LoG");
        Image hess = ImageFeatures.getHessian(dog, logScale, false)[0].setName("hess");
        //Image hessDet = ImageFeatures.getHessianDeterminant(dog, logScale, false).setName("hessDet");
        if (debug) {
            ImageDisplayer disp = new IJImageDisplayer();
            //disp.showImage(smoothed);
            disp.showImage(dog);
            disp.showImage(log);
            disp.showImage(hess);
            disp.showImage(input);
        }
        double t0 = IJAutoThresholder.runThresholder(dog, mask, AutoThresholder.Method.Otsu, 0);
        logger.trace("threshold 0: {}", t0);
        ObjectPopulation pop1 = SimpleThresholder.run(dog, t0);
        pop1.filter(null, new ObjectPopulation.Thickness().setX(2).setY(2));
        pop1.filter(null, new ObjectPopulation.Size().setMin(50));
        
        // split each object
        ImageByte seedMap = new ImageByte("seeds", input);
        ImageByte labelMap = new ImageByte("masks", input);
        ArrayList<Object3D> objects = new ArrayList<Object3D>();
        //pop1.keepOnlyLargestObject(); // for testing purpose
        for (Object3D o : pop1.getObjects()) {
            double normValue = BasicMeasurements.getPercentileValue(o, 0.5, smoothed);
            //if (optimizationParameters!=null) optimizationParameters[10] = BasicMeasurements.getPercentileValue(o, 0.25, smoothed);
            //if (optimizationParameters!=null) optimizationParameters[11] = BasicMeasurements.getPercentileValue(o, 0.5, smoothed);
            //if (optimizationParameters!=null) optimizationParameters[12] = BasicMeasurements.getPercentileValue(o, 0.75, smoothed);
            
            o.draw(labelMap, 1);
            objects.addAll(split(dog, labelMap, null, o, splitThreshold, seedMap, normValue)); // ajouter filtrage de longueur?
            o.draw(labelMap, 0);
        }
        return new ObjectPopulation(objects, input);
    }
    
    public static ArrayList<Object3D> split(Image input, ImageMask mask, Image normMap, final Object3D object, double splitThreshold, ImageByte seedMap, double normFactor) {
        BoundingBox bounds = object.getBounds();
        //ArrayList<int[]> yBounds = analyseYDiffProfile(input, 1, normMap,  bounds, splitThreshold, normFactor);
        ArrayList<int[]> yBounds = analyseYProfile(input, bounds, splitThreshold, normFactor);
        if (yBounds.isEmpty()) return new ArrayList<Object3D>(){{add(object);}};
        if (yBounds.get(yBounds.size()-1)[1]<bounds.getyMax()-1) yBounds.add(new int[]{yBounds.get(yBounds.size()-1)[1], bounds.getyMax()}); // add last element
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
    
    protected static ArrayList<int[]> analyseYDiffProfile(Image intensities, double derScale, Image grad, BoundingBox projBounds, double splitThld, double normFactor) {
        int yOffset = projBounds.getyMin();
        ImageFloat diffY = ImageFeatures.getDerivative(intensities, 1, 0, 1, 0, false).setName("diffY"); 
        ImageFloat diffY2 = ImageFeatures.getDerivative(intensities, 1, 0, 2, 0, false).setName("diffY"); 
        if (grad==null) {
            //grad = ImageFeatures.getGradientMagnitude(intensities, derScale, false).setName("grad");
            //grad = ImageFeatures.getDerivative(intensities, derScale, 1, 0, 0, false).setName("diffX"); 
        } // utiliser le gradient calculé précédement?
        //ImageFloat norm=new ImageFloat("grad proj", projBounds.getSizeY(), ImageOperations.meanProjection(grad, ImageOperations.Axis.Y, projBounds));
        //norm=ImageFeatures.gaussianSmooth(norm, 20, 1, true);
        
        
        float[] projValues = ImageOperations.meanProjection(intensities, ImageOperations.Axis.Y, projBounds);

        float[] projDiff = ImageOperations.meanProjection(diffY, ImageOperations.Axis.Y, projBounds);
        float[] projDiff2 = ImageOperations.meanProjection(diffY2, ImageOperations.Axis.Y, projBounds);
        
        float[] projDiffNorm=new float[projDiff.length];
        for (int y = 0; y<projDiff.length; ++y) projDiffNorm[y]=(float)(projDiff[y]/normFactor);
        float[] projDiff2Norm=new float[projDiff2.length];
        for (int y = 0; y<projDiff2.length; ++y) projDiff2Norm[y]=(float)(projDiff2[y]/normFactor);
        //for (int y = 0; y<projDiff.length; ++y) if (norm.getPixel(y, 0, 0)>0) projDiffNorm[y]=projDiff[y]/norm.getPixel(y, 0, 0);
        if (debug) {
            Utils.plotProfile("values", projValues);
            Utils.plotProfile("diffNorm", projDiffNorm);
            Utils.plotProfile("diff2", projDiff2);
            Utils.plotProfile("diff2Norm", projDiff2Norm);
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
        if (optimizationParameters!=null) {
            Collections.sort(amplitudes);
            Collections.sort(amplitudesNonNorm);
            double amp = 0;
            double count = 0;
            for (int i = amplitudesNonNorm.size()-1; i>=0 && i>=amplitudesNonNorm.size()-5; --i) {
                amp+=amplitudesNonNorm.get(i);
                count++;
            }
            if (count!=0) optimizationParameters[0] = amp/count;
            amp = 0;
            count = 0;
            for (int i = 0; i<amplitudesNonNorm.size() && i<=Math.max(4, amplitudesNonNorm.size()-5); ++i) {
                amp+=amplitudesNonNorm.get(i);
                count++;
            }
            if (count!=0) optimizationParameters[1] = amp/count;
        }
        
        //amplitudes = amplitudes.subList(Math.max(0, amplitudes.size()-5), amplitudes.size());
        //amplitudesNonNorm = amplitudesNonNorm.subList(Math.max(0, amplitudes.size()-6), amplitudes.size());
        //logger.debug("amplitudes normalisée par {}: {}", normFactor, amplitudes);
        //logger.debug("amplitudes: {}", amplitudesNonNorm);
        
        
        return yBounds;
    }
    
    protected static ArrayList<int[]> analyseYProfile(Image intensities, BoundingBox projBounds, double splitThld, double normFactor) {
        int minLenght = 20; // a mettre en parametre: longueure minimale
        //int peakSize= 8; // limit peak extension..
        int yOffset = projBounds.getyMin();
        
        float[] projValues = ImageOperations.meanProjection(intensities, ImageOperations.Axis.Y, projBounds);
        //for (int y = 0; y<projDiff.length; ++y) if (norm.getPixel(y, 0, 0)>0) projDiffNorm[y]=projDiff[y]/norm.getPixel(y, 0, 0);
        if (debug) {
            Utils.plotProfile("values", projValues);
        }
        int[] regMax = ArrayUtil.getRegionalExtrema(projValues, 1, true);
        logger.trace("reg max diff2: {}, offset: {}", regMax, yOffset);
        int[] regMin = ArrayUtil.getRegionalExtrema(projValues, minLenght/2, false); 
        float[] amplitude=null;
        if (debug) amplitude = new float[projValues.length];
        ArrayList<int[]> yBounds = new ArrayList<int[]>(regMin.length);
        int lastMaxIdx = 0;
        for (int i = regMin[0]==0?1:0; i<regMin.length; ++i) {
            
            // recherche du max local le plus proche
            while(lastMaxIdx<regMax.length && regMax[lastMaxIdx]<regMin[i]) ++lastMaxIdx;
            float amp = 0;
            if (lastMaxIdx<regMax.length) amp = projValues[regMax[lastMaxIdx]] - projValues[regMin[i]];
            if (lastMaxIdx>0) amp += projValues[regMax[lastMaxIdx-1]] - projValues[regMin[i]];
            if (amp>splitThld * normFactor) {
                int lower = yBounds.isEmpty() ? 0 : yBounds.get(yBounds.size()-1)[1];
                yBounds.add(new int[]{lower+yOffset, regMin[i]+yOffset});
                logger.trace("add separation: min: {}, max: {}, min+offset: {}, max+offset:{}, amplitude: {}",lower, regMin[i], lower+yOffset, regMin[i]+yOffset, amplitude[i]);
            }
            if (amplitude!=null) amplitude[regMin[i]]=(float)(amp/normFactor);
        }
        if (debug) {
            Utils.plotProfile("amplitude", amplitude);
        }
        
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
