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

import boa.gui.GUI;
import dataStructure.objects.StructureObject;
import image.BoundingBox;
import image.Image;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import utils.Pair;

/**
 *
 * @author jollion
 */
public abstract class ImageObjectInterface {
    final protected List<StructureObject> parents;
    final protected int parentStructureIdx;
    final protected int childStructureIdx;
    final protected boolean is2D;
    protected boolean guiMode = true;
    public ImageObjectInterface(List<StructureObject> parents, int childStructureIdx) {
        if (parents.isEmpty()) throw new IllegalArgumentException("Empty parent list");
        parentStructureIdx = parents.get(0).getStructureIdx();
        if (parents.size()>1) for (StructureObject p : parents) if (p.getStructureIdx()!=parentStructureIdx) throw new IllegalArgumentException("Parents must be of same structure");
        if (parentStructureIdx>childStructureIdx) throw new IllegalArgumentException("Structure: "+childStructureIdx +" cannot be child of structure: "+parents.get(0).getStructureIdx());
        this.parents = parents;
        this.childStructureIdx = childStructureIdx;
        is2D = this.parents.get(0).is2D();
    }
    public StructureObject getParent() {return parents.get(0);}
    public List<StructureObject> getParents() {return parents;}
    public abstract ImageObjectInterfaceKey getKey();
    public abstract void reloadObjects();
    public abstract Pair<StructureObject, BoundingBox> getClickedObject(int x, int y, int z);
    public abstract void addClickedObjects(BoundingBox selection, List<Pair<StructureObject, BoundingBox>> list);
    public abstract BoundingBox getObjectOffset(StructureObject object);
    public abstract ImageInteger generateLabelImage();
    public abstract void drawObjects(ImageInteger image);
    public abstract Image generateRawImage(int structureIdx, boolean executeInBackground);
    public abstract boolean isTimeImage();
    public int getChildStructureIdx() {return childStructureIdx;}
    public abstract List<Pair<StructureObject, BoundingBox>> getObjects();
    public List<Pair<StructureObject, BoundingBox>> pairWithOffset(Collection<StructureObject> objects) {
        List<Pair<StructureObject, BoundingBox>> res = new ArrayList<>(objects.size());
        for (StructureObject o : objects) {
            BoundingBox b = this.getObjectOffset(o);
            if (b!=null) res.add(new Pair(o, b));
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
        if (o instanceof ImageObjectInterface) return ((ImageObjectInterface)o).parents.equals(parents) && ((ImageObjectInterface)o).childStructureIdx==childStructureIdx;
        else return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 73 * hash + (this.parents != null ? this.parents.hashCode() : 0);
        hash = 73 * hash + this.childStructureIdx;
        return hash;
    }
}
