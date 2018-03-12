/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
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
