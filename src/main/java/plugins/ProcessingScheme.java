/*
 * Copyright (C) 2015 jollion
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
package plugins;

import configuration.parameters.PostFilterSequence;
import configuration.parameters.PreFilterSequence;
import dataStructure.objects.StructureObject;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author jollion
 */
public interface ProcessingScheme extends Plugin { //Multithreaded
    public ProcessingScheme addPreFilters(PreFilter... preFilters);
    public ProcessingScheme addPostFilters(PostFilter... postFilters);
    public ProcessingScheme addPreFilters(Collection<PreFilter> preFilters);
    public ProcessingScheme addPostFilters(Collection<PostFilter> postFilters);
    public PreFilterSequence getPreFilters();
    public PostFilterSequence getPostFilters();
    public Segmenter getSegmenter();
    //public void segmentThenTrack(int structureIdx, List<StructureObject> parentTrack);
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack);
    public void trackOnly(int structureIdx, List<StructureObject> parentTrack);
}
