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

import boa.ui.ManualEdition;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import boa.plugins.ObjectFeature;
import boa.plugins.ToolTip;
import boa.plugins.TrackPostFilter;
import static boa.plugins.plugins.track_post_filter.PostFilter.MERGE_POLICY_TT;
import boa.utils.ArrayUtil;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.ThreadRunner;
import boa.utils.Utils;
import static boa.utils.Utils.parallele;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public class RemoveTrackByFeature implements TrackPostFilter, ToolTip {
    PluginParameter<ObjectFeature> feature = new PluginParameter<>("Feature", ObjectFeature.class, false).setToolTipText("Feature computed on each object of the track");
    ChoiceParameter statistics = new ChoiceParameter("Statistics", new String[]{"Mean", "Median", "Quantile"}, "mean", false);
    NumberParameter quantile = new BoundedNumberParameter("Quantile", 3, 0.5, 0, 1);
    ConditionalParameter statCond = new ConditionalParameter(statistics).setActionParameters("Quantile", quantile).setToolTipText("Statistics to summarize the distribution of computed features");
    NumberParameter threshold = new NumberParameter("Threshold", 4, 0);
    BooleanParameter keepOverThreshold = new BooleanParameter("Keep over threshold", true).setToolTipText("If true, track will be removed if the statitics value is under the threshold");
    ChoiceParameter mergePolicy = new ChoiceParameter("Merge Policy", Utils.toStringArray(PostFilter.MERGE_POLICY.values()), PostFilter.MERGE_POLICY.ALWAYS_MERGE.toString(), false).setToolTipText(MERGE_POLICY_TT);
    
    @Override
    public String getToolTipText() {
        return "Compute a feature on each object of a track, then a statistic on the distribution, and compare it to a threshold, in order to decided if the track should be removed or not";
    }
    
    
    public RemoveTrackByFeature setMergePolicy(PostFilter.MERGE_POLICY policy) {
        mergePolicy.setSelectedItem(policy.toString());
        return this;
    }
    
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
        Map<Region, Double> valueMap = new ConcurrentHashMap<>();
        // compute feature for each object, by parent
        Consumer<StructureObject> exe = parent -> {
            RegionPopulation pop = parent.getChildRegionPopulation(structureIdx);
            ObjectFeature f = feature.instanciatePlugin();
            f.setUp(parent, structureIdx, pop);
            Map<Region, Double> locValueMap = pop.getRegions().stream().collect(Collectors.toMap(o->o, o-> f.performMeasurement(o)));
            valueMap.putAll(locValueMap);
        };
        ThreadRunner.executeAndThrowErrors(parallele(parentTrack.stream(), true), exe);
        // resume in one value per track
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        List<StructureObject> objectsToRemove = new ArrayList<>();
        for (List<StructureObject> track : allTracks.values()) {
            List<Double> values = Utils.transform(track, so->valueMap.get(so.getRegion()));
            double value;
            switch (statistics.getSelectedIndex()) {
                case 0:
                    value = ArrayUtil.mean(values);
                    break;
                case 1:
                    value = ArrayUtil.median(values);
                    break;
                default:
                    value = ArrayUtil.quantile(values, quantile.getValue().doubleValue());
                    break;
            }
            if (keepOverThreshold.getSelected()) {
                if (value<threshold.getValue().doubleValue()) objectsToRemove.addAll(track);
            } else {
                if (value>threshold.getValue().doubleValue()) objectsToRemove.addAll(track);
            }
        }
        BiPredicate<StructureObject, StructureObject> mergePredicate = PostFilter.MERGE_POLICY.valueOf(mergePolicy.getSelectedItem()).mergePredicate;
        if (!objectsToRemove.isEmpty()) ManualEdition.deleteObjects(null, objectsToRemove, mergePredicate, false); // only delete
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{feature, statCond, threshold, keepOverThreshold, mergePolicy};
    }

    
}
