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
import ij.plugin.filter.MaximumFinder;
import image.BoundingBox;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
public class TrackMask extends ImageObjectInterface {
    BoundingBox[] trackOffset;
    MultipleStructureObjectMask[] trackObjects;
    int maxParentY, maxParentZ;
    static final int intervalX=5;
    
    public TrackMask(StructureObject[] parentTrack, int childStructureIdx) {
        super(parentTrack[0], childStructureIdx);
        trackOffset = new BoundingBox[parentTrack.length];
        trackObjects = new MultipleStructureObjectMask[parentTrack.length];
        
        for (int i = 0; i<parentTrack.length; ++i) { // compute global Y and Z max to center parent masks
            if (maxParentY<parentTrack[i].getObject().getBounds().getSizeY()) maxParentY=parentTrack[i].getObject().getBounds().getSizeY();
            if (maxParentZ<parentTrack[i].getObject().getBounds().getSizeZ()) maxParentZ=parentTrack[i].getObject().getBounds().getSizeZ();
        }
        
        int currentOffsetX=0;
        for (int i = 0; i<parentTrack.length; ++i) {
            trackOffset[i] = parentTrack[i].getBounds().duplicate();
            trackOffset[i].translate(currentOffsetX, (int)(maxParentY/2.0-trackOffset[i].getSizeY()/2.0), (int)(maxParentZ/2.0-trackOffset[i].getSizeZ()/2.0));
            trackObjects[i] = new MultipleStructureObjectMask(parentTrack[i], childStructureIdx, trackOffset[i]);
            currentOffsetX+=intervalX+parentTrack[i].getObject().getBounds().getSizeX();
        }
    }
    
    @Override
    public StructureObject getClickedObject(int x, int y, int z) {
        // recherche du parent: 
        int i = Arrays.binarySearch(trackOffset, new BoundingBox(x, x, 0, 0, 0, 0), new bbComparatorX());
        if (i<0) i=-i-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        if (trackOffset[i].contains(x, y, z)) return trackObjects[i].getClickedObject(x, y, z);
        else return null;
    }

    @Override
    public HashMap<BoundingBox, ImageInteger> getSelectObjectMasksWithOffset(StructureObject... selectedObjects) {
        if (selectedObjects==null) return new HashMap<BoundingBox, ImageInteger>(0);
        if (selectedObjects.length==1) return trackObjects[getTrackIndex(getParent(selectedObjects[0]))].getSelectObjectMasksWithOffset(selectedObjects); //cas frequent traité a part
        // regrouper les objets selectionner par parent:
        HashMap<StructureObject, ArrayList<StructureObject>> parentMapSelectedObjects = new HashMap<StructureObject, ArrayList<StructureObject>>();
        for (StructureObject o : selectedObjects) {
            StructureObject p = getParent(o);
            ArrayList<StructureObject> list = parentMapSelectedObjects.get(p);
            if (list==null) {
                list = new ArrayList<StructureObject>();
                parentMapSelectedObjects.put(p, list);
            }
            list.add(o);
        }
        // dans chaque parent -> chercher les objets correspondants
        HashMap<BoundingBox, ImageInteger> res = new HashMap<BoundingBox, ImageInteger>(selectedObjects.length);
        for (Entry<StructureObject, ArrayList<StructureObject>> e : parentMapSelectedObjects.entrySet()) res.putAll(trackObjects[getTrackIndex(e.getKey())].getSelectObjectMasksWithOffset(e.getValue().toArray(new StructureObject[e.getValue().size()])));
        return res;
    }
    
    private StructureObject getParent(StructureObject object) { // le parent n'est pas forcément direct
        StructureObject p=object.getParent();
        while(p.getStructureIdx()!=parent.getStructureIdx()) p=p.getParent();
        return p;
    }
    
    private int getTrackIndex(StructureObject object) {
        return object.getTimePoint()-parent.getTimePoint();
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
        return true;
    }
    
    class bbComparatorX implements Comparator<BoundingBox>{

        public int compare(BoundingBox arg0, BoundingBox arg1) {
            return Integer.compare(arg0.getxMin(), arg1.getxMin());
        }
        
    }
}
