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
import boa.image.Image;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.List;
import boa.plugins.ConfigurableTransformation;
import boa.plugins.plugins.thresholders.HistogramAnalyzer;
import static boa.utils.Utils.parallele;
import java.util.stream.IntStream;

/**
 *
 * @author jollion
 */
public class SaturateHyperfluoBacteria implements ConfigurableTransformation {
    NumberParameter maxSignalProportion = new BoundedNumberParameter("Maximum Saturated Signal Amount Proportion", 3, 0.2, 0, 1).setToolTipText("Condition on amount of signal for detection of hyperfluo. bacteria: <br />Total amount of foreground signal / amount of Hyperfluo signal &lt; this threshold"); 
    NumberParameter minSignalRatio = new BoundedNumberParameter("Minimum Signal Ratio", 2, 3, 2, null).setToolTipText("Condition on signal value for detection of hyperfluo. bacteria: <br />Mean Hyperfluo signal / Mean Foreground signal > this threshold");
    //NumberParameter frameInterval = new BoundedNumberParameter("Frame Interval", 0, 2000, 1, null).setToolTipText("Frames are grouped by interval and saturated data is searched at each interval");
    
    Parameter[] parameters = new Parameter[]{maxSignalProportion, minSignalRatio};
    double saturateValue= Double.NaN;
    boolean configured = false;
    
    public SaturateHyperfluoBacteria setForegroundProportion(double maxSignalAmountProportion, double minSignalRatio) {
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
        
        Histogram histo = Histogram.getHisto256(allImages, null);
        HistogramAnalyzer ha = new HistogramAnalyzer(histo, true).setVerbose(testMode);
        if (testMode) ha.plot();
        saturateValue = ha.getSaturationThreshold(minSignalRatio.getValue().doubleValue(), maxSignalProportion.getValue().doubleValue());
        /*
        double[] mm = ImageOperations.getMinAndMax(allImages);
        int frameInterval = this.frameInterval.getValue().intValue();
        
        List<Image> combinedImages = new ArrayList<>(fMax/frameInterval + 1 );
        if (frameInterval==1) combinedImages = allImages;
        else for (int f = 0; f<fMax; f+=frameInterval)  {
            int fEnd = f+2*frameInterval>fMax ? fMax : f+frameInterval; // minimum frameInterval per range
            combinedImages.add(Image.mergeZPlanes(allImages.subList(f, fEnd)));
        }
        saturateValue = parallele(combinedImages.stream(), !testMode).mapToDouble(im -> {
           Histogram histo = im.getHisto256(mm[0], mm[1], null, null);
           
           return ha.getSaturationThreshold(minSignalRatio.getValue().doubleValue(), maxSignalProportion.getValue().doubleValue());
        }).filter(d->!Double.isNaN(d)).min().orElse(Double.NaN);*/
        
        long t1 = System.currentTimeMillis();
        configured = true;
        logger.debug("SaturateHistoAuto: {} ({}ms)",saturateValue, t1-t0);
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
