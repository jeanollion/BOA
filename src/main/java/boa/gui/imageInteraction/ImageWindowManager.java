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

import boa.gui.GUI;
import static boa.gui.GUI.logger;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import ij.ImagePlus;
import image.BoundingBox;
import image.Image;
import image.ImageInteger;
import image.ImageOperations;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import utils.Pair;
import static utils.Pair.unpair2;
import utils.Utils;

/**
 *
 * @author jollion
 * @param <T> image class
 * @param <U> object ROI class
 * @param <V> track ROI class
 */
public abstract class ImageWindowManager<T, U, V> {
    public final static Color[] palette = new Color[]{new Color(166, 206, 227, 150), new Color(31,120,180, 150), new Color(178,223,138, 150), new Color(51,160,44, 150), new Color(251,154,153, 150), new Color(253,191,111, 150), new Color(255,127,0, 150), new Color(255,255,153, 150), new Color(177,89,40, 150)};
    public final static Color defaultRoiColor = new Color(255, 0, 255, 150);
    public static Color getColor(int idx) {return palette[idx%palette.length];}
    protected final static Color trackErrorColor = new Color(255, 0, 0);
    protected final static Color trackCorrectionColor = new Color(0, 0, 255);
    final static int trackArrowStrokeWidth = 3;
    protected final HashMap<ImageObjectInterfaceKey, ImageObjectInterface> imageObjectInterfaces;
    protected final HashMap<Image, ImageObjectInterfaceKey> imageObjectInterfaceMap;
    protected final HashMap<Image, Boolean> isLabelImage;
    final ImageObjectListener listener;
    final ImageDisplayer<T> displayer;
    int interactiveStructureIdx;
    
    // displayed objects 
    protected final Map<Pair<StructureObject, BoundingBox>, U> objectRoiMap = new HashMap<Pair<StructureObject, BoundingBox>, U>();
    protected final Map<Pair<StructureObject, StructureObject>, V> parentTrackHeadTrackRoiMap=new HashMap<Pair<StructureObject, StructureObject>, V>();
    protected final Map<Image, Set<U>> displayedLabileObjectRois = new HashMap<Image, Set<U>>();
    protected final Map<Image, Set<V>> displayedLabileTrackRois = new HashMap<Image, Set<V>>();
    
    public ImageWindowManager(ImageObjectListener listener, ImageDisplayer displayer) {
        this.listener=listener;
        this.displayer=displayer;
        imageObjectInterfaceMap = new HashMap<Image, ImageObjectInterfaceKey>();
        isLabelImage = new HashMap<Image, Boolean>();
        imageObjectInterfaces = new HashMap<ImageObjectInterfaceKey, ImageObjectInterface>();
    }
    
    public ImageDisplayer<T> getDisplayer() {return displayer;}
    
    //protected abstract T getImage(Image image);
    
    public void setInteractiveStructure(int structureIdx) {
        this.interactiveStructureIdx=structureIdx;
    }
    
    public void addImage(Image image, ImageObjectInterface i, boolean labelImage, boolean displayImage) {
        //ImageObjectInterface i = getImageObjectInterface(parent, childStructureIdx, timeImage);
        if (!imageObjectInterfaces.containsValue(i)) throw new RuntimeException("image object interface should be created through the manager");
        //T dispImage = getImage(image);
        imageObjectInterfaceMap.put(image, new ImageObjectInterfaceKey(i.parent, i.childStructureIdx, i.isTimeImage()));
        isLabelImage.put(image, labelImage);
        if (displayImage) {
            displayer.showImage(image);
            addMouseListener(image);
        }
    }
    
    public void resetImageObjectInterface(StructureObject parent, int childStructureIdx) {
        imageObjectInterfaces.remove(new ImageObjectInterfaceKey(parent, childStructureIdx, false));
    }
    
