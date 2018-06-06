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
package boa.plugins.plugins.processing_scheme;

import boa.configuration.parameters.TrackPostFilterSequence;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.plugins.ProcessingScheme;
import boa.plugins.ProcessingSchemeWithTracking;
import boa.plugins.TrackPostFilter;
import boa.plugins.plugins.track_pre_filters.PreFilters;
import java.util.Collection;

/**
 *
 * @author Jean Ollion
 */
public abstract class SegmentationAndTrackingProcessingScheme<T extends SegmentationAndTrackingProcessingScheme> extends SegmentationProcessingScheme<T> implements ProcessingSchemeWithTracking<T> {
    protected TrackPostFilterSequence trackPostFilters = new TrackPostFilterSequence("Track Post-Filters").setToolTipText("Post-filters performed after tracking, and the whole parent branch");
    @Override public TrackPreFilterSequence getTrackPreFilters(boolean addPreFilters) {
        if (addPreFilters && !preFilters.isEmpty()) return trackPreFilters.duplicate().addAtFirst(new PreFilters().add(preFilters));
        return trackPreFilters;
    }
    public <T extends ProcessingSchemeWithTracking> T addTrackPostFilters(TrackPostFilter... postFilter) {
        trackPostFilters.add(postFilter);
        return (T)this;
    }
    
    public <T extends ProcessingSchemeWithTracking> T  addTrackPostFilters(Collection<TrackPostFilter> postFilter) {
        trackPostFilters.add(postFilter);
        return (T)this;
    }
    @Override
    public TrackPostFilterSequence getTrackPostFilters() {
        return trackPostFilters;
    }
}
