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
import java.util.Arrays;
import java.util.HashMap;

/**
 *
 * @author nasique
 */
public class SingleStructureObjectMask extends ImageObjectInterface {

    public SingleStructureObjectMask(StructureObject object) {
        super(object, object.getStructureIdx());
    }
    
    @Override public StructureObject getClickedObject(int x, int y, int z) {
        if (parent.getMask().insideMask(x, y, z)) return parent;
        else return null;
    }

    @Override public BoundingBox getObjectOffset(StructureObject object) {
        if (object==null) return null;
        if (object==parent) {
            BoundingBox b = parent.getMask().getBoundingBox().duplicate();
            return b.translate(-b.getxMin(), -b.getyMin(), -b.getzMin());
        } else return null;
    }

    @Override public ImageInteger generateImage() {
        return (ImageInteger)parent.getMask().setName("mask of object: time: "+parent.getTimePoint()+ " structure: "+parent.getStructureIdx()+ " idx: "+parent.getIdx());
    }
    
    @Override public Image generateRawImage(int structureIdx) {
        return parent.getRawImage(structureIdx);
    }

    @Override
    public boolean isTimeImage() {
        return false;
    }

}
