/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.transformations;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import static boa.core.TaskRunner.logger;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import ij.process.AutoThresholder;
import boa.image.BlankMask;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.image.ThresholdMask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import boa.measurement.BasicMeasurements;
import boa.plugins.SimpleThresholder;
import boa.plugins.Thresholder;
import boa.plugins.Transformation;
import boa.plugins.plugins.thresholders.BackgroundFit;
import static boa.plugins.plugins.thresholders.BackgroundFit.smooth;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.plugins.plugins.thresholders.ConstantValue;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.image.processing.Filters;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.ReusableQueue;
import boa.utils.ThreadRunner;
import boa.utils.ThreadRunner.ThreadAction;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class SaturateHistogramHyperfluoBacteria implements Transformation {
    PluginParameter<SimpleThresholder> thresholdBck = new PluginParameter<>("Background Threshold", SimpleThresholder.class, new BackgroundThresholder(3, 6, 3), false); //new ConstantValue(50)
    PluginParameter<SimpleThresholder> thresholdHyper = new PluginParameter<>("HyperFluo Threshold", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false); //new ConstantValue(50)
    NumberParameter foregroundProportion = new BoundedNumberParameter("Hyperfluorecent cells foreground proportion threshold", 2, 0.45, 0, 1); 
    NumberParameter minimumVolume = new BoundedNumberParameter("Minimum volume of signal (pixels)", 0, 10000, 1, null);
    Parameter[] parameters = new Parameter[]{thresholdBck, thresholdHyper, foregroundProportion, minimumVolume};
    ArrayList<Double> configData = new ArrayList<>(2);
    
    public SaturateHistogramHyperfluoBacteria setForegroundProportion(double proportion) {
        this.foregroundProportion.setValue(proportion);
        return this;
    }
    
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        configData.clear();
        List<Image> allImages = new ArrayList<>();
        //List<Image> imageTemp = new ArrayList<>();
        int tpMax = inputImages.getFrameNumber();
        //int count =0;
        for (int f = 0; f<tpMax; ++f) {
            Image<? extends Image> image = inputImages.getImage(channelIdx, f);
            if (image.getSizeZ()>1) {
                int plane = inputImages.getBestFocusPlane(f);
                if (plane<0) throw new RuntimeException("SaturateHistogramHyperFluoBacteria can only be run on 2D images AND no autofocus algorithm was set");
                image = image.splitZPlanes().get(plane);
            }
            allImages.add(image);
            /*if (im.getSizeZ()>1) imageTemp.addAll(im.splitZPlanes());
            else imageTemp.add(im);
            if (count==imageN) {
                count=0;
                allImages.add(Image.mergeZPlanes(imageTemp));
                imageTemp.clear();
            } else ++count;*/
        }
        if (allImages.isEmpty()) {
            configData.add(Double.NaN);
            logger.error("No image");
            return;
        }
        double pThld = foregroundProportion.getValue().doubleValue();
        long t0 = System.currentTimeMillis();
        int minimumCount = minimumVolume.getValue().intValue();
        Double[] thlds = new Double[allImages.size()];
        ReusableQueue.Reset<ImageByte> r = im -> {ImageOperations.fill(im, 0, null); return im;};
        ReusableQueue<ImageByte> masks = new ReusableQueue<>(()->new ImageByte("", allImages.get(0)), r);
        ThreadRunner.execute(allImages, false, (Image image, int idx) -> {
            ImageByte mask = masks.pull();
            thlds[idx] = getThld(image, pThld, thresholdBck.instanciatePlugin(), thresholdHyper.instanciatePlugin() , mask, minimumCount, idx);
            masks.push(mask);
        });
        List<Double> thldsList = new ArrayList<>(Arrays.asList(thlds));
        thldsList.removeIf(d -> Double.isInfinite(d) || Double.isNaN(d));
        if (testMode) logger.debug("SaturateHisto: #satu: {},, list: {}", thldsList.size(), thldsList);
        double thld = ArrayUtil.median(thldsList);
        //double thld = Arrays.stream(thlds).min((d1, d2)->Double.compare(d1, d2)).get();
        long t1 = System.currentTimeMillis();
        if (Double.isFinite(thld)) configData.add(thld);
        else configData.add(Double.NaN);
        logger.debug("SaturateHistoAuto: {} ({}ms)", Utils.toStringList(configData), t1-t0);
    }
    public void saturateHistogram(Image im) {
        double thld = getThld(im, this.foregroundProportion.getValue().doubleValue(), this.thresholdBck.instanciatePlugin(), this.thresholdHyper.instanciatePlugin(), null, this.minimumVolume.getValue().intValue(), 0);
        if (!Double.isNaN(thld) && Double.isFinite(thld)) SaturateHistogram.saturate(thld, thld, im);
    }
    private double getThld(Image im, double proportionThld, SimpleThresholder thlderBack, SimpleThresholder thlderHyper, ImageInteger backThld, int minimumCount, int idx) {
        double thldBack = thlderBack.runSimpleThresholder(im, null);
        double thldHyper = thlderHyper.runSimpleThresholder(im, null);
        backThld=ImageOperations.threshold(im, thldBack, true, true, false, backThld);
        ImageMask hyperThld = new ThresholdMask(im, thldHyper, true, true);
        // remove small obejcts (if background is too low isolated pixels)
        ImageOperations.filterObjects(backThld, backThld, o->o.getSize()<=1);
        double count = backThld.count();
        if (count<minimumCount) return Double.POSITIVE_INFINITY;
        double countHyper = hyperThld.count();
        if (testMode) logger.debug("idx:{} thldBack:{} hyper: {}, count back: {}, hyper: {}, prop: {}", idx, thldBack, thldHyper, count, countHyper, countHyper / count);
        double proportion = countHyper / count;
        if (proportion<proportionThld && count-countHyper>minimumCount) { // recompute hyper thld within seg bact
            ImageMask thldMask = new ThresholdMask(im, thldBack, true, true);
            double thldHyper2 = IJAutoThresholder.runThresholder(im, thldMask, AutoThresholder.Method.Otsu);
            // ThldHyper is over-estimated. ThldHyper = maximum value of objects that do not contain pixel over ThldHyp +1
            List<Region> objects = ImageLabeller.labelImageList(thldMask);
            List<Double> maxValues = Utils.transform(objects, o->BasicMeasurements.getMaxValue(o, im));
            maxValues.removeIf(v->v>=thldHyper2);
            double thldHyper3 = maxValues.isEmpty() ? thldHyper2 : Math.min(Collections.max(maxValues)*1.15, thldHyper2);
            if (testMode) logger.debug("SaturateHisto: {} proportion: {} (of total image: {}) back {}, thldHyper: {} (on whole image: {}), max values of non-saturated objects: {}", idx, proportion, count/im.getSizeXYZ(), thldBack, thldHyper2, thldHyper,thldHyper3);
            return thldHyper3;
        }
        else return Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return configData.size()==1;
    }
    

    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (Double.isNaN(configData.get(0))) return image;
        SaturateHistogram.saturate(configData.get(0), configData.get(0), image);
        return image;
    }

    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
