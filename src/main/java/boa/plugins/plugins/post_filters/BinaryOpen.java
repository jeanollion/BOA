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
package boa.plugins.plugins.post_filters;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ScaleXYZParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.ImageInteger;
import boa.image.TypeConverter;
import boa.image.processing.BinaryMorphoEDT;
import boa.plugins.PostFilter;
import boa.image.processing.Filters;
import boa.image.processing.neighborhood.Neighborhood;
import boa.plugins.MultiThreaded;
import boa.plugins.ToolTip;

/**
 *
 * @author jollion
 */
public class BinaryOpen implements PostFilter, MultiThreaded, ToolTip {
    ScaleXYZParameter scale = new ScaleXYZParameter("Opening Radius", 3, 1, true);
    
    @Override
    public String getToolTipText() {
        return "Performs an opening operation on region masks<br />Useful to remove small protuberances<br />When several segmented regions are present, the filter is applied label-wise";
    }
    
    @Override
    public RegionPopulation runPostFilter(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        boolean edt = scale.getScaleXY()>=8;
        Neighborhood n = edt?null:Filters.getNeighborhood(scale.getScaleXY(), scale.getScaleZ(parent.getScaleXY(), parent.getScaleZ()), parent.getMask());
        for (Region o : childPopulation.getRegions()) {
            ImageInteger closed = edt? TypeConverter.toImageInteger(BinaryMorphoEDT.binaryOpen(o.getMask(), scale.getScaleXY(), scale.getScaleZ(parent.getScaleXY(), parent.getScaleZ()), parallele), null) 
                    : Filters.binaryOpen(o.getMaskAsImageInteger(), null, n, parallele);
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
