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
package boa.plugins;

import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObjectProcessing;
import boa.image.Image;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
public interface SegmenterMultiple extends Segmenter {
    /**
     * Runs the segmenter
     * @param input
     * @param structureIdx
     * @param parent
     * @return a map that contains several possible solutions of segmentation, associated to a value that represents 
     */
    public TreeMap<Double, RegionPopulation> runSegmenterMultiple(Image input, int structureIdx, StructureObjectProcessing parent);
    
    /**
     * 
     * @param input
     * @param structureIdx
     * @param parent
     * @return the best solution of segmentation within all the possible solutions (returned by {@link SegmenterMultiple#runSegmenterMultiple(image.Image, int, dataStructure.objects.StructureObjectProcessing) }
     */
    @Override public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent);
}
