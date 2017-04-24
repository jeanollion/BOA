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

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChannelImageParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import dataStructure.containers.InputImages;
import ij.process.AutoThresholder;
import image.BlankMask;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageMask;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import static plugins.Plugin.logger;
import plugins.SimpleThresholder;
import plugins.Thresholder;
import plugins.Transformation;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.IJAutoThresholder;
import utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class RemoveStripesSignalExclusion implements Transformation {
    ChannelImageParameter signalExclusion = new ChannelImageParameter("Channel for Signal Exclusion", -1, true);
    PluginParameter<SimpleThresholder> signalExclusionThreshold = new PluginParameter<>("Signal Exclusion Threshold", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false); //new ConstantValue(150)
    Parameter[] parameters = new Parameter[]{signalExclusion, signalExclusionThreshold};
    List<List<List<Double>>> meanTZY = new ArrayList<>();
    
    public RemoveStripesSignalExclusion() {}
    
    public RemoveStripesSignalExclusion(int signalExclusion) {
        if (signalExclusion>=0) this.signalExclusion.setSelectedIndex(signalExclusion);
    }
    
    public RemoveStripesSignalExclusion setMethod(SimpleThresholder thlder) {
        this.signalExclusionThreshold.setPlugin(thlder);
        return this;
    }
    
    @Override
    public void computeConfigurationData(final int channelIdx, final InputImages inputImages) {
        final int chExcl = signalExclusion.getSelectedIndex();
        final double exclThld = signalExclusionThreshold.instanciatePlugin().runThresholder(inputImages.getImage(chExcl, inputImages.getDefaultTimePoint()));
        logger.debug("remove stripes thld: {}", exclThld);
        final ThreadRunner tr = new ThreadRunner(0, inputImages.getTimePointNumber());
        final ImageInteger[] exclusionMasks = (chExcl>=0) ?  new ImageInteger[tr.size()] : null;
        Double[][][] meanX = new Double[inputImages.getTimePointNumber()][][];
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
                            meanX[idx] = computeMeanX(inputImages.getImage(channelIdx, idx), signalExclusion, exclThld, exclusionMask);
                        }
                    }
                }
            );
        }
        tr.startAndJoin();
        meanTZY.clear();
        for (int f = 0; f<meanX.length; ++f) {
            List<List<Double>> resL = new ArrayList<>(meanX[f].length);
            for (Double[] meanY : meanX[f]) resL.add(Arrays.asList(meanY));
            meanTZY.add(resL);
        }
    }
    
    public static Double[][] computeMeanX(Image image, Image exclusionSignal, double exclusionThreshold, ImageInteger exclusionMask) {
        if (exclusionSignal!=null && !image.sameSize(exclusionSignal)) throw new Error("Image and exclusion signal should have same dimensions");
        if (exclusionMask!=null && !image.sameSize(exclusionMask)) throw new Error("Image and exclusion mask should have same dimensions");
        if (exclusionMask!=null) ImageOperations.threshold(exclusionSignal, exclusionThreshold, false, true, true, exclusionMask);
        else exclusionMask = new BlankMask(image);
        //ImageWindowManagerFactory.showImage(exclusionMask.duplicate("excl mask"));
        Double[][] res = new Double[image.getSizeZ()][image.getSizeY()];
        for (int z=0; z<image.getSizeZ(); ++z) {
            for (int y = 0; y<image.getSizeY(); ++y) {
                double sum = 0;
                double count = 0;
                for (int x = 0; x<image.getSizeX(); ++x) {
                    if (exclusionMask.insideMask(x, y, z)) {
                        ++count;
                        sum+=image.getPixel(x, y, z);
                    }
                }
                res[z][y] = count>0 ? sum/count : 1;
            }
        }
        return res;
    }

    @Override
    public ImageFloat applyTransformation(int channelIdx, int timePoint, Image image) {
        if (meanTZY==null || meanTZY.isEmpty() || meanTZY.size()<timePoint) throw new Error("RemoveStripes transformation not configured: "+ (meanTZY==null?"null":  meanTZY.size()));
        List<List<Double>> muZY = meanTZY.get(timePoint);
        ImageFloat im = new ImageFloat("removeStripes", image);
        //ImageFloat imTest = new ImageFloat("removeStripes", image);
        for (int z = 0; z<image.getSizeZ(); ++z) {
            List<Double> muY = muZY.get(z);
            for (int y = 0; y<image.getSizeY(); ++y) {
                double mu = muY.get(y);
                for (int x = 0; x<image.getSizeX(); ++x) {
                    im.setPixel(x, y, z, image.getPixel(x, y, z)-mu);
                    //imTest.setPixel(x, y, z, mu);
                }
            }
        }
        //ImageWindowManagerFactory.showImage(imTest);
        return im;
    }
    
    @Override
    public List getConfigurationData() {
        return meanTZY;
    }
    @Override
    public Transformation.SelectionMode getOutputChannelSelectionMode() {
        return Transformation.SelectionMode.SAME;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return meanTZY!=null && meanTZY.size()==totalTimePointNumber;
    }
}
