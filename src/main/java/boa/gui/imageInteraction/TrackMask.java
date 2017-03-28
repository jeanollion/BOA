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
import ij.plugin.filter.MaximumFinder;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageInteger;
import image.ImageOperations;
import static image.ImageOperations.pasteImage;
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
import utils.Pair;

/**
 *
 * @author jollion
 */
public class TrackMask extends ImageObjectInterface {
    BoundingBox[] trackOffset;
    StructureObjectMask[] trackObjects;
    int maxParentY, maxParentZ;
    static final int updateImageFrequency=10;
    static final int intervalX=0; //5
    static final float displayMinMaxFraction = 0.9f;
    
    public TrackMask(List<StructureObject> parentTrack, int childStructureIdx) {
        super(parentTrack, childStructureIdx);
        //logger.debug("creating track mask from head: {}, size: {}", parentTrack.get(0), parentTrack.size());
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
            //trackOffset[i].translate(currentOffsetX, (int)(maxParentY/2.0-trackOffset[i].getSizeY()/2.0), (int)(maxParentZ/2.0-trackOffset[i].getSizeZ()/2.0)); // Y & Z middle of parent track
            trackOffset[i].translate(currentOffsetX, 0, 0); // Y & Z up of parent track
            trackObjects[i] = new StructureObjectMask(parentTrack.get(i), childStructureIdx, trackOffset[i]);
            currentOffsetX+=intervalX+trackOffset[i].getSizeX();
            logger.trace("current index: {}, current bounds: {} current offsetX: {}", i, trackOffset[i], currentOffsetX);
        }
        if (guiMode) {
            //load objects in another thread 
            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    for (StructureObjectMask m : trackObjects) m.getObjects();
                }
            });
            t.start();
        } else for (StructureObjectMask m : trackObjects) m.getObjects();
    }
    
    @Override public List<StructureObject> getParents() {
        return this.parents;
    }
    
    @Override public ImageObjectInterfaceKey getKey() {
        return new ImageObjectInterfaceKey(parents, childStructureIdx, true);
    }
    
    @Override
    public void reloadObjects() {
        for (StructureObjectMask m : trackObjects) m.reloadObjects();
    }
    
    @Override
    public Pair<StructureObject, BoundingBox> getClickedObject(int x, int y, int z) {
        if (is2D) z=0; //do not take in account z in 2D case.
        // recherche du parent: 
        int i = Arrays.binarySearch(trackOffset, new BoundingBox(x, x, 0, 0, 0, 0), new bbComparatorX());
        if (i<0) i=-i-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        //logger.debug("getClicked object: index: {}, parent: {}, #children: {}", i, i>=0?trackObjects[i]:"", i>=0? trackObjects[i].getObjects().size():"");
        if (i>=0 && trackOffset[i].contains(x, y, z)) return trackObjects[i].getClickedObject(x, y, z);
        else return null;
    }
    
    @Override
    public void addClickedObjects(BoundingBox selection, List<Pair<StructureObject, BoundingBox>> list) {
        if (is2D && selection.getSizeZ()>0) selection=new BoundingBox(selection.getxMin(), selection.getxMax(), selection.getyMin(), selection.getyMax(), 0, 0);
        int iMin = Arrays.binarySearch(trackOffset, new BoundingBox(selection.getxMin(), selection.getxMin(), 0, 0, 0, 0), new bbComparatorX());
        if (iMin<0) iMin=-iMin-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        int iMax = Arrays.binarySearch(trackOffset, new BoundingBox(selection.getxMax(), selection.getxMax(), 0, 0, 0, 0), new bbComparatorX());
        if (iMax<0) iMax=-iMax-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        //logger.debug("looking for objects from time: {} to time: {}", iMin, iMax);
        for (int i = iMin; i<=iMax; ++i) trackObjects[i].addClickedObjects(selection, list);
    }
    
    public int getClosestFrame(int x) {
        int i = Arrays.binarySearch(trackOffset, new BoundingBox(x, x, 0, 0, 0, 0), new bbComparatorX());
        if (i<0) i=-i-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        return trackObjects[i].parent.getFrame();
    }

    @Override
    public BoundingBox getObjectOffset(StructureObject object) {
        if (object==null) return null;
        
        //if (object.getFrame()<parent.getFrame()) logger.error("Object not in track : Object: {} parent: {}", object, parent);
        int idx = object.getFrame()-parents.get(0).getFrame();
        if (idx<trackObjects.length && idx>0 && parents.get(idx).getFrame()==object.getFrame()) return trackObjects[idx].getObjectOffset(object);
        else { // case of uncontinuous tracks -> search whole track
            idx = Collections.binarySearch(parents, object, (o1, o2) -> Integer.compare(o1.getFrame(), o2.getFrame()));
            if (idx<0) return null;
            BoundingBox res =  trackObjects[idx].getObjectOffset(object);
            if (res!=null) return res;
            int idx2 = idx-1;
            while (idx2>=0 && parents.get(idx2).getFrame()==object.getFrame()) {
                res =  trackObjects[idx2].getObjectOffset(object);
                if (res!=null) return res;
                --idx2;
            }
            idx2=idx+1;
            while (idx2<trackObjects.length && parents.get(idx2).getFrame()==object.getFrame()) {
                res=  trackObjects[idx2].getObjectOffset(object);
                if (res!=null) return res;
                ++idx2;
            }
        } 
        return null;
    }
    

    
    public void trimTrack(List<Pair<StructureObject, BoundingBox>> track) {
        int tpMin = parents.get(0).getFrame();
        int tpMax = parents.get(parents.size()-1).getFrame();
        track.removeIf(o -> o.key.getFrame()<tpMin || o.key.getFrame()>tpMax);
    }

    @Override
    public ImageInteger generateImage() {
        int maxLabel = 0; 
        for (StructureObjectMask o : trackObjects) {
            int label = o.getMaxLabel();
            if (label>maxLabel) maxLabel = label;
        }
        String structureName;
        if (GUI.hasInstance() && GUI.getDBConnection()!=null && GUI.getDBConnection().getExperiment()!=null) structureName = GUI.getDBConnection().getExperiment().getStructure(childStructureIdx).getName(); 
        else structureName= childStructureIdx+"";
        final ImageInteger displayImage = ImageInteger.createEmptyLabelImage("Track: Parent:"+parents+" Segmented Image of: "+structureName, maxLabel, new BlankMask("", trackOffset[trackOffset.length-1].getxMax()+1, this.maxParentY, this.maxParentZ).setCalibration(parents.get(0).getMaskProperties().getScaleXY(), parents.get(0).getMaskProperties().getScaleZ()));
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
        t.start();
        if (!guiMode) try {
            t.join();
        } catch (InterruptedException ex) {
            logger.error("draw error", ex);
        }
    }
    
    @Override public Image generateRawImage(final int structureIdx) {
        
        Image image0 = trackObjects[0].generateRawImage(structureIdx);
        String structureName;
        if (GUI.hasInstance() && GUI.getDBConnection()!=null && GUI.getDBConnection().getExperiment()!=null) structureName = GUI.getDBConnection().getExperiment().getStructure(structureIdx).getName(); 
        else structureName= structureIdx+"";
        final Image displayImage =  Image.createEmptyImage("Track: Parent:"+parents+" Raw Image of"+structureName, image0, new BlankMask("", trackOffset[trackOffset.length-1].getxMax()+1, this.maxParentY, Math.max(image0.getSizeZ(), this.maxParentZ)).setCalibration(parents.get(0).getMaskProperties().getScaleXY(), parents.get(0).getMaskProperties().getScaleZ()));
        pasteImage(image0, displayImage, trackOffset[0]);
        final double[] minAndMax = image0.getMinAndMax(null);
        // draw image in another thread..
        // update display every X paste...
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                for (int i = 1; i<trackObjects.length; ++i) {
                    Image image = trackObjects[i].generateRawImage(structureIdx);
                    double[] mm = image.getMinAndMax(null);
                    if (mm[0]<minAndMax[0]) minAndMax[0]=mm[0];
                    if (mm[1]>minAndMax[1]) minAndMax[1]=mm[1];
                    pasteImage(image, displayImage, trackOffset[i]);
                    if (count>=updateImageFrequency) {
                        ImageWindowManagerFactory.getImageManager().getDisplayer().updateImageDisplay(displayImage, minAndMax[0], (float)((1-displayMinMaxFraction) * minAndMax[0] + displayMinMaxFraction*minAndMax[1]));
                        count=0;
                    } else count++;
                }
                // autodisplay
                ImageWindowManagerFactory.getImageManager().getDisplayer().updateImageDisplay(displayImage, minAndMax[0], (float)((1-displayMinMaxFraction) * minAndMax[0] + displayMinMaxFraction*minAndMax[1]));
            }
        });
        t.start();
        if (!guiMode) try {
            t.join();
        } catch (InterruptedException ex) {
            logger.error("generateRawImage error", ex);
        }
        return displayImage;
    }
    
    public boolean containsTrack(StructureObject trackHead) {
        if (childStructureIdx==parentStructureIdx) return trackHead.getStructureIdx()==this.childStructureIdx && trackHead.getTrackHeadId().equals(this.parents.get(0).getId());
        else return trackHead.getStructureIdx()==this.childStructureIdx && trackHead.getParentTrackHeadId().equals(this.parents.get(0).getId());
    }

    @Override
    public boolean isTimeImage() {
        return true;
    }

    @Override
    public ArrayList<Pair<StructureObject, BoundingBox>> getObjects() {
        ArrayList<Pair<StructureObject, BoundingBox>> res = new ArrayList<Pair<StructureObject, BoundingBox>>();
        for (StructureObjectMask m : trackObjects) res.addAll(m.getObjects());
        return res;
    }
    
    class bbComparatorX implements Comparator<BoundingBox>{

        public int compare(BoundingBox arg0, BoundingBox arg1) {
            return Integer.compare(arg0.getxMin(), arg1.getxMin());
        }
        
    }
}
