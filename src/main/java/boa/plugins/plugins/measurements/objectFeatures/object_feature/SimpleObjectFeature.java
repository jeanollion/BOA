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
package boa.plugins.plugins.measurements.objectFeatures.object_feature;

import boa.configuration.parameters.Parameter;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.plugins.ObjectFeature;

/**
 *
 * @author Jean Ollion
 */
public abstract class SimpleObjectFeature implements ObjectFeature {
    protected StructureObject parent;
    protected int childStructureIdx;
    @Override public Parameter[] getParameters() {
        return new Parameter[0];
    }

    @Override public SimpleObjectFeature setUp(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        this.parent=parent;
        this.childStructureIdx=childStructureIdx;
        return this;
    }
    
}
