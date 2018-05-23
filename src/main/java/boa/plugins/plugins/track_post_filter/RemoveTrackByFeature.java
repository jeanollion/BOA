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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import boa.plugins.MultiThreaded;
import boa.plugins.ObjectFeature;
import boa.plugins.TrackPostFilter;
import boa.utils.ArrayUtil;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.ThreadRunner;
import boa.utils.Utils;
import static boa.utils.Utils.parallele;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 *
 * @author jollion
 */
public class RemoveTrackByFeature implements TrackPostFilter, MultiThreaded {
    PluginParameter<ObjectFeature> feature = new PluginParameter<>("Feature", ObjectFeature.class, false);
    ChoiceParameter statistics = new ChoiceParameter("Statistics", new String[]{"Mean", "Median", "Quantile"}, "mean", false);
    NumberParameter quantile = new BoundedNumberParameter("Quantile", 3, 0.5, 0, 1);
    ConditionalParameter statCond = new ConditionalParameter(statistics).setActionParameters("Quantile", new Parameter[]{quantile});
    NumberParameter threshold = new NumberParameter("Threshold", 4, 0);
    BooleanParameter keepOverThreshold = new BooleanParameter("Keep over threshold", true);
    ChoiceParameter mergePolicy = new ChoiceParameter("Merge Policy", Utils.toStringArray(PostFilter.MERGE_POLICY.values()), PostFilter.MERGE_POLICY.ALWAYS_MERGE.toString(), false).setToolTipText("When removing an object/track that has a previous object (p) that was linked to this object and one other object (n). p is now linked to one single object n. This parameter controls wheter / in which conditions should p's track and n's track be merged.<br/><ul><li>NEVER_MERGE: never merge tracks</li><li>ALWAYS_MERGE: always merge tracks</li><li>MERGE_TRACKS_SIZE_COND: merge tracks only if size(n)>0.8 * size(p) (useful for bacteria linking)</li></ul>");

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
        Map<Region, Double> valueMap = new HashMap<>();
        // compute feature for each object, by parent
        Consumer<StructureObject> exe = parent -> {
            RegionPopulation pop = parent.getObjectPopulation(structureIdx);
            ObjectFeature f = feature.instanciatePlugin();
            f.setUp(parent, structureIdx, pop);
            Map<Region, Double> locValueMap = new HashMap<>(pop.getRegions().size());
            for (Region o : pop.getRegions()) locValueMap.put(o, f.performMeasurement(o));
            synchronized(valueMap) {
                valueMap.putAll(locValueMap);
            }
        };
        ThreadRunner.exexcuteAndThrowErrors(parallele(parentTrack.stream(), multithreaded), exe);
        // compute one value per track
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
        if (!objectsToRemove.isEmpty()) ManualCorrection.deleteObjects(null, objectsToRemove, mergePredicate, false); // only delete
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{feature, statCond, threshold, keepOverThreshold, mergePolicy};
    }
    // multithreaded interface
    boolean multithreaded;
    @Override
    public void setMultithread(boolean multithreaded) {
        this.multithreaded=multithreaded;
    }
}
