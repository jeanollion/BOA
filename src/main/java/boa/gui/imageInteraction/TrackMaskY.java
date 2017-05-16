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
public class TrackMaskY extends TrackMask {
    int maxParentX, maxParentZ;
    
    public TrackMaskY(List<StructureObject> parentTrack, int childStructureIdx) {
        super(parentTrack, childStructureIdx);
        int maxX=0, maxZ=0;
        for (int i = 0; i<parentTrack.size(); ++i) { // compute global Y and Z max to center parent masks
            if (maxX<parentTrack.get(i).getObject().getBounds().getSizeX()) maxX=parentTrack.get(i).getObject().getBounds().getSizeX();
            if (maxZ<parentTrack.get(i).getObject().getBounds().getSizeZ()) maxZ=parentTrack.get(i).getObject().getBounds().getSizeZ();
        }
        maxParentX=maxX;
        maxParentZ=maxZ;
        logger.trace("track mask image object: max parent X-size: {} z-size: {}", maxParentX, maxParentZ);
        int currentOffsetY=0;
        for (int i = 0; i<parentTrack.size(); ++i) {
            trackOffset[i] = parentTrack.get(i).getBounds().duplicate().translateToOrigin(); 
            //trackOffset[i].translate(currentOffsetX, (int)(maxParentY/2.0-trackOffset[i].getSizeY()/2.0), (int)(maxParentZ/2.0-trackOffset[i].getSizeZ()/2.0)); // Y & Z middle of parent track
            trackOffset[i].translate(0, currentOffsetY, 0); // X & Z up of parent track
            trackObjects[i] = new StructureObjectMask(parentTrack.get(i), childStructureIdx, trackOffset[i]);
            currentOffsetY+=intervalX+trackOffset[i].getSizeY();
            logger.trace("current index: {}, current bounds: {} current offsetX: {}", i, trackOffset[i], currentOffsetY);
        }
        for (StructureObjectMask m : trackObjects) m.getObjects();
    }
    
    
    @Override
    public Pair<StructureObject, BoundingBox> getClickedObject(int x, int y, int z) {
        if (is2D) z=0; //do not take in account z in 2D case.
        // recherche du parent: 
        int i = Arrays.binarySearch(trackOffset, new BoundingBox(0, 0, y, y, 0, 0), new bbComparatorY());
        if (i<0) i=-i-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        //logger.debug("getClicked object: index: {}, parent: {}, #children: {}", i, i>=0?trackObjects[i]:"", i>=0? trackObjects[i].getObjects().size():"");
        if (i>=0 && trackOffset[i].contains(x, y, z)) return trackObjects[i].getClickedObject(x, y, z);
        else return null;
    }
    
    @Override
    public void addClickedObjects(BoundingBox selection, List<Pair<StructureObject, BoundingBox>> list) {
        if (is2D && selection.getSizeZ()>0) selection=new BoundingBox(selection.getxMin(), selection.getxMax(), selection.getyMin(), selection.getyMax(), 0, 0);
        int iMin = Arrays.binarySearch(trackOffset, new BoundingBox(selection.getxMin(), selection.getxMin(), 0, 0, 0, 0), new bbComparatorY());
        if (iMin<0) iMin=-iMin-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        int iMax = Arrays.binarySearch(trackOffset, new BoundingBox(selection.getxMax(), selection.getxMax(), 0, 0, 0, 0), new bbComparatorY());
        if (iMax<0) iMax=-iMax-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        //logger.debug("looking for objects from time: {} to time: {}", iMin, iMax);
        for (int i = iMin; i<=iMax; ++i) trackObjects[i].addClickedObjects(selection, list);
    }
    
    @Override
    public int getClosestFrame(int x, int y) {
        int i = Arrays.binarySearch(trackOffset, new BoundingBox(0, 0, y, y, 0, 0), new bbComparatorY());
        if (i<0) i=-i-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        return trackObjects[i].parent.getFrame();
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
        final ImageInteger displayImage = ImageInteger.createEmptyLabelImage("Track: Parent:"+parents+" Segmented Image of: "+structureName, maxLabel, new BlankMask("", this.maxParentX, trackOffset[trackOffset.length-1].getyMax()+1, this.maxParentZ).setCalibration(parents.get(0).getMaskProperties().getScaleXY(), parents.get(0).getMaskProperties().getScaleZ()));
        draw(displayImage);
        return displayImage;
    }
        
    @Override public Image generateRawImage(final int structureIdx) {
        Image image0 = trackObjects[0].generateRawImage(structureIdx);
        String structureName;
        if (GUI.hasInstance() && GUI.getDBConnection()!=null && GUI.getDBConnection().getExperiment()!=null) structureName = GUI.getDBConnection().getExperiment().getStructure(structureIdx).getName(); 
        else structureName= structureIdx+"";
        final Image displayImage =  Image.createEmptyImage("Track: Parent:"+parents.get(0)+" Raw Image of"+structureName, image0, new BlankMask("", this.maxParentX, trackOffset[trackOffset.length-1].getyMax()+1, Math.max(image0.getSizeZ(), this.maxParentZ)).setCalibration(parents.get(0).getMaskProperties().getScaleXY(), parents.get(0).getMaskProperties().getScaleZ()));
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
    
    class bbComparatorY implements Comparator<BoundingBox>{
        @Override
        public int compare(BoundingBox arg0, BoundingBox arg1) {
            return Integer.compare(arg0.getyMin(), arg1.getyMin());
        }
        
    }
}
