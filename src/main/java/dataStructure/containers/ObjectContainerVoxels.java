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
package dataStructure.containers;

import dataStructure.objects.Object3D;
import static dataStructure.objects.Object3D.logger;
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
import de.caluga.morphium.annotations.Embedded;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jollion
 */
@Embedded(polymorph = true)
public class ObjectContainerVoxels extends ObjectContainer {

    int[] x, y, z;

    public ObjectContainerVoxels(StructureObject structureObject) {
        super(structureObject);
        createCoordsArrays(structureObject.getObject());
    }

    @Override
    public void updateObject() {
        if (structureObject.getObject().getVoxels() != null) {
            createCoordsArrays(structureObject.getObject());
            bounds = structureObject.getObject().getBounds();
        } else {
            x = null;
            y = null;
            z = null;
        }
    }

    private void createCoordsArrays(Object3D object) {
        if (object.is3D()) {
            List<Voxel> voxels = object.getVoxels();
            x = new int[voxels.size()];
            y = new int[voxels.size()];
            z = new int[voxels.size()];
            int idx = 0;
            for (Voxel v : voxels) {
                x[idx] = v.x;
                y[idx] = v.y;
                z[idx++] = v.z;
            }
        } else {
            List<Voxel> voxels = object.getVoxels();
            x = new int[voxels.size()];
            y = new int[voxels.size()];
            z = null;
            int idx = 0;
            for (Voxel v : voxels) {
                x[idx] = v.x;
                y[idx++] = v.y;
            }
        }
    }

    private ArrayList<Voxel> getVoxels() {
        if (x == null || y == null) {
            return new ArrayList(0);
        }
        if (z != null) {
            ArrayList<Voxel> voxels = new ArrayList<Voxel>(x.length);
            for (int i = 0; i < x.length; ++i) {
                voxels.add(new Voxel(x[i], y[i], z[i]));
            }
            return voxels;
        } else {
            ArrayList<Voxel> voxels = new ArrayList<Voxel>(x.length);
            for (int i = 0; i < x.length; ++i) {
                voxels.add(new Voxel(x[i], y[i], 0));
            }
            return voxels;
        }
    }

    public Object3D getObject() {
        return new Object3D(getVoxels(), structureObject.getIdx() + 1, bounds, structureObject.getScaleXY(), structureObject.getScaleZ());
    }

    @Override
    public void deleteObject() {
        bounds = null;
        x = null;
        y = null;
        z = null;
    }

    @Override
    public void relabelObject(int newIdx) {
    }
}
