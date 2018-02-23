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

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.input_image.InputImages;
import boa.image.Image;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import boa.plugins.SimpleThresholder;
import boa.plugins.Transformation;
import boa.plugins.TransformationTimeIndependent;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.image.processing.ImageFeatures;
import boa.utils.ThreadRunner;
import boa.image.ThresholdMask;
import java.util.List;
import boa.plugins.Autofocus;
/**
 *
 * @author jollion
 */
public class SelectBestFocusPlane implements Transformation, Autofocus {
    ArrayList<Integer> bestFocusPlaneIdxT = new ArrayList<Integer>();
    NumberParameter gradientScale = new BoundedNumberParameter("Gradient Scale", 0, 3, 1, 10);
    PluginParameter<SimpleThresholder> signalExclusionThreshold = new PluginParameter<>("Signal Exclusion Threshold", SimpleThresholder.class, new BackgroundThresholder(2.5, 3, 3), true); //new ConstantValue(150)    Parameter[] parameters = new Parameter[]{gradientScale};
    Parameter[] parameters = new Parameter[]{gradientScale, signalExclusionThreshold};
    public SelectBestFocusPlane() {}
    public SelectBestFocusPlane(double gradientScale) {
        this.gradientScale.setValue(gradientScale);
    }
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    @Override
    public void computeConfigurationData(final int channelIdx, final InputImages inputImages)  throws Exception {
        final double scale = gradientScale.getValue().doubleValue();
        final Integer[] conf = new Integer[inputImages.getFrameNumber()];
        if (inputImages.getSizeZ(channelIdx)>1) {
            final ThreadRunner tr = new ThreadRunner(0, conf.length, 0);
            for (int i = 0; i<tr.threads.length; i++) {
                tr.threads[i] = new Thread(
                    new Runnable() {
                        public void run() { 
                            for (int t = tr.ai.getAndIncrement(); t<tr.end; t = tr.ai.getAndIncrement()) {
                                Image image = inputImages.getImage(channelIdx, t);
                                if (image.sizeZ()>1) {
                                    List<Image> planes = image.splitZPlanes();
                                    SimpleThresholder thlder = signalExclusionThreshold.instanciatePlugin();
                                    conf[t] = getBestFocusPlane(planes, scale, thlder, null);
                                    logger.debug("select best focus plane: time:{}, plane: {}", t, conf[t]);
                                }
                            }
                        }
                    }
                );
            }
            tr.startAndJoin();
            tr.throwErrorIfNecessary("");
        }
        bestFocusPlaneIdxT.addAll(Arrays.asList(conf));
    }
    
    
    @Override
    public int getBestFocusPlane(Image image, ImageMask mask) {
        if (image.sizeZ()<=1) return 0;
        return getBestFocusPlane(image.splitZPlanes(), this.gradientScale.getValue().doubleValue(), this.signalExclusionThreshold.instanciatePlugin(), mask);
    }
    
    public static int getBestFocusPlane(List<Image> planes, double scale, SimpleThresholder thlder, ImageMask globalMask) {
        double maxValues = -Double.MAX_VALUE;
        ImageMask mask = null;
        int max=-1;
        for (int zz = 0; zz<planes.size(); ++zz) {
            if (thlder!=null) {
                final ImageMask maskThld = new ThresholdMask(planes.get(zz), thlder.runSimpleThresholder(planes.get(zz), globalMask), true, false);
                final int zzz = zz;
                if (globalMask!=null) mask = new ThresholdMask(planes.get(zz), (x, y, z)->globalMask.insideMask(x, y, zzz)&&maskThld.insideMask(x, y, z), (xy, z)->globalMask.insideMask(xy, zzz)&&maskThld.insideMask(xy, z), true);
                else mask = maskThld;
                if (mask.count()==0) continue;
            } else if (globalMask!=null) {
                final int zzz = zz;
                mask = new ThresholdMask(planes.get(zz), (x, y, z)->globalMask.insideMask(x, y, zzz), (xy, z)->globalMask.insideMask(xy, zzz), true);
            }
            double temp = evalPlane(planes.get(zz), scale, mask);
            if (temp>maxValues) {
                maxValues = temp;
                max = zz;
            }
        }
        logger.debug("get best focus plane: {}/{}", max, planes.size());
        if (max==-1) max = planes.size()/2;
        return max;
    }
    public static double evalPlane(Image plane, double scale, ImageMask mask) {
        Image gradient = ImageFeatures.getGradientMagnitude(plane, scale, false);
        return ImageOperations.getMeanAndSigma(gradient, mask, null)[0];
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (bestFocusPlaneIdxT==null || timePoint>=bestFocusPlaneIdxT.size()) throw new RuntimeException("SelectBestFocusPlane transformation is not configured");
        if (image.sizeZ()>1) return image.getZPlane(bestFocusPlaneIdxT.get(timePoint));
        else return image;
    }

    public ArrayList getConfigurationData() {
        return bestFocusPlaneIdxT;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return bestFocusPlaneIdxT !=null && bestFocusPlaneIdxT.size() == totalTimePointNumber;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}

    
}
