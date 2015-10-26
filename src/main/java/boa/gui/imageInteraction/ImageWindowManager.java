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
import image.BoundingBox;
import image.Image;
import image.ImageInteger;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import utils.Utils;

/**
 *
 * @author jollion
 * @param <T> image class
 */
public abstract class ImageWindowManager<T> {
    public final static Color[] palette = new Color[]{new Color(166, 206, 227), new Color(31,120,180), new Color(178,223,138), new Color(51,160,44), new Color(251,154,153), new Color(253,191,111), new Color(255,127,0), new Color(255,255,153), new Color(177,89,40)};
    protected final static Color trackErrorColor = new Color(255, 0, 0);
    protected final static Color trackCorrectionColor = new Color(0, 0, 255);
    final static int trackArrowStrokeWidth = 3;
    private final HashMap<ImageObjectInterfaceKey, ImageObjectInterface> imageObjectInterfaces;
    private final HashMap<Image, ImageObjectInterface> imageObjectInterfaceMap;
    final ImageObjectListener listener;
    final ImageDisplayer<T> displayer;
    
    public ImageWindowManager(ImageObjectListener listener, ImageDisplayer displayer) {
        this.listener=listener;
        this.displayer=displayer;
        imageObjectInterfaceMap = new HashMap<Image, ImageObjectInterface>();
        imageObjectInterfaces = new HashMap<ImageObjectInterfaceKey, ImageObjectInterface>();
    }
    
    public ImageDisplayer getDisplayer() {return displayer;}
    
    //protected abstract T getImage(Image image);
    
    public void addImage(Image image, ImageObjectInterface i, boolean displayImage) {
        //ImageObjectInterface i = getImageObjectInterface(parent, childStructureIdx, timeImage);
        if (!imageObjectInterfaces.containsValue(i)) throw new RuntimeException("image object interface should be created through the manager");
        //T im = getImage(image);
        imageObjectInterfaceMap.put(image, i);
        if (displayImage) {
            displayer.showImage(image);
            addMouseListener(image);
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
    
    public ImageObjectInterface getImageTrackObjectInterface(ArrayList<StructureObject> parentTrack, int childStructureIdx) {
        if (parentTrack.isEmpty()) {
            logger.warn("cannot open track image of length == 0" );
            return null;
        }
        ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(parentTrack.get(0), childStructureIdx, true));
        if (i==null) {
            i = new TrackMask(parentTrack, childStructureIdx);
            imageObjectInterfaces.put(new ImageObjectInterfaceKey(parentTrack.get(0), childStructureIdx, true), i);
        } 
        return i;
    }
    
    protected void reloadObjects(StructureObject parent, int childStructureIdx, boolean track) {
        if (track) parent=parent.getTrackHead();
        ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(parent, childStructureIdx, track));
        if (i!=null) {
            i.reloadObjects();
            for (Entry<Image, ImageObjectInterface> e : imageObjectInterfaceMap.entrySet()) if (e.getValue().equals(i)) {
                i.draw((ImageInteger)e.getKey());
                if (!track) getDisplayer().updateImageDisplay(e.getKey());
            }
        }
    }
    
    public void reloadObjects(StructureObject parent, int childStructureIdx) {
        reloadObjects(parent, childStructureIdx, true);
        reloadObjects(parent, childStructureIdx, false);
    } 
    
    protected ImageObjectInterface getImageObjectInterface(Image image) {return imageObjectInterfaceMap.get(image);}
    
    public void removeImage(Image image) {
        imageObjectInterfaceMap.remove(image);
        //removeClickListener(image);
    }
    
    public abstract void addMouseListener(Image image);
    
    //public abstract void removeClickListener(Image image);
    