    public ImageObjectInterface getImageObjectInterface(StructureObject parent, int childStructureIdx, boolean createIfNotExisting) {
        ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(parent, childStructureIdx, false));
        if (i==null && createIfNotExisting) {
            i= new StructureObjectMask(parent, childStructureIdx);
            imageObjectInterfaces.put(i.getKey(), i);
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
            imageObjectInterfaces.put(i.getKey(), i);
            i.setGUIMode(GUI.hasInstance());
        } 
        return i;
    }
    
    public ImageObjectInterface getImageTrackObjectInterfaceIfExisting(StructureObject parentTrackHead, int childStructureIdx) {
        return imageObjectInterfaces.get(new ImageObjectInterfaceKey(parentTrackHead.getTrackHead(), childStructureIdx, true));
    }
    
    protected void reloadObjects_(StructureObject parent, int childStructureIdx, boolean track) {
        if (track) parent=parent.getTrackHead();
        ImageObjectInterfaceKey key = new ImageObjectInterfaceKey(parent, childStructureIdx, track);
        ImageObjectInterface i = imageObjectInterfaces.get(key);
        if (i!=null) {
            i.reloadObjects();
            for (Entry<Image, ImageObjectInterfaceKey> e : imageObjectInterfaceMap.entrySet()) if (e.getValue().equals(key)) {
                //logger.debug("updating image: {}", e.getKey().getName());
                if (isLabelImage.get(e.getKey())) {
                    ImageOperations.fill(((ImageInteger)e.getKey()), 0, null);
                    i.draw((ImageInteger)e.getKey());
                }
                if (!track) getDisplayer().updateImageDisplay(e.getKey());
            }
        }
    }
    
    public void reloadObjects(StructureObject parent, int childStructureIdx, boolean wholeTrack) {
        reloadObjects_(parent, childStructureIdx, true); // reload track images
        if (wholeTrack) { // reload other images
            StructureObject parentTrack = parent.getTrackHead();
            while (parentTrack!=null) {
                reloadObjects_(parentTrack, childStructureIdx, false);
                parentTrack=parentTrack.getNext();
            }
        } else reloadObjects_(parent, childStructureIdx, false);
        if (parent.getParent()!=null) reloadObjects(parent.getParent(), childStructureIdx, wholeTrack);
    } 
    
    public ImageObjectInterface getCurrentImageObjectInterface() {
        T current = getDisplayer().getCurrentImage();
        if (current!=null) {
            Image im = getDisplayer().getImage(current);
            if (im!=null) {
                return getImageObjectInterface(im);
            }
        }
        return null;
    }
    public ImageObjectInterfaceKey getImageObjectInterfaceKey(Image image) {
        return imageObjectInterfaceMap.get(image);
    }
    public ImageObjectInterface getImageObjectInterface(Image image) {
        ImageObjectInterfaceKey key = imageObjectInterfaceMap.get(image);
        if (key==null) return null;
        if (isLabelImage.get(image)) return this.imageObjectInterfaces.get(key);
        else { // for raw image, use the childStructureIdx set by the GUI. Creates the ImageObjectInterface if necessary 
            return getImageObjectInterface(image, interactiveStructureIdx);
        }
    }
    public ImageObjectInterface getImageObjectInterface(Image image, int structureIdx) {
        ImageObjectInterfaceKey key = imageObjectInterfaceMap.get(image);
        if (key==null) return null;
        if (key.parent.getStructureIdx()>structureIdx) return null;
        ImageObjectInterface i = this.imageObjectInterfaces.get(key.getKey(structureIdx));
        if (i==null) {
            ImageObjectInterface ref = this.imageObjectInterfaces.get(key);
            // create imageObjectInterface
            if (ref.isTimeImage()) i = this.getImageTrackObjectInterface(((TrackMask)ref).parentTrack, structureIdx);
            else i = this.getImageObjectInterface(ref.parent, structureIdx, true);
            this.imageObjectInterfaces.put(i.getKey(), i);
        }
        return i;
    }
    
    public void removeImage(Image image) {
        imageObjectInterfaceMap.remove(image);
        //removeClickListener(image);
    }
    
    public abstract void addMouseListener(Image image);
    /**
     * 
     * @param image
     * @return list of coordinates (x, y, z starting from 0) within the image, in voxel unit
     */
    protected abstract List<int[]> getSelectedPointsOnImage(T image);
    /**
     * 
     * @param image
     * @return mapping of containing objects (parents) to relative (to the parent) coordinated of selected point 
     */
    public Map<StructureObject, List<int[]>> getParentSelectedPointsMap(Image image, int parentStructureIdx) {
        T dispImage;
        if (image==null) {
            dispImage = displayer.getCurrentImage();
            if (dispImage==null) return null;
            image = displayer.getImage(dispImage);
        } else dispImage = displayer.getImage(image);
        if (dispImage==null) return null;
        ImageObjectInterface i = this.getImageObjectInterface(image, parentStructureIdx);
        if (i==null) return null;
        
        List<int[]> rawCoordinates = getSelectedPointsOnImage(dispImage);
        Map<StructureObject, List<int[]>> map = new HashMap<StructureObject, List<int[]>>();
        for (int[] c : rawCoordinates) {
            Pair<StructureObject, BoundingBox> parent = i.getClickedObject(c[0], c[1], c[2]);
            if (parent!=null) {
                c[0]-=parent.value.getxMin();
                c[1]-=parent.value.getyMin();
                c[2]-=parent.value.getzMin();
                List<int[]> children = map.get(parent.key);
                if (children==null) {
                    children = new ArrayList<int[]>();
                    map.put(parent.key, children);
                }
                children.add(c);
                logger.debug("adding point: {} to parent: {} located: {}", c, parent.key, parent.value);
            }
        }
        return map;
        
    }
    //public abstract void removeClickListener(Image image);
    
    public Pair<StructureObject, BoundingBox> getClickedObject(Image image, int x, int y, int z) {
        ImageObjectInterface i = getImageObjectInterface(image);
        if (i!=null) {
            return i.getClickedObject(x, y, z);
        } else logger.warn("image: {} is not registered for click");
        return null;
    }

    public void displayAllObjectsOnCurrentImage() {
        T im= displayer.getCurrentImage();
        if (im==null) return;
        Image image = displayer.getImage(im);
        ImageObjectInterface i =  getImageObjectInterface(image, interactiveStructureIdx);
        displayObjects(image, i.getObjects(), defaultRoiColor, true);
        listener.fireObjectSelected(Pair.unpair(i.getObjects()), true);
    }
    
    
    protected abstract void displayObject(T image, U roi);
    protected abstract void hideObject(T image, U roi);
    protected abstract U generateObjectRoi(Pair<StructureObject, BoundingBox> object, boolean image2D, Color color);
    protected abstract void setObjectColor(U roi, Color color);
    
    public void displayObjects(Image image, List<Pair<StructureObject, BoundingBox>> objectsToDisplay, Color color, boolean labileObjects) {
        if (objectsToDisplay.isEmpty() || (objectsToDisplay.get(0)==null)) return;
        if (color==null) color = ImageWindowManager.defaultRoiColor;
        T dispImage;
        if (image==null) {
            dispImage = displayer.getCurrentImage();
            if (dispImage==null) return;
            image = displayer.getImage(dispImage);
        }
        else dispImage = displayer.getImage(image);
        if (dispImage==null || image==null) return;
        Set<U> labiles = null;
        if (labileObjects) {
            labiles = this.displayedLabileObjectRois.get(image);
            if (labiles==null) {
                labiles = new HashSet<U>();
                displayedLabileObjectRois.put(image, labiles);
            }
        }
        for (Pair<StructureObject, BoundingBox> p : objectsToDisplay) {
            if (p==null || p.key==null) continue;
            //logger.debug("getting mask of object: {}", o);
            U roi=objectRoiMap.get(p);
            if (roi==null) {
                roi = generateObjectRoi(p, image.getSizeZ()<=1, color);
                objectRoiMap.put(p, roi);
            } else {
                setObjectColor(roi, color);
            }
            if (labiles==null || !labiles.contains(roi)) displayObject(dispImage, roi);
            if (labiles!=null) labiles.add(roi);
        }
        displayer.updateImageRoiDisplay(image);
    }
    
    public void hideObjects(Image image, List<Pair<StructureObject, BoundingBox>> objects) {
        T dispImage;
        if (image==null) {
            dispImage = getDisplayer().getCurrentImage();
            if (dispImage==null) return;
            image = getDisplayer().getImage(dispImage);
        } else dispImage = getDisplayer().getImage(image);
        if (dispImage==null || image==null) return;
        Set<U> disp = this.displayedLabileObjectRois.get(image);
        for (Pair<StructureObject, BoundingBox> p : objects) {
            U roi=objectRoiMap.get(p);
            if (roi!=null) {
                hideObject(dispImage, roi);
                if (disp!=null) disp.remove(roi);
            }
        }
        displayer.updateImageRoiDisplay(image);
        //if (listener!=null) listener.fireObjectDeselected(Pair.unpair(objects));
    }
    public void hideAllObjects(Image image, boolean labileObjects) {
        T dispImage;
        if (image==null) {
            dispImage = displayer.getCurrentImage();
            if (dispImage==null) return;
            image = displayer.getImage(dispImage);
        }
        else dispImage = displayer.getImage(image);
        if (dispImage==null) return;
        if (!labileObjects) this.displayedLabileObjectRois.remove(image);
        hideAllObjects(dispImage);
        if (!labileObjects) {
            Set<U> rois = displayedLabileObjectRois.get(image);
            if (rois!=null) for (U r : rois) displayObject(dispImage, r);
        }
        displayer.updateImageRoiDisplay(image);
        /*if (listener!=null) {
            if (!labileObjects) listener.fireObjectSelected(Pair.unpair(getLabileObjects(image)), false);
            else listener.fireObjectSelected(null, false);
        }*/
    }
    protected abstract void hideAllObjects(T image);
    public void displayLabileObjects(Image image) {
        if (image==null) return;
        Set<U> rois = this.displayedLabileObjectRois.get(image);
        if (rois!=null) {
            T dispImage = displayer.getImage(image);
            if (dispImage==null) return;
            for (U t: rois) displayObject(dispImage, t);
            displayer.updateImageRoiDisplay(image);
        }
        //if (listener!=null) listener.fireObjectSelected(Pair.unpair(getLabileObjects(image)), true);
    }

    
    public void hideLabileObjects(Image image) {
        if (image==null) return;
        Set<U> rois = this.displayedLabileObjectRois.remove(image);
        if (rois!=null) {
            T dispImage = displayer.getImage(image);
            if (dispImage==null) return;
            for (U roi: rois) hideObject(dispImage, roi);
            displayer.updateImageRoiDisplay(image);
            //if (listener!=null) listener.fireObjectDeselected(Pair.unpair(getLabileObjects(image)));
        }
    }
    
    protected List<Pair<StructureObject, BoundingBox>> getLabileObjects(Image image) {
        Set<U> rois = displayedLabileObjectRois.get(image);
        if (rois!=null) {
            List<Pair<StructureObject, BoundingBox>> pairs = Utils.getKeys(this.objectRoiMap, new ArrayList<U>(rois));
            Utils.removeDuplicates(pairs, false);
            return pairs;
        } else return Collections.emptyList();
    }
    
    /// track-related methods

    protected abstract void displayTrack(T image, V roi);
    protected abstract void hideTrack(T image, V roi);
    protected abstract V generateTrackRoi(List<Pair<StructureObject, BoundingBox>> track, boolean image2D, Color color);
    protected abstract void setTrackColor(V roi, Color color);
    
    public void displayTrack(Image image, ImageObjectInterface i, List<Pair<StructureObject, BoundingBox>> track, Color color, boolean labile) {
        //logger.debug("display selected track: image: {}, addToCurrentTracks: {}, track length: {} color: {}", image,addToCurrentSelectedTracks, track==null?"null":track.size(), color);
        if (track==null || track.isEmpty()) return;
        T dispImage;
        if (image==null) {
            dispImage = getDisplayer().getCurrentImage();
            if (dispImage==null) return;
            image = getDisplayer().getImage(dispImage);
        } else dispImage = getDisplayer().getImage(image);
        if (dispImage==null || image==null) return;
        if (i==null) {
            i=this.getImageObjectInterface(image);
            if (i==null) return;
        }
        StructureObject trackHead = track.get(track.size()>1 ? 1 : 0).key.getTrackHead(); // idx = 1 because track might begin with previous object
        boolean canDisplayTrack = i instanceof TrackMask;
        //canDisplayTrack = canDisplayTrack && ((TrackMask)i).parent.getTrackHead().equals(trackHead.getParent().getTrackHead()); // same track head
        canDisplayTrack = canDisplayTrack && i.getParent().getStructureIdx()<trackHead.getStructureIdx();
        if (canDisplayTrack) {
            TrackMask tm = (TrackMask)i;
            canDisplayTrack = track.get(0).key.getTimePoint()>=tm.parentTrack.get(0).getTimePoint() 
                    && track.get(track.size()-1).key.getTimePoint()<=tm.parentTrack.get(tm.parentTrack.size()-1).getTimePoint();
        }
        if (canDisplayTrack) { 
            if (i.getKey().childStructureIdx!=track.get(0).key.getStructureIdx()) i = imageObjectInterfaces.get(i.getKey().getKey(trackHead.getStructureIdx()));
            if (((TrackMask)i).getParent()==null) logger.error("Track mask parent null!!!");
            else if (((TrackMask)i).getParent().getTrackHead()==null) logger.error("Track mask parent trackHead null!!!");
            Pair<StructureObject, StructureObject> key = new Pair(((TrackMask)i).getParent().getTrackHead(), trackHead);
            Set<V> disp = null;
            if (labile) {
                disp = displayedLabileTrackRois.get(image);
                if (disp==null) {
                    disp = new HashSet<V>();
                    displayedLabileTrackRois.put(image, disp);
                }
            }
            V roi = this.parentTrackHeadTrackRoiMap.get(key);
            if (roi==null) {
                roi = generateTrackRoi(track, i.is2D, color);
                parentTrackHeadTrackRoiMap.put(key, roi);
            } else setTrackColor(roi, color);
            if (disp==null || !disp.contains(roi)) displayTrack(dispImage, roi);
            if (disp!=null) disp.add(roi);
            displayer.updateImageRoiDisplay(image);
        } else logger.warn("image cannot display selected track: ImageObjectInterface null? {}, is Track? {}", i==null, i instanceof TrackMask);
    }
    public void hideTracks(Image image, ImageObjectInterface i, List<StructureObject> trackHeads) {
        T dispImage;
        if (image==null) {
            dispImage = getDisplayer().getCurrentImage();
            if (dispImage==null) return;
            image = getDisplayer().getImage(dispImage);
        } else dispImage = getDisplayer().getImage(image);
        if (dispImage==null || image==null) return;
        if (i==null) {
            i=this.getImageObjectInterface(image);
            if (i==null) return;
        }
        Set<V> disp = this.displayedLabileTrackRois.get(image);
        StructureObject parentTrackHead = i.getParent().getTrackHead();
        for (StructureObject th : trackHeads) {
            V roi=this.parentTrackHeadTrackRoiMap.get(new Pair(parentTrackHead, th));
            if (roi!=null) {
                hideTrack(dispImage, roi);
                if (disp!=null) disp.remove(roi);
            }
        }
        displayer.updateImageRoiDisplay(image);
        
    }
    
    public void hideAllTracks(Image image, boolean labileTracks) {
        T dispImage;
        if (image==null) {
            dispImage = displayer.getCurrentImage();
            if (dispImage==null) return;
            image = displayer.getImage(dispImage);
        }
        else dispImage = displayer.getImage(image);
        if (dispImage==null) return;
        if (labileTracks) this.displayedLabileTrackRois.remove(image);
        hideAllTracks(dispImage);
        if (!labileTracks) {
            Set<V> tracks = displayedLabileTrackRois.get(image);
            if (tracks!=null) for (V t : tracks) displayTrack(dispImage, t);
        }
        displayer.updateImageRoiDisplay(image);
        /*if (listener!=null) {
            if (!labileTracks) listener.fireTracksSelected(getLabileTrackHeads(image), false);
            else listener.fireTracksSelected(null, false);
        }*/
    }

    
    protected abstract void hideAllTracks(T image);
    public void displayLabileTracks(Image image) {
        if (image==null) return;
        Set<V> tracks = this.displayedLabileTrackRois.get(image);
        if (tracks!=null) {
            T dispImage = displayer.getImage(image);
            if (dispImage==null) return;
            for (V t: tracks) displayTrack(dispImage, t);
            displayer.updateImageRoiDisplay(image);
            //if (listener!=null) listener.fireTracksDeselected(getLabileTrackHeads(image));
        }
    }
    public void hideLabileTracks(Image image) {
        if (image==null) return;
        Set<V> tracks = this.displayedLabileTrackRois.remove(image);
        if (tracks!=null) {
            T dispImage = displayer.getImage(image);
            if (dispImage==null) return;
            for (V t: tracks) hideTrack(dispImage, t);
            displayer.updateImageRoiDisplay(image);
            //if (listener!=null) listener.fireTracksDeselected(getLabileTrackHeads(image));
        }
    }
    
    public void displayTrackAllImages(ImageObjectInterface i, boolean addToCurrentSelectedTracks, List<Pair<StructureObject, BoundingBox>> track, Color color, boolean labile) {
        if (i==null && track!=null && !track.isEmpty()) i = this.getImageObjectInterface(track.get(0).key.getTrackHead(), track.get(0).key.getStructureIdx(), false);
        if (i==null) return;
        ArrayList<Image> images= Utils.getKeys(this.imageObjectInterfaceMap, i.getKey().getKey(-1));
        //logger.debug("display track on {} images", images.size());
        for (Image image : images) {
            if (!addToCurrentSelectedTracks) hideAllTracks(image, true);
            displayTrack(image, i, track, color, labile);
        }
    }
    
    protected List<StructureObject> getLabileTrackHeads(Image image) {
        Set<V> rois = this.displayedLabileTrackRois.get(image);
        if (rois!=null) {
            List<Pair<StructureObject, StructureObject>> pairs = Utils.getKeys(this.parentTrackHeadTrackRoiMap, new ArrayList<V>(rois));
            List<StructureObject> res = unpair2(pairs);
            Utils.removeDuplicates(res, false);
            return res;
        } else return Collections.emptyList();
    }
    
    public void goToNextTrackError(Image trackImage, ArrayList<StructureObject> tracks) {
        //ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(rois.get(0).getParent().getTrackHead(), rois.get(0).getStructureIdx(), true));
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
        //logger.debug("Current Display range: {}, maxTimePoint: {}, number of selected rois: {}", currentDisplayRange, maxTimePoint, rois.size());
        StructureObject nextError = getNextError(maxTimePoint, tracks);
        if (nextError==null) logger.info("No errors detected after timepoint: {}", maxTimePoint);
        else {
            BoundingBox off = tm.getObjectOffset(nextError);
            int mid = (int)off.getXMean();
            if (mid+currentDisplayRange.getSizeX()/2>=trackImage.getSizeX()) mid = trackImage.getSizeX()-currentDisplayRange.getSizeX()/2;
            if (mid-currentDisplayRange.getSizeX()/2<0) mid = currentDisplayRange.getSizeX()/2;
            BoundingBox nextDisplayRange = new BoundingBox(mid-currentDisplayRange.getSizeX()/2, mid+currentDisplayRange.getSizeX()/2, currentDisplayRange.getyMin(), currentDisplayRange.getyMax(), currentDisplayRange.getzMin(), currentDisplayRange.getzMax());
            logger.info("Error detected @ timepoint: {}, xMid: {}, update display range: {}", nextError.getTimePoint(), mid,  nextDisplayRange);
            displayer.setDisplayRange(nextDisplayRange, trackImage);
        }
    }
    public void goToPreviousTrackError(Image trackImage, ArrayList<StructureObject> tracks) {
        //ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(rois.get(0).getParent().getTrackHead(), rois.get(0).getStructureIdx(), true));
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
            if (mid+currentDisplayRange.getSizeX()/2>=trackImage.getSizeX()) mid = trackImage.getSizeX()-currentDisplayRange.getSizeX()/2;
            if (mid-currentDisplayRange.getSizeX()/2<0) mid = currentDisplayRange.getSizeX()/2;
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
        // get all rois to maximal value < errorTimePoint
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
    
    public static List<StructureObject> extendTrack(List<StructureObject> track) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(track.size()+2);
        StructureObject prev = track.get(0).getPrevious();
        if (prev!=null) res.add(prev);
        res.addAll(track);
        StructureObject next = track.get(track.size()-1).getNext();
        if (next!=null) res.add(next);
        return res;
    }
}
