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
package plugins.plugins.postFilters;

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.ObjectPopulation.Filter;
import dataStructure.objects.ObjectPopulation.Size;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import plugins.PostFilter;

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
    
    @Override public ObjectPopulation runPostFilter(StructureObject parent, int childStructureIdx, ObjectPopulation childPopulation) {
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
