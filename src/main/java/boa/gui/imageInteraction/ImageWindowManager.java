/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.gui.imageInteraction;

import boa.gui.GUI;
import static boa.gui.GUI.logger;
import boa.core.DefaultWorker;
import static boa.data_structure.Measurements.asString;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import static boa.data_structure.StructureObjectUtils.frameComparator;
import static boa.data_structure.StructureObjectUtils.setAllChildren;
import boa.image.BoundingBox;
import boa.image.MutableBoundingBox;
import ij.ImagePlus;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.Offset;
import boa.image.SimpleBoundingBox;
import boa.image.processing.ImageOperations;
import java.awt.Color;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;
import boa.measurement.MeasurementExtractor;
import boa.utils.ArrayFileWriter;
import boa.utils.HashMapGetCreate;
import boa.utils.HashMapGetCreate.Factory;
import boa.utils.HashMapGetCreate.SetFactory;
import boa.utils.Pair;
import static boa.utils.Pair.unpairKeys;
import static boa.utils.Pair.unpairValues;
import boa.utils.Palette;
import boa.utils.Utils;

/**
 *
 * @author jollion
 * @param <I> image class
 * @param <U> object ROI class
 * @param <V> track ROI class
 */
public abstract class ImageWindowManager<I, U, V> {
    public static enum RegisteredImageType { Interactive, RawInput, PreProcessed; }
    public static boolean displayTrackMode;
    public final static Color[] palette = new Color[]{new Color(166, 206, 227, 150), new Color(31,120,180, 150), new Color(178,223,138, 150), new Color(51,160,44, 150), new Color(251,154,153, 150), new Color(253,191,111, 150), new Color(255,127,0, 150), new Color(255,255,153, 150), new Color(177,89,40, 150)};
    public final static Color defaultRoiColor = new Color(255, 0, 255, 150);
    public static Color getColor(int idx) {return palette[idx%palette.length];}
    protected final static Color trackErrorColor = new Color(255, 0, 0);
    protected final static Color trackCorrectionColor = new Color(0, 0, 255);
    public static Color getColor() {
        return Palette.getColor(150, trackErrorColor, trackCorrectionColor);
    }
    final static int trackArrowStrokeWidth = 3;
    protected final HashMap<ImageObjectInterfaceKey, ImageObjectInterface> imageObjectInterfaces;
    protected final HashMap<Image, ImageObjectInterfaceKey> imageObjectInterfaceMap;
    protected final HashMapGetCreate<StructureObject, List<List<StructureObject>>> trackHeadTrackMap;
    protected final LinkedHashMap<String, I> displayedRawInputFrames = new LinkedHashMap<>();
    protected final LinkedHashMap<String, I> displayedPrePocessedFrames = new LinkedHashMap<>();
    protected final LinkedList<Image> displayedInteractiveImages = new LinkedList<>();
    final ImageObjectListener listener;
    final ImageDisplayer<I> displayer;
    int interactiveStructureIdx;
    int displayedImageNumber = 20;
    ZoomPane localZoom;
    // displayed objects 
    protected final Map<Pair<StructureObject, BoundingBox>, U> objectRoiMap = new HashMap<>();
    protected final Map<Pair<StructureObject, StructureObject>, V> parentTrackHeadTrackRoiMap=new HashMap<>();
    protected final Map<Pair<StructureObject, BoundingBox>, U> labileObjectRoiMap = new HashMap<>();
    protected final Map<Pair<StructureObject, StructureObject>, V> labileParentTrackHeadTrackRoiMap=new HashMap<>();
    protected final HashMapGetCreate<Image, Set<U>> displayedLabileObjectRois = new HashMapGetCreate<>(new SetFactory<>());
    protected final HashMapGetCreate<Image, Set<V>> displayedLabileTrackRois = new HashMapGetCreate<>(new SetFactory<>());
    
    protected final Map<Image, DefaultWorker> runningWorkers = new HashMap<>();
    
    public ImageWindowManager(ImageObjectListener listener, ImageDisplayer<I> displayer) {
        this.listener=null;
        this.displayer=displayer;
        imageObjectInterfaceMap = new HashMap<>();
        imageObjectInterfaces = new HashMap<>();
        trackHeadTrackMap = new HashMapGetCreate<>(new HashMapGetCreate.ListFactory());
    }
    public void setDisplayImageLimit(int limit) {
        this.displayedImageNumber=limit;
    }
    public RegisteredImageType getRegisterType(Object image) {
        if (image instanceof Image) {
            if (displayedInteractiveImages.contains((Image)image)) return RegisteredImageType.Interactive;
            else return null;
        }
        if (this.displayedRawInputFrames.values().contains(image)) return RegisteredImageType.RawInput;
        if (this.displayedPrePocessedFrames.values().contains(image)) return RegisteredImageType.PreProcessed;
        try {
            I im = (I) image;
            if (displayedInteractiveImages.contains(getDisplayer().getImage(im))) return RegisteredImageType.Interactive;
        } catch(Exception e) {}
        
        return null;
    }
    void addLocalZoom(Component parent) {
        MouseAdapter ma = new MouseAdapter() {
            private void update(MouseEvent e) {
                if (localZoom ==null) return;
                localZoom.setParent(parent);
                localZoom.updateLocation(e);
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                update(e);
            }
            @Override
            public void mouseDragged(MouseEvent e){
                update(e);
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                update(e);
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                update(e);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (localZoom ==null) return;
                else localZoom.detach();
            }
        };
        parent.addMouseListener(ma);
        parent.addMouseMotionListener(ma);
    }
    public void toggleActivateLocalZoom() {
        if (localZoom==null) {
            localZoom =  GUI.getInstance()==null? new ZoomPane() : new ZoomPane(GUI.getInstance().getLocalZoomLevel(), GUI.getInstance().getLocalZoomArea());
        } else {
            localZoom.detach();
            localZoom = null;
        }
    }
    public abstract void toggleSetObjectCreationTool();
    public Map<Image, DefaultWorker> getRunningWorkers() {
        return runningWorkers;
    }
    public void stopAllRunningWorkers() {
        for (DefaultWorker w : runningWorkers.values()) w.cancel(true);
        runningWorkers.clear();
    }
    public void flush() {
        if (!runningWorkers.isEmpty()) logger.debug("flush: will stop {} running workers", runningWorkers.size());
        stopAllRunningWorkers();
        if (!objectRoiMap.isEmpty()) logger.debug("flush: will remove {} rois", objectRoiMap.size());
        objectRoiMap.clear();
        parentTrackHeadTrackRoiMap.clear();
        if (!labileObjectRoiMap.isEmpty()) logger.debug("flush: will remove {} rois", labileObjectRoiMap.size());
        labileObjectRoiMap.clear();
        labileParentTrackHeadTrackRoiMap.clear();
        displayedLabileObjectRois.clear();
        displayedLabileTrackRois.clear();
        displayer.flush();
        imageObjectInterfaces.clear();
        if (!imageObjectInterfaceMap.isEmpty()) logger.debug("flush: will remove {} images", imageObjectInterfaceMap.size());
        imageObjectInterfaceMap.clear();
        trackHeadTrackMap.clear();
        displayedRawInputFrames.clear();
        displayedPrePocessedFrames.clear();
        displayedInteractiveImages.clear();
    }
    public void closeNonInteractiveWindows() {
        closeLastInputImages(0);
    }
    public ImageDisplayer<I> getDisplayer() {return displayer;}
    
