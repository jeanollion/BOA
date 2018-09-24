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
import java.util.List;

/**
 *
 * @author Jean Ollion
 */

public interface SegmenterScore extends Segmenter {
    /**
     * 
     * @param input
     * @param structureIdx strcture that will be segmented
     * @param parent parent object of the objects to be segmented
     * @return an object population. This type of segmenter must set a quality variable to each segmented object
     */
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent);
}
