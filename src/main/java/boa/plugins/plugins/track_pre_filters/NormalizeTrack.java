/*
 * Copyright (C) 2017 jollion
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
package boa.plugins.plugins.track_pre_filters;

import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObject;
import boa.image.Image;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import boa.plugins.TrackPreFilter;

/**
 *
 * @author jollion
 */
public class NormalizeTrack  implements TrackPreFilter {

    //@Override
    public Map<StructureObject, Image> filter(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages) throws Exception {
        /*List<Image> planes = new ArrayList<>(parentsByF.size());
        for (int t = minT; t<maxT; ++t) planes.add(getImage(t));
        ThresholdHisto thresholdHisto = new ThresholdHisto(planes, minT, null, null);
        double[] minAndMax = new double[2];
        minAndMax[0] = thresholdHisto.minAndMax[0];
        if (blackBackground) minAndMax[1] = ImageOperations.getPercentile(thresholdHisto.histoAll, thresholdHisto.minAndMax, thresholdHisto.byteHisto, 0.99)[0];
        else minAndMax[1] = thresholdHisto.minAndMax[1];
        thresholdHisto.freeMemory();
        double scale = 1 / (minAndMax[1] - minAndMax[0]);
        double offset = -minAndMax[0] * scale;
        if (invert) {
            scale = -scale;
            offset = 1 - offset;
        }
        logger.debug("normalization: range: [{}-{}] scale: {} off: {}", minAndMax[0], minAndMax[1], scale, offset);
        for (int t = minT; t<maxT; ++t) {
            Image trans = ImageOperations.affineOperation(planes.get(t-minT), null, scale, offset);
            inputImages.put(t, trans);
        }
        //this.setParentImages(false);
        so.getPreFilters().removeAllElements(); // preFilters already applied
                */
        return null;
    }

    @Override
    public Parameter[] getParameters() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
