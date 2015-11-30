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
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import image.BoundingBox;
import java.util.ArrayList;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
@Entity
public class RegionVoxelsDB {
    @Id ObjectId id;
    int[] x, y, z;
    String fieldName;
    public RegionVoxelsDB(StructureObject structureObject) {
        createCoordsArrays(structureObject.getObject());
        this.fieldName=structureObject.getFieldName();
    }
    
    public ObjectId getId() {
        return id;
    }

    public BoundingBox updateObject(StructureObject structureObject) {
        if (structureObject.getObject().getVoxels() != null) {
            createCoordsArrays(structureObject.getObject());
        } else {
            x = null;
            y = null;
            z = null;
        }
        structureObject.getDAO().getRegionDAO().store(this);
        return structureObject.getObject().getBounds();
    }

    private void createCoordsArrays(Object3D object) {
        if (object.is3D()) {
            ArrayList<Voxel> voxels = object.getVoxels();
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
            ArrayList<Voxel> voxels = object.getVoxels();
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

    public ArrayList<Voxel> getVoxels() {
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


    public void deleteObject() {
        x = null;
        y = null;
        z = null;
    }
}
