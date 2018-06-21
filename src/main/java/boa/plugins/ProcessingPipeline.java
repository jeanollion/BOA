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
package boa.plugins;

import boa.configuration.parameters.PostFilterSequence;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.data_structure.StructureObject;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import boa.utils.Pair;

/**
 *
 * @author jollion
 * @param <T> type of ProcessingScheme
 */
public interface ProcessingPipeline<T extends ProcessingPipeline> extends Plugin { //Multithreaded
    public  T addPreFilters(PreFilter... preFilters);
    public  T addPostFilters(PostFilter... postFilters);
    public  T addTrackPreFilters(TrackPreFilter... trackPreFilters);
    public  T addPreFilters(Collection<PreFilter> preFilters);
    public  T addPostFilters(Collection<PostFilter> postFilters);
    public  T addTrackPreFilters(Collection<TrackPreFilter> trackPreFilters);
    public TrackPreFilterSequence getTrackPreFilters(boolean addPreFilters);
    public PreFilterSequence getPreFilters();
    public PostFilterSequence getPostFilters();
    public Segmenter getSegmenter();
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack);
    public void trackOnly(int structureIdx, List<StructureObject> parentTrack);
}
