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
package dataStructure.containers;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.IJImageWindowManager;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.RoiDecoder;
import ij.io.RoiEncoder;
import ij.plugin.filter.Filler;
import image.IJImageWrapper;
import static image.Image.logger;
import image.ImageByte;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 *
 * @author jollion
 */
@Embedded(polymorph = true)
public class ObjectContainerIjRoi extends ObjectContainer {
    //@Transient HashMap<Integer, Roi> roiZTemp;
    HashMap<String, byte[]> roiZ;
    
    public ObjectContainerIjRoi(StructureObject structureObject) {
        super(structureObject);
        createRoi(structureObject.getObject());
    }

    @Override
    public void updateObject() {
        if (structureObject.getObject().getMask() != null) {
            createRoi(structureObject.getObject());
            bounds = structureObject.getObject().getBounds();
        } else roiZ = null;
    }

    private void createRoi(Object3D object) {
        HashMap<Integer, Roi> roiZTemp = IJImageWindowManager.getRoi(object.getMask(), object.getBounds(), object.is3D());
        roiZ = new HashMap<String, byte[]>(roiZTemp.size());
        for (Entry<Integer, Roi> e : roiZTemp.entrySet()) roiZ.put(e.getKey().toString(), RoiEncoder.saveAsByteArray(e.getValue()));
    }
    
    private ImageByte getMask() {
        ImageStack stack = new ImageStack(bounds.getSizeX(), bounds.getSizeY(), bounds.getSizeZ());
        for (Entry<String, byte[]> e : roiZ.entrySet()) {
            int z = Integer.parseInt(e.getKey())-bounds.getzMin()+1;
            Roi r = RoiDecoder.openFromByteArray(e.getValue());
            r.setPosition(z);
            r.setLocation(0, 0);
            stack.setProcessor(r.getMask(), z);
            //logger.debug("Roi: Z: {}, bounds: {}", z-1, r.getBounds());
        }
        ImageByte res = (ImageByte) IJImageWrapper.wrap(new ImagePlus("MASK", stack));
        res.setCalibration(bounds.getImageProperties(structureObject.getScaleXY(), structureObject.getScaleZ())).addOffset(bounds);
        return res;
    }

    public Object3D getObject() {
        return new Object3D(getMask(), structureObject.getIdx() + 1);
    }

    @Override
    public void deleteObject() {
        bounds = null;
        roiZ = null;
    }

    @Override
    public void relabelObject(int newIdx) {
    }
}
