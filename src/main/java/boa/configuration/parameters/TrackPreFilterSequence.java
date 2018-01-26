/*
 * Copyright (C) 2016 jollion
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
package boa.configuration.parameters;

import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.Image;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import boa.plugins.MultiThreaded;
import boa.plugins.PostFilter;
import boa.plugins.PreFilter;
import boa.plugins.TrackPostFilter;
import boa.plugins.TrackPreFilter;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public class TrackPreFilterSequence extends PluginParameterList<TrackPreFilter> {
    
    public TrackPreFilterSequence(String name) {
        super(name, "Track Pre-Filter", TrackPreFilter.class);
    }
    @Override
    public TrackPreFilterSequence duplicate() {
        TrackPreFilterSequence res = new TrackPreFilterSequence(name);
        res.setContentFrom(this);
        return res;
    }
    
    public TreeMap<StructureObject, Image> filter(int structureIdx, List<StructureObject> parentTrack, ExecutorService executor) throws MultipleException {
        if (parentTrack.isEmpty()) return new TreeMap<>();
        MultipleException e = new MultipleException();
        boolean first = true;
        TreeMap<StructureObject, Image> images = new TreeMap<>(parentTrack.stream().collect(Collectors.toMap(o->o, o->o.getRawImage(structureIdx))));
        for (TrackPreFilter p : this.get()) {
            if (p instanceof MultiThreaded) ((MultiThreaded)p).setExecutor(executor);
            try {
                //logger.debug("executing tpf: {}", p.getClass().getSimpleName());
                p.filter(structureIdx, images, first);
            } catch (MultipleException me) {
                e.getExceptions().addAll(me.getExceptions());
            } catch (Exception ee) {
                e.getExceptions().add(new Pair(parentTrack.get(0).toString(), ee));
            }
            first = false;
        }
        if (!e.getExceptions().isEmpty()) throw e;
        return images;
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
