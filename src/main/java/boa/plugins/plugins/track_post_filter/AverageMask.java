/*
 * Copyright (C) 2017 jollion
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
package boa.plugins.plugins.track_post_filter;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PostFilterSequence;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.BlankMask;
import boa.image.ImageFloat;
import boa.image.ImageInt;
import boa.image.ImageInteger;
import boa.image.processing.ImageOperations;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import boa.plugins.TrackPostFilter;

/**
 *
 * @author jollion
 */
public class AverageMask implements TrackPostFilter{
    ChoiceParameter referencePoint = new ChoiceParameter("Reference Point", new String[]{"Upper-left corner"}, "Upper-left corner", false);
    //PostFilterSequence postFilters = new PostFilterSequence("Post-Filters");
    //BooleanParameter postFilterOnAverageImage = new BooleanParameter("Run post-filters on", "Average Image Over Frames", "Each Frame", true);
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) throws Exception {
        Map<StructureObject, List<StructureObject>> allTracks=  StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        for (List<StructureObject> track : allTracks.values()) averageMask(track);
    }
    private void averageMask(List<StructureObject> track) {
        if (referencePoint.getSelectedIndex()==0) { // upper left corner
            // size = maximal size
            int maxX = Collections.max(track, (o1, o2)->Integer.compare(o1.getMask().getSizeX(), o2.getMask().getSizeX())).getMask().getSizeX();
            int maxY = Collections.max(track, (o1, o2)->Integer.compare(o1.getMask().getSizeY(), o2.getMask().getSizeY())).getMask().getSizeY();
            int maxZ = Collections.max(track, (o1, o2)->Integer.compare(o1.getMask().getSizeZ(), o2.getMask().getSizeZ())).getMask().getSizeZ();
            ImageInteger sum = ImageInteger.createEmptyLabelImage("average mask", track.size()+1, new BlankMask("", maxX, maxY, maxZ));
            for (StructureObject o : track) {
                ImageInteger mask = o.getMask();
                for (int z = 0; z<sum.getSizeZ(); ++z) {
                    for (int y=0; y<sum.getSizeY(); ++y) {
                        for (int x=0; x<sum.getSizeX(); ++x) {
                            if (mask.contains(x, y, z)) sum.setPixel(x, y, z, sum.getPixel(x, y, z)+mask.getPixel(x, y, z));
                        }
                    }
                }
            }
            int threshold = track.size()/2;
            sum = ImageOperations.threshold(sum, threshold, true, false);
            for (StructureObject o : track) {
                ImageInteger mask = o.getMask();
                for (int z = 0; z<mask.getSizeZ(); ++z) {
                    for (int y=0; y<mask.getSizeY(); ++y) {
                        for (int x=0; x<mask.getSizeX(); ++x) {
                            mask.setPixel(x, y, z, sum.insideMask(x, y, z)?1:0);
                        }
                    }
                }
                RegionPopulation pop = new RegionPopulation(mask, true);
                pop.translate(mask.getBoundingBox(), true);
                o.setObject(pop.getObjects().get(0));
            }
        }
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{referencePoint};
    }
    
}
