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

import static boa.gui.GUI.logger;
import dataStructure.objects.StructureObject;
import image.ImageInteger;
import java.util.HashMap;

/**
 *
 * @author nasique
 */
public abstract class ImageWindowManager<T> {
    final HashMap<T, ImageObjectInterface> imageObjectInterfaceMap;
    final ImageObjectListener listener;
    public ImageWindowManager(ImageObjectListener listener) {
        this.listener=listener;
        imageObjectInterfaceMap = new HashMap<T, ImageObjectInterface>();
    }
    
    public void addImage(T image, ImageObjectInterface imageObjectInterface) {
        imageObjectInterfaceMap.put(image, imageObjectInterface);
        addClickListener(image);
    }
    
    public void removeImage(T image) {
        imageObjectInterfaceMap.remove(image);
        removeClickListener(image);
    }
    
    public abstract void addClickListener(T image);
    
    public abstract void removeClickListener(T image);
    
    public StructureObject getClickedObject(T image, int x, int y, int z) {
        ImageObjectInterface i = imageObjectInterfaceMap.get(image);
        if (i!=null) {
            return i.getClickedObject(x, y, z);
        } else logger.warn("image: {} is not registered for click");
        return null;
    }
    
    public abstract void selectObjects(T image, StructureObject... selectedObjects);
    public abstract void unselectObjects(T image);
}
