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
import image.ImageInteger;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 * @author nasique
 */
public class SingleStructureObjectMask implements ImageObjectInterface {
    StructureObject object;

    public SingleStructureObjectMask(StructureObject object) {
        this.object = object;
    }
    
    @Override public StructureObject getClickedObject(int x, int y, int z) {
        if (object.getMask().insideMask(x, y, z)) return object;
        else return null;
    }

    @Override public ArrayList<ImageInteger> getSelectObjectMasksWithOffset(StructureObject... selectedObjects) {
        if (selectedObjects!=null && selectedObjects.length>0) {
            if (Arrays.asList(selectedObjects).contains(object)) {
                ArrayList<ImageInteger> masks = new ArrayList<ImageInteger>(1);
                masks.add(object.getMask());
                return masks;
            }
        } 
        return null;
    }

    public ImageInteger generateImage() {
        return object.getMask();
    }

}