    //protected abstract I getImage(Image image);
    
    public void setInteractiveStructure(int structureIdx) {
        this.interactiveStructureIdx=structureIdx;
    }
    
    public int getInteractiveStructure() {
        return interactiveStructureIdx;
    }
    
    public Image getImage(ImageObjectInterface i) {
        if (i==null) {
            logger.error("cannot get image if IOI null");
            return null;
        }
        List<Image> list = Utils.getKeys(imageObjectInterfaceMap, new ImageObjectInterfaceKey(i.parents, i.childStructureIdx, i.isTimeImage()));
        if (list.isEmpty()) return null;
        else return list.get(0);
    }
    public Image getImage(ImageObjectInterface i, int displayStructureIdx) {
        List<Image> list = Utils.getKeys(imageObjectInterfaceMap, new ImageObjectInterfaceKey(i.parents, displayStructureIdx, i.isTimeImage()));
        if (list.isEmpty()) return null;
        else return list.get(0);
    }
    
    public void setActive(Image image) {
        boolean b =  displayedInteractiveImages.remove(image);
        if (b) displayedInteractiveImages.add(image);
    }
    
    public void addImage(Image image, ImageObjectInterface i, int displayedStructureIdx, boolean displayImage) {
        if (image==null) return;
        //ImageObjectInterface i = getImageObjectInterface(parent, childStructureIdx, timeImage);
        logger.debug("adding image: {} (hash: {}), IOI exists: {} ({})", image.getName(), image.hashCode(), imageObjectInterfaces.containsKey(i.getKey()), imageObjectInterfaces.containsValue(i));
        if (!imageObjectInterfaces.containsValue(i)) {
            //throw new RuntimeException("image object interface should be created through the manager");
            imageObjectInterfaces.put(i.getKey(), i);
        }
        //T dispImage = getImage(image);
        imageObjectInterfaceMap.put(image, new ImageObjectInterfaceKey(i.parents, displayedStructureIdx, i.isTimeImage()));
        if (displayImage) {
            displayImage(image, i);
            if (i instanceof TrackMask && ((TrackMask)i).imageCallback.containsKey(image)) this.displayer.addMouseWheelListener(image, ((TrackMask)i).imageCallback.get(image));
        }
        
    }
    
    public void displayImage(Image image, ImageObjectInterface i) {
        long t0 = System.currentTimeMillis();
        displayer.showImage(image);
        long t1 = System.currentTimeMillis();
        displayedInteractiveImages.add(image);
        addMouseListener(image);
        addWindowClosedListener(image, e-> {
            DefaultWorker w = runningWorkers.get(image);
            if (w!=null) {
                logger.debug("interruptin generation of closed image: {}", image.getName());
                w.cancel(true);
            }
            displayedInteractiveImages.remove(image);
            return null;
        });
        long t2 = System.currentTimeMillis();
        GUI.updateRoiDisplayForSelections(image, i);
        long t3 = System.currentTimeMillis();
        closeLastActiveImages(displayedImageNumber);
        long t4 = System.currentTimeMillis();
        logger.debug("display image: show: {} ms, add list: {}, update ROI: {}, close last active image: {}", t1-t0, t2-t1, t3-t2, t4-t3);
    }
    public String getPositionOfInputImage(I image) {
        String pos = Utils.getOneKey(displayedRawInputFrames, image);
        if (pos!=null) return pos;
        return Utils.getOneKey(displayedPrePocessedFrames, image);
    }
    public void addInputImage(String position, I image, boolean raw) {
        if (image==null) return;
        addWindowClosedListener(image, e-> {
            if (raw) displayedRawInputFrames.remove(position);
            else displayedPrePocessedFrames.remove(position);
            return null;
        });
        if (raw) displayedRawInputFrames.put(position, image);
        else displayedPrePocessedFrames.put(position,  image);
        closeLastInputImages(displayedImageNumber);
    }
    public void closeLastActiveImages(int numberOfKeptImages) {
        logger.debug("close active images: total opened {} limit: {}", displayedInteractiveImages.size(), numberOfKeptImages);
        if (numberOfKeptImages<=0) return;
        if (displayedInteractiveImages.size()>numberOfKeptImages) {
            Iterator<Image> it = displayedInteractiveImages.iterator();
            while(displayedInteractiveImages.size()>numberOfKeptImages && it.hasNext()) {
                Image next = it.next();
                it.remove();
                displayer.close(next);
            }
        }
    }
    public void closeLastInputImages(int numberOfKeptImages) {
        //logger.debug("close input images: raw: {} pp: {} limit: {}", displayedRawInputFrames.size(), displayedPrePocessedFrames.size(), numberOfKeptImages);
        if (numberOfKeptImages<=0) return;
        if (displayedRawInputFrames.size()>numberOfKeptImages) {
            Iterator<String> it = displayedRawInputFrames.keySet().iterator();
            while(displayedRawInputFrames.size()>numberOfKeptImages && it.hasNext()) {
                String i = it.next();
                I im = displayedRawInputFrames.get(i);
                it.remove();
                displayer.close(im);
            }
        }
        if (displayedPrePocessedFrames.size()>numberOfKeptImages) {
            Iterator<String> it = displayedPrePocessedFrames.keySet().iterator();
            while(displayedPrePocessedFrames.size()>numberOfKeptImages && it.hasNext()) {
                String i = it.next();
                I im = displayedPrePocessedFrames.get(i);
                it.remove();
                displayer.close(im);
            }
        }
    }
    
    public void resetImageObjectInterface(StructureObject parent, int childStructureIdx) {
        imageObjectInterfaces.remove(new ImageObjectInterfaceKey(new ArrayList<StructureObject>(1){{add(parent);}}, childStructureIdx, false));
    }
    
