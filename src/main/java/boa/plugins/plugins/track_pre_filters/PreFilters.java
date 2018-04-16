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

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.StructureObject;
import boa.image.Image;
import boa.plugins.MultiThreaded;
import boa.plugins.PreFilter;
import boa.plugins.TrackPreFilter;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.ThreadRunner;
import static boa.utils.Utils.parallele;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 *
 * @author jollion
 */
public class PreFilters implements TrackPreFilter, MultiThreaded {
    PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters");
    
    // multithreaded interface
    boolean multithreaded;
    @Override
    public void setMultithread(boolean multithreaded) {
        this.multithreaded=multithreaded;
    }
    
    @Override
    public void filter(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages, boolean canModifyImages) {
        if (preFilters.isEmpty()) return;
        Consumer<Entry<StructureObject, Image>> c  = e->e.setValue(preFilters.filter(e.getValue(), e.getKey().getMask()));
        parallele(preFilteredImages.entrySet().stream(), multithreaded).forEach(c);
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{preFilters};
    }
    public PreFilters removeAll() {
        preFilters.removeAllElements();
        return this;
    }
    public PreFilters add(PreFilterSequence sequence) {     
        preFilters.add(sequence.get());
        return this;
    }
    public PreFilters add(PreFilter... instances) {
        preFilters.add(instances);
        return this;
    }
    
    public PreFilters add(Collection<PreFilter> instances) {
        preFilters.add(instances);
        return this;
    }
}
