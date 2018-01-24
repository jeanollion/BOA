/*
 * Copyright (C) 2016 jollion
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
import java.util.List;

/**
 *
 * @author jollion
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
