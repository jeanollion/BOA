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
package boa.data_structure.region_container;

import boa.gui.image_interaction.IJImageDisplayer;
import boa.gui.image_interaction.IJImageWindowManager;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.gui.image_interaction.IJImageWindowManager.Roi3D;
import boa.ui.GUI;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.image.BlankMask;
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
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.Offset;
import boa.image.SimpleImageProperties;
import boa.image.SimpleOffset;
import boa.image.TypeConverter;
import boa.image.processing.ImageOperations;
import ij.plugin.filter.ThresholdToSelection;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.IntStream;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 *
 * @author jollion
 */

public class RegionContainerIjRoi extends RegionContainer {
    List<byte[]> roiZ; // persists
    Roi3D roi; // convention: location of ROI = location of object & postition starts from 1 // not persistant 
    public RegionContainerIjRoi(StructureObject structureObject) {
        super(structureObject);
        createRoi(structureObject.getRegion());
    }

    @Override
    public void update() {
        super.update();
        if (structureObject.getRegion().getMask() != null) createRoi(structureObject.getRegion());
        else {
            roiZ = null;
            roi = null;
        }
    }

    private void createRoi(Region object) {
        roi = createRoi(object.getMask(), object.getBounds(), object.is2D());
    }
    private void encodeRoi() {
        roiZ = new ArrayList<>(roi.size());
        roi.entrySet().stream().sorted((e1, e2)->Integer.compare(e1.getKey(), e2.getKey()))
                .forEach(e->roiZ.add(RoiEncoder.saveAsByteArray(e.getValue())));
    }
    /**
     * 
     * @return the ROI if existing null if not
     */
    public Roi3D getRoi() {
        return roi;
    }
    private void decodeRoi() {
        roi = new Roi3D(roiZ.size());
        int z=0;
        for (byte[] b : roiZ) {
            Roi r = RoiDecoder.openFromByteArray(b);
            r.setPosition(z+1+bounds.zMin());
            //r.setLocation(bounds.xMin(), bounds.yMin()); // encoded in ROI ?
            roi.put(z+bounds.zMin(), r);
            ++z;
        }
    }
    private synchronized ImageByte getMask() {
        ImageStack stack = new ImageStack(bounds.sizeX(), bounds.sizeY(), bounds.sizeZ());
        if (roi==null) decodeRoi();
        IntStream.rangeClosed(bounds.zMin(), bounds.zMax()).forEachOrdered(z -> {
            Roi r = roi.get(z);
            Rectangle bds = r.getBounds();
            ImageProcessor mask = r.getMask();
            if (mask.getWidth()!=stack.getWidth() || mask.getHeight()!=stack.getHeight()) { // need to paste image
                ImageByte i = (ImageByte)IJImageWrapper.wrap(new ImagePlus("", mask)).translate(new SimpleOffset(bds.x, bds.y, 0));
                logger.debug("object: {} paste image during ij roi decoding: roi: {} object bounds: {}", structureObject, i.getBoundingBox(), bounds);
                mask = IJImageWrapper.getImagePlus(i.cropWithOffset(bounds)).getProcessor();
            }
            stack.setProcessor(mask, z-bounds.zMin()+1);
        });
        ImageByte res = (ImageByte) IJImageWrapper.wrap(new ImagePlus("MASK", stack));
        //logger.debug("creating object for: {}, scale: {}", structureObject, structureObject.getScaleXY());
        res.setCalibration(new SimpleImageProperties(bounds, structureObject.getScaleXY(), structureObject.getScaleZ())).translate(bounds);
        return res;
    }

    @Override
    public Region getRegion() {
        return new Region(getMask(), structureObject.getIdx() + 1, is2D);
    }

    @Override
    public void deleteRegion() {
        bounds = null;
        roiZ = null;
    }

    @Override
    public void relabelRegion(int newIdx) {
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
        if (roiZ ==null) encodeRoi();
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
    protected RegionContainerIjRoi() {}
    
    /**
     *
     * @param mask
     * @param offset
     * @param is3D
     * @return mapping of Roi to Z-slice (taking into account the provided offset)
     */
    public static Roi3D createRoi(ImageMask mask, Offset offset, boolean is3D) {
        if (offset == null) {
            GUI.logger.error("ROI creation : offset null for mask: {}", mask.getName());
            return null;
        }
        IJImageWindowManager.Roi3D res = new IJImageWindowManager.Roi3D(mask.sizeZ());
        if (mask instanceof BlankMask) {
            for (int z = 0; z < mask.sizeZ(); ++z) {
                Roi rect = new Roi(0, 0, mask.sizeX(), mask.sizeY());
                rect.setLocation(offset.xMin(), offset.yMin());
                rect.setPosition(z +1+ offset.zMin());
                res.put(z + mask.zMin(), rect);
            }
            return res;
        }
        ThresholdToSelection tts = new ThresholdToSelection();
        ImageInteger maskIm = TypeConverter.toImageInteger(mask, null); // copy only if necessary
        ImagePlus maskPlus = IJImageWrapper.getImagePlus(maskIm);
        tts.setup("", maskPlus);
        int maxLevel = ImageInteger.getMaxValue(maskIm, true); // TODO necessary ??
        for (int z = 0; z < mask.sizeZ(); ++z) {
            ImageProcessor ip = maskPlus.getStack().getProcessor(z + 1);
            ip.setThreshold(1, maxLevel, ImageProcessor.NO_LUT_UPDATE);
            tts.run(ip);
            Roi roi = maskPlus.getRoi();
            if (roi != null) {
                //roi.setPosition(z+1+mask.getOffsetZ());
                Rectangle bds = roi.getBounds();
                if (bds == null) {
                    continue;
                }
                roi.setLocation(bds.x + offset.xMin(), bds.y + offset.yMin());
                roi.setPosition(z + 1 + offset.zMin()); 
                res.put(z + offset.zMin(), roi);
            }
        }
        return res;
    }
}
