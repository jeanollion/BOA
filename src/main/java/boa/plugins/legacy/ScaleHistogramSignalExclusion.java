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
package boa.plugins.legacy;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChannelImageParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.image.BlankMask;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import static boa.plugins.Plugin.logger;
import boa.plugins.Transformation;
import boa.utils.ThreadRunner;
import java.util.function.DoublePredicate;

/**
 *
 * @author jollion
 */
public class ScaleHistogramSignalExclusion implements Transformation {
    BoundedNumberParameter sigmaTh= new BoundedNumberParameter("Theorical Sigma", 2, 5, 1, null);
    BoundedNumberParameter muTh= new BoundedNumberParameter("Theorical Mean", 2, 100, 1, null);
    ChannelImageParameter signalExclusion = new ChannelImageParameter("Channel for Signal Exclusion", -1, true);
    BoundedNumberParameter signalExclusionThreshold = new BoundedNumberParameter("Signal Exclusion Threshold", 1, 50, 0, null);
    BooleanParameter vertical = new BooleanParameter("Vertical lines of Signal", true);
    BooleanParameter excludeZero = new BooleanParameter("Exclude Zero Values", true);
    Parameter[] parameters = new Parameter[]{sigmaTh, muTh, signalExclusion, signalExclusionThreshold, vertical, excludeZero};
    ArrayList<ArrayList<Double>> meanSigmaT = new ArrayList<ArrayList<Double>>();;
    
    public ScaleHistogramSignalExclusion() {}
    
    public ScaleHistogramSignalExclusion(double muTh, double sigmaTh, int signalExclusion, double signalExclusionThreshold, boolean verticalSignal) {
        this.sigmaTh.setValue(sigmaTh);
        this.muTh.setValue(muTh);
        if (signalExclusion>=0) this.signalExclusion.setSelectedIndex(signalExclusion);
        this.signalExclusionThreshold.setValue(signalExclusionThreshold);
        this.vertical.setSelected(verticalSignal);
    }
    Map<Integer, Image> testMasks;
    @Override
    public void computeConfigurationData(final int channelIdx, final InputImages inputImages) {
        if (testMode) testMasks = new HashMap<>(inputImages.getFrameNumber());
        final int chExcl = signalExclusion.getSelectedIndex();
        final double exclThld = signalExclusionThreshold.getValue().doubleValue();
        final boolean underThreshold = true;
        final boolean vertical = this.vertical.getSelected();
        final boolean excludeZero = this.excludeZero.getSelected();
        final ThreadRunner tr = new ThreadRunner(0, inputImages.getFrameNumber());
        final ImageInteger[] exclusionMasks = (chExcl>=0) ?  new ImageInteger[tr.size()] : null;
        final Double[][] muSigma = new Double[inputImages.getFrameNumber()][];
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
                            muSigma[idx] = computeMeanSigma(inputImages.getImage(channelIdx, idx), signalExclusion, exclThld, vertical, excludeZero, exclusionMask, idx);
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
        if (testMode && !testMasks.isEmpty()) {
            Image[][] maskTC = new Image[testMasks.size()][1];
            for (Entry<Integer, Image> e : testMasks.entrySet()) maskTC[e.getKey()][0] = e.getValue();
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("Exclusion signal mask", maskTC);
            testMasks.clear();
        }
    }
    
    public Double[] computeMeanSigma(Image image, Image exclusionSignal, double exclusionThreshold, boolean vertical, boolean excludeZero, ImageInteger exclusionMask, int timePoint) {
        if (exclusionSignal!=null && !image.sameDimensions(exclusionSignal)) throw new RuntimeException("Image and exclusion signal should have same dimensions");
        if (exclusionMask!=null && !image.sameDimensions(exclusionMask)) throw new RuntimeException("Image and exclusion mask should have same dimensions");
        long t0 = System.currentTimeMillis();
        ImageMask exclusionMaskMask;
        if (exclusionMask!=null) {
            ImageOperations.threshold(exclusionSignal, exclusionThreshold, false, true, true, exclusionMask);
            if (vertical) homogenizeVerticalLines(exclusionMask);
            if (testMode) testMasks.put(timePoint, exclusionMask.duplicate());
            exclusionMaskMask=exclusionMask;
        }
        else exclusionMaskMask = new BlankMask(image);
        DoublePredicate func = excludeZero ? v -> v!=0: null;
        double[] res=  ImageOperations.getMeanAndSigma(image, exclusionMaskMask, func);
        long t1 = System.currentTimeMillis();
        //logger.debug("ScaleHistogram signal exclusion: timePoint: {}, mean sigma: {}, signal exclusion? {}, processing time: {}", timePoint, res, exclusionSignal!=null, t1-t0);
        return new Double[]{res[0], res[1]};
    }
    
    protected static void homogenizeVerticalLines(ImageInteger mask) {
        for (int z = 0; z<mask.sizeZ(); ++z) {
            for (int x = 0; x<mask.sizeX(); ++x) {
                for (int y = 0; y<mask.sizeY(); ++y) {
                    if (!mask.insideMask(x, y, z)) {
                        for (y = 0; y<mask.sizeY(); ++y) {mask.setPixel(x, y, z, 0);}
                    }
                }
            }
        }
    }
        
    public ImageFloat applyTransformation(int channelIdx, int timePoint, Image image) {
        if (meanSigmaT==null || meanSigmaT.isEmpty() || meanSigmaT.size()<timePoint) throw new RuntimeException("ScaleHistogram transformation not configured: "+ (meanSigmaT==null?"null":  meanSigmaT.size()));
        ArrayList<Double> muSig = this.meanSigmaT.get(timePoint);
        double alpha = muSig.get(1) / this.sigmaTh.getValue().doubleValue();
        double beta = muSig.get(0) - alpha * this.muTh.getValue().doubleValue();
        if (excludeZero.getSelected()) {
            ImageFloat output;
            if (image instanceof ImageFloat) output = (ImageFloat) image;
            else output= new ImageFloat("", image);
            int sizeZ= output.sizeZ();
            int sizeXY = output.sizeXY();
            double m = 1d/alpha;
            double add = -beta/alpha;
            double value;
            for (int z = 0; z<sizeZ; ++z) {
                for (int xy=0; xy<sizeXY; ++xy) {
                    value = image.getPixel(xy, z);
                    if (value!=0) output.setPixel(xy, z, image.getPixel(xy, z)*m+add);
                }
            }
            return output;
        } else return (ImageFloat)ImageOperations.affineOperation(image, image instanceof ImageFloat? image: new ImageFloat("", 0, 0, 0), 1d/alpha, -beta/alpha);
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
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return meanSigmaT!=null && meanSigmaT.size()==totalTimePointNumber;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
