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
import ij.ImagePlus;
import image.Image;
import image.ImageInteger;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author jollion
 */
public abstract class ImageWindowManager<T> {
    private final HashMap<ImageObjectInterfaceKey, ImageObjectInterface> imageObjectInterfaces;
    private final HashMap<T, ImageObjectInterface> imageObjectInterfaceMap;
    final ImageObjectListener listener;
    final ImageDisplayer<T> displayer;
    
    public ImageWindowManager(ImageObjectListener listener, ImageDisplayer displayer) {
        this.listener=listener;
        this.displayer=displayer;
        imageObjectInterfaceMap = new HashMap<T, ImageObjectInterface>();
        imageObjectInterfaces = new HashMap<ImageObjectInterfaceKey, ImageObjectInterface>();
    }
    
    public ImageDisplayer getDisplayer() {return displayer;}
    
    protected abstract T getImage(Image image);
    
    public void addImage(Image image, ImageObjectInterface i, boolean displayImage) {
        //ImageObjectInterface i = getImageObjectInterface(parent, childStructureIdx, timeImage);
        if (!imageObjectInterfaces.containsValue(i)) throw new RuntimeException("image object interface should be created through the manager");
        T im = getImage(image);
        imageObjectInterfaceMap.put(im, i);
        if (displayImage) {
            displayer.showImage(im);
            addMouseListener(im);
        }
    }
    
    public void resetImageObjectInterface(StructureObject parent, int childStructureIdx) {
        imageObjectInterfaces.remove(new ImageObjectInterfaceKey(parent, childStructureIdx, false));
    }
    
    public ImageObjectInterface getImageObjectInterface(StructureObject parent, int childStructureIdx) {
        ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(parent, childStructureIdx, false));
        if (i==null) {
            i= new StructureObjectMask(parent, childStructureIdx);
            imageObjectInterfaces.put(new ImageObjectInterfaceKey(parent, childStructureIdx, false), i);
        } 
        return i;
    }
    
    public ImageObjectInterface getImageTrackObjectInterface(StructureObject[] parentTrack, int childStructureIdx) {
        ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(parentTrack[0], childStructureIdx, true));
        if (i==null) {
            i = new TrackMask(parentTrack, childStructureIdx);
            imageObjectInterfaces.put(new ImageObjectInterfaceKey(parentTrack[0], childStructureIdx, true), i);
        } 
        return i;
    }
    
    protected ImageObjectInterface get(T image) {return imageObjectInterfaceMap.get(image);}
    
    public void removeImage(T image) {
        imageObjectInterfaceMap.remove(image);
        removeClickListener(image);
    }
    
    public abstract void addMouseListener(T image);
    
    public abstract void removeClickListener(T image);
    
    public StructureObject getClickedObject(T image, int x, int y, int z) {
        ImageObjectInterface i = imageObjectInterfaceMap.get(image);
        if (i!=null) {
            return i.getClickedObject(x, y, z);
        } else logger.warn("image: {} is not registered for click");
        return null;
    }
    
    public abstract void selectObjects(T image, boolean addToCurrentSelection, StructureObject... selectedObjects);
    public abstract void unselectObjects(T image);
    public abstract void displayTrack(ImagePlus image, boolean addToCurrentSelectedTracks, StructureObject[] track, Color color);
}
