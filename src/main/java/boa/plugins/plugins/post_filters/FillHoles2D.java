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

import boa.configuration.parameters.Parameter;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.plugins.PostFilter;
import boa.plugins.ToolTip;

/**
 *
 * @author jollion
 */
public class FillHoles2D implements PostFilter, ToolTip {
    
    @Override
    public String getToolTipText() {
        return "Fills the holes in segmented regions";
    }
    
    public FillHoles2D() {}
    @Override
    public RegionPopulation runPostFilter(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        boa.image.processing.FillHoles2D.fillHoles(childPopulation);
        return childPopulation;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[0];
    }

    
    
}
