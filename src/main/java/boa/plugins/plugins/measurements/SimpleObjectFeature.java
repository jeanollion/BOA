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
package boa.plugins.plugins.measurements;

import boa.configuration.parameters.Parameter;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.plugins.ObjectFeature;

/**
 *
 * @author jollion
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
