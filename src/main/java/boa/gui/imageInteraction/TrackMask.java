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

import boa.core.DefaultWorker;
import boa.core.DefaultWorker.WorkerTask;
import boa.gui.GUI;
import static boa.gui.GUI.logger;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import ij.plugin.filter.MaximumFinder;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.Offset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import boa.utils.Pair;

/**
 *
 * @author jollion
 */
public abstract class TrackMask extends ImageObjectInterface {
    BoundingBox[] trackOffset;
    StructureObjectMask[] trackObjects;
    static final int updateImageFrequency=10;
    static final int interval=0; 
    static final float displayMinMaxFraction = 0.9f;
    
    public TrackMask(List<StructureObject> parentTrack, int childStructureIdx) {
        super(parentTrack, childStructureIdx);
        trackOffset = new BoundingBox[parentTrack.size()];
        trackObjects = new StructureObjectMask[parentTrack.size()];
        
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
    
    public abstract int getClosestFrame(int x, int y);
    
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
    public abstract Image generateEmptyImage(String name, Image type);
    @Override public <T extends ImageObjectInterface> T setDisplayPreFilteredImages(boolean displayPreFilteredImages) {
        super.setDisplayPreFilteredImages(displayPreFilteredImages);
        for (StructureObjectMask m : trackObjects) m.setDisplayPreFilteredImages(displayPreFilteredImages);
        return (T)this;
    }
    @Override public Image generatemage(final int structureIdx, boolean executeInBackground) {
        // use track image only if parent is first element of track image
        if (trackObjects[0].parent.getOffsetInTrackImage()!=null && trackObjects[0].parent.getOffsetInTrackImage().xMin()==0 && trackObjects[0].parent.getTrackImage(structureIdx)!=null) return trackObjects[0].parent.getTrackImage(structureIdx);
        /*long t00 = System.currentTimeMillis();
        for (int i =0; i<trackObjects.length; ++i) {
            trackObjects[i].generateRawImage(structureIdx);
        }
        long t01 = System.currentTimeMillis();
        logger.debug("total loading time: {}", t01-t00);*/
        Image image0 = trackObjects[0].generatemage(structureIdx, false);
        if (image0==null) return null;
        String structureName;
        if (getParent().getExperiment()!=null) structureName = getParent().getExperiment().getStructure(structureIdx).getName(); 
        else structureName= structureIdx+"";
        final Image displayImage =  generateEmptyImage("Track: Parent:"+parents.get(0)+" Raw Image of"+structureName, image0);
        Image.pasteImage(image0, displayImage, trackOffset[0]);
        //final double[] minAndMax = image0.getMinAndMax(null);
        //long[] totalTime = new long[1];
        // draw image in another thread..
        // update display every paste...
        WorkerTask t= new WorkerTask() {
            int count = 0;
            @Override
            public String run(int i) {
                //long t0 = System.currentTimeMillis();
                Image image = trackObjects[i].generatemage(structureIdx, false);
                Image.pasteImage(image, displayImage, trackOffset[i]);
                if (count>=updateImageFrequency || i==trackObjects.length-1) {
                    ImageWindowManagerFactory.getImageManager().getDisplayer().updateImageDisplay(displayImage); //, minAndMax[0], (float)((1-displayMinMaxFraction) * minAndMax[0] + displayMinMaxFraction*minAndMax[1])
                    //t4 = System.currentTimeMillis();
                    count=0;
                } else count++;
                return null;
            }

        };
        if (executeInBackground) {
            DefaultWorker w = DefaultWorker.execute(t, trackObjects.length, null);
            ImageWindowManagerFactory.getImageManager().runningWorkers.put(displayImage, w);
            w.setEndOfWork(()->{ 
                ImageWindowManagerFactory.getImageManager().runningWorkers.remove(displayImage); 
                ImageWindowManagerFactory.getImageManager().getDisplayer().updateImageDisplay(displayImage);  //, minAndMax[0], (float)((1-displayMinMaxFraction) * minAndMax[0] + displayMinMaxFraction*minAndMax[1])
            });
        } else {
            DefaultWorker.executeInForeground(t, trackObjects.length);
        }
        return displayImage;
    }

    
    @Override public void drawObjects(final ImageInteger image) {
        trackObjects[0].drawObjects(image);
        double[] mm = image.getMinAndMax(null, trackObjects[0].parent.getBounds());
        // draw image in another thread..
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                for (int i = 1; i<trackObjects.length; ++i) {
                    trackObjects[i].drawObjects(image);
                    //double[] mm2 = image.getMinAndMax(null, trackObjects[0].parent.getBounds());
                    //if (mm[0]>mm2[0]) mm[0] = mm2[0];
                    //if (mm[1]<mm2[1]) mm[1] = mm2[1];
                    if (count>=updateImageFrequency) {
                        
                        ImageWindowManagerFactory.getImageManager().getDisplayer().updateImageDisplay(image, mm[0], mm[1]); // do not cmopute min and max. Keep track of min and max?
                        count=0;
                    } else count++;
                }
                ImageWindowManagerFactory.getImageManager().getDisplayer().updateImageDisplay(image);
            }
        });
        t.start();
        if (!guiMode) try {
            t.join();
        } catch (InterruptedException ex) {
            logger.error("draw error", ex);
        }
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
        ArrayList<Pair<StructureObject, BoundingBox>> res = new ArrayList<>();
        for (StructureObjectMask m : trackObjects) res.addAll(m.getObjects());
        return res;
    }
    
}
