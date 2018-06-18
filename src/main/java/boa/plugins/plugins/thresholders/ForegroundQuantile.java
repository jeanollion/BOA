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
package boa.plugins.plugins.thresholders;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObjectProcessing;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.ImageMask;
import boa.plugins.SimpleThresholder;
import boa.plugins.Thresholder;
import boa.plugins.ThresholderHisto;
import boa.plugins.ToolTip;

/**
 *
 * @author Jean Ollion
 */
public class ForegroundQuantile implements ThresholderHisto, SimpleThresholder, ToolTip {
    NumberParameter quantile = new BoundedNumberParameter("Foreground quantile", 5, 0.25, 0, 1);
    NumberParameter sigmaParameter = new BoundedNumberParameter("Background Fit Sigma Parameter", 5, 5, 1, null);
    
    @Override
    public String getToolTipText() {
        return "Use BackgroundFit thresholder to assess which part of the value distribution is foreground. Return the quantile value of foreground distribution";
    }
    
    @Override
    public double runThresholderHisto(Histogram histogram) {
        double thld = BackgroundFit.backgroundFit(histogram, sigmaParameter.getValue().doubleValue());
        Histogram fore = histogram.duplicate((int)histogram.getIdxFromValue(thld), histogram.data.length);
        return fore.getQuantiles(quantile.getValue().doubleValue())[0];
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{sigmaParameter, quantile};
    }

    @Override
    public double runSimpleThresholder(Image image, ImageMask mask) {
        return runThresholderHisto(HistogramFactory.getHistogram(()->image.stream(mask, true), HistogramFactory.BIN_SIZE_METHOD.AUTO));
    }

    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        return runSimpleThresholder(input, structureObject.getMask());
    }

    
    
}
