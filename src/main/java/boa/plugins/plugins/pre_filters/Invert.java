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
package boa.plugins.plugins.pre_filters;

import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import boa.image.ImageMask;
import boa.plugins.PreFilter;

/**
 *
 * @author jollion
 */
public class Invert implements PreFilter {

    public Image runPreFilter(Image input, ImageMask mask) {
        input = input.duplicate("inverted");
        input.invert();
        return input;
    }

    public Parameter[] getParameters() {
        return new Parameter[0];
    }
    
}
