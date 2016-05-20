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
package utils.clustering;

import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import utils.clustering.Object3DCluster.InterfaceVoxels;

/**
 *
 * @author jollion
 */
public abstract class InterfaceVoxelSet<T extends InterfaceVoxelSet<T>> extends InterfaceObject3DImpl<T> implements InterfaceVoxels<T> {
    Set<Voxel> voxels;

    public InterfaceVoxelSet(Object3D e1, Object3D e2) {
        super(e1, e2);
        voxels=new HashSet<Voxel>();
    }
    
    public InterfaceVoxelSet(Object3D e1, Object3D e2, Set<Voxel> voxels) {
        super(e1, e2);
        this.voxels=voxels;
    }

    public void addPair(Voxel v1, Voxel v2) {
        voxels.add(v1);
        voxels.add(v2);
    }
    
    public Set<Voxel> getVoxels() {
        return voxels;
    }

    @Override
    public void performFusion() {
        getE1().addVoxels(getE2().getVoxels());
    }

    @Override 
    public void fusionInterface(T otherInterface, Comparator<? super Object3D> elementComparator) {
        fusionInterfaceSetElements(otherInterface, Object3DCluster.object3DComparator);
        voxels.addAll(((InterfaceVoxelSet)otherInterface).voxels);
    }
}
