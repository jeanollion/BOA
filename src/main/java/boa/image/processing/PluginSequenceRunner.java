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
package boa.image.processing;

import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectPreProcessing;
import boa.data_structure.StructureObjectProcessing;
import boa.image.BlankMask;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.ImageProperties;
import java.util.ArrayList;
import boa.plugins.Plugin;
import boa.plugins.PostFilter;
import boa.plugins.PreFilter;
import boa.plugins.Segmenter;

/**
 *
 * @author jollion
 */
public class PluginSequenceRunner {
    public static Image preFilterImage(Image input, StructureObjectPreProcessing structureObject, ArrayList<PreFilter> preFilters) {
        if (preFilters==null || preFilters.isEmpty()) return input;
        else {
            Image currentImage = input.duplicate("");
            for (PreFilter p : preFilters) {
                currentImage = p.runPreFilter(currentImage, structureObject);
                currentImage.setCalibration(input);
                if (currentImage.sameSize(input)) currentImage.resetOffset().addOffset(input);
            }
            return currentImage;
        }
    }
    
    public static RegionPopulation postFilterImage(RegionPopulation objectPopulation, int structureIdx, StructureObject structureObject, ArrayList<PostFilter> postFilters) {
        if (postFilters==null || postFilters.isEmpty()) return objectPopulation;
        else {
            ImageProperties initialProperites = objectPopulation.getImageProperties();
            RegionPopulation currentObjectPopulation=objectPopulation;
            for (PostFilter p : postFilters) {
                currentObjectPopulation = p.runPostFilter(structureObject, structureIdx, objectPopulation);
                currentObjectPopulation.setProperties(initialProperites, true);
            }
            return currentObjectPopulation;
        }
    }
}
