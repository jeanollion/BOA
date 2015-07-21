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
import dataStructure.objects.Voxel3D;
import de.caluga.morphium.annotations.Embedded;
import image.BoundingBox;
import java.util.ArrayList;

/**
 *
 * @author jollion
 */
@Embedded(polymorph=true)
public class ObjectContainerVoxels extends ObjectContainer {
    ArrayList<Voxel3D> voxels;
    int label;

    public ObjectContainerVoxels(Object3D object) {
        super(object.getBounds(), object.getScaleXY(), object.getScaleZ());
        voxels=object.getVoxels();
        label=object.getLabel();
    }
    
    public void updateObject(Object3D object) {
        voxels = object.getVoxels();
        bounds=object.getBounds();
        label = object.getLabel();
    }
    
    public Object3D getObject() {
        return new Object3D(voxels, label, scaleXY, scaleZ, bounds);
    }
    
    public ArrayList<Voxel3D> getVoxels() {
        return voxels;
    }
    
    //morphia
    public ObjectContainerVoxels(){};

}
