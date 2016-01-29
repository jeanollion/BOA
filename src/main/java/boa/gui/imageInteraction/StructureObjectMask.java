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

import boa.gui.GUI;
import static boa.gui.GUI.logger;
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
public class StructureObjectMask extends ImageObjectInterface {

    BoundingBox[] offsets;
    ArrayList<StructureObject> objects;
    BoundingBox additionalOffset;

    public StructureObjectMask(StructureObject parent, int childStructureIdx) {
        super(parent, childStructureIdx);
        this.additionalOffset = new BoundingBox(0, 0, 0);
    }

    public StructureObjectMask(StructureObject parent, int childStructureIdx, BoundingBox additionalOffset) {
        super(parent, childStructureIdx);
        this.additionalOffset = additionalOffset;
    }

    @Override
    public ImageObjectInterfaceKey getKey() {
        return new ImageObjectInterfaceKey(parent, childStructureIdx, false);
    }

    public BoundingBox[] getOffsets() {
        if (offsets==null) reloadObjects();
        return offsets;
    }
    
    public void reloadObjects() {
        if (childStructureIdx == parent.getStructureIdx()) {
            objects = new ArrayList<StructureObject>(1);
            objects.add(parent);
            offsets = new BoundingBox[1];
            offsets[0] = parent.getRelativeBoundingBox(parent).translate(additionalOffset.getxMin(), additionalOffset.getyMin(), additionalOffset.getzMin());
        } else {
            objects = parent.getChildren(childStructureIdx);
            offsets = new BoundingBox[objects.size()];
            for (int i = 0; i < offsets.length; ++i) {
                offsets[i] = objects.get(i).getRelativeBoundingBox(parent).translate(additionalOffset.getxMin(), additionalOffset.getyMin(), additionalOffset.getzMin());
            }
        }
    }

    @Override public ArrayList<StructureObject> getObjects() {
        if (objects == null) {
            reloadObjects();
        }
        return objects;
    }

    @Override
    public StructureObject getClickedObject(int x, int y, int z) {
        if (is2D) {
            z = 0;
        }
        getOffsets();
        for (int i = 0; i < offsets.length; ++i) {
            if (offsets[i].contains(x, y, z)) {
                if (getObjects().get(i).getMask().insideMask(x - offsets[i].getxMin(), y - offsets[i].getyMin(), z - offsets[i].getzMin())) {
                    return objects.get(i);
                }
            }
        }
        return null;
    }
    
    @Override
    public void addClickedObjects(BoundingBox selection, List<StructureObject> list) {
        if (is2D && selection.getSizeZ()>0) selection=new BoundingBox(selection.getxMin(), selection.getxMax(), selection.getyMin(), selection.getyMax(), 0, 0);
        getOffsets();
        for (int i = 0; i < offsets.length; ++i) if (offsets[i].hasIntersection(selection)) list.add(getObjects().get(i));
    }

    @Override
    public BoundingBox getObjectOffset(StructureObject object) {
        if (object == null) {
            return null;
        }
        int i = getObjects().indexOf(object);
        if (i >= 0) {
            return offsets[i];
        } else {
            logger.warn("object: {} not found", object);
            return null;
        }
    }

    @Override
    public ImageInteger generateImage() {
        ImageInteger displayImage = ImageInteger.createEmptyLabelImage("Segmented Image of structure: " + childStructureIdx, getMaxLabel(), parent.getMaskProperties());
        draw(displayImage);
        return displayImage;
    }

    @Override
    public Image generateRawImage(int structureIdx) {
        return parent.getRawImage(structureIdx);
    }

    @Override
    public void draw(ImageInteger image) {
        for (int i = 0; i < getOffsets().length; ++i) {
            getObjects().get(i).getObject().drawWithoutObjectOffset(image, objects.get(i).getObject().getLabel(), offsets[i]);
        }
    }

    public int getMaxLabel() {
        int maxLabel = 0;
        for (StructureObject o : getObjects()) {
            if (o.getObject().getLabel() > maxLabel) {
                maxLabel = o.getObject().getLabel();
            }
        }
        return maxLabel;
    }

    @Override
    public boolean isTimeImage() {
        return false;
    }



}
