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
package boa.data_structure.region_container;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.IJImageWindowManager;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.RoiDecoder;
import ij.io.RoiEncoder;
import ij.plugin.filter.Filler;
import ij.process.ImageProcessor;
import boa.image.MutableBoundingBox;
import boa.image.IJImageWrapper;
import boa.image.Image;
import static boa.image.Image.logger;
import boa.image.ImageByte;
import boa.image.processing.ImageOperations;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 *
 * @author jollion
 */

public class ObjectContainerIjRoi extends ObjectContainer {
    ArrayList<byte[]> roiZ;
    
    public ObjectContainerIjRoi(StructureObject structureObject) {
        super(structureObject);
        createRoi(structureObject.getObject());
    }

    @Override
    public void updateObject() {
        super.updateObject();
        if (structureObject.getObject().getMask() != null) createRoi(structureObject.getObject());
        else roiZ = null;
    }

    private void createRoi(Region object) {
        Map<Integer, Roi> roiZTemp = IJImageWindowManager.createRoi(object.getMask(), object.getBounds(), object.is2D());
        roiZ = new ArrayList<>(roiZTemp.size());
        roiZTemp = new TreeMap<>(roiZTemp);
        for (Entry<Integer, Roi> e : roiZTemp.entrySet()) roiZ.add(RoiEncoder.saveAsByteArray(e.getValue()));
    }
    
    private ImageByte getMask() {
        ImageStack stack = new ImageStack(bounds.sizeX(), bounds.sizeY(), bounds.sizeZ());
        int z= 1;
        for (byte[] b : roiZ) {
            Roi r = RoiDecoder.openFromByteArray(b);
            r.setPosition(z);
            Rectangle bds = r.getBounds();
            r.setLocation(bds.x-bounds.xMin(), bds.y-bounds.yMin());
            ImageProcessor mask = r.getMask();
            if (mask.getWidth()!=stack.getWidth() || mask.getHeight()!=stack.getHeight()) { // need to paste image
                ImageByte i = (ImageByte)IJImageWrapper.wrap(new ImagePlus("", mask));
                ImageByte iOut = new ImageByte("", bounds.sizeX(), bounds.sizeY(), 1);
                Image.pasteImage(i, iOut, new MutableBoundingBox(bds.x-bounds.xMin(), bds.y-bounds.yMin(), 0));
                mask = IJImageWrapper.getImagePlus(iOut).getProcessor();
            }
            stack.setProcessor(mask, z);
            //logger.debug("Roi: Z: {}, bounds: {}", z-1, r.getBounds());
            ++z;
        }
        ImageByte res = (ImageByte) IJImageWrapper.wrap(new ImagePlus("MASK", stack));
        //logger.debug("creating object for: {}, scale: {}", structureObject, structureObject.getScaleXY());
        res.setCalibration(bounds.getImageProperties(structureObject.getScaleXY(), structureObject.getScaleZ())).translate(bounds);
        return res;
    }

    public Region getObject() {
        return new Region(getMask(), structureObject.getIdx() + 1, is2D);
    }

    @Override
    public void deleteObject() {
        bounds = null;
        roiZ = null;
    }

    @Override
    public void relabelObject(int newIdx) {
    }
    @Override
    public void initFromJSON(Map json) {
        super.initFromJSON(json);
        if (json.containsKey("roi")) {
            roiZ = new ArrayList<>(1);
            roiZ.add(Base64.getDecoder().decode((String)json.get("roi")));
        } else if (json.containsKey("roiZ")) {
            JSONArray rois = (JSONArray)json.get(("roiZ"));
            roiZ = new ArrayList<>(rois.size());
            for (int i = 0; i<rois.size(); ++i) roiZ.add(Base64.getDecoder().decode((String)rois.get(i)));
        }
    }
    @Override
    public JSONObject toJSON() {
        JSONObject res = super.toJSON();
        if (roiZ.size()>1) {
            JSONArray rois = new JSONArray();
            for (byte[] bytes: this.roiZ) {
                rois.add(Base64.getEncoder().encodeToString(bytes));
            }
            res.put("roiZ", rois);
        } else if (roiZ.size()==1) {
            res.put("roi", Base64.getEncoder().encodeToString(roiZ.get(0)));
        }
        return res;
    }
    protected ObjectContainerIjRoi() {}
}
