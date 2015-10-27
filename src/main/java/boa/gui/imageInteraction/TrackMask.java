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

import static boa.gui.GUI.logger;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import ij.plugin.filter.MaximumFinder;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageInteger;
import static image.ImageOperations.pasteImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
import javax.swing.SwingUtilities;

/**
 *
 * @author jollion
 */
public class TrackMask extends ImageObjectInterface {
    BoundingBox[] trackOffset;
    StructureObjectMask[] trackObjects;
    int maxParentY, maxParentZ;
    static final int updateImageFrequency=100;
    static final int intervalX=5;
    ArrayList<StructureObject> parentTrack;
    public TrackMask(ArrayList<StructureObject> parentTrack, int childStructureIdx) {
        super(parentTrack.get(0), childStructureIdx);
        this.parentTrack=parentTrack;
        trackOffset = new BoundingBox[parentTrack.size()];
        trackObjects = new StructureObjectMask[parentTrack.size()];
        int maxY=0, maxZ=0;
        for (int i = 0; i<parentTrack.size(); ++i) { // compute global Y and Z max to center parent masks
            if (maxY<parentTrack.get(i).getObject().getBounds().getSizeY()) maxY=parentTrack.get(i).getObject().getBounds().getSizeY();
            if (maxZ<parentTrack.get(i).getObject().getBounds().getSizeZ()) maxZ=parentTrack.get(i).getObject().getBounds().getSizeZ();
        }
        maxParentY=maxY;
        maxParentZ=maxZ;
        logger.trace("track mask image object: max parent Y-size: {} z-size: {}", maxParentY, maxParentZ);
        int currentOffsetX=0;
        for (int i = 0; i<parentTrack.size(); ++i) {
            trackOffset[i] = parentTrack.get(i).getBounds().duplicate().translateToOrigin();
            trackOffset[i].translate(currentOffsetX, (int)(maxParentY/2.0-trackOffset[i].getSizeY()/2.0), (int)(maxParentZ/2.0-trackOffset[i].getSizeZ()/2.0));
            trackObjects[i] = new StructureObjectMask(parentTrack.get(i), childStructureIdx, trackOffset[i]);
            currentOffsetX+=intervalX+trackOffset[i].getSizeX();
            logger.trace("current index: {}, current bounds: {} current offsetX: {}", i, trackOffset[i], currentOffsetX);
        }
    }
    
    @Override
    public void reloadObjects() {
        for (StructureObjectMask m : trackObjects) m.reloadObjects();
    }
    
    @Override
    public StructureObject getClickedObject(int x, int y, int z) {
        // recherche du parent: 
        int i = Arrays.binarySearch(trackOffset, new BoundingBox(x, x, 0, 0, 0, 0), new bbComparatorX());
        if (i<0) i=-i-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        if (trackOffset[i].contains(x, y, z)) return trackObjects[i].getClickedObject(x, y, z);
        else return null;
    }
    
    public int getClosestTimePoint(int x) {
        int i = Arrays.binarySearch(trackOffset, new BoundingBox(x, x, 0, 0, 0, 0), new bbComparatorX());
        if (i<0) i=-i-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        return trackObjects[i].parent.getTimePoint();
    }

    @Override
    public BoundingBox getObjectOffset(StructureObject object) {
        if (object==null) return null;
        return trackObjects[getTrackIndex(getParent(object))].getObjectOffset(object);
    }
    
    private StructureObject getParent(StructureObject object) { // le parent n'est pas forcément direct
        StructureObject p=object;
        while(p.getStructureIdx()!=parent.getStructureIdx()) p=p.getParent();
        return p;
    }
    
    private int getTrackIndex(StructureObject object) {
        return object.getTimePoint()-parent.getTimePoint();
    }

    @Override
    public ImageInteger generateImage() {
        int maxLabel = 0; 
        for (StructureObjectMask o : trackObjects) {
            int label = o.getMaxLabel();
            if (label>maxLabel) maxLabel = label;
        }
        final ImageInteger displayImage = ImageInteger.createEmptyLabelImage("TimeLapse Image of structure: "+childStructureIdx, maxLabel, new BlankMask("", trackOffset[trackOffset.length-1].getxMax()+1, this.maxParentY, this.maxParentZ).setCalibration(parent.getMaskProperties().getScaleXY(), parent.getMaskProperties().getScaleZ()));
        draw(displayImage);
        return displayImage;
    }
    
    @Override public void draw(final ImageInteger image) {
        trackObjects[0].draw(image);
        // draw image in another thread..
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                for (int i = 1; i<trackObjects.length; ++i) {
                    trackObjects[i].draw(image);
                    if (count>=updateImageFrequency) {
                        ImageWindowManagerFactory.getImageManager().getDisplayer().updateImageDisplay(image);
                        count=0;
                    } else count++;
                    ImageWindowManagerFactory.getImageManager().getDisplayer().updateImageDisplay(image);
                }
                
            }
        });
        SwingUtilities.invokeLater(t);
    }
    
    @Override public Image generateRawImage(final int structureIdx) {
        Image image0 = trackObjects[0].generateRawImage(structureIdx);
        final Image displayImage =  Image.createEmptyImage("TimeLapse Image of structure: "+structureIdx, image0, new BlankMask("", trackOffset[trackOffset.length-1].getxMax()+1, this.maxParentY, Math.max(image0.getSizeZ(), this.maxParentZ)).setCalibration(parent.getMaskProperties().getScaleXY(), parent.getMaskProperties().getScaleZ()));
        pasteImage(image0, displayImage, trackOffset[0]);
        // draw image in another thread..
        // update display every X paste...
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                for (int i = 1; i<trackObjects.length; ++i) {
                    pasteImage(trackObjects[i].generateRawImage(structureIdx), displayImage, trackOffset[i]);
                    if (count>=updateImageFrequency) {
                        ImageWindowManagerFactory.getImageManager().getDisplayer().updateImageDisplay(displayImage);
                        count=0;
                    } else count++;
                }
                ImageWindowManagerFactory.getImageManager().getDisplayer().updateImageDisplay(displayImage);
            }
        });
        SwingUtilities.invokeLater(t);
        return displayImage;
    }
    
    public boolean containsTrack(StructureObject trackHead) {
        if (childStructureIdx==parent.getStructureIdx()) return trackHead.getStructureIdx()==this.childStructureIdx && trackHead.getTrackHeadId().equals(this.parent.getId());
        else return trackHead.getStructureIdx()==this.childStructureIdx && trackHead.getParentTrackHeadId().equals(this.parent.getId());
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
