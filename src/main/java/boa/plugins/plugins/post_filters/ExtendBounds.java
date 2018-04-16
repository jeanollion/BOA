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
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.plugins.PostFilter;
import java.util.Arrays;

/**
 *
 * @author Jean Ollion
 */
public class ExtendBounds implements PostFilter {
    public enum OUT_OF_BOUNDS_CONDITION {
        TRIM("Trim"),
        KEEP_SIZE("Keep Global Size"),
        KEE_CENTER("Keep Center");
        public final String name;
        private OUT_OF_BOUNDS_CONDITION(String name) {this.name = name;}
        public static OUT_OF_BOUNDS_CONDITION get(String name) {
            return Arrays.stream(OUT_OF_BOUNDS_CONDITION.values()).filter(s->s.name.equals(name)).findAny().orElseThrow(()->new RuntimeException("Out of bound condition not found"));
        }
        public static String[] names() {
            return Arrays.stream(OUT_OF_BOUNDS_CONDITION.values()).map(s->s.name).toArray(l->new String[l]);
        }
    }
    BoundedNumberParameter x = new BoundedNumberParameter("X-axis", 0, 5, 0, null).setToolTipText("Number of pixel to add to bounds on both sides in X direction");
    ChoiceParameter outOfBoundX = new ChoiceParameter("X-axis out-of-bound", OUT_OF_BOUNDS_CONDITION.names(), TRIM.name)
    @Override
    public RegionPopulation runPostFilter(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Parameter[] getParameters() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
