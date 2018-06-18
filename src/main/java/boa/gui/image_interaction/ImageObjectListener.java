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
package boa.gui.image_interaction;

import boa.data_structure.StructureObject;
import boa.image.MutableBoundingBox;
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
