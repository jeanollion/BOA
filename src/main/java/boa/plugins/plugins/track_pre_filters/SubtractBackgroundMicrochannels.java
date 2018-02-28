/*
 * Copyright (C) 2018 jollion
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
 * You should have received a copyDataFrom of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package boa.plugins.plugins.track_pre_filters;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.SimpleBoundingBox;
import boa.image.SimpleOffset;
import boa.image.TypeConverter;
import boa.image.processing.ImageOperations;
import boa.image.processing.ImageTransformation;
import boa.plugins.TrackPreFilter;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;
import boa.utils.ThreadRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
public class SubtractBackgroundMicrochannels implements TrackPreFilter{
    BooleanParameter isDarkBck = new BooleanParameter("Image Background", "Dark", "Light", false);
    BooleanParameter smooth = new BooleanParameter("Perform Smoothing", false);
    NumberParameter radius = new BoundedNumberParameter("Radius", 0, 10000, 1, null).setToolTipText("Higher value -> less homogeneity/faster. Radius of the paraboloïd will be this value * mean Y size of microchannels");
    Parameter[] parameters = new Parameter[]{radius, isDarkBck, smooth};
    @Override
    public void filter(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages, boolean canModifyImages) {
        //smooth.setSelected(true);
        // construct one single image 
        TrackMaskYWithMirroring tm = new TrackMaskYWithMirroring(new ArrayList<>(preFilteredImages.keySet()), true, true);
        ImageFloat allImagesY = (ImageFloat)tm.generateEmptyImage("sub mc", new ImageFloat("", 0, 0, 0));
        int idx = 0;
        for (StructureObject o : tm.parents) {
            Image im = preFilteredImages.get(o);
            if (!(im instanceof ImageFloat) || !canModifyImages) {
                im = TypeConverter.toFloat(im, null);
                preFilteredImages.replace(o, im);
            }
            fillBorders(o.getObject(), im);
            tm.pasteMirror(im, allImagesY, idx++);
        }
        //ImageWindowManagerFactory.showImage(allImagesY);
        int sizeY = allImagesY.sizeY();
        double mirrorProportion = radius.getValue().doubleValue()<preFilteredImages.size()*0.75 ? 0.5 : 1;
        int offsetY = (int)(allImagesY.sizeY()*mirrorProportion);
        ImageFloat[] allImagesYArray = new ImageFloat[]{allImagesY};
        allImagesY = ThreadRunner.executeUntilFreeMemory(()-> {return mirrorY(allImagesYArray[0], offsetY);}, 200); // mirror image on both Y ends
        allImagesYArray[0] = allImagesY;
        // apply filter
        double radius = (sizeY/(double)preFilteredImages.size())*(this.radius.getValue().doubleValue());
        //logger.debug("necessary memory: {}MB", allImagesY.getSizeXY()*32/8000000);
        ThreadRunner.executeUntilFreeMemory(()-> {IJSubtractBackground.filter(allImagesYArray[0], radius, true, !isDarkBck.getSelected(), smooth.getSelected(), false, false);});
        allImagesY = allImagesY.crop(allImagesY.getBoundingBox().setyMin(offsetY).setyMax(offsetY+sizeY-1)); // crop
        // recover data
        idx = 0;
        for (StructureObject o : tm.parents) Image.pasteImage(allImagesY, preFilteredImages.get(o), null, tm.getObjectOffset(idx++, 1));
        logger.debug("subtrack backgroun microchannel done");
    }
    
    
    
    private static ImageFloat mirrorY(ImageFloat input, int size) { 
        ImageFloat res = new ImageFloat("", input.sizeX(), input.sizeY()+2*size, input.sizeZ());
        Image.pasteImage(input, res, new SimpleOffset(0, size, 0));
        Image imageFlip = ImageTransformation.flip(input, ImageTransformation.Axis.Y);
        Image.pasteImage(imageFlip, res, null, input.getBoundingBox().resetOffset().setyMin(input.sizeY()-size));
        Image.pasteImage(imageFlip, res, new SimpleOffset(0, size+input.sizeY(), 0), input.getBoundingBox().resetOffset().setyMax(size-1));
        return res;
    }
    private static void fillBorders(Region o, Image input) {
        if (!(o.getMask() instanceof BlankMask)) {
            ImageMask mask = o.getMask();
            BoundingBox.loop(mask, (x, y, z)-> {
                if (mask.insideMaskWithOffset(x, y, z)) return;
                Voxel closest = o.getContour().stream().min((v1, v2)->Double.compare(v1.getDistanceSquare(x, y, z), v2.getDistanceSquare(x, y, z))).get();
                input.setPixelWithOffset(x, y, z, input.getPixelWithOffset(closest.x, closest.y, closest.z));
            });
            
        }
    }
    

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    private static class TrackMaskYWithMirroring {
        int maxParentSizeX, maxParentSizeZ;
        SimpleBoundingBox[] trackOffset;
        List<StructureObject> parents;
        int mirror=1;
        private TrackMaskYWithMirroring(List<StructureObject> parentTrack, boolean middleXZ, boolean mirror) {
            trackOffset = new SimpleBoundingBox[parentTrack.size()];
            if (mirror) this.mirror=3;
            this.parents=parentTrack;
            int maxX=0, maxZ=0;
            for (int i = 0; i<parentTrack.size(); ++i) { // compute global Y and Z max to center parent masks
                if (maxX<parentTrack.get(i).getObject().getBounds().sizeX()) maxX=parentTrack.get(i).getObject().getBounds().sizeX();
                if (maxZ<parentTrack.get(i).getObject().getBounds().sizeZ()) maxZ=parentTrack.get(i).getObject().getBounds().sizeZ();
            }
            maxParentSizeX=maxX;
            maxParentSizeZ=maxZ;
            logger.trace("track mask image object: max parent X-size: {} z-size: {}", maxParentSizeX, maxParentSizeZ);
            int currentOffsetY=0;
            for (int i = 0; i<parentTrack.size(); ++i) {
                trackOffset[i] = new SimpleBoundingBox(parentTrack.get(i).getBounds()).resetOffset(); 
                if (middleXZ) trackOffset[i].translate((int)((maxParentSizeX-1)/2.0-(trackOffset[i].sizeX()-1)/2.0), currentOffsetY , (int)((maxParentSizeZ-1)/2.0-(trackOffset[i].sizeZ()-1)/2.0)); // Y & Z middle of parent track
                else trackOffset[i].translate(0, currentOffsetY, 0); // X & Z up of parent track
                currentOffsetY+=trackOffset[i].sizeY()*this.mirror;
                logger.trace("current index: {}, current bounds: {} current offsetX: {}", i, trackOffset[i], currentOffsetY);
            }
        }
        public Image generateEmptyImage(String name, Image type) {
            return Image.createEmptyImage(name, type, new BlankMask( this.maxParentSizeX, trackOffset[trackOffset.length-1].yMin()+trackOffset[trackOffset.length-1].sizeY()*mirror, Math.max(type.sizeZ(), this.maxParentSizeZ)).setCalibration(parents.get(0).getMaskProperties().getScaleXY(), parents.get(0).getMaskProperties().getScaleZ()));
        }   
        public BoundingBox getObjectOffset(int idx, int position) {
            if (mirror==1) return trackOffset[idx];
            switch (position) {
                case 0:
                    return trackOffset[idx];
                case 1:
                    return trackOffset[idx].duplicate().translate(0, trackOffset[idx].sizeY(), 0);
                    
                case 2:
                    return trackOffset[idx].duplicate().translate(0, trackOffset[idx].sizeY()*2, 0);
                    
                default:
                    return null;
            }
        }
        public void pasteMirror(Image source, Image dest, int idx) {
            Image.pasteImage(source, dest, getObjectOffset(idx, 1)); // center
            correctSides(dest, idx, 1);
            if (mirror==1) return;
            Image imageFlip = ImageTransformation.flip(source, ImageTransformation.Axis.Y);
            Image.pasteImage(imageFlip, dest, getObjectOffset(idx, 0));
            correctSides(dest, idx, 0);
            Image.pasteImage(imageFlip, dest, getObjectOffset(idx, 2));
            correctSides(dest, idx, 2);
        }
        private void correctSides(Image dest, int idx, int position) { // when some mask don't have the same width, zeros can remain on the sides -> fill them with the nearest value
            BoundingBox bds = getObjectOffset(idx, position);
            if (bds.xMin()>dest.xMin() || bds.xMax()<dest.xMax()) {
                for (int y = bds.yMin(); y<=bds.yMax(); ++y) {
                    for (int x=dest.xMin(); x<bds.xMin(); ++x) dest.setPixel(x, y, bds.zMin(), dest.getPixel(bds.xMin(), y, bds.zMin()));
                    for (int x=bds.xMax()+1; x<=dest.xMax(); ++x) dest.setPixel(x, y, bds.zMin(), dest.getPixel(bds.xMax(), y, bds.zMin()));
                }
            }
        }
    }
}
