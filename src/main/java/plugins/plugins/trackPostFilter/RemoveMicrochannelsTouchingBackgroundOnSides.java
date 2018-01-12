/*
 * Copyright (C) 2017 jollion
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
package plugins.plugins.trackPostFilter;

import boa.gui.ManualCorrection;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.StructureParameter;
import dataStructure.objects.Region;
import dataStructure.objects.RegionPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import dataStructure.objects.Voxel;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageLabeller;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import plugins.TrackPostFilter;

/**
 * When a rotation occurs > 0 filled background can be added, if background on the image is not centered, this can lead to arfifacts. 
 * This transformation is intended to remove microchannel track if they contain 0-filled background.
 * @author jollion
 */
public class RemoveMicrochannelsTouchingBackgroundOnSides implements TrackPostFilter {
    StructureParameter backgroundStructure = new StructureParameter("Background");
    NumberParameter XMargin = new BoundedNumberParameter("X margin", 0, 8, 0, null).setToolTipText("To avoid removing microchannels touching background from the upper or lower side, this will cut the upper and lower part of the microchannel. In pixels");
    public RemoveMicrochannelsTouchingBackgroundOnSides() {}
    public RemoveMicrochannelsTouchingBackgroundOnSides(int backgroundStructureIdx) {
        this.backgroundStructure.setSelectedStructureIdx(backgroundStructureIdx);
    }
    
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) throws Exception {
        if (backgroundStructure.getSelectedStructureIdx()<0) throw new IllegalArgumentException("Background structure not configured");
        if (parentTrack.isEmpty()) return;
        Map<Integer, StructureObject> parentTrackByF = StructureObjectUtils.splitByFrame(parentTrack);
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        if (allTracks.isEmpty()) return;
        List<StructureObject> objectsToRemove = new ArrayList<>();
        // left-most
        StructureObject object = Collections.min(allTracks.keySet(), (o1, o2)->Double.compare(o1.getBounds().getXMean(), o2.getBounds().getXMean()));
        Image image = parentTrackByF.get(object.getFrame()).getRawImage(backgroundStructure.getSelectedStructureIdx());
        RegionPopulation bck = ImageLabeller.labelImage(Arrays.asList(new Voxel[]{new Voxel(0, 0, 0), new Voxel(0, image.getSizeY()-1, 0 )}), image, true);
        //ImageWindowManagerFactory.showImage(bck.getLabelMap().duplicate("left background"));
        if (intersectWithBackground(object, bck)) objectsToRemove.addAll(allTracks.get(object));
        
        // right-most
        if (allTracks.size()>1) {
            object = Collections.max(allTracks.keySet(), (o1, o2)->Double.compare(o1.getBounds().getXMean(), o2.getBounds().getXMean()));
            image = parentTrackByF.get(object.getFrame()).getRawImage(backgroundStructure.getSelectedStructureIdx());
            bck = ImageLabeller.labelImage(Arrays.asList(new Voxel[]{new Voxel(image.getSizeX()-1, 0, 0), new Voxel(image.getSizeX()-1, image.getSizeY()-1, 0 )}), image, true);
            //ImageWindowManagerFactory.showImage(bck.getLabelMap().duplicate("right background"));
            if (intersectWithBackground(object, bck)) objectsToRemove.addAll(allTracks.get(object));
        }
        if (!objectsToRemove.isEmpty()) ManualCorrection.deleteObjects(null, objectsToRemove, false);
    }
    private boolean intersectWithBackground(StructureObject object, RegionPopulation bck) {
        bck.filter(o->o.getSize()>10); // 
        Region cutObject =object.getObject();
        int XMargin = this.XMargin.getValue().intValue();
        if (XMargin>0 && object.getBounds().getSizeY()>2*XMargin) {
            BoundingBox bds = object.getBounds();
            cutObject = new Region(new BlankMask("", bds.getSizeX(), bds.getSizeY()-2*XMargin, bds.getSizeZ(), bds.getxMin(), bds.getyMin()+XMargin, bds.getzMin(), object.getScaleXY(), object.getScaleZ()), cutObject.getLabel(), cutObject.is2D());
        }
        for (Region o : bck.getObjects()) {
            int inter = o.getIntersectionCountMaskMask(cutObject, null, null);
            if (inter>0) {
                logger.debug("remove track: {} (object: {}), intersection with bck object: {}", object, cutObject.getBounds(), inter);
                return true;
            }
        }
        return false;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{backgroundStructure, XMargin};
    }
    
}
