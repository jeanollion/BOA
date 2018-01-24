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
package boa.plugins.plugins.thresholders;

import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObjectProcessing;
import boa.image.Image;
import boa.image.ImageMask;
import boa.plugins.SimpleThresholder;
import boa.plugins.Thresholder;

/**
 *
 * @author jollion
 */
public class ConstantValue implements SimpleThresholder, Thresholder {
    NumberParameter value = new NumberParameter("Value:", 8, 1);
    
    public ConstantValue() {}
    public ConstantValue(double value) {
        this.value.setValue(value);
    }
    
    public Parameter[] getParameters() {
        return new Parameter[]{value};
    }

    public boolean does3D() {
        return true;
    }
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        return value.getValue().doubleValue();
    }
    @Override
    public double runSimpleThresholder(Image input, ImageMask mask) {
        return value.getValue().doubleValue();
    }
}
