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
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.IJAutoThresholder;
import utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class RemoveStripesSignalExclusion implements Transformation {
    ChannelImageParameter signalExclusion = new ChannelImageParameter("Channel for Signal Exclusion", -1, true);
    PluginParameter<SimpleThresholder> signalExclusionThreshold = new PluginParameter<>("Signal Exclusion Threshold", SimpleThresholder.class, new BackgroundThresholder(2.5, 2.5, 2), false); //new ConstantValue(150)
    BooleanParameter addGlobalMean = new BooleanParameter("Add global mean (avoid negative values)", true);
    Parameter[] parameters = new Parameter[]{signalExclusion, signalExclusionThreshold, addGlobalMean};
    List<List<List<Double>>> meanTZY = new ArrayList<>();
    
    public RemoveStripesSignalExclusion() {}
    
    public RemoveStripesSignalExclusion(int signalExclusion) {
        if (signalExclusion>=0) this.signalExclusion.setSelectedIndex(signalExclusion);
    }
    public RemoveStripesSignalExclusion setAddGlobalMean(boolean addGlobalMean) {
        this.addGlobalMean.setSelected(addGlobalMean);
        return this;
    }
    public RemoveStripesSignalExclusion setMethod(SimpleThresholder thlder) {
        this.signalExclusionThreshold.setPlugin(thlder);
        return this;
    }
    
    @Override
    public void computeConfigurationData(final int channelIdx, final InputImages inputImages) {
        final int chExcl = signalExclusion.getSelectedIndex();
        final double exclThld = signalExclusionThreshold.instanciatePlugin().runThresholder(inputImages.getImage(chExcl, inputImages.getDefaultTimePoint()));
        final boolean addGlobalMean = this.addGlobalMean.getSelected();
        //logger.debug("remove stripes thld: {}", exclThld);
        final ThreadRunner tr = new ThreadRunner(0, inputImages.getFrameNumber());
        Double[][][] meanX = new Double[inputImages.getFrameNumber()][][];
        for (int i = 0; i<tr.threads.length; i++) {
            tr.threads[i] = new Thread(
                    new Runnable() {  
                    public void run() {
                        for (int idx = tr.ai.getAndIncrement(); idx<tr.end; idx = tr.ai.getAndIncrement()) {
                            Image signalExclusion=null;
                            if (chExcl>=0) signalExclusion = inputImages.getImage(chExcl, idx);
                            meanX[idx] = computeMeanX(inputImages.getImage(channelIdx, idx), signalExclusion, exclThld, addGlobalMean);
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
    
    public static Double[][] computeMeanX(Image image, Image exclusionSignal, double exclusionThreshold, boolean addGlobalMean) {
        if (exclusionSignal!=null && !image.sameSize(exclusionSignal)) throw new Error("Image and exclusion signal should have same dimensions");
        //ImageWindowManagerFactory.showImage(exclusionMask.duplicate("excl mask"));
        int[] xyz = new int[3];
        Function<int[], Boolean> includeCoord = exclusionSignal==null? c->true : c -> exclusionSignal.getPixel(c[0], c[1], c[2])<exclusionThreshold;
        Double[][] res = new Double[image.getSizeZ()][image.getSizeY()];
        double globalSum=0;
        double globalCount=0;
        for (int z=0; z<image.getSizeZ(); ++z) {
            xyz[2]=z;
            for (int y = 0; y<image.getSizeY(); ++y) {
                xyz[1]=y;
                double sum = 0;
                double count = 0;
                for (int x = 0; x<image.getSizeX(); ++x) {
                    xyz[0]=x;
                    if (includeCoord.apply(xyz)) {
                        ++count;
                        sum+=image.getPixel(x, y, z);
                    }
                }
                res[z][y] = count>0 ? sum/count : 0;
                globalSum+=sum;
                globalCount+=count;
            }
        }
        if (addGlobalMean) {
            double globalMean = globalCount>0?globalSum/globalCount : 0;
            for (int z=0; z<image.getSizeZ(); ++z) {
                for (int y = 0; y<image.getSizeY(); ++y) {
                    res[z][y]-=globalMean;
                }
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
