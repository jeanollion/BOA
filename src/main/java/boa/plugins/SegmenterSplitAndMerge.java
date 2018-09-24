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
package boa.plugins;

import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.image.Image;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author Jean Ollion
 */
public interface SegmenterSplitAndMerge extends Segmenter {
    /**
     * Split an object into several objects
     * @param parent
     * @param structureIdx
     * @param o object to be splitted
     * @param result list in which put the resulting objects
     * @return a value representing the cost of splitting the object, NaN if the object could not be split
     */
    public double split(StructureObject parent, int structureIdx, Region o, List<Region> result);
    /**
     * Compute Merge Cost & removes from the list objects that are not in contact with the first object from the list
     * @param parent
     * @param structureIdx
     * @param objects objects to be merged
     * @return a value representing the cost of merging the objects, NaN if none of the objects are in contact. 
     */
    public double computeMergeCost(StructureObject parent, int structureIdx, List<Region> objects);
}
