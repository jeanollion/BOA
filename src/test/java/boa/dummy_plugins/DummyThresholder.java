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
package boa.dummy_plugins;

import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObjectProcessing;
import boa.image.Image;
import boa.plugins.Thresholder;

/**
 *
 * @author jollion
 */
public class DummyThresholder implements Thresholder {

    public Parameter[] getParameters() {
        return new Parameter[]{new NumberParameter("number", 2, 2.22)};
    }

    public boolean does3D() {
        return true;
    }
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        return 0;
    }
}
