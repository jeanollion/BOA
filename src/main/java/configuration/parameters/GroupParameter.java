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
package configuration.parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author jollion
 */
public class GroupParameter extends SimpleContainerParameter {
    protected List<Parameter> parameters;
    
    public GroupParameter() {super();}
    public GroupParameter(String name, Parameter... parameters) {
        super(name);
        this.parameters = Arrays.asList(parameters);
        initChildList();
    }
    public GroupParameter(String name, Collection<Parameter> parameters) {
        super(name);
        this.parameters = new ArrayList<Parameter>(parameters);
        initChildList();
    }
    
    @Override
    protected void initChildList() {
        super.initChildren(parameters);
    }
    
    @Override
    public GroupParameter duplicate() {
        List<Parameter> dup = ParameterUtils.duplicateList(parameters);
        return new GroupParameter(name, dup);
    }
    
}
