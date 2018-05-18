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
package boa.plugins.plugins.track_pre_filters;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObject;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.plugins.ToolTip;
import boa.plugins.TrackPreFilter;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import static boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.logger;
import ij.process.AutoThresholder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
public class Saturate implements TrackPreFilter, ToolTip {
    NumberParameter maxSat = new BoundedNumberParameter("Max Saturation proportion", 3, 0.03, 0, 1);
    String toolTip = "<html>Saturation of bright values in Bright Field images. <br />Performed on all images of a track are considered at once. <br />A threshold is computed on the histogram of all images, using the MaxEntropy method. <br />The proportion of saturated pixels should not be higer than indicated in the \"Max Saturation proportion\" parameter.</html>";
    
    public Saturate() {}
    public Saturate(double maxProportion) {
        this.maxSat.setValue(maxProportion);
    }
    
    @Override
    public void filter(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages, boolean canModifyImages) {
        Map<Image, ImageMask> maskMap = TrackPreFilter.getMaskMap(preFilteredImages);
        Histogram histo = HistogramFactory.getHistogram(()->Image.stream(maskMap, true).parallel(), HistogramFactory.allImagesAreInteger(maskMap.keySet()));
        double sv = IJAutoThresholder.runThresholder(AutoThresholder.Method.MaxEntropy, histo); //Shanbhag
        double svBin = (int)histo.getIdxFromValue(sv);
        // limit to saturagePercentage
        double sat = histo.getQuantiles(1-maxSat.getValue().doubleValue())[0];
        double satBin = histo.getIdxFromValue(sat);
        if (satBin>svBin) {
            svBin = satBin;
            sv = sat;
        }
        for (int i = (int)svBin; i<histo.data.length; ++i) histo.data[i]=0;
        logger.debug("saturate value: {}", sv);
        for (Entry<StructureObject, Image> e : preFilteredImages.entrySet()) {
            Image im = e.getValue();
            if (!canModifyImages) im = im.duplicate();
            ImageOperations.trimValues(im, sv, sv, false);
            if (!canModifyImages) e.setValue(im);
        }
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{maxSat};
    }

    @Override
    public String getToolTipText() {
        return toolTip;
    }
    
}
