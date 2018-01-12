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
package plugins.plugins.trackPostFilter;

import boa.gui.ManualCorrection;
import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import dataStructure.objects.Region;
import dataStructure.objects.RegionPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import plugins.MultiThreaded;
import plugins.ObjectFeature;
import plugins.TrackPostFilter;
import utils.ArrayUtil;
import utils.MultipleException;
import utils.Pair;
import utils.ThreadRunner;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class RemoveTrackByFeature implements TrackPostFilter, MultiThreaded {
    PluginParameter<ObjectFeature> feature = new PluginParameter<>("Feature", ObjectFeature.class, false);
    ChoiceParameter statistics = new ChoiceParameter("Statistics", new String[]{"Mean", "Median", "Quatile"}, "mean", false);
    NumberParameter quantile = new BoundedNumberParameter("Quantile", 3, 0.5, 0, 1);
    ConditionalParameter statCond = new ConditionalParameter(statistics).setActionParameters("Quatile", new Parameter[]{quantile});
    NumberParameter threshold = new NumberParameter("Threshold", 4, 0);
    BooleanParameter keepOverThreshold = new BooleanParameter("Keep over threshold", true);
    ExecutorService executor;
    
    public RemoveTrackByFeature setFeature(ObjectFeature feature, double thld, boolean keepOverThld) {
        this.feature.setPlugin(feature);
        this.threshold.setValue(thld);
        this.keepOverThreshold.setSelected(keepOverThld);
        return this;
    }
    public RemoveTrackByFeature setQuantileValue(double quantile) {
        this.statistics.setSelectedIndex(2);
        this.quantile.setValue(quantile);
        return this;
    }
    public RemoveTrackByFeature setStatistics(int stat) {
        this.statistics.setSelectedIndex(stat);
        return this;
    }
    
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) throws MultipleException {
        if (!feature.isOnePluginSet()) return;
        Map<Region, Double> valueMap = new HashMap<>();
        // compute feature for each object, by parent
        List<Pair<String, Exception>> errors = ThreadRunner.execute(parentTrack, false, (parent, idx) -> {
            RegionPopulation pop = parent.getObjectPopulation(structureIdx);
            ObjectFeature f = feature.instanciatePlugin();
            f.setUp(parent, structureIdx, pop);
            Map<Region, Double> locValueMap = new HashMap<>(pop.getObjects().size());
            for (Region o : pop.getObjects()) locValueMap.put(o, f.performMeasurement(o, null));
            synchronized(valueMap) {
                valueMap.putAll(locValueMap);
            }
        }, executor, null);
        // compute one value per track
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        List<StructureObject> objectsToRemove = new ArrayList<>();
        for (List<StructureObject> track : allTracks.values()) {
            List<Double> values = Utils.transform(track, so->valueMap.get(so.getObject()));
            double value;
            if (statistics.getSelectedIndex()==0) value = ArrayUtil.mean(values);
            else if (statistics.getSelectedIndex()==1) value = ArrayUtil.median(values);
            else value = ArrayUtil.quantile(values, quantile.getValue().doubleValue());
            if (keepOverThreshold.getSelected()) {
                if (value<threshold.getValue().doubleValue()) objectsToRemove.addAll(track);
            } else {
                if (value>threshold.getValue().doubleValue()) objectsToRemove.addAll(track);
            }
        }
        if (!objectsToRemove.isEmpty()) ManualCorrection.deleteObjects(null, objectsToRemove, false); // only delete
        
        if (!errors.isEmpty()) { // throw one exception for all
            throw new MultipleException(errors);
        }
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{feature, statCond, threshold, keepOverThreshold};
    }
    
    @Override
    public void setExecutor(ExecutorService executor) {
        this.executor=executor;
    }
    
}