    public ImageObjectInterface getImageObjectInterface(StructureObject parent, int childStructureIdx, boolean createIfNotExisting) {
        ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(new ArrayList<StructureObject>(1){{add(parent);}}, childStructureIdx, false));
        if (i==null && createIfNotExisting) {
            i= new StructureObjectMask(parent, childStructureIdx);
            imageObjectInterfaces.put(i.getKey(), i);
        } 
        return i;
    }
    public TrackMask generateTrackMask(List<StructureObject> parentTrack, int childStructureIdx) {
        //setAllChildren(parentTrack, childStructureIdx); // if set -> tracking test cannot work ? 
        BoundingBox bb = parentTrack.get(0).getBounds();
        return bb.sizeY()>=bb.sizeX() ? new TrackMaskX(parentTrack, childStructureIdx, false) : new TrackMaskY(parentTrack, childStructureIdx);
    }
    public ImageObjectInterface getImageTrackObjectInterface(List<StructureObject> parentTrack, int childStructureIdx) {
        
        if (parentTrack.isEmpty()) {
            logger.warn("cannot open track image of length == 0" );
            return null;
        }
        ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(parentTrack, childStructureIdx, true));
        logger.debug("getIOI: hash: {} ({}), exists: {}, trackHeadTrackMap: {}", parentTrack.hashCode(), new ImageObjectInterfaceKey(parentTrack, childStructureIdx, true).hashCode(), i!=null, trackHeadTrackMap.containsKey(parentTrack.get(0)));
        if (i==null) {
            long t0 = System.currentTimeMillis();
            i = generateTrackMask(parentTrack, childStructureIdx);
            long t1 = System.currentTimeMillis();
            imageObjectInterfaces.put(i.getKey(), i);
            trackHeadTrackMap.getAndCreateIfNecessary(parentTrack.get(0)).add(parentTrack);
            i.setGUIMode(GUI.hasInstance());
            long t2 = System.currentTimeMillis();
            logger.debug("create IOI: {} key: {}, creation time: {} ms + {} ms", i.hashCode(), i.getKey().hashCode(), t1-t0, t2-t1);
        } 
        return i;
    }
    
    /*public ImageObjectInterface getImageTrackObjectInterfaceIfExisting(StructureObject parentTrackHead, int childStructureIdx) {
        List<StructureObject> track = getTrack(parentTrackHead);
        if (track==null) return null;
        return imageObjectInterfaces.get(new ImageObjectInterfaceKey(track, childStructureIdx, true));
    }
    
    private List<StructureObject> getTrack(StructureObject trackHead) {
        List<List<StructureObject>> tracks = this.trackHeadTrackMap.get(trackHead);
        if (tracks==null || tracks.isEmpty()) return null;
        if (tracks.size()>1) {
            for (List<StructureObject> track : tracks) {
                for (StructureObject o : track) {
                    if (o.getTrackHead()!=trackHead) break; // not a real track
                }
                return track;
            }
        }
        return null;
    }*/
    protected void reloadObjects__(ImageObjectInterfaceKey key, boolean track) {
        
        ImageObjectInterface i = imageObjectInterfaces.get(key);
        if (i!=null) {
            logger.debug("reloading object for parentTrackHead: {} structure: {}", key.parent.get(0), key.displayedStructureIdx);
            i.reloadObjects();
            /*
            // also reload all label images!!
            for (Entry<Image, ImageObjectInterfaceKey> e : imageObjectInterfaceMap.entrySet()) if (e.getValue().equals(key)) {
                //logger.debug("updating image: {}", e.getKey().getName());
                if (isLabelImage.get(e.getKey())) {
                    ImageOperations.fill(((ImageInteger)e.getKey()), 0, null);
                    i.drawObjects((ImageInteger)e.getKey());
                }
                hideAllRois(null, true, true);
                if (!track) getDisplayer().updateImageDisplay(e.getKey());
            }
            */
        }
    }
    protected void reloadObjects_(StructureObject parent, int childStructureIdx, boolean track) {
        if (track) parent=parent.getTrackHead();
        if (!trackHeadTrackMap.containsKey(parent)) {
            final StructureObject p = parent;
            reloadObjects__(new ImageObjectInterfaceKey(new ArrayList<StructureObject>(1){{add(p);}}, childStructureIdx, track), track);
        } else {
            for (List<StructureObject> l : trackHeadTrackMap.get(parent)) {
                reloadObjects__(new ImageObjectInterfaceKey(l, childStructureIdx, track), track);
            }
        }
        
        
        
    }
    
    public void reloadObjects(StructureObject parent, int childStructureIdx, boolean wholeTrack) {
        reloadObjects_(parent, childStructureIdx, true); // reload track images
        if (wholeTrack) { // reload each image of the track
            List<StructureObject> track = StructureObjectUtils.getTrack(parent.getTrackHead(), false);
            for (StructureObject o : track) reloadObjects_(o, childStructureIdx, false);
        } else reloadObjects_(parent, childStructureIdx, false);
        if (parent.getParent()!=null) reloadObjects(parent.getParent(), childStructureIdx, wholeTrack);
        this.resetObjectsAndTracksRoi();
    } 
    
    public ImageObjectInterface getCurrentImageObjectInterface() {
        I current = getDisplayer().getCurrentImage();
        if (current!=null) {
            RegisteredImageType type = this.getRegisterType(current);
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
        if (image==null) {
            image = getDisplayer().getCurrentImage2();
            if (image==null) return null;
        }
        ImageObjectInterfaceKey key = imageObjectInterfaceMap.get(image);
        if (key==null) return null;
        return getImageObjectInterface(image, interactiveStructureIdx); // use the interactive structure. Creates the ImageObjectInterface if necessary 
    }
    public ImageObjectInterface getImageObjectInterface(Image image, int structureIdx) {
        if (image==null) {
            image = getDisplayer().getCurrentImage2();
            if (image==null) {
                return null;
            }
        }
        ImageObjectInterfaceKey key = imageObjectInterfaceMap.get(image);
        if (key==null) return null;
        //if (key.parent.get(0).getStructureIdx()>structureIdx) return null;
        ImageObjectInterface i = this.imageObjectInterfaces.get(key.getKey(structureIdx));
        
        if (i==null) {
            ImageObjectInterface ref = ImageObjectInterfaceKey.getOneElementIgnoreStructure(key, imageObjectInterfaces);
            if (ref==null) logger.error("IOI not found: ref: {} ({}), all IOI: {}", key, key.getKey(-1), imageObjectInterfaces.keySet());
            else logger.debug("creating IOI: ref: {}", ref);
            // create imageObjectInterface
            if (ref.isTimeImage()) i = this.getImageTrackObjectInterface(((TrackMask)ref).parents, structureIdx);
            else i = this.getImageObjectInterface(ref.parents.get(0), structureIdx, true);
            this.imageObjectInterfaces.put(i.getKey(), i);
        } 
        return i;
    }
    
    public void removeImage(Image image) {
        imageObjectInterfaceMap.remove(image);
        //removeClickListener(image);
    }
    public void removeImageObjectInterface(ImageObjectInterfaceKey key) {
        // ignore structure
        Iterator<Entry<ImageObjectInterfaceKey, ImageObjectInterface>> it = imageObjectInterfaces.entrySet().iterator();
        while(it.hasNext()) if (it.next().getKey().equalsIgnoreStructure(key)) it.remove();
        Iterator<Entry<Image, ImageObjectInterfaceKey>> it2 = imageObjectInterfaceMap.entrySet().iterator();
        while(it2.hasNext()) if (it2.next().getValue().equalsIgnoreStructure(key)) it2.remove();
    }
    
    public abstract void addMouseListener(Image image);
    public abstract void addWindowListener(Object image, WindowListener wl);
    public void addWindowClosedListener(Object image, Function<WindowEvent, Void> closeFunction) {
        addWindowListener(image, new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) { }
            @Override
            public void windowClosing(WindowEvent e) {}
            @Override
            public void windowClosed(WindowEvent e) {
                closeFunction.apply(e);
            }
            @Override
            public void windowIconified(WindowEvent e) { }
            @Override
            public void windowDeiconified(WindowEvent e) { }
            @Override
            public void windowActivated(WindowEvent e) { }
            @Override
            public void windowDeactivated(WindowEvent e) { }
        });
    }
    /**
     * 
     * @param image
     * @return list of coordinates (x, y, z starting from 0) within the image, in voxel unit
     */
    protected abstract List<int[]> getSelectedPointsOnImage(I image);
    /**
     * 
     * @param image
     * @return mapping of containing objects (parents) to relative (to the parent) coordinated of selected point 
     */
    public Map<StructureObject, List<int[]>> getParentSelectedPointsMap(Image image, int parentStructureIdx) {
        I dispImage;
        if (image==null) {
            dispImage = displayer.getCurrentImage();
            if (dispImage==null) return null;
            image = displayer.getImage(dispImage);
        } else dispImage = displayer.getImage(image);
        if (dispImage==null) return null;
        ImageObjectInterface i = this.getImageObjectInterface(image, parentStructureIdx);
        if (i==null) return null;
        
        List<int[]> rawCoordinates = getSelectedPointsOnImage(dispImage);
        HashMapGetCreate<StructureObject, List<int[]>> map = new HashMapGetCreate<StructureObject, List<int[]>>(new HashMapGetCreate.ListFactory<StructureObject, int[]>());
        for (int[] c : rawCoordinates) {
            Pair<StructureObject, BoundingBox> parent = i.getClickedObject(c[0], c[1], c[2]);
            if (parent!=null) {
                c[0]-=parent.value.xMin();
                c[1]-=parent.value.yMin();
                c[2]-=parent.value.zMin();
                List<int[]> children = map.getAndCreateIfNecessary(parent.key);
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

    public void displayAllObjects(Image image) {
        if (image==null) {
            image = getDisplayer().getCurrentImage2();
            if (image==null) return;
        }
        ImageObjectInterface i =  getImageObjectInterface(image, interactiveStructureIdx);
        if (i==null) {
            logger.error("no image object interface found for image: {} and structure: {}", image.getName(), interactiveStructureIdx);
            return;
        }
        displayObjects(image, i.getObjects(), defaultRoiColor, true, false);
        if (listener!=null) listener.fireObjectSelected(Pair.unpairKeys(i.getObjects()), true);
    }
    
    public void displayAllTracks(Image image) {
        if (image==null) {
            image = getDisplayer().getCurrentImage2();
            if (image==null) return;
        }
        ImageObjectInterface i =  getImageObjectInterface(image, interactiveStructureIdx);
        if (i==null) {
            logger.error("no image object interface found for image: {} and structure: {}", image.getName(), interactiveStructureIdx);
            return;
        }
        Collection<StructureObject> objects = Pair.unpairKeys(i.getObjects());
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(objects, true);
        //for (Entry<StructureObject, List<StructureObject>> e : allTracks.entrySet()) logger.debug("th:{}->{}", e.getKey(), e.getValue());
        displayTracks(image, i, allTracks.values(), true);
        //if (listener!=null) 
    }
    
    
    public abstract void displayObject(I image, U roi);
    public abstract void hideObject(I image, U roi);
    protected abstract U generateObjectRoi(Pair<StructureObject, BoundingBox> object, Color color);
    protected abstract void setObjectColor(U roi, Color color);
    
    public void setRoiModifier(RoiModifier<U> modifier) {this.roiModifier=modifier;}
    RoiModifier<U> roiModifier;
    public static interface RoiModifier<U> {
        public void modifyRoi(Pair<StructureObject, BoundingBox> currentObject, U currentRoi, Collection<Pair<StructureObject, BoundingBox>> objectsToDisplay);
    }
    public void displayObjects(Image image, Collection<Pair<StructureObject, BoundingBox>> objectsToDisplay, Color color, boolean labileObjects, boolean hideIfAlreadyDisplayed) {
        if (objectsToDisplay.isEmpty() || (objectsToDisplay.iterator().next()==null)) return;
        if (color==null) color = ImageWindowManager.defaultRoiColor;
        I dispImage;
        if (image==null) {
            dispImage = displayer.getCurrentImage();
            if (dispImage==null) return;
            image = displayer.getImage(dispImage);
        }
        else dispImage = displayer.getImage(image);
        if (dispImage==null || image==null) return;
        Set<U> labiles = labileObjects ? this.displayedLabileObjectRois.getAndCreateIfNecessary(image) : null;
        Map<Pair<StructureObject, BoundingBox>, U> map = labileObjects ? this.labileObjectRoiMap : objectRoiMap;
        long t0 = System.currentTimeMillis();
        for (Pair<StructureObject, BoundingBox> p : objectsToDisplay) {
            if (p==null || p.key==null) continue;
            //logger.debug("getting mask of object: {}", o);
            U roi=map.get(p);
            if (roi==null) {
                roi = generateObjectRoi(p, color);
                map.put(p, roi);
                //if (!labileObjects) logger.debug("add non labile object: {}, found by keyonly? {}", p.key, map.containsKey(new Pair(p.key, null)));
            } else {
                setObjectColor(roi, color);
            }
            if (roiModifier!=null) roiModifier.modifyRoi(p, roi, objectsToDisplay);
            if (labileObjects) {
                if (labiles.contains(roi)) {
                    if (hideIfAlreadyDisplayed) {
                        hideObject(dispImage, roi);
                        labiles.remove(roi);
                        logger.debug("display -> inverse state: hide: {}", p.key);
                        Object attr = new HashMap<String, Object>(0);
                        try {
                            Field attributes = StructureObject.class.getDeclaredField("attributes"); attributes.setAccessible(true);
                            attr = attributes.get(p.key);
                        } catch (Exception e) {}
                        logger.debug("isTH: {}, values: {}, attributes: {}", p.key.isTrackHead(), p.key.getMeasurements().getValues(), attr);
                    }
                } else {
                    displayObject(dispImage, roi);
                    labiles.add(roi);
                }
            } else displayObject(dispImage, roi);
        }
        long t1 = System.currentTimeMillis();
        displayer.updateImageRoiDisplay(image);
        long t2 = System.currentTimeMillis();
        logger.debug("display {} objects: create roi & add to overlay: {}, update display: {}", objectsToDisplay.size(), t1-t0, t2-t1);
    }
    
    public void hideObjects(Image image, Collection<Pair<StructureObject, BoundingBox>> objects, boolean labileObjects) {
        I dispImage;
        if (image==null) {
            image = getDisplayer().getCurrentImage2();
            if (image==null) return;
        } 
        dispImage = getDisplayer().getImage(image);
        if (dispImage==null) return;
        Set<U> selectedObjects = labileObjects ? this.displayedLabileObjectRois.get(image) : null;
        Map<Pair<StructureObject, BoundingBox>, U> map = labileObjects ? labileObjectRoiMap : objectRoiMap;
        for (Pair<StructureObject, ?> p : objects) {
            //logger.debug("hiding: {}", p.key);
            U roi=map.get(p);
            if (roi!=null) {
                hideObject(dispImage, roi);
                if (selectedObjects!=null) selectedObjects.remove(roi);
            }
            //logger.debug("hide object: {} found? {}", p.key, roi!=null);
        }
        displayer.updateImageRoiDisplay(image);
    }

    public void displayLabileObjects(Image image) {
        if (image==null) {
            image = getDisplayer().getCurrentImage2();
            if (image==null) return;
        }
        Set<U> rois = this.displayedLabileObjectRois.get(image);
        if (rois!=null) {
            I dispImage = displayer.getImage(image);
            if (dispImage==null) return;
            for (U roi: rois) displayObject(dispImage, roi);
            displayer.updateImageRoiDisplay(image);
        }
        //if (listener!=null) listener.fireObjectSelected(Pair.unpair(getLabileObjects(image)), true);
    }

    
    public void hideLabileObjects(Image image) {
        if (image==null) {
            image = getDisplayer().getCurrentImage2();
            if (image==null) return;
        }
        Set<U> rois = this.displayedLabileObjectRois.remove(image);
        if (rois!=null) {
            I dispImage = displayer.getImage(image);
            if (dispImage==null) return;
            for (U roi: rois) hideObject(dispImage, roi);
            displayer.updateImageRoiDisplay(image);
            //if (listener!=null) listener.fireObjectDeselected(Pair.unpair(getLabileObjects(image)));
        }
    }
    
    public List<StructureObject> getSelectedLabileObjects(Image image) {
        if (image==null) {
            image = getDisplayer().getCurrentImage2();
            if (image==null) return Collections.emptyList();
        }
        Set<U> rois = displayedLabileObjectRois.get(image);
        if (rois!=null) {
            List<Pair<StructureObject, BoundingBox>> pairs = Utils.getKeys(labileObjectRoiMap, rois);
            List<StructureObject> res = Pair.unpairKeys(pairs);
            Utils.removeDuplicates(res, false);
            return res;
        } else return Collections.emptyList();
    }
    
    /// track-related methods
    
    protected abstract void displayTrack(I image, V roi);
    protected abstract void hideTrack(I image, V roi);
    protected abstract V generateTrackRoi(List<Pair<StructureObject, BoundingBox>> track, Color color);
    protected abstract void setTrackColor(V roi, Color color);
    public void displayTracks(Image image, ImageObjectInterface i, Collection<List<StructureObject>> tracks, boolean labile) {
        if (image==null) {
            image = displayer.getCurrentImage2();
            if (image==null) return;
        }
        if (i ==null) {
            i = this.getImageObjectInterface(image);
            
        }
        logger.debug("image: {}, OI: {}", image.getName(), i.getClass().getSimpleName());
        for (List<StructureObject> track : tracks) {
            displayTrack(image, i, i.pairWithOffset(track), getColor() , labile);
        }
        //GUI.updateRoiDisplayForSelections(image, i);
    }
    public void displayTrack(Image image, ImageObjectInterface i, List<Pair<StructureObject, BoundingBox>> track, Color color, boolean labile) {
        //logger.debug("display selected track: image: {}, track length: {} color: {}", image, track==null?"null":track.size(), color);
        if (track==null || track.isEmpty()) return;
        I dispImage;
        if (image==null) {
            dispImage = getDisplayer().getCurrentImage();
            if (dispImage==null) return;
            image = getDisplayer().getImage(dispImage);
        } else dispImage = getDisplayer().getImage(image);
        if (dispImage==null || image==null) return;
        if (i==null) {
            i=this.getImageObjectInterface(image);
            //logger.debug("image: {}, OI: {}", image.getName(), i.getClass().getSimpleName());
            if (i==null) return;
        }
        StructureObject trackHead = track.get(track.size()>1 ? 1 : 0).key.getTrackHead(); // idx = 1 because track might begin with previous object
        boolean canDisplayTrack = i instanceof TrackMask;
        //canDisplayTrack = canDisplayTrack && ((TrackMask)i).parent.getTrackHead().equals(trackHead.getParent().getTrackHead()); // same track head
        canDisplayTrack = canDisplayTrack && i.getParent().getStructureIdx()<=trackHead.getStructureIdx();
        if (canDisplayTrack) {
            TrackMask tm = (TrackMask)i;
            tm.trimTrack(track);
            canDisplayTrack = !track.isEmpty();
        }
        Map<Pair<StructureObject, StructureObject>, V> map = labile ? labileParentTrackHeadTrackRoiMap : parentTrackHeadTrackRoiMap;
        if (canDisplayTrack) { 
            if (i.getKey().displayedStructureIdx!=trackHead.getStructureIdx()) {
                i = getImageTrackObjectInterface(((TrackMask)i).parents, trackHead.getStructureIdx());
            }
            if (((TrackMask)i).getParent()==null) logger.error("Track mask parent null!!!");
            else if (((TrackMask)i).getParent().getTrackHead()==null) logger.error("Track mask parent trackHead null!!!");
            Pair<StructureObject, StructureObject> key = new Pair(((TrackMask)i).getParent().getTrackHead(), trackHead);
            Set<V>  disp = null;
            if (labile) disp = displayedLabileTrackRois.getAndCreateIfNecessary(image);
            V roi = map.get(key);
            if (roi==null) {
                roi = generateTrackRoi(track, color);
                map.put(key, roi);
            } else setTrackColor(roi, color);
            if (disp==null || !disp.contains(roi)) displayTrack(dispImage, roi);
            if (disp!=null) disp.add(roi);
            displayer.updateImageRoiDisplay(image);
        } else logger.warn("image cannot display selected track: ImageObjectInterface null? {}, is Track? {}", i==null, i instanceof TrackMask);
    }
    
    public void hideTracks(Image image, ImageObjectInterface i, Collection<StructureObject> trackHeads, boolean labile) {
        I dispImage;
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
        Map<Pair<StructureObject, StructureObject>, V> map = labile ? labileParentTrackHeadTrackRoiMap : parentTrackHeadTrackRoiMap;
        for (StructureObject th : trackHeads) {
            V roi=map.get(new Pair(parentTrackHead, th));
            if (roi!=null) {
                hideTrack(dispImage, roi);
                if (disp!=null) disp.remove(roi);
            }
        }
        //GUI.updateRoiDisplayForSelections(image, i);
        displayer.updateImageRoiDisplay(image);
        
    }
    
    protected abstract void hideAllRois(I image);
    public void hideAllRois(Image image, boolean labile, boolean nonLabile) {
        if (!labile && !nonLabile) return;
        I im = getDisplayer().getImage(image);
        if (im !=null) hideAllRois(im);
        if (!labile) {
            displayLabileObjects(image);
            displayLabileTracks(image);
        } else {
            displayedLabileTrackRois.remove(image);
            displayedLabileObjectRois.remove(image);
        }
        if (!nonLabile) {
            GUI.updateRoiDisplayForSelections(image, null);
        }
    }
    public void displayLabileTracks(Image image) {
        if (image==null) {
            image = getDisplayer().getCurrentImage2();
            if (image==null) return;
        }
        Set<V> tracks = this.displayedLabileTrackRois.get(image);
        if (tracks!=null) {
            I dispImage = displayer.getImage(image);
            if (dispImage==null) return;
            for (V roi: tracks) displayTrack(dispImage, roi);
            displayer.updateImageRoiDisplay(image);
        }
    }
    public void hideLabileTracks(Image image) {
        if (image==null) {
            image = getDisplayer().getCurrentImage2();
            if (image==null) return;
        }
        Set<V> tracks = this.displayedLabileTrackRois.remove(image);
        if (tracks!=null) {
            I dispImage = displayer.getImage(image);
            if (dispImage==null) return;
            for (V roi: tracks) hideTrack(dispImage, roi);
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
            if (!addToCurrentSelectedTracks) hideAllRois(image, true, true); // TODO only tracks?
            displayTrack(image, i, track, color, labile);
        }
    }
    
    public List<StructureObject> getSelectedLabileTrackHeads(Image image) {
        if (image==null) {
            image = getDisplayer().getCurrentImage2();
            if (image==null) return Collections.emptyList();
        }
        Set<V> rois = this.displayedLabileTrackRois.get(image);
        if (rois!=null) {
            List<Pair<StructureObject, StructureObject>> pairs = Utils.getKeys(labileParentTrackHeadTrackRoiMap, rois);
            List<StructureObject> res = unpairValues(pairs);
            Utils.removeDuplicates(res, false);
            return res;
        } else return Collections.emptyList();
    }
    
    public void removeObjects(Collection<StructureObject> objects, boolean removeTrack) {
        for (Image image : this.displayedLabileObjectRois.keySet()) {
            ImageObjectInterface i = this.getImageObjectInterface(image);
            if (i!=null) hideObjects(image, i.pairWithOffset(objects), true);
        }
        for (StructureObject object : objects) {
            Pair k = new Pair(object, null);
            Utils.removeFromMap(objectRoiMap, k);
            Utils.removeFromMap(labileObjectRoiMap, k);
        }
        if (removeTrack) removeTracks(StructureObjectUtils.getTrackHeads(objects));
    }
    
    public void removeTracks(Collection<StructureObject> trackHeads) {
        for (Image image : this.displayedLabileTrackRois.keySet()) hideTracks(image, null, trackHeads, true);
        for (StructureObject trackHead : trackHeads) {
            Pair k = new Pair(null, trackHead);
            Utils.removeFromMap(labileParentTrackHeadTrackRoiMap, k);
            Utils.removeFromMap(parentTrackHeadTrackRoiMap, k);
        }
    }
    
    public void resetObjectsAndTracksRoi() {
        for (Image image : imageObjectInterfaceMap.keySet()) hideAllRois(image, true, true);
        objectRoiMap.clear();
        labileObjectRoiMap.clear();
        labileParentTrackHeadTrackRoiMap.clear();
        parentTrackHeadTrackRoiMap.clear();
    }
    
    public void goToNextTrackError(Image trackImage, List<StructureObject> trackHeads, boolean next) {
        //ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(rois.get(0).getParent().getTrackHead(), rois.get(0).getStructureIdx(), true));
        if (trackImage==null) {
            I selectedImage = displayer.getCurrentImage();
            trackImage = displayer.getImage(selectedImage);
            if (trackImage==null) return;
        }
        ImageObjectInterface i = this.getImageObjectInterface(trackImage);
        if (i==null || !i.isTimeImage()) {
            logger.warn("selected image is not a track image");
            return;
        }
        TrackMask tm = (TrackMask)i;
        if (trackHeads==null || trackHeads.isEmpty()) trackHeads = this.getSelectedLabileTrackHeads(trackImage);
        if (trackHeads==null || trackHeads.isEmpty()) {
            List<StructureObject> allObjects = Pair.unpairKeys(i.getObjects());
            trackHeads = new ArrayList<>(StructureObjectUtils.getTrackHeads(allObjects));
        }
        if (trackHeads==null || trackHeads.isEmpty()) return;
        Collections.sort(trackHeads);
        BoundingBox currentDisplayRange = this.displayer.getDisplayRange(trackImage);
        int minTimePoint = tm.getClosestFrame(currentDisplayRange.xMin(), currentDisplayRange.yMin());
        int maxTimePoint = tm.getClosestFrame(currentDisplayRange.xMax(), currentDisplayRange.yMax());
        if (next) {
            if (maxTimePoint == i.getParents().get(i.getParents().size()-1).getFrame()) return;
            if (maxTimePoint>minTimePoint+2) maxTimePoint-=2;
            else maxTimePoint--;
        } else {
            if (minTimePoint == i.getParents().get(0).getFrame()) return;
            if (maxTimePoint>minTimePoint+2) minTimePoint+=2;
            else minTimePoint++;
        }
        //logger.debug("Current Display range: {}, maxTimePoint: {}, number of selected rois: {}", currentDisplayRange, maxTimePoint, rois.size());
        StructureObject nextError = next ? getNextError(maxTimePoint, trackHeads) : getPreviousError(minTimePoint, trackHeads);
        if (nextError==null) logger.info("No errors detected {} timepoint: {}", next? "after": "before", maxTimePoint);
        else {
            BoundingBox off = tm.getObjectOffset(nextError);
            if (off==null) trackHeads = new ArrayList<> (trackHeads);
            while(off==null) {
                trackHeads.remove(nextError);
                nextError = getNextObject(nextError.getFrame(), trackHeads, next);
                if (nextError==null) return;
                off = tm.getObjectOffset(nextError);
            }
            int midX = (off.xMin()+off.xMax())/2;
            if (midX+currentDisplayRange.sizeX()/2>=trackImage.sizeX()) midX = trackImage.sizeX()-currentDisplayRange.sizeX()/2;
            if (midX-currentDisplayRange.sizeX()/2<0) midX = currentDisplayRange.sizeX()/2;
            
            int midY = (off.yMin()+off.yMax())/2;
            if (midY+currentDisplayRange.sizeY()/2>=trackImage.sizeY()) midY = trackImage.sizeY()-currentDisplayRange.sizeY()/2;
            if (midY-currentDisplayRange.sizeY()/2<0) midY = currentDisplayRange.sizeY()/2;
            
            SimpleBoundingBox nextDisplayRange = new SimpleBoundingBox(midX-currentDisplayRange.sizeX()/2, midX+currentDisplayRange.sizeX()/2, midY-currentDisplayRange.sizeY()/2, midY+currentDisplayRange.sizeY()/2, currentDisplayRange.zMin(), currentDisplayRange.zMax());
            logger.info("Error detected @ timepoint: {}, xMid: {}, update display range: {}", nextError.getFrame(), midX,  nextDisplayRange);
            displayer.setDisplayRange(nextDisplayRange, trackImage);
        }
    }
    /**
     * Center this image on the objects at next (or previous, if {@param next} is false) undiplayed frames
     * @param trackImage
     * @param objects
     * @param next 
     * @return true if display has changed
     */
    public boolean goToNextObject(Image trackImage, List<StructureObject> objects, boolean next) {
        //ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(rois.get(0).getParent().getTrackHead(), rois.get(0).getStructureIdx(), true));
        if (trackImage==null) {
            I selectedImage = displayer.getCurrentImage();
            trackImage = displayer.getImage(selectedImage);
        }
        ImageObjectInterface i = this.getImageObjectInterface(trackImage);
        if (!i.isTimeImage()) {
            logger.warn("selected image is not a track image");
            return false;
        }
        TrackMask tm = (TrackMask)i;
        if (objects==null || objects.isEmpty()) objects = this.getSelectedLabileObjects(trackImage);
        if (objects==null || objects.isEmpty()) objects = Pair.unpairKeys(i.getObjects());
        if (objects==null || objects.isEmpty()) return false;
        BoundingBox currentDisplayRange = this.displayer.getDisplayRange(trackImage);
        int minTimePoint = tm.getClosestFrame(currentDisplayRange.xMin(), currentDisplayRange.yMin());
        int maxTimePoint = tm.getClosestFrame(currentDisplayRange.xMax(), currentDisplayRange.yMax());
        if (next) {
            if (maxTimePoint == i.getParents().get(i.getParents().size()-1).getFrame()) return false;
            if (maxTimePoint>minTimePoint+2) maxTimePoint-=2;
            else maxTimePoint--;
        } else {
            if (minTimePoint == i.getParents().get(0).getFrame()) return false;
            if (maxTimePoint>minTimePoint+2) minTimePoint+=2;
            else minTimePoint++;
        }
        logger.debug("Current Display range: {}, maxTimePoint: {}, minTimePoint: {}, number of objects: {}", currentDisplayRange, maxTimePoint, minTimePoint, objects.size());
        Collections.sort(objects, frameComparator()); // sort by frame
        StructureObject nextObject = getNextObject(next? maxTimePoint: minTimePoint, objects, next);
        if (nextObject==null) {
            logger.info("No object detected {} timepoint: {}", next? "after" : "before", maxTimePoint);
            return false;
        }
        else {
            BoundingBox off = tm.getObjectOffset(nextObject);
            if (off==null) objects = new ArrayList<>(objects);
            while(off==null) {
                objects = new ArrayList<>(objects);
                objects.remove(nextObject);
                nextObject = getNextObject(nextObject.getFrame(), objects, next);
                if (nextObject==null) {
                    logger.info("No object detected {} timepoint: {}", next? "after" : "before", maxTimePoint);
                    return false;
                }
                off = tm.getObjectOffset(nextObject);
            }
            int midX = (off.xMin()+off.xMax())/2;
            if (midX+currentDisplayRange.sizeX()/2>=trackImage.sizeX()) midX = trackImage.sizeX()-currentDisplayRange.sizeX()/2-1;
            if (midX-currentDisplayRange.sizeX()/2<0) midX = currentDisplayRange.sizeX()/2;
            
            int midY = (off.yMin()+off.yMax())/2;
            if (midY+currentDisplayRange.sizeY()/2>=trackImage.sizeY()) midY = trackImage.sizeY()-currentDisplayRange.sizeY()/2-1;
            if (midY-currentDisplayRange.sizeY()/2<0) midY = currentDisplayRange.sizeY()/2;
            
            MutableBoundingBox nextDisplayRange = new MutableBoundingBox(midX-currentDisplayRange.sizeX()/2, midX+currentDisplayRange.sizeX()/2, midY-currentDisplayRange.sizeY()/2, midY+currentDisplayRange.sizeY()/2, currentDisplayRange.zMin(), currentDisplayRange.zMax());
            if (!nextDisplayRange.equals(currentDisplayRange)) {
                logger.info("Object detected @ timepoint: {}, xMid: {}, update display range: {} (current was: {}", nextObject.getFrame(), midX,  nextDisplayRange, currentDisplayRange);
                displayer.setDisplayRange(nextDisplayRange, trackImage);
                return true;
            } return false;
        }
    }
    
    private static StructureObject getNextObject(int timePointLimit, List<StructureObject> objects, boolean next) {
        if (objects.isEmpty()) return null;
        int idx = Collections.binarySearch(objects, new StructureObject(timePointLimit, null, null), frameComparator());
        if (idx>=0) return objects.get(idx);
        int insertionPoint = -idx-1;
        if (next) {
            if (insertionPoint<objects.size()) return objects.get(insertionPoint);
        } else {
            if (insertionPoint>0) return objects.get(insertionPoint-1);
        }
        return null;
    }
    
    private static StructureObject getNextError(int maxTimePoint, List<StructureObject> tracksHeads) {
        if (tracksHeads.isEmpty()) return null;
        StructureObject[] trackArray = tracksHeads.toArray(new StructureObject[tracksHeads.size()]);
        boolean change = true;
        boolean remainTrack = true;
        int currentTimePoint = maxTimePoint;
        while(remainTrack) {
            change = false;
            remainTrack= false;
            for (int trackIdx = 0; trackIdx<trackArray.length; ++trackIdx) {
                if (trackArray[trackIdx]!=null) {
                    remainTrack=true;
                    if (trackArray[trackIdx].getFrame()<currentTimePoint) {
                        trackArray[trackIdx]=trackArray[trackIdx].getNext(); 
                        change=true;
                    }
                    if (trackArray[trackIdx]!=null && trackArray[trackIdx].getFrame()==currentTimePoint && trackArray[trackIdx].hasTrackLinkError(true, true)) return trackArray[trackIdx];
                }
            }
            if (!change) ++currentTimePoint;
        }
        
        return null;
    }
    private static StructureObject getPreviousError(int minTimePoint, List<StructureObject> trackHeads) {
        if (trackHeads.isEmpty()) return null;
        StructureObject[] trackArray = trackHeads.toArray(new StructureObject[trackHeads.size()]);
        // get all rois to maximal value < errorTimePoint
        for (int trackIdx = 0; trackIdx<trackArray.length; ++trackIdx) {
            if (trackArray[trackIdx].getFrame()>=minTimePoint) trackArray[trackIdx] = null;
            else while (trackArray[trackIdx].getNext()!=null && trackArray[trackIdx].getFrame()<minTimePoint-1) trackArray[trackIdx] = trackArray[trackIdx].getNext();
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
                    if (trackArray[trackIdx].getFrame()>currentTimePoint) {
                        trackArray[trackIdx]=trackArray[trackIdx].getPrevious();
                        change=true;
                    }
                    if (trackArray[trackIdx]!=null && trackArray[trackIdx].getFrame()==currentTimePoint && trackArray[trackIdx].hasTrackLinkError(true, true)) return trackArray[trackIdx];
                }
            }
            if (!change) --currentTimePoint;
        }
        
        return null;
    }
    protected JPopupMenu getMenu(Image image) {
        List<StructureObject> sel =getSelectedLabileObjects(image);
        if (sel.isEmpty()) return null;
        else if (sel.size()==1) return getMenu(sel.get(0));
        else {
            Collections.sort(sel);
            if (sel.size()>50) sel=sel.subList(0, 50);
            return getMenu(sel);
        }
    }
    
    private JPopupMenu getMenu(StructureObject o) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(new JMenuItem(o.toString()));
        menu.add(new JMenuItem("Prev:"+o.getPrevious()));
        menu.add(new JMenuItem("Next:"+o.getNext()));
        menu.add(new JMenuItem("TrackHead:"+o.getTrackHead()));
        menu.add(new JMenuItem("Time: "+toString(o.getMeasurements().getCalibratedTimePoint())));
        menu.add(new JMenuItem("IsTrackHead: "+o.isTrackHead()));
        menu.add(new JMenuItem("Is2D: "+o.getRegion().is2D()));
        //DecimalFormat df = new DecimalFormat("#.####");
        if (o.getAttributes()!=null && !o.getAttributes().isEmpty()) {
            menu.addSeparator();
            for (Entry<String, Object> en : new TreeMap<>(o.getAttributes()).entrySet()) {
                JMenuItem item = new JMenuItem(en.getKey()+": "+toString(en.getValue()));
                menu.add(item);
                item.setAction(new AbstractAction(item.getActionCommand()) {
                    @Override
                        public void actionPerformed(ActionEvent ae) {
                            java.awt.datatransfer.Transferable stringSelection = new StringSelection(en.getValue().toString());
                            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            clipboard.setContents(stringSelection, null);
                        }
                });
            }
        }

        menu.addSeparator();
        for (Entry<String, Object> en : new TreeMap<>(o.getMeasurements().getValues()).entrySet()) {
            JMenuItem item = new JMenuItem(en.getKey()+": "+toString(en.getValue()));
            menu.add(item);
            item.setAction(new AbstractAction(item.getActionCommand()) {
                @Override
                    public void actionPerformed(ActionEvent ae) {
                        java.awt.datatransfer.Transferable stringSelection = new StringSelection(en.getValue().toString());
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(stringSelection, null);
                    }
            });
        }
        
        return menu;
    }
    private JPopupMenu getMenu(List<StructureObject> list) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(new JMenuItem(Utils.toStringList(list)));
        menu.add(new JMenuItem("Prev:"+Utils.toStringList(list, o->o.getPrevious()==null?"NA":o.getPrevious().toString())));
        menu.add(new JMenuItem("Next:"+Utils.toStringList(list, o->o.getNext()==null?"NA":o.getNext().toString())));
        List<String> thList = Utils.transform(list, o->o.getTrackHead()==null?"NA":o.getTrackHead().toString());
        replaceRepetedValues(thList);
        menu.add(new JMenuItem("TrackHead:"+Utils.toStringList(thList)));
        //DecimalFormat df = new DecimalFormat("#.####E0");
        // getAllAttributeKeys
        Collection<String> attributeKeys = new HashSet();
        Collection<String> mesKeys = new HashSet();
        for (StructureObject o : list) {
            if (o.getAttributes()!=null && !o.getAttributes().isEmpty()) attributeKeys.addAll(o.getAttributes().keySet());
            mesKeys.addAll(o.getMeasurements().getValues().keySet());
        }
        attributeKeys=new ArrayList(attributeKeys);
        Collections.sort((List)attributeKeys);
        mesKeys=new ArrayList(mesKeys);
        Collections.sort((List)mesKeys);
        
        if (!attributeKeys.isEmpty()) {
            menu.addSeparator();
            for (String s : attributeKeys) {
                List<Object> values = new ArrayList(list.size());
                for (StructureObject o : list) values.add(o.getAttribute(s));
                replaceRepetedValues(values);
                menu.add(new JMenuItem(s+": "+Utils.toStringList(values, v -> toString(v))));
            }
        }
        if (!mesKeys.isEmpty()) {
            menu.addSeparator();
            for (String s : mesKeys) {
                List<Object> values = new ArrayList(list.size());
                for (StructureObject o : list) values.add(o.getMeasurements().getValue(s));
                replaceRepetedValues(values);
                menu.add(new JMenuItem(s+": "+Utils.toStringList(values, v -> toString(v) )));
            }
        }
        return menu;
    }
    private static void replaceRepetedValues(List list) {
        if (list.size()<=1) return;
        Object lastValue=list.get(0);
        for (int i = 1; i<list.size(); ++i) {
            if (lastValue==null) lastValue = list.get(i);
            else if (lastValue.equals(list.get(i))) {
                list.remove(i);
                list.add(i, "%");
            } else lastValue = list.get(i);
        }
    }
    private static String toString(Object o) {
        return asString(o, MeasurementExtractor.numberFormater);
        //return o instanceof Number ? Utils.format((Number) o, 3) : o.toString();
    }   
}