    public StructureObject getClickedObject(Image image, int x, int y, int z) {
        ImageObjectInterface i = imageObjectInterfaceMap.get(image);
        if (i!=null) {
            return i.getClickedObject(x, y, z);
        } else logger.warn("image: {} is not registered for click");
        return null;
    }
    
    
    public abstract void selectObjects(Image image, boolean addToCurrentSelection, StructureObject... selectedObjects);
    public abstract void unselectObjects(Image image);
    public abstract void displayTrack(Image image, boolean addToCurrentSelectedTracks, ArrayList<StructureObject> track, Color color);
    public void displayTrackAllImages(ImageObjectInterface i, boolean addToCurrentSelectedTracks, ArrayList<StructureObject> track, Color color) {
        if (i==null && track!=null && !track.isEmpty()) i = this.getImageObjectInterface(track.get(0).getTrackHead(), track.get(0).getStructureIdx());
        ArrayList<Image> images= Utils.getKeys(this.imageObjectInterfaceMap, i);
        for (Image image : images) displayTrack(image, addToCurrentSelectedTracks, track, color);
    }
    public void goToNextTrackError(Image trackImage, ArrayList<StructureObject> tracks) {
        //ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(tracks.get(0).getParent().getTrackHead(), tracks.get(0).getStructureIdx(), true));
        if (tracks==null || tracks.isEmpty()) return;
        if (trackImage==null) {
            T selectedImage = displayer.getCurrentImage();
            trackImage = displayer.getImage(selectedImage);
        }
        ImageObjectInterface i = this.getImageObjectInterface(trackImage);
        if (!i.isTimeImage()) {
            logger.warn("selected image is not a track image");
            return;
        }
        TrackMask tm = (TrackMask)i;
        BoundingBox currentDisplayRange = this.displayer.getDisplayRange(trackImage);
        int minTimePoint = tm.getClosestTimePoint(currentDisplayRange.getxMin());
        int maxTimePoint = tm.getClosestTimePoint(currentDisplayRange.getxMax());
        if (maxTimePoint>minTimePoint+2) maxTimePoint-=2;
        else maxTimePoint--;
        //logger.debug("Current Display range: {}, maxTimePoint: {}, number of selected tracks: {}", currentDisplayRange, maxTimePoint, tracks.size());
        StructureObject nextError = getNextError(maxTimePoint, tracks);
        if (nextError==null) logger.info("No errors detected after timepoint: {}", maxTimePoint);
        else {
            
            BoundingBox off = tm.getObjectOffset(nextError);
            int mid = (int)off.getXMean();
            BoundingBox nextDisplayRange = new BoundingBox(mid-currentDisplayRange.getSizeX()/2, mid+currentDisplayRange.getSizeX()/2, currentDisplayRange.getyMin(), currentDisplayRange.getyMax(), currentDisplayRange.getzMin(), currentDisplayRange.getzMax());
            logger.info("Error detected @ timepoint: {}, xMid: {}, update display range: {}", nextError.getTimePoint(), mid,  nextDisplayRange);
            displayer.setDisplayRange(nextDisplayRange, trackImage);
        }
    }
    public void goToPreviousTrackError(Image trackImage, ArrayList<StructureObject> tracks) {
        //ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(tracks.get(0).getParent().getTrackHead(), tracks.get(0).getStructureIdx(), true));
        if (tracks==null || tracks.isEmpty()) return;
        if (trackImage==null) {
            T selectedImage = displayer.getCurrentImage();
            trackImage = displayer.getImage(selectedImage);
        }
        ImageObjectInterface i = this.getImageObjectInterface(trackImage);
        if (!i.isTimeImage()) {
            logger.warn("selected image is not a track image");
            return;
        }
        TrackMask tm = (TrackMask)i;
        BoundingBox currentDisplayRange = this.displayer.getDisplayRange(trackImage);
        int minTimePoint = tm.getClosestTimePoint(currentDisplayRange.getxMin());
        int maxTimePoint = tm.getClosestTimePoint(currentDisplayRange.getxMax());
        if (maxTimePoint>minTimePoint+2) minTimePoint+=2;
        else minTimePoint++;
        logger.debug("Current Display range: {}, minTimePoint: {}, number of selected tracks: {}", currentDisplayRange, minTimePoint, tracks.size());
        StructureObject prevError = getPreviousError(minTimePoint, tracks);
        if (prevError==null) logger.info("No errors detected before timepoint: {}", minTimePoint);
        else {
            BoundingBox off = tm.getObjectOffset(prevError);
            int mid = (int)off.getXMean();
            BoundingBox nextDisplayRange = new BoundingBox(mid-currentDisplayRange.getSizeX()/2, mid+currentDisplayRange.getSizeX()/2, currentDisplayRange.getyMin(), currentDisplayRange.getyMax(), currentDisplayRange.getzMin(), currentDisplayRange.getzMax());
            logger.info("Error detected @ timepoint: {}, xMid: {}, update display range: {}", prevError.getTimePoint(), mid,  nextDisplayRange);
            displayer.setDisplayRange(nextDisplayRange, trackImage);
        }
    }
    private static StructureObject getNextError(int maxTimePoint, ArrayList<StructureObject> tracks) {
        StructureObject[] trackArray = tracks.toArray(new StructureObject[tracks.size()]);
        boolean change = true;
        boolean remainTrack = true;
        int currentTimePoint = maxTimePoint;
        while(remainTrack) {
            change = false;
            remainTrack= false;
            for (int trackIdx = 0; trackIdx<trackArray.length; ++trackIdx) {
                if (trackArray[trackIdx]!=null) {
                    remainTrack=true;
                    if (trackArray[trackIdx].getTimePoint()<currentTimePoint) {
                        trackArray[trackIdx]=trackArray[trackIdx].getNext(); 
                        change=true;
                    }
                    if (trackArray[trackIdx]!=null && trackArray[trackIdx].getTimePoint()==currentTimePoint && trackArray[trackIdx].getTrackFlag()!=null) return trackArray[trackIdx];
                }
            }
            if (!change) ++currentTimePoint;
        }
        
        return null;
    }
    
    private static StructureObject getPreviousError(int minTimePoint, ArrayList<StructureObject> tracks) {
        StructureObject[] trackArray = tracks.toArray(new StructureObject[tracks.size()]);
        // get all tracks to maximal value < errorTimePoint
        for (int trackIdx = 0; trackIdx<trackArray.length; ++trackIdx) {
            if (trackArray[trackIdx].getTimePoint()>=minTimePoint) trackArray[trackIdx] = null;
            else while (trackArray[trackIdx].getNext()!=null && trackArray[trackIdx].getTimePoint()<minTimePoint-1) trackArray[trackIdx] = trackArray[trackIdx].getNext();
        }
        
        boolean change = true;
        boolean remainTrack = true;
        int currentTimePoint = minTimePoint-1;
        while(remainTrack) {
            change = false;
            remainTrack= false;
            for (int trackIdx = 0; trackIdx<trackArray.length; ++trackIdx) {
                if (trackArray[trackIdx]!=null) {
                    remainTrack=true;
                    if (trackArray[trackIdx].getTimePoint()>currentTimePoint) {
                        trackArray[trackIdx]=trackArray[trackIdx].getPrevious();
                        change=true;
                    }
                    if (trackArray[trackIdx]!=null && trackArray[trackIdx].getTimePoint()==currentTimePoint && trackArray[trackIdx].getTrackFlag()!=null) return trackArray[trackIdx];
                }
            }
            if (!change) --currentTimePoint;
        }
        
        return null;
    }

}
