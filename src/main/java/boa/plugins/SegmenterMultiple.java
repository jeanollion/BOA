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

import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObjectProcessing;
import boa.image.Image;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author Jean Ollion
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
