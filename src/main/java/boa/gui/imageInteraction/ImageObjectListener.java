/*
 * Copyright (C) 2015 nasique
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
package boa.gui.imageInteraction;

import boa.data_structure.StructureObject;
import boa.image.BoundingBox;
import java.util.List;
import boa.utils.Pair;

/**
 *
 * @author jollion
 */
public interface ImageObjectListener {
    public void fireObjectSelected(List<StructureObject> selectedObject, boolean addToSelection);
    public void fireObjectDeselected(List<StructureObject> deselectedObject);
    public void fireTracksSelected(List<StructureObject> selectedTrackHeads, boolean addToSelection);
    public void fireTracksDeselected(List<StructureObject> deselectedTrackHeads);
    public void fireDeselectAllTracks(int structureIdx);
    public void fireDeselectAllObjects(int structureIdx);
}
