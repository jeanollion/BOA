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
package boa.plugins.plugins.track_post_filter;

import boa.gui.ManualCorrection;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import ij.process.AutoThresholder;
import boa.image.Histogram;
import boa.image.Image;
import boa.image.processing.ImageOperations;
import boa.image.ThresholdMask;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import boa.plugins.TrackPostFilter;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.utils.ArrayUtil;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class RemoveMicrochannelsWithOverexpression implements TrackPostFilter {
    
    BoundedNumberParameter intensityPercentile = new BoundedNumberParameter("Intensity Quantile", 2, 99, 90, 100).setToolTipText("(%) quantile of intensity withing microchannel");
    BoundedNumberParameter interQuartileFactor = new BoundedNumberParameter("Inter-Quartile Factor", 2, 5, 0.5, null).setToolTipText("Threshold will be Median value + Inter-Quartile Range * Inter-Quartile Factor. Over this threshold a microchannel is consider to contain cells overexpressing the fluorescent protein");
    BooleanParameter trim = new BooleanParameter("Remove Method", "Trim Track", "Remove Whole Track", true);
    Parameter[] parameters = new Parameter[]{intensityPercentile, interQuartileFactor, trim};
    final static int successiveSaturated = 3;
    public RemoveMicrochannelsWithOverexpression() {}
    public RemoveMicrochannelsWithOverexpression(double intensityPercentile, double interQuartileFactor) {
        this.intensityPercentile.setValue(intensityPercentile);
        this.interQuartileFactor.setValue(interQuartileFactor);
    }
    public RemoveMicrochannelsWithOverexpression setTrim(boolean trim) {
        this.trim.setSelected(trim);
        return this;
    }
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) {
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        double per = intensityPercentile.getValue().doubleValue()/100d;
        Map<StructureObject, Double> value = Utils.flattenMap(allTracks).stream().collect(Collectors.toMap(o->o, o->ImageOperations.getQuantiles(o.getRawImage(structureIdx), o.getMask(), null, per)[0]));
        List<Double> distribution = new ArrayList<>(value.values());
        double quart1 = ArrayUtil.quantile(distribution, 0.25);
        double quart2 = ArrayUtil.quantile(distribution, 0.5);
        double quart3 = ArrayUtil.quantile(distribution, 0.75);
        double thld = quart2 + (quart3-quart1) * interQuartileFactor.getValue().doubleValue();
        if (testMode) logger.debug("RemoveMicrochannelsWithOverexpression Q1: {}, Q2: {}, Q3: {}, Thld: {}", quart1, quart2, quart3, thld);
        List<StructureObject> objectsToRemove = new ArrayList<>();
        for (Entry<StructureObject, List<StructureObject>> e : allTracks.entrySet()) {
            int sat = 0;
            int idx = 0;
            for (StructureObject o : e.getValue()) {
                if (value.get(o)>thld) ++sat;
                if (sat>=successiveSaturated) {
                    if (trim.getSelected()) {
                        int idxStart = idx-sat+1;
                        if (idxStart==1) idxStart = 0;
                        objectsToRemove.addAll(e.getValue().subList(idx-sat+1, e.getValue().size()));
                    } else objectsToRemove.addAll(e.getValue());
                }
                ++idx;
            }
        }
        //logger.debug("remove track trackLength: #objects to remove: {}", objectsToRemove.size());
        if (!objectsToRemove.isEmpty()) ManualCorrection.deleteObjects(null, objectsToRemove, false);
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    boolean testMode;
    public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
