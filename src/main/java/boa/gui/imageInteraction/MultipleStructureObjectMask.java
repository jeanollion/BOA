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
import image.Image;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 *
 * @author jollion
 */
public class MultipleStructureObjectMask extends ImageObjectInterface {
    BoundingBox[] offsets;
    ArrayList<StructureObject> objects;
    
    public MultipleStructureObjectMask(StructureObject parent, int childStructureIdx) {
        super(parent, childStructureIdx);
        int[] path = parent.getExperiment().getPathToStructure(parent.getStructureIdx(), childStructureIdx);
        objects = StructureObjectUtils.getAllObjects(parent, path);
        offsets=new BoundingBox[objects.size()];
        for (int i = 0; i<offsets.length; ++i) offsets[i]=objects.get(i).getRelativeBoundingBox(parent);
    }
    
    public MultipleStructureObjectMask(StructureObject parent, int childStructureIdx, BoundingBox offset) {
        super(parent, childStructureIdx);
        int[] path = parent.getExperiment().getPathToStructure(parent.getStructureIdx(), childStructureIdx);
        objects = StructureObjectUtils.getAllObjects(parent, path);
        offsets=new BoundingBox[objects.size()];
        for (int i = 0; i<offsets.length; ++i) offsets[i]=objects.get(i).getRelativeBoundingBox(parent).translate(offset.getxMin(), offset.getyMin(), offset.getzMin());
    }

    @Override
    public StructureObject getClickedObject(int x, int y, int z) {
        for (int i = 0; i<offsets.length; ++i) if (offsets[i].contains(x, y, z)) if (objects.get(i).getMask().insideMask(x, y, z)) return objects.get(i);
        return null;
    }

    @Override
    public BoundingBox getObjectOffset(StructureObject object) {
        if (object==null) return null;
        int i = objects.indexOf(object);
        if (i>=0) return offsets[i];
        else return null;
    }

    @Override
    public ImageInteger generateImage() {
        ImageInteger displayImage = ImageInteger.createEmptyLabelImage("Segmented Image of structure: "+childStructureIdx, getMaxLabel(), parent.getMaskProperties());
        draw(displayImage);
        return displayImage;
    }
    
    @Override public Image generateRawImage(int structureIdx) {
        return parent.getRawImage(structureIdx);
    }
    
    public void draw(ImageInteger image) {
        for (int i = 0; i<offsets.length; ++i) objects.get(i).getObject().draw(image, objects.get(i).getObject().getLabel(), offsets[i]);
    }
    
    public int getMaxLabel() {
        int maxLabel = 0; 
        for (StructureObject o : objects) if (o.getObject().getLabel()>maxLabel) maxLabel = o.getObject().getLabel();
        return maxLabel;
    }

    @Override
    public boolean isTimeImage() {
        return false;
    }
    
}
