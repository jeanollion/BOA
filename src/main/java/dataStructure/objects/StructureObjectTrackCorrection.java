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
package dataStructure.objects;

import dataStructure.objects.StructureObject.TrackFlag;
import java.util.ArrayList;
import java.util.List;
import plugins.ObjectSplitter;

/**
 *
 * @author jollion
 */
public interface StructureObjectTrackCorrection extends StructureObjectTracker {
    public boolean hasTrackLinkError();
    /**
     * 
     * @return the next element of the track that contains a track link error, as defined by the tracker; null is there are no next track error;
     */
    public StructureObjectTrackCorrection getNextTrackError();
    /**
     * 
     * @return a list containing the sibling (structureObjects that have the same previous object) at the next division, null if there are no siblings. If there are siblings, the first object of the list is contained in the track.
     */
    public List<StructureObject> getNextDivisionSiblings();
    /**
     * 
     * @return a list containing the sibling (structureObjects that have the same previous object) at the previous division, null if there are no siblings. If there are siblings, the first object of the list is contained in the track.
     */
    public List<StructureObject> getPreviousDivisionSiblings();
    public void merge(StructureObjectTrackCorrection other);
    public StructureObjectTrackCorrection split(ObjectSplitter splitter);
    public StructureObject resetTrackHead();
    public StructureObject setTrackFlag(TrackFlag flag);
    @Override public StructureObjectTrackCorrection getPrevious();
    @Override public StructureObjectTrackCorrection getNext();
    
}
