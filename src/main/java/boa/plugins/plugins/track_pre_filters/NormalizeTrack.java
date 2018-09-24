/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.track_pre_filters;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObject;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.processing.ImageOperations;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import boa.plugins.TrackPreFilter;
import java.util.ArrayList;
import java.util.Map.Entry;

/**
 *
 * @author jollion
 */
public class NormalizeTrack  implements TrackPreFilter {
    NumberParameter saturation = new BoundedNumberParameter("Saturation", 3, 0.99, 0, 1);
    BooleanParameter invert = new BooleanParameter("Invert", false);
    public NormalizeTrack() {}
    public NormalizeTrack(double saturation, boolean invert) {
        this.saturation.setValue(saturation);
        this.invert.setSelected(invert);
    }
    @Override
    public void filter(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages, boolean canModifyImage) {
        Histogram histo = HistogramFactory.getHistogram(()->Image.stream(preFilteredImages.values()).parallel(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
        double[] minAndMax = new double[2];
        minAndMax[0] = histo.min;
        if (saturation.getValue().doubleValue()<1) minAndMax[1] = histo.getQuantiles(saturation.getValue().doubleValue())[0];
        else minAndMax[1] = histo.getMaxValue();
        double scale = 1 / (minAndMax[1] - minAndMax[0]);
        double offset = -minAndMax[0] * scale;
        if (invert.getSelected()) {
            scale = -scale;
            offset = 1 - offset;
        }
        logger.debug("normalization: range: [{}-{}] scale: {} off: {}", minAndMax[0], minAndMax[1], scale, offset);
        for (Entry<StructureObject, Image> e : preFilteredImages.entrySet()) {
            Image trans = ImageOperations.affineOperation(e.getValue(), canModifyImage?e.getValue():null, scale, offset);
            e.setValue(trans);
        }
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{saturation, invert};
    }
    
}
