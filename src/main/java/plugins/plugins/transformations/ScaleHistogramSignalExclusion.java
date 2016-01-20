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

import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChannelImageParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import image.BlankMask;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import static plugins.Plugin.logger;
import plugins.Transformation;
import utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class ScaleHistogramSignalExclusion implements Transformation {
    BoundedNumberParameter sigmaTh= new BoundedNumberParameter("Theorical Sigma", 2, 5, 1, null);
    BoundedNumberParameter muTh= new BoundedNumberParameter("Theorical Mean", 2, 100, 1, null);
    ChannelImageParameter signalExclusion = new ChannelImageParameter("Channel for Signal Exclusion", -1, true);
    BoundedNumberParameter signalExclusionThreshold = new BoundedNumberParameter("Signal Exclusion Threshold", 1, 50, 0, null);
    BooleanParameter underThreshold = new BooleanParameter("Consider only signal under threshold", true);
    Parameter[] parameters = new Parameter[]{sigmaTh, muTh, signalExclusion, signalExclusionThreshold, underThreshold};
    ArrayList<ArrayList<Double>> meanSigmaT = new ArrayList<ArrayList<Double>>();;
    
    public ScaleHistogramSignalExclusion() {}
    
    public ScaleHistogramSignalExclusion(double muTh, double sigmaTh, int signalExclusion, double signalExclusionThreshold, boolean underThreshold) {
        this.sigmaTh.setValue(sigmaTh);
        this.muTh.setValue(muTh);
        if (signalExclusion>=0) this.signalExclusion.setSelectedIndex(signalExclusion);
        this.signalExclusionThreshold.setValue(signalExclusionThreshold);
        this.underThreshold.setSelected(underThreshold);
    }
    
    public void computeConfigurationData(final int channelIdx, final InputImages inputImages) {
        final int chExcl = signalExclusion.getSelectedIndex();
        final double exclThld = signalExclusionThreshold.getValue().doubleValue();
        final boolean underThreshold = this.underThreshold.getSelected();
        final ThreadRunner tr = new ThreadRunner(0, inputImages.getTimePointNumber());
        final ImageInteger[] exclusionMasks = (chExcl>=0) ?  new ImageInteger[tr.size()] : null;
        final Double[][] muSigma = new Double[inputImages.getTimePointNumber()][];
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
                            muSigma[idx] = computeMeanSigma(inputImages.getImage(channelIdx, idx), signalExclusion, exclThld, underThreshold, exclusionMask, idx);
                        }
                    }
                }
            );
        }
        tr.startAndJoin();
        meanSigmaT=new ArrayList<ArrayList<Double>>(muSigma.length);
        for (Double[] d : muSigma) {
            //logger.debug("muSigma: {}", (Object[])d);
            meanSigmaT.add(new ArrayList<Double>(Arrays.asList(d)));
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
        }
    }
    
    private double[] getNext(Double[][] muSigma, int startIdx, int length, boolean next) { // actual length, sumMu, sumSigma
        if (next) {
            while(startIdx)
        }
    }*/
    
    
    public static Double[] computeMeanSigma(Image image, Image exclusionSignal, double exclusionThreshold, boolean underThreshold, ImageInteger exclusionMask, int timePoint) {
        long t0 = System.currentTimeMillis();
        if (exclusionMask!=null) ImageOperations.threshold(exclusionSignal, exclusionThreshold, !underThreshold, true, true, exclusionMask);
        else exclusionMask = new BlankMask(image);
        double[] res=   ImageOperations.getMeanAndSigma(image, exclusionMask);
        long t1 = System.currentTimeMillis();
        //logger.debug("ScaleHistogram signal exclusion: timePoint: {}, mean sigma: {}, signal exclusion? {}, processing time: {}", timePoint, res, exclusionSignal!=null, t1-t0);
        return new Double[]{res[0], res[1]};
    }
    
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (meanSigmaT==null || meanSigmaT.isEmpty() || meanSigmaT.size()<timePoint) throw new Error("ScaleHistogram transformation not configured: "+ (meanSigmaT==null?"null":  meanSigmaT.size()));
        ArrayList<Double> muSig = this.meanSigmaT.get(timePoint);
        double alpha = muSig.get(1) / this.sigmaTh.getValue().doubleValue();
        double beta = muSig.get(0) - alpha * this.muTh.getValue().doubleValue();
        return ImageOperations.affineOperation(image, image instanceof ImageFloat? image: new ImageFloat("", 0, 0, 0), 1d/alpha, -beta/alpha);
    }
    
    public ArrayList getConfigurationData() {
        return meanSigmaT;
    }

    public Transformation.SelectionMode getOutputChannelSelectionMode() {
        return Transformation.SelectionMode.SAME;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
}
