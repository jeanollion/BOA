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
package boa.gui.imageInteraction;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.BoundingBox;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 *
 * @author jollion
 */
public class MultipleStructureObjectMask extends ImageObjectInterface {
    HashBiMap<BoundingBox, StructureObject> objects;
    
    public MultipleStructureObjectMask(StructureObject parent, int childStructureIdx) {
        super(parent, childStructureIdx);
        int[] path = parent.getExperiment().getPathToStructure(parent.getStructureIdx(), childStructureIdx);
        ArrayList<StructureObject> list = StructureObjectUtils.getAllObjects(parent, path);
        objects = HashBiMap.create(list.size());
        for (StructureObject o : list) objects.put(o.getParent().getRelativeBoundingBox(parent), o);
    }

    @Override
    public StructureObject getClickedObject(int x, int y, int z) {
        for (Entry<BoundingBox, StructureObject> e : objects.entrySet()) if (e.getKey().contains(x, y, z)) if (e.getValue().getMask().insideMask(x, y, z)) return e.getValue();
        return null;
    }

    @Override
    public HashMap<BoundingBox, ImageInteger> getSelectObjectMasksWithOffset(StructureObject... selectedObjects) {
        if (selectedObjects==null) return new HashMap<BoundingBox, ImageInteger>(0);
        HashMap<BoundingBox, ImageInteger> res = new HashMap<BoundingBox, ImageInteger>(selectedObjects.length);
        BiMap<StructureObject, BoundingBox> inverse = objects.inverse();
        for (StructureObject o : selectedObjects) if (inverse.containsKey(o)) res.put(inverse.get(o), o.getMask());
        return res;
    }

    @Override
    public ImageInteger generateImage() {
        int maxLabel = 0; 
        for (StructureObject o : objects.values()) if (o.getObject().getLabel()>maxLabel) maxLabel = o.getObject().getLabel();
        ImageInteger displayImage = ImageInteger.createEmptyLabelImage("Segmented Image of structure: "+childStructureIdx, maxLabel, parent.getMaskProperties());
        for (Entry<BoundingBox, StructureObject> e : objects.entrySet()) e.getValue().getObject().draw(displayImage, e.getValue().getObject().getLabel(), e.getKey());
        return displayImage;
    }

    @Override
    public boolean isTimeImage() {
        return false;
    }
    
}
