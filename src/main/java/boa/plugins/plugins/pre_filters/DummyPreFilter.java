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
package boa.plugins.plugins.pre_filters;

import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import boa.image.ImageMask;
import boa.plugins.PreFilter;

/**
 *
 * @author jollion
 */
public class DummyPreFilter implements PreFilter{
    NumberParameter n1 = new NumberParameter("number1", 2, 2);
    ChoiceParameter c1 = new ChoiceParameter("CHoice", new String[]{"c1", "c2"}, "c1", true);
    public Image runPreFilter(Image input, ImageMask mask) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public Parameter[] getParameters() {
        return new Parameter[]{n1, c1};
    }

    public boolean isTimeDependent() {
        return false;
    }

    public boolean does3D() {
        return true;
    }
    
}