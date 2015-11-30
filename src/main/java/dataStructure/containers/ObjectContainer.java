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
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import image.BoundingBox;

/**
 *
 * @author jollion
 */

@Embedded(polymorph=true)
public abstract class ObjectContainer {
    public static int MAX_VOX_3D = 1200000; //(1 vox = 12B)
    public static int MAX_VOX_2D = 1900000; //(1 vox =8B)
    public static final int MAX_VOX_3D_EMB = 50;
    public static final int MAX_VOX_2D_EMB = 75;
    @Transient protected StructureObject structureObject;
    BoundingBox bounds;
    
    public ObjectContainer(StructureObject structureObject) {
        this.structureObject=structureObject;
        this.bounds=structureObject.getBounds();
    }
    public void setStructureObject(StructureObject structureObject) {
        this.structureObject=structureObject;
    }
    protected float getScaleXY() {return structureObject.getMicroscopyField().getScaleXY();}
    protected float getScaleZ() {return structureObject.getMicroscopyField().getScaleZ();}
    public abstract Object3D getObject();
    public abstract void updateObject();
    public abstract void deleteObject();
    public abstract void relabelObject(int newIdx);
}
