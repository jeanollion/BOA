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
package boa.configuration.parameters;

import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.Image;
import java.util.Collection;
import java.util.List;
import boa.plugins.TrackPreFilter;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public class TrackPreFilterSequence extends PluginParameterList<TrackPreFilter, TrackPreFilterSequence> {
    
    public TrackPreFilterSequence(String name) {
        super(name, "Track Pre-Filter", TrackPreFilter.class);
    }
    @Override
    public TrackPreFilterSequence duplicate() {
        TrackPreFilterSequence res = new TrackPreFilterSequence(name);
        res.setContentFrom(this);
        return res;
    }
    private static boolean allPFImagesAreSet(List<StructureObject> parentTrack, int structureIdx) {
        for (StructureObject o : parentTrack) if (o.getPreFilteredImage(structureIdx)==null) return false;
        return true;
    }
    public void filter(int structureIdx, List<StructureObject> parentTrack) throws MultipleException {
        if (parentTrack.isEmpty()) return;
        if (isEmpty() && allPFImagesAreSet(parentTrack, structureIdx)) return; // if no preFilters &  only add raw images if no prefiltered image is present
        boolean first = true;
        TreeMap<StructureObject, Image> images = new TreeMap<>(parentTrack.stream().collect(Collectors.toMap(o->o, o->o.getRawImage(structureIdx))));
        for (TrackPreFilter p : this.get()) {
            p.filter(structureIdx, images, !first);
            first = false;
        }
        for (Entry<StructureObject, Image> en : images.entrySet()) {
            en.getKey().setPreFilteredImage(en.getValue(), structureIdx);
        }
    }
    @Override public TrackPreFilterSequence addAtFirst(TrackPreFilter... instances) {
        super.add(instances);
        return this;
    }
    @Override public TrackPreFilterSequence add(TrackPreFilter... instances) {
        super.add(instances);
        return this;
    }
    
    @Override public TrackPreFilterSequence add(Collection<TrackPreFilter> instances) {
        super.add(instances);
        return this;
    }
}
