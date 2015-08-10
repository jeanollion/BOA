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
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import image.BoundingBox;

/**
 *
 * @author jollion
 */

@Embedded(polymorph=true)
public abstract class ObjectContainer {
    public static final int MAX_VOX = 5000; //(10 vox ~ 1kb)
    @Transient protected float scaleXY, scaleZ;
    BoundingBox bounds;
    public ObjectContainer(BoundingBox bounds, float scaleXY, float scaleZ) {
        this.bounds=bounds;
        this.scaleXY=scaleXY;
        this.scaleZ=scaleZ;
    }
    public void setScale(float scaleXY, float scaleZ) {
        this.scaleXY=scaleXY;
        this.scaleZ=scaleZ;
    }
    public abstract Object3D getObject();
    public abstract void updateObject(Object3D object);
    
}
