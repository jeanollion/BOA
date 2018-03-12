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
package boa.plugins.plugins.post_filters;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.RegionPopulation;
import boa.data_structure.RegionPopulation.Filter;
import boa.data_structure.RegionPopulation.Size;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.plugins.PostFilter;

/**
 *
 * @author jollion
 */
public class SizeFilter implements PostFilter {
    BoundedNumberParameter minSize = new BoundedNumberParameter("Minimum Size", 0, 0, 0, null);
    BoundedNumberParameter maxSize = new BoundedNumberParameter("Maximum Size", 0, 0, 0, null);
    Parameter[] parameters = new Parameter[]{minSize, maxSize};
    
    public SizeFilter(){}
    public SizeFilter(int min, int max){
        minSize.setValue(min);
        maxSize.setValue(max);
    }
    
    @Override public RegionPopulation runPostFilter(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        Size f = new Size();
        if (minSize.getValue().intValue()>0) f.setMin(minSize.getValue().intValue());
        if (maxSize.getValue().intValue()>0) f.setMax(maxSize.getValue().intValue());
        childPopulation.filter(f);
        return childPopulation;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
