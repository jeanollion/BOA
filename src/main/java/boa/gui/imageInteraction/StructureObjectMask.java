/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.gui.imageInteraction;

import boa.gui.GUI;
import static boa.gui.GUI.logger;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.BoundingBox;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.Offset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import boa.utils.Pair;

/**
 *
 * @author jollion
 */
public class StructureObjectMask extends ImageObjectInterface {

    BoundingBox[] offsets;
    List<StructureObject> objects;
    final StructureObject parent;
    BoundingBox additionalOffset;
    
    public StructureObjectMask(StructureObject parent, int childStructureIdx) {
        super(new ArrayList<StructureObject>(1){{add(parent);}}, childStructureIdx);
        this.parent= parent;
        this.additionalOffset = new MutableBoundingBox(0, 0, 0);
    }

    public StructureObjectMask(StructureObject parent, int childStructureIdx, BoundingBox additionalOffset) {
        super(new ArrayList<StructureObject>(1){{add(parent);}}, childStructureIdx);
        this.parent= parent;
        this.additionalOffset = additionalOffset;
    }

    @Override
    public ImageObjectInterfaceKey getKey() {
        return new ImageObjectInterfaceKey(parents, childStructureIdx, false);
    }

    public BoundingBox[] getOffsets() {
        if (offsets==null || objects==null || offsets.length!=objects.size()) reloadObjects();
        return offsets;
    }
    
    @Override public void reloadObjects() {
        if (childStructureIdx == parentStructureIdx) {
            objects = this.parents;
            offsets = new BoundingBox[1];
            offsets[0] = parent.getRelativeBoundingBox(parent).translate(additionalOffset);
        } else {
            objects = parents.get(0).getChildren(childStructureIdx);
            if (objects==null) {
                //logger.error("no objects for parent: {}, structure: {}", parents.get(0), childStructureIdx);
                offsets= new BoundingBox[0];
            }
            else offsets = new BoundingBox[objects.size()];
            for (int i = 0; i < offsets.length; ++i) {
                offsets[i] = objects.get(i).getRelativeBoundingBox(parent).translate(additionalOffset);
            }
        }
    }

    @Override public List<Pair<StructureObject, BoundingBox>> getObjects() {
        if (objects == null) reloadObjects();
        if (objects==null) return Collections.EMPTY_LIST;
        getOffsets();
        ArrayList<Pair<StructureObject, BoundingBox>> res = new ArrayList<>(objects.size());
        for (int i = 0; i < offsets.length; ++i) {
            res.add(new Pair(objects.get(i), offsets[i]));
        }
        return res;
    }

    @Override
    public Pair<StructureObject, BoundingBox> getClickedObject(int x, int y, int z) {
        if (objects == null) reloadObjects();
        if (is2D()) {
            z = 0;
        }
        getOffsets();
        for (int i = 0; i < offsets.length; ++i) {
            if (offsets[i].containsWithOffset(x, y, z)) {
                if (objects.get(i).getMask().insideMask(x - offsets[i].xMin(), y - offsets[i].yMin(), z - offsets[i].zMin())) {
                    return new Pair(objects.get(i), offsets[i]);
                }
            }
        }
        return null;
    }
    
    @Override
    public void addClickedObjects(BoundingBox selection, List<Pair<StructureObject, BoundingBox>> list) {
        getObjects();
        if (is2D()) {
            for (int i = 0; i < offsets.length; ++i) if (BoundingBox.intersect2D(offsets[i], selection)) list.add(new Pair(objects.get(i), offsets[i]));
        } else {
            for (int i = 0; i < offsets.length; ++i) if (BoundingBox.intersect(offsets[i], selection)) list.add(new Pair(objects.get(i), offsets[i]));
        }
        
        
    }

    @Override
    public BoundingBox getObjectOffset(StructureObject object) {
        if (object == null) {
            return null;
        }
        if (objects==null) reloadObjects();
        int i = this.childStructureIdx==object.getStructureIdx()? objects.indexOf(object) : -1;
        if (i >= 0) {
            return offsets[i];
        } else {
            StructureObject p = object.getFirstCommonParent(parent); // do not display objects that don't have a common parent not root
            if (p!=null && !p.isRoot()) return object.getRelativeBoundingBox(parent).translate(additionalOffset);
            else return null;
        }
    }

    @Override
    public ImageInteger generateLabelImage() {
        ImageInteger displayImage = ImageInteger.createEmptyLabelImage("Segmented Image of structure: " + childStructureIdx, getMaxLabel(), parent.getMaskProperties());
        drawObjects(displayImage);
        return displayImage;
    }

    @Override
    public Image generatemage(int structureIdx, boolean executeInBackground) {
        return displayPreFilteredImages? generateFilteredImage(structureIdx, executeInBackground) : parent.getRawImage(structureIdx);
    }
    public Image generateFilteredImage(int structureIdx, boolean executeInBackground) {
        return parent.getPreFilteredImage(structureIdx)==null?parent.getRawImage(structureIdx):parent.getPreFilteredImage(structureIdx);
    }

    @Override
    public void drawObjects(ImageInteger image) {
        if (objects == null) reloadObjects();
        for (int i = 0; i < getOffsets().length; ++i) {
            objects.get(i).getRegion().drawWithoutObjectOffset(image, objects.get(i).getRegion().getLabel(), offsets[i]);
        }
    }

    public int getMaxLabel() {
        if (objects == null) reloadObjects();
        int maxLabel = 0;
        for (StructureObject o : objects) {
            if (o.getRegion().getLabel() > maxLabel) {
                maxLabel = o.getRegion().getLabel();
            }
        }
        return maxLabel;
    }

    @Override
    public boolean isTimeImage() {
        return false;
    }



}
