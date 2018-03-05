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
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import ij.plugin.filter.MaximumFinder;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.Offset;
import boa.image.SimpleBoundingBox;
import boa.image.SimpleImageProperties;
import boa.image.SimpleOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import boa.utils.Pair;

/**
 *
 * @author jollion
 */
public class TrackMaskX extends TrackMask {
    int maxParentSizeY, maxParentSizeZ;
    
    public TrackMaskX(List<StructureObject> parentTrack, int childStructureIdx, boolean middleYZ) {
        super(parentTrack, childStructureIdx);
        int maxY=0, maxZ=0;
        for (int i = 0; i<parentTrack.size(); ++i) { // compute global Y and Z max to center parent masks
            if (maxY<parentTrack.get(i).getRegion().getBounds().sizeY()) maxY=parentTrack.get(i).getRegion().getBounds().sizeY();
            if (maxZ<parentTrack.get(i).getRegion().getBounds().sizeZ()) maxZ=parentTrack.get(i).getRegion().getBounds().sizeZ();
        }
        maxParentSizeY=maxY;
        maxParentSizeZ=maxZ;
        logger.trace("track mask image object: max parent Y-size: {} z-size: {}", maxParentSizeY, maxParentSizeZ);
        int currentOffsetX=0;
        for (int i = 0; i<parentTrack.size(); ++i) {
            trackOffset[i] = new SimpleBoundingBox(parentTrack.get(i).getBounds()).resetOffset(); 
            if (middleYZ) trackOffset[i].translate(new SimpleOffset(currentOffsetX, (int)((maxParentSizeY-1)/2.0-(trackOffset[i].sizeY()-1)/2.0), (int)((maxParentSizeZ-1)/2.0-(trackOffset[i].sizeZ()-1)/2.0))); // Y & Z middle of parent track
            else trackOffset[i].translate(new SimpleOffset(currentOffsetX, 0, 0)); // Y & Z up of parent track
            trackObjects[i] = new StructureObjectMask(parentTrack.get(i), childStructureIdx, trackOffset[i]);
            currentOffsetX+=interval+trackOffset[i].sizeX();
            logger.trace("current index: {}, current bounds: {} current offsetX: {}", i, trackOffset[i], currentOffsetX);
        }
        for (StructureObjectMask m : trackObjects) m.getObjects();
    }
    
    @Override
    public Pair<StructureObject, BoundingBox> getClickedObject(int x, int y, int z) {
        if (is2D()) z=0; //do not take in account z in 2D case.
        // recherche du parent: 
        int i = Arrays.binarySearch(trackOffset, new SimpleOffset(x, 0, 0), new OffsetComparatorX());
        if (i<0) i=-i-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        //logger.debug("getClicked object: index: {}, parent: {}, #children: {}", i, i>=0?trackObjects[i]:"", i>=0? trackObjects[i].getObjects().size():"");
        if (i>=0 && trackOffset[i].containsWithOffset(x, y, z)) return trackObjects[i].getClickedObject(x, y, z);
        else return null;
    }
    
    @Override
    public void addClickedObjects(BoundingBox selection, List<Pair<StructureObject, BoundingBox>> list) {
        if (is2D() && selection.sizeZ()>0) selection=new SimpleBoundingBox(selection.xMin(), selection.xMax(), selection.yMin(), selection.yMax(), 0, 0);
        int iMin = Arrays.binarySearch(trackOffset, new SimpleOffset(selection.xMin(), 0, 0), new OffsetComparatorX());
        if (iMin<0) iMin=-iMin-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        int iMax = Arrays.binarySearch(trackOffset, new SimpleOffset(selection.xMax(), 0, 0), new OffsetComparatorX());
        if (iMax<0) iMax=-iMax-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        //logger.debug("looking for objects from time: {} to time: {}", iMin, iMax);
        for (int i = iMin; i<=iMax; ++i) trackObjects[i].addClickedObjects(selection, list);
    }
    
    @Override
    public int getClosestFrame(int x, int y) {
        int i = Arrays.binarySearch(trackOffset, new SimpleOffset(x, 0, 0), new OffsetComparatorX());
        if (i<0) i=-i-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        if (i<0) logger.error("get closest frame: x:{}, trackOffset:[{}, {}]",x, trackOffset[0], trackOffset[trackOffset.length-1]);
        return trackObjects[i].parent.getFrame();
    }

    @Override
    public ImageInteger generateLabelImage() {
        int maxLabel = 0; 
        for (StructureObjectMask o : trackObjects) {
            int label = o.getMaxLabel();
            if (label>maxLabel) maxLabel = label;
        }
        String structureName;
        if (GUI.hasInstance() && GUI.getDBConnection()!=null && GUI.getDBConnection().getExperiment()!=null) structureName = GUI.getDBConnection().getExperiment().getStructure(childStructureIdx).getName(); 
        else structureName= childStructureIdx+"";
        final ImageInteger displayImage = ImageInteger.createEmptyLabelImage("Track: Parent:"+parents+" Segmented Image of: "+structureName, maxLabel, new SimpleImageProperties( trackOffset[trackOffset.length-1].xMax()+1, this.maxParentSizeY, this.maxParentSizeZ,parents.get(0).getMaskProperties().getScaleXY(), parents.get(0).getMaskProperties().getScaleZ()));
        drawObjects(displayImage);
        return displayImage;
    }
    @Override 
    public Image generateEmptyImage(String name, Image type) {
        return  Image.createEmptyImage(name, type, new SimpleImageProperties(trackOffset[trackOffset.length-1].xMax()+1, this.maxParentSizeY, Math.max(type.sizeZ(), this.maxParentSizeZ),parents.get(0).getMaskProperties().getScaleXY(), parents.get(0).getMaskProperties().getScaleZ()));
    }
    
    class OffsetComparatorX implements Comparator<Offset>{
        @Override
        public int compare(Offset arg0, Offset arg1) {
            return Integer.compare(arg0.xMin(), arg1.xMin());
        }
        
    }
}
