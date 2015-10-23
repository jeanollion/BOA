/*
 * Copyright (C) 2015 nasique
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
package boa.gui.imageInteraction;

import dataStructure.objects.StructureObject;
import image.BoundingBox;
import image.Image;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author jollion
 */
public abstract class ImageObjectInterface {
    final protected StructureObject parent;
    final protected int childStructureIdx;

    public ImageObjectInterface(StructureObject parent, int childStructureIdx) {
        this.parent = parent;
        this.childStructureIdx = childStructureIdx;
    }
    
    public abstract void reloadObjects();
    public abstract StructureObject getClickedObject(int x, int y, int z);
    public abstract BoundingBox getObjectOffset(StructureObject object);
    public abstract ImageInteger generateImage();
    public abstract void draw(ImageInteger image);
    public abstract Image generateRawImage(int structureIdx);
    public abstract boolean isTimeImage();
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof ImageObjectInterface) return ((ImageObjectInterface)o).parent.equals(parent) && ((ImageObjectInterface)o).childStructureIdx==childStructureIdx;
        else return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 73 * hash + (this.parent != null ? this.parent.hashCode() : 0);
        hash = 73 * hash + this.childStructureIdx;
        return hash;
    }
}
