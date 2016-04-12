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
import java.util.List;
import java.util.Map.Entry;
import utils.Pair;

/**
 *
 * @author jollion
 */
public abstract class ImageObjectInterface {
    final protected StructureObject parent;
    final protected int childStructureIdx;
    final protected boolean is2D;
    protected boolean guiMode = true;
    public ImageObjectInterface(StructureObject parent, int childStructureIdx) {
        if (parent.getStructureIdx()>childStructureIdx) throw new IllegalArgumentException("Structure: "+childStructureIdx +" cannot be child of structure: "+parent.getStructureIdx());
        this.parent = parent;
        this.childStructureIdx = childStructureIdx;
        is2D = this.parent.is2D();
    }
    public StructureObject getParent() {return parent;}
    public abstract ImageObjectInterfaceKey getKey();
    public abstract void reloadObjects();
    public abstract Pair<StructureObject, BoundingBox> getClickedObject(int x, int y, int z);
    public abstract void addClickedObjects(BoundingBox selection, List<Pair<StructureObject, BoundingBox>> list);
    public abstract BoundingBox getObjectOffset(StructureObject object);
    public abstract ImageInteger generateImage();
    public abstract void draw(ImageInteger image);
    public abstract Image generateRawImage(int structureIdx);
    public abstract boolean isTimeImage();
    public int getChildStructureIdx() {return childStructureIdx;}
    public abstract ArrayList<Pair<StructureObject, BoundingBox>> getObjects();
    public List<Pair<StructureObject, BoundingBox>> pairWithOffset(List<StructureObject> objects) {
        List<Pair<StructureObject, BoundingBox>> res = new ArrayList<Pair<StructureObject, BoundingBox>>(objects.size());
        for (StructureObject o : objects) {
            BoundingBox b = this.getObjectOffset(o);
            if (b!=null) {
                res.add(new Pair(o, b));
            }
        }
        return res;
    }
    /**
     * 
     * @param guiMode if set to true, display of images and retrieve of objects is done in another thread
     */
    public void setGUIMode(boolean guiMode) {
        this.guiMode=guiMode;
    }
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
