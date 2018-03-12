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
import java.util.List;

/**
 *
 * @author jollion
 */
public interface TrackerSegmenter extends Tracker {
    /**
     * This method segment & assign the children of each element of the {@param parentTrack}, and sets the track links for each child. Segmenter might implement TrackParametrizable, should be honored in this method
     * @param structureIdx index of child structure to be segmented and tracked
     * @param parentTrack parent track, sorted in the order of increasing timePoint
     * @param trackPreFilters filters to apply before segmentation
     * @param postFilters filters to apply after segmentation
     */
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, TrackPreFilterSequence trackPreFilters, PostFilterSequence postFilters);
    /**
     * Optional method, can return null
     * @return the segmenter used for the tracking process
     */
    public Segmenter getSegmenter();
}
