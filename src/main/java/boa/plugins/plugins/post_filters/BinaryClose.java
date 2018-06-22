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
import boa.configuration.parameters.ScaleXYZParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.ImageInteger;
import boa.plugins.PostFilter;
import boa.image.processing.Filters;
import boa.image.processing.neighborhood.Neighborhood;
import boa.plugins.MultiThreaded;
import boa.plugins.ToolTip;

/**
 *
 * @author jollion
 */
public class BinaryClose implements PostFilter, MultiThreaded, ToolTip {
    ScaleXYZParameter scale = new ScaleXYZParameter("Radius", 5, 1, true);
    
    @Override
    public String getToolTipText() {
        return "Performs a close operation on region masks<br />Useful to fill invaginations<br />When several segmented regions are present, the filter is applied label-wise";
    }
    
    public BinaryClose() {}
    public BinaryClose(double radius) {
        this.scale.setScaleXY(radius);
    }
    @Override
    public RegionPopulation runPostFilter(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        Neighborhood n = Filters.getNeighborhood(scale.getScaleXY(), scale.getScaleZ(parent.getScaleXY(), parent.getScaleZ()), parent.getMask());
        for (Region o : childPopulation.getRegions()) {
            ImageInteger closed = Filters.binaryCloseExtend(o.getMaskAsImageInteger(), n, parallele);
            o.setMask(closed);
        }
        return childPopulation;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{scale};
    }
    boolean parallele;
    @Override
    public void setMultithread(boolean parallele) {
        this.parallele=parallele;
    }

}
