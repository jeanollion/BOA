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
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import java.util.ArrayList;
import java.util.Set;
import org.mongodb.morphia.annotations.Embedded;

/**
 *
 * @author jollion
 */
@Embedded
public class ObjectContainerVoxels implements ObjectContainer {
    ArrayList<Voxel3D> voxels; // a convertir en tableau si morphia ne veut pas. 
    BoundingBox bounds;
    float scaleXY, scaleZ;
    
    public ObjectContainerVoxels(Object3D object) {
        bounds=object.getBounds();
        //voxels=object.getVoxels().toArray(new Voxel3D[object.getVoxels().size()]);
        voxels = object.getVoxels();
        scaleXY=object.getScaleXY();
        scaleZ=object.getScaleZ();
    }
    
    public Object3D getObject() {
        return new Object3D(voxels, scaleXY, scaleZ, bounds);
    }
    
    public ArrayList<Voxel3D> getVoxels() {
        return voxels;
    }
    
    //morphia
    private ObjectContainerVoxels(){};

    
}
