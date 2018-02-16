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
package boa.plugins.plugins.transformations;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChannelImageParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.input_image.InputImages;
import ij.process.AutoThresholder;
import boa.image.BlankMask;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.image.ThresholdMask;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import static boa.plugins.Plugin.logger;
import boa.plugins.SimpleThresholder;
import boa.plugins.Thresholder;
import boa.plugins.Transformation;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.plugins.plugins.thresholders.ConstantValue;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.utils.ArrayUtil;
import boa.utils.ThreadRunner;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class RemoveStripesSignalExclusion implements Transformation {
    ChannelImageParameter signalExclusion = new ChannelImageParameter("Channel for Signal Exclusion", -1, true);
    PluginParameter<SimpleThresholder> signalExclusionThreshold = new PluginParameter<>("Signal Exclusion Threshold", SimpleThresholder.class, new BackgroundThresholder(2.5, 3, 3), false); //new ConstantValue(150)
    BooleanParameter signalExclusionBool2 = new BooleanParameter("Second Signal Exclusion", false);
    ChannelImageParameter signalExclusion2 = new ChannelImageParameter("Channel for Signal Exclusion 2", -1, false);
    PluginParameter<SimpleThresholder> signalExclusionThreshold2 = new PluginParameter<>("Signal Exclusion Threshold 2", SimpleThresholder.class, new BackgroundThresholder(4, 5, 2), false);
    ConditionalParameter signalExclusionCond = new ConditionalParameter(signalExclusionBool2).setActionParameters("true", new Parameter[]{signalExclusion2, signalExclusionThreshold2});
    BooleanParameter addGlobalMean = new BooleanParameter("Add global mean (avoid negative values)", true);
    BooleanParameter trimNegativeValues = new BooleanParameter("Set Negative values to Zero", false);
    ConditionalParameter addGMCond = new ConditionalParameter(addGlobalMean).setActionParameters("false", new Parameter[]{trimNegativeValues});
    Parameter[] parameters = new Parameter[]{signalExclusion, signalExclusionThreshold, signalExclusionCond, addGMCond};
    float[][][] meanFZY;
    public RemoveStripesSignalExclusion() {}
    
    public RemoveStripesSignalExclusion(int signalExclusion) {
        if (signalExclusion>=0) this.signalExclusion.setSelectedIndex(signalExclusion);
    }
    public RemoveStripesSignalExclusion setAddGlobalMean(boolean addGlobalMean) {
        this.addGlobalMean.setSelected(addGlobalMean);
        return this;
    }
    public RemoveStripesSignalExclusion setTrimNegativeValues(boolean trim) {
        this.trimNegativeValues.setSelected(trim);
        return this;
    }
    public RemoveStripesSignalExclusion setMethod(SimpleThresholder thlder) {
        this.signalExclusionThreshold.setPlugin(thlder);
        return this;
    }
    public RemoveStripesSignalExclusion setSecondSignalExclusion(int channel2, SimpleThresholder thlder) {
        if (channel2>=0 && thlder!=null) {
            this.signalExclusionThreshold2.setPlugin(thlder);
            this.signalExclusion2.setSelectedIndex(channel2);
            this.signalExclusionBool2.setSelected(true);
        } else this.signalExclusionBool2.setSelected(false);
        return this;
    }
    Map<Integer, Image> testMasks, testMasks2;
    @Override
    public void computeConfigurationData(final int channelIdx, final InputImages inputImages)  throws Exception {
        final int chExcl = signalExclusion.getSelectedIndex();
        final int chExcl2 = this.signalExclusionBool2.getSelected() ? signalExclusion2.getSelectedIndex() : -1;
        if (testMode && chExcl>=0) {
            testMasks = new ConcurrentHashMap<>();
            if (chExcl>=0) testMasks2 = new ConcurrentHashMap<>();
        }
        // one threshold for all frames or one threshold per frame ? 
        /*double thld1=Double.NaN, thld2=Double.NaN;
        if (chExcl>=0) {
            long t0 = System.currentTimeMillis();
            List<Integer> imageIdx = InputImages.chooseNImagesWithSignal(inputImages, chExcl, 10); // faire image moyenne = moyenne des thld? 
            long t1= System.currentTimeMillis();
            List<Double> thdls1 = Utils.transform(imageIdx, i->signalExclusionThreshold.instanciatePlugin().runSimpleThresholder(inputImages.getImage(chExcl, i), null));
            long t2 = System.currentTimeMillis();
            logger.debug("choose 10 images: {}, apply bckThlder: {}", t1-t0, t2-t1);
            thld1 = ArrayUtil.median(thdls1);
            if (chExcl2>=0) {
                List<Double> thdls2 = Utils.transform(imageIdx, i->signalExclusionThreshold2.instanciatePlugin().runSimpleThresholder(inputImages.getImage(chExcl2, i), null));
                thld2 = ArrayUtil.median(thdls2);
            }
        }
        final double[] exclThld = chExcl>=0?(chExcl2>=0? new double[]{thld1, thld2} : new double[]{thld1} ) : new double[0];*/
        final boolean addGlobalMean = this.addGlobalMean.getSelected();
        //logger.debug("remove stripes thld: {}", exclThld);
        final ThreadRunner tr = new ThreadRunner(0, inputImages.getFrameNumber());
        meanFZY = new float[inputImages.getFrameNumber()][][];
        for (int i = 0; i<tr.threads.length; i++) {
            tr.threads[i] = new Thread(
                    new Runnable() {  
                    public void run() {
                        for (int frame = tr.ai.getAndIncrement(); frame<tr.end; frame = tr.ai.getAndIncrement()) {
                            Image currentImage = inputImages.getImage(channelIdx, frame);
                            ImageMask m;
                            if (chExcl>=0) {
                                Image se1 = inputImages.getImage(chExcl, frame);
                                double thld1 = signalExclusionThreshold.instanciatePlugin().runSimpleThresholder(se1, null);
                                ThresholdMask mask = currentImage.getSizeZ()>1 && se1.getSizeZ()==1 ? new ThresholdMask(se1, thld1, true, true, 0):new ThresholdMask(se1, thld1, true, true);
                                if (testMode) testMasks.put(frame, TypeConverter.toByteMask(mask, null, 1));
                                if (chExcl2>=0) {
                                    Image se2 = inputImages.getImage(chExcl2, frame);
                                    double thld2 = signalExclusionThreshold2.instanciatePlugin().runSimpleThresholder(se2, null);
                                    ThresholdMask mask2 = currentImage.getSizeZ()>1 && se2.getSizeZ()==1 ? new ThresholdMask(se2, thld2, true, true, 0):new ThresholdMask(se2, thld2, true, true);
                                    if (testMode) testMasks2.put(frame, TypeConverter.toByteMask(mask2, null, 1));
                                    mask = ThresholdMask.or(mask, mask2);
                                }
                                m = mask;
                            } else m = new BlankMask(currentImage);
                            meanFZY[frame] = computeMeanX(currentImage, m, addGlobalMean);
                            if (frame%100==0) logger.debug("tp: {} {}", frame, Utils.getMemoryUsage());
                        }
                    }
                }
            );
        }
        tr.startAndJoin();
        tr.throwErrorIfNecessary("");
        if (testMode) { // make stripes images
            Image[][] stripesTC = new Image[meanFZY.length][1];
            for (int f = 0; f<meanFZY.length; ++f) {
                stripesTC[f][0] = new ImageFloat("removeStripes", inputImages.getImage(channelIdx, f));
                for (int z = 0; z<stripesTC[f][0].getSizeZ(); ++z) {
                    for (int y = 0; y<stripesTC[f][0].getSizeY(); ++y) {
                        for (int x = 0; x<stripesTC[f][0].getSizeX(); ++x) {
                            stripesTC[f][0].setPixel(x, y, z, meanFZY[f][z][y]);
                        }
                    }
                }
            }
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("Stripes", stripesTC);
        }
        if (testMode) {
            if (!testMasks.isEmpty()) {
                Image[][] maskTC = new Image[testMasks.size()][1];
                for (Map.Entry<Integer, Image> e : testMasks.entrySet()) maskTC[e.getKey()][0] = e.getValue();
                ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("Exclusion signal mask", maskTC);
                testMasks.clear();
            }
            if (!testMasks2.isEmpty()) {
                Image[][] maskTC = new Image[testMasks2.size()][1];
                for (Map.Entry<Integer, Image> e : testMasks2.entrySet()) maskTC[e.getKey()][0] = e.getValue();
                ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("Exclusion signal mask2", maskTC);
                testMasks2.clear();
            }
        }
    }
    
    public static float[][] computeMeanX(Image image, ImageMask mask, boolean addGlobalMean) {
        float[][] res = new float[image.getSizeZ()][image.getSizeY()];
        double globalSum=0;
        double globalCount=0;
        for (int z=0; z<image.getSizeZ(); ++z) {
            for (int y = 0; y<image.getSizeY(); ++y) {
                double sum = 0;
                double count = 0;
                for (int x = 0; x<image.getSizeX(); ++x) {
                    if (!mask.insideMask(x, y, z)) {
                        ++count;
                        sum+=image.getPixel(x, y, z);
                    }
                }
                res[z][y] = count>0 ? (float)(sum/count) : 0f;
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
    public static Image removeMeanX(Image source, Image output, float[][] muZY) {
        if (output==null || !output.sameDimensions(source)) {
            output = new ImageFloat("", source);
        }
        for (int z = 0; z<output.getSizeZ(); ++z) {
            for (int y = 0; y<output.getSizeY(); ++y) {
                for (int x = 0; x<output.getSizeX(); ++x) {
                    output.setPixel(x, y, z, source.getPixel(x, y, z)-muZY[z][y]);
                }
            }
        }
        
        return output;
    }
    public static Image removeStripes(Image image, ImageMask exclusionSignalMask, boolean addGlobalMean) {
        return removeMeanX(image, image instanceof ImageFloat ? image : null, computeMeanX(image, exclusionSignalMask, addGlobalMean));
    }
    
    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (meanFZY==null || meanFZY.length<timePoint) throw new RuntimeException("RemoveStripes transformation not configured: "+ (meanFZY==null?"null":  meanFZY.length));
        Image res = removeMeanX(image, image instanceof ImageFloat ? image : null, meanFZY[timePoint]);
        if (trimNegativeValues.getSelected()) ImageOperations.trimValues(res, 0, 0, true);
        if (timePoint%100==0) logger.debug(Utils.getMemoryUsage());
        return res;
    }
    
    @Override
    public Transformation.SelectionMode getOutputChannelSelectionMode() {
        return Transformation.SelectionMode.SAME;
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return meanFZY!=null && meanFZY.length==totalTimePointNumber;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
