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
package boa.plugins.plugins.processing_pipeline;

import boa.configuration.parameters.PostFilterSequence;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.plugins.PostFilter;
import boa.plugins.PreFilter;
import boa.plugins.TrackPreFilter;
import java.util.Collection;
import boa.plugins.ProcessingPipeline;

/**
 *
 * @author Jean Ollion
 * @param <T>
 */
public abstract class SegmentationProcessingPipeline<T extends SegmentationProcessingPipeline> implements ProcessingPipeline<T> {
    protected PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters").setToolTipText("Pre-filters performed from each parent on raw image of the structure's channel (frame independent)");
    protected TrackPreFilterSequence trackPreFilters = new TrackPreFilterSequence("Track Pre-Filters").setToolTipText("Track-Pre-filters performed after pre-filters, on the whole parent track");
    protected PostFilterSequence postFilters = new PostFilterSequence("Post-Filters").setToolTipText("Post-filters run on each segmented population (frame-independent), after segmentation and before tracking");
    @Override public  T addPreFilters(PreFilter... preFilter) {
        preFilters.add(preFilter);
        return (T)this;
    }
    @Override public  T addTrackPreFilters(TrackPreFilter... trackPreFilter) {
        trackPreFilters.add(trackPreFilter);
        return (T) this;
    }
    @Override public T addTrackPreFilters(Collection<TrackPreFilter> trackPreFilter) {
        trackPreFilters.add(trackPreFilter);
        return (T)this;
    }
    @Override public  T addPostFilters(PostFilter... postFilter) {
        postFilters.add(postFilter);
        return (T)this;
    }
    @Override public  T addPreFilters(Collection<PreFilter> preFilter) {
        preFilters.add(preFilter);
        return (T)this;
    }
    @Override public T addPostFilters(Collection<PostFilter> postFilter){
        postFilters.add(postFilter);
        return (T)this;
    }
    public  T setPreFilters(PreFilterSequence preFilters) {
        this.preFilters=preFilters;
        return (T)this;
    }
    public T setTrackPreFilters(TrackPreFilterSequence trackPreFilters) {
        this.trackPreFilters=trackPreFilters;
        return (T)this;
    }
    public T setPostFilters(PostFilterSequence postFilters) {
        this.postFilters=postFilters;
        return (T)this;
    }
    @Override public PreFilterSequence getPreFilters() {
        return preFilters;
    }
    @Override public TrackPreFilterSequence getTrackPreFilters(boolean addPreFilters) {
        if (addPreFilters && !preFilters.isEmpty()) return trackPreFilters.duplicate().addAtFirst(boa.plugins.plugins.track_pre_filters.PreFilter.splitPreFilterSequence(preFilters));
        else return trackPreFilters;
    }
    @Override public PostFilterSequence getPostFilters() {
        return postFilters;
    }
}
