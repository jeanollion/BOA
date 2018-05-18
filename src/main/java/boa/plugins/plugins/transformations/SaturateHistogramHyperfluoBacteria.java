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
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.List;
import boa.plugins.ConfigurableTransformation;
import boa.plugins.SimpleThresholder;
import boa.plugins.plugins.thresholders.BackgroundFit;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.plugins.plugins.thresholders.HistogramAnalyzer;
import static boa.utils.Utils.parallele;
import java.util.stream.IntStream;

/**
 *
 * @author jollion
 */
public class SaturateHistogramHyperfluoBacteria implements ConfigurableTransformation {
    PluginParameter<SimpleThresholder> thresholder = new PluginParameter<>("Background Threshold", SimpleThresholder.class, new BackgroundFit(10), false); 
    NumberParameter maxSignalProportion = new BoundedNumberParameter("Maximum Saturated Signal Amount Proportion", 3, 0.2, 0, 1).setToolTipText("Condition on amount of signal for detection of hyperfluo. bacteria: <br />Total amount of foreground signal / amount of Hyperfluo signal &lt; this threshold"); 
    NumberParameter minSignalRatio = new BoundedNumberParameter("Minimum Signal Ratio", 2, 7.5, 2, null).setToolTipText("Condition on signal value for detection of hyperfluo. bacteria: <br />Mean Hyperfluo signal / Mean Foreground signal > this threshold");
    
    Parameter[] parameters = new Parameter[]{thresholder, maxSignalProportion, minSignalRatio};
    double saturateValue= Double.NaN;
    boolean configured = false;
    
    public SaturateHistogramHyperfluoBacteria setForegroundProportion(double maxSignalAmountProportion, double minSignalRatio) {
        this.maxSignalProportion.setValue(maxSignalAmountProportion);
        this.minSignalRatio.setValue(minSignalRatio);
        return this;
    }
    
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        List<Image> allImages = new ArrayList<>();
        //List<Image> imageTemp = new ArrayList<>();
        int fMax = inputImages.getFrameNumber();
        //int count =0;
        for (int f = 0; f<fMax; ++f) {
            Image<? extends Image> image = inputImages.getImage(channelIdx, f);
            if (image.sizeZ()>1) {
                int plane = inputImages.getBestFocusPlane(f);
                if (plane<0) throw new RuntimeException("SaturateHistogramHyperFluoBacteria can only be run on 2D images AND no autofocus algorithm was set");
                image = image.splitZPlanes().get(plane);
            }
            allImages.add(image);
        }
        if (allImages.isEmpty()) {
            logger.error("No image");
            return;
        } else logger.debug("saturate histo: images: {}", allImages.size());
        long t0 = System.currentTimeMillis();
        
        Histogram histo = HistogramFactory.getHistogram(()->Image.stream(allImages).parallel(), HistogramFactory.allImagesAreInteger(allImages));
        double[] bckMuStd = new double[2];
        double bckThld = thresholder.instanciatePlugin().runSimpleThresholder(Image.mergeZPlanes(allImages), null);
        Histogram histoFore = histo.duplicate((int)histo.getIdxFromValue(bckThld)+1, histo.data.length);
        double foreThld = histoFore.getQuantiles(0.5)[0];
        
        double satThld = bckMuStd[0] + (foreThld - bckMuStd[0]) * this.minSignalRatio.getValue().doubleValue();
        if (satThld<histo.getMaxValue()) {
            // condition on signal amount
            double satSignal = histo.count((int)histo.getIdxFromValue(satThld), histo.data.length);
            double totalSignal = histo.count((int)histo.getIdxFromValue(bckThld), histo.data.length);
            logger.debug("sat signal proportion: {}, ", satSignal / totalSignal);
            if (maxSignalProportion.getValue().doubleValue() * totalSignal > satSignal) {
                saturateValue = satThld;
            }
        }
         
        long t1 = System.currentTimeMillis();
        configured = true;
        logger.debug("SaturateHistoAuto: {}  , bck : {}, thld: {} fore: {}, saturation thld: {}, image range: {} computation time {}ms",saturateValue, bckMuStd[0], bckThld, foreThld, satThld, new double[]{histo.min, histo.getMaxValue()}, t1-t0);
    }
    
    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return configured;
    }

    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (Double.isNaN(saturateValue)) return image;
        SaturateHistogram.saturate(saturateValue, saturateValue, image);
        return image;
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
