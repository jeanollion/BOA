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

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChannelImageParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import image.BlankMask;
import image.Image;
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
    BoundedNumberParameter sigmaTh= new BoundedNumberParameter("Theorical Sigma", 2, 7.83, 1, null);
    BoundedNumberParameter muTh= new BoundedNumberParameter("Theorical Mean", 2, 106, 1, null);
    ChannelImageParameter signalExclusion = new ChannelImageParameter("Channel for Signal Exclusion");
    BoundedNumberParameter signalExclusionThreshold = new BoundedNumberParameter("Signal Exclusion Threshold", 1, 50, 0, null);
    Parameter[] parameters = new Parameter[]{sigmaTh, muTh, signalExclusion, signalExclusionThreshold};
    ArrayList<ArrayList<Double>> meanSigmaT;
    
    public ScaleHistogramSignalExclusion() {}
    
    public ScaleHistogramSignalExclusion(double sigmaTh, double muTh, int signalExclusion) {
        this.sigmaTh.setValue(sigmaTh);
        this.muTh.setValue(muTh);
        if (signalExclusion>=0) this.signalExclusion.setSelectedIndex(signalExclusion);
    }
    
    public void computeConfigurationData(final int channelIdx, final InputImages inputImages) {
        final int chExcl = signalExclusion.getSelectedIndex();
        final double exclThld = signalExclusionThreshold.getValue().doubleValue();
        final ThreadRunner tr = new ThreadRunner(0, inputImages.getTimePointNumber());
        final ImageInteger[] exclusionMasks = (chExcl>=0) ?  new ImageInteger[tr.size()] : null;
        final Double[][] sigmaMu = new Double[inputImages.getTimePointNumber()][];
        for (int i = 0; i<tr.threads.length; i++) {
            final int trIdx = i;
            tr.threads[i] = new Thread(
                    new Runnable() {  
                    public void run() {
                        for (int idx = tr.ai.getAndIncrement(); idx<tr.end; idx = tr.ai.getAndIncrement()) {
                            Image signalExclusion=null;
                            ImageInteger exclusionMask = null;
                            if (chExcl>=0) {
                                signalExclusion = inputImages.getImage(chExcl, channelIdx);
                                exclusionMask = exclusionMasks[trIdx];
                            }
                            sigmaMu[idx] = computeMeanSigma(inputImages.getImage(channelIdx, idx), signalExclusion, exclThld, exclusionMask, idx);
                        }
                    }
                }
            );
        }
        tr.startAndJoin();
        for (Double[] d : sigmaMu) meanSigmaT.add(new ArrayList<Double>(Arrays.asList(d)));
    }
    public static Double[] computeMeanSigma(Image image, Image exclusionSignal, double exclusionThreshold, ImageInteger exclusionMask, int timePoint) {
        long t0 = System.currentTimeMillis();
        if (exclusionMask!=null) ImageOperations.threshold(exclusionSignal, exclusionThreshold, false, true, true, exclusionMask);
        else exclusionMask = new BlankMask(image);
        double[] res=   ImageOperations.getMeanAndSigma(image, exclusionMask);
        long t1 = System.currentTimeMillis();
        logger.debug("ScaleHistogram signal exclusion: timePoint: {}, mean sigma: {}, signal exclusion? {}, processing time: {}", timePoint, res, exclusionSignal!=null, t1-t0);
        return new Double[]{res[0], res[1]};
    }
    
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (meanSigmaT==null || meanSigmaT.isEmpty() || meanSigmaT.size()<=timePoint) throw new Error("ScaleHistogram transformation not configured");
        ArrayList<Double> muSig = this.meanSigmaT.get(timePoint);
        double alpha = muSig.get(1) / this.sigmaTh.getValue().doubleValue();
        double beta = muSig.get(0) - alpha * this.muTh.getValue().doubleValue();
        return ImageOperations.affineOperation(image, image instanceof ImageFloat? image: null, 1d/alpha, -beta/alpha);
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
