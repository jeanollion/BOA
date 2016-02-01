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
package plugins.plugins.transformations;

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChannelImageParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import image.BlankMask;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageFormat;
import image.ImageInteger;
import image.ImageMask;
import image.ImageOperations;
import image.ImageWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import static plugins.Plugin.logger;
import plugins.Transformation;
import utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class ScaleHistogramSignalExclusionY implements Transformation {
    BoundedNumberParameter sigmaTh= new BoundedNumberParameter("Theorical Sigma", 2, 5, 1, null);
    BoundedNumberParameter muTh= new BoundedNumberParameter("Theorical Mean", 2, 100, 1, null);
    BoundedNumberParameter slidingWindowSize= new BoundedNumberParameter("Sliding Window Size", 0, 50, 1, null);
    ChannelImageParameter signalExclusion = new ChannelImageParameter("Channel for Signal Exclusion", -1, true);
    BoundedNumberParameter signalExclusionThreshold = new BoundedNumberParameter("Signal Exclusion Threshold", 1, 50, 0, null);
    BooleanParameter underThreshold = new BooleanParameter("Consider only signal under threshold", true);
    BooleanParameter excludeZero = new BooleanParameter("Exclude Zero Values", true);
    BoundedNumberParameter signalMaxThreshold= new BoundedNumberParameter("Signal Max Threshold", 2, 200, 0, null);
    Parameter[] parameters = new Parameter[]{sigmaTh, muTh, signalExclusion, signalExclusionThreshold, underThreshold, excludeZero, signalMaxThreshold, slidingWindowSize};
    ArrayList<ArrayList<ArrayList<Double>>> meanSigmaTY = new ArrayList<ArrayList<ArrayList<Double>>>();
    
    public ScaleHistogramSignalExclusionY() {}
    
    public ScaleHistogramSignalExclusionY(double muTh, double sigmaTh, int signalExclusion, double signalExclusionThreshold, double signalMaxThreshold, boolean underThreshold) {
        this.sigmaTh.setValue(sigmaTh);
        this.muTh.setValue(muTh);
        if (signalExclusion>=0) this.signalExclusion.setSelectedIndex(signalExclusion);
        this.signalExclusionThreshold.setValue(signalExclusionThreshold);
        this.underThreshold.setSelected(underThreshold);
        this.signalMaxThreshold.setValue(signalMaxThreshold);
    }
    
    public void computeConfigurationData(final int channelIdx, final InputImages inputImages) {
        final int chExcl = signalExclusion.getSelectedIndex();
        final double exclThld = signalExclusionThreshold.getValue().doubleValue();
        final double signalMaxThreshold = this.signalMaxThreshold.getValue().doubleValue();
        final boolean underThreshold = this.underThreshold.getSelected();
        final boolean includeZero = !this.excludeZero.getSelected();
        final int windowSize = this.slidingWindowSize.getValue().intValue();
        final ThreadRunner tr = new ThreadRunner(0, inputImages.getTimePointNumber());
        final ImageInteger[] exclusionMasks = (chExcl>=0) ?  new ImageInteger[tr.size()] : null;
        final Double[][][] muSigmaTY = new Double[inputImages.getTimePointNumber()][][];
        for (int i = 0; i<tr.threads.length; i++) {
            final int trIdx = i;
            tr.threads[i] = new Thread(
                    new Runnable() {  
                    public void run() {
                        for (int idx = tr.ai.getAndIncrement(); idx<tr.end; idx = tr.ai.getAndIncrement()) {
                            Image signalExclusion=null;
                            ImageInteger exclusionMask = null;
                            if (chExcl>=0) {
                                signalExclusion = inputImages.getImage(chExcl, idx);
                                if (exclusionMasks[trIdx]==null) exclusionMasks[trIdx] = new ImageByte("", signalExclusion);
                                exclusionMask = exclusionMasks[trIdx];
                            }
                            muSigmaTY[idx] = computeMeanSigmaY(inputImages.getImage(channelIdx, idx), signalExclusion, exclThld, underThreshold, includeZero, signalMaxThreshold, exclusionMask, windowSize, idx);
                        }
                    }
                }
            );
        }
        tr.startAndJoin();
        meanSigmaTY=new ArrayList<ArrayList<ArrayList<Double>>>(muSigmaTY.length);
        for (Double[][] d : muSigmaTY) {
            ArrayList<ArrayList<Double>> al = new ArrayList<ArrayList<Double>>();
            meanSigmaTY.add(al);
            for (Double[] dd : d) al.add(new ArrayList<Double>(Arrays.asList(dd)));
        }
    }
    
    /*private Double[][] getTheoricalMuSigma(double blinkMuThld, int windowSize, Double[][] muSigma) {
        Double[][] res = new Double[muSigma.length][2];
        int lastUnBlinkIdx, nextUnBlinkIdx;
        for (int i = 0; i<muSigma.length; ++i) {
            if (muSigma[i][0]<=blinkMuThld) lastUnBlinkIdx=i;
            else { // blinking
                //look for next BlinkIdx
                nextUnBlinkIdx=i+1;
                while (nextUnBlinkIdx<muSigma.length && muSigma[nextUnBlinkIdx][0]>blinkMuThld) ++nextUnBlinkIdx;
                
                // look for windowSize/2 previous elements 
                int unBlinkCount=0;
                int unBlinkPrevIdx;
                // look for windowSize-unBlinkCount next elements
                
                // look for windowSize-unBlinkCount previous elements
            }
        }3
    }
    
    private double[] getNext(Double[][] muSigma, int startIdx, int length, boolean next) { // actual length, sumMu, sumSigma
        if (next) {
            while(startIdx)
        }
    }*/
    
    
    public static Double[][] computeMeanSigmaY(Image image, Image exclusionSignal, double exclusionThreshold, boolean underThreshold, boolean includeZero, double signalMaxThreshold, ImageInteger exclusionMask, int windowSize, int timePoint) {
        if (exclusionSignal!=null && !image.sameSize(exclusionSignal)) throw new Error("Image and exclusion signal should have same dimensions");
        if (exclusionMask!=null && !image.sameSize(exclusionMask)) throw new Error("Image and exclusion mask should have same dimensions");
        long t0 = System.currentTimeMillis();
        if (exclusionMask!=null) {
            ImageOperations.threshold(exclusionSignal, exclusionThreshold, !underThreshold, true, true, exclusionMask);
            homogenizeVerticalLines(exclusionMask);
        }
        else exclusionMask = new BlankMask(image);
        Double[][] res=  getMeanAndSigmaExcludeZeroY(image, exclusionMask,includeZero, signalMaxThreshold, windowSize) ;
        long t1 = System.currentTimeMillis();
        //logger.debug("ScaleHistogram signal exclusion: timePoint: {}, mean sigma: {}, signal exclusion? {}, processing time: {}", timePoint, res, exclusionSignal!=null, t1-t0);
        return res;
    }
    
    private static void homogenizeVerticalLines(ImageInteger mask) {
        for (int z = 0; z<mask.getSizeZ(); ++z) {
            for (int x = 0; x<mask.getSizeX(); ++x) {
                for (int y = 0; y<mask.getSizeY(); ++y) {
                    if (!mask.insideMask(x, y, z)) {
                        for (y = 0; y<mask.getSizeY(); ++y) {mask.setPixel(x, y, z, 0);}
                    }
                }
            }
        }
    }
    
    public static Double[][] getMeanAndSigmaExcludeZeroY(Image image, ImageMask mask, boolean includeZero, double signalMaxThreshold, int windowSize) {
        if (mask==null) mask = new BlankMask(image);
        double value;
        double[][] sumSum2Count = new double[image.getSizeY()][3];
        
        for (int y = 0; y<image.getSizeY(); ++y) {
            double sum = 0;
            double sum2 = 0;
            double count = 0;
            for (int z = 0; z < image.getSizeZ(); ++z) {
                for (int x = 0; x < image.getSizeX(); ++x) {
                    if (mask.insideMask(x, y, z)) {
                        value = image.getPixel(x, y, z);
                        if ((includeZero || value!=0) && value<signalMaxThreshold) {
                            sum += value;
                            ++count;
                            sum2 += value * value;
                        }
                    }
                }
            }
            sumSum2Count[y][0]  = sum;
            sumSum2Count[y][1]  = sum2;
            sumSum2Count[y][2]  = count;
            //if (y%100==0) logger.debug("y: {}, count: {}, includeZero: {}, signalMaxThld: {}", y, count, includeZero, signalMaxThreshold);
        }
        
        Double[][] meanSigma = new Double[image.getSizeY()][2];
        
        for (int y = 0; y<image.getSizeY(); y++) { // TODO improve speed -> flux entrant et sortant
            int yStart = Math.max(0, y-windowSize/2);
            int yEnd = Math.min(image.getSizeY()-1, y+windowSize/2);
            double mean=0, count=0, sigma=0;
            for (int yy = yStart; yy<=yEnd; ++yy) { // sliding smooth
                mean+=sumSum2Count[yy][0];
                sigma+=sumSum2Count[yy][1];
                count+=sumSum2Count[yy][2];
            }
            mean /= count;
            sigma /= count;
            meanSigma[y][0] = mean;
            meanSigma[y][1] = Math.sqrt(sigma - mean * mean);
        }
        return meanSigma;
    }
    
    public ImageFloat applyTransformation(int channelIdx, int timePoint, Image image) {
        if (meanSigmaTY==null || meanSigmaTY.isEmpty() || meanSigmaTY.size()<timePoint) throw new Error("ScaleHistogram transformation not configured: "+ (meanSigmaTY==null?"null":  meanSigmaTY.size()));
        ArrayList<ArrayList<Double>> muSigY = this.meanSigmaTY.get(timePoint);
        double[] alphaY = new double[muSigY.size()];
        double[] betaY = new double[muSigY.size()];
        double[] mY = new double[muSigY.size()];
        double[] addY = new double[muSigY.size()];
        for (int y = 0; y<alphaY.length; ++y) {
            alphaY[y] = muSigY.get(y).get(1) / this.sigmaTh.getValue().doubleValue();
            betaY[y] = muSigY.get(y).get(0) - alphaY[y] * this.muTh.getValue().doubleValue();
            mY[y] = 1d/alphaY[y];
            addY[y] = -betaY[y] / alphaY[y];
        }
        boolean includeZero = !excludeZero.getSelected();

        ImageFloat output;
        if (image instanceof ImageFloat) output = (ImageFloat) image;
        else output= new ImageFloat("", image);
        int sizeZ= output.getSizeZ();
        int sizeX = output.getSizeX();
        int sizeY = output.getSizeY();
        double value;
        for (int z = 0; z<sizeZ; ++z) {
            for (int y=0; y<sizeY; ++y) {
                for (int x=0; x<sizeX; ++x) {
                    value = image.getPixel(x, y, z);
                    if (includeZero || value!=0) output.setPixel(x, y, z, image.getPixel(x, y, z)*mY[y]+addY[y]);
                }
            }
        }
        return output;
        
    }
    
    public ArrayList getConfigurationData() {
        return meanSigmaTY;
    }

    public Transformation.SelectionMode getOutputChannelSelectionMode() {
        return Transformation.SelectionMode.SAME;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
}
