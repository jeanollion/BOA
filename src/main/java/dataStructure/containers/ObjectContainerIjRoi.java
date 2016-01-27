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
import ij.process.ImageProcessor;
import image.BoundingBox;
import image.IJImageWrapper;
import static image.Image.logger;
import image.ImageByte;
import image.ImageOperations;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
@Embedded(polymorph = true)
public class ObjectContainerIjRoi extends ObjectContainer {
    //@Transient HashMap<Integer, Roi> roiZTemp;
    ArrayList<byte[]> roiZ;
    
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
        Map<Integer, Roi> roiZTemp = IJImageWindowManager.getRoi(object.getMask(), object.getBounds(), object.is3D());
        roiZ = new ArrayList<byte[]>(roiZTemp.size());
        roiZTemp = new TreeMap<Integer, Roi>(roiZTemp);
        for (Entry<Integer, Roi> e : roiZTemp.entrySet()) roiZ.add(RoiEncoder.saveAsByteArray(e.getValue()));
    }
    
    private ImageByte getMask() {
        ImageStack stack = new ImageStack(bounds.getSizeX(), bounds.getSizeY(), bounds.getSizeZ());
        int z= 1;
        for (byte[] b : roiZ) {
            Roi r = RoiDecoder.openFromByteArray(b);
            r.setPosition(z);
            Rectangle bds = r.getBounds();
            r.setLocation(bds.x-bounds.getxMin(), bds.y-bounds.getyMin());
            ImageProcessor mask = r.getMask();
            if (mask.getWidth()!=stack.getWidth() || mask.getHeight()!=stack.getHeight()) { // need to paste image
                ImageByte i = (ImageByte)IJImageWrapper.wrap(new ImagePlus("", mask));
                ImageByte iOut = new ImageByte("", bounds.getSizeX(), bounds.getSizeY(), 1);
                ImageOperations.pasteImage(i, iOut, new BoundingBox(bds.x-bounds.getxMin(), bds.y-bounds.getyMin(), 0));
                mask = IJImageWrapper.getImagePlus(iOut).getProcessor();
            }
            stack.setProcessor(mask, z);
            //logger.debug("Roi: Z: {}, bounds: {}", z-1, r.getBounds());
            ++z;
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
