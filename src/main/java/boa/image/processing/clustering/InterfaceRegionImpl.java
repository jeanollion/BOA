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
package boa.image.processing.clustering;

import boa.data_structure.Region;
import boa.data_structure.Voxel;
import java.util.Comparator;

/**
 *
 * @author jollion
 */
public abstract class InterfaceRegionImpl<T extends Interface<Region, T>> extends InterfaceImpl<Region, T> implements InterfaceRegion<T> {

    public InterfaceRegionImpl(Region e1, Region e2) {
        super(e1, e2, RegionCluster.regionComparator);
    }

    @Override
    public void performFusion() {
        e1.addVoxels(e2.getVoxels());
    }

}
