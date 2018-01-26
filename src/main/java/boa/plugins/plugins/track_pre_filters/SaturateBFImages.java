/*
 * Copyright (C) 2018 jollion
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
import boa.image.Histogram;
import boa.image.Image;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.plugins.TrackPreFilter;
import static boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.logger;
import boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.ThresholdHisto;
import ij.process.AutoThresholder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
public class SaturateBFImages implements TrackPreFilter {

    @Override
    public void filter(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages, boolean canModifyImages) throws Exception {
        Map<Image, ImageMask> maskMap = TrackPreFilter.getMaskMap(preFilteredImages);
        Histogram histo = Histogram.getHisto256(maskMap, null);
        ... todo: saturate method cf threshold histo
        ThresholdHisto thresholdHisto = new ThresholdHisto(planes, minT, null, AutoThresholder.Method.MaxEntropy);
            double saturateValue = thresholdHisto.saturateValue;
            logger.debug("saturate value: {}", saturateValue);
            int idx = 0;
            for (StructureObject o : this.parentsByF.values()) {
                Image trans = planes.get(idx++);
                trans = trans.duplicate();
                ImageOperations.trimValues(trans, saturateValue, saturateValue, false);
                trans = this.preFilters.filter(trans, o.getMask());
                inputImages.put(o.getFrame(), trans);
            }
            //this.setParentImages(false);
            so.getPreFilters().removeAllElements(); // preFilters already applied
            saturated = true;
        
    }

    @Override
    public Parameter[] getParameters() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
