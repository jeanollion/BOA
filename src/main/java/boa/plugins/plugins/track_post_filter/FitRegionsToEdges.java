/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.track_post_filter;

import boa.configuration.parameters.BooleanParameter;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import boa.data_structure.Voxel2D;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.SimpleBoundingBox;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.List;
import boa.plugins.PostFilter;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.watershed.WatershedTransform;
import boa.image.processing.split_merge.SplitAndMergeEdge;
import boa.image.processing.split_merge.SplitAndMergeRegionCriterion;
import boa.plugins.ToolTip;
import boa.plugins.TrackPostFilter;
import boa.plugins.plugins.pre_filters.ImageFeature;
import boa.plugins.plugins.pre_filters.Sigma;
import boa.plugins.plugins.segmenters.EdgeDetector;
import boa.utils.Utils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

/**
 *
 * @author Jean Ollion
 */
public class FitRegionsToEdges implements TrackPostFilter, ToolTip {
    protected PreFilterSequence watershedMap = new PreFilterSequence("Watershed Map").add(new Sigma(3).setMedianRadius(2)).setToolTipText("Watershed map, separation between regions are at area of maximal intensity of this map"); //new ImageFeature().setFeature(ImageFeature.Feature.StructureMax).setScale(1.5).setSmoothScale(1.5)
    BoundedNumberParameter trimUpperPixels = new BoundedNumberParameter("Trim Upper Pixels", 0, 0, 0, null).setToolTipText("Erase Pixels N upper pixels of each regions");
    BoundedNumberParameter fitMargin = new BoundedNumberParameter("Fit margin", 0, 9, 5, null).setToolTipText("Fit will be done in a window around segmented microchannel, with this margin on the left , right & upper sides");
    BoundedNumberParameter morphoRadius = new BoundedNumberParameter("Open / close radius", 1, 4, 0, null).setToolTipText("Radius for morpholical close (remove small invaginations) and open (remove small protuberances) <br /> 0 for no close & no open <br />Must but inferior to half of the width of channels");
    BooleanParameter resetBounds = new BooleanParameter("Reset Bounds", true).setToolTipText("Set the bounds of microchannel to the bounds of fitted object<br />If average mask track-post-filter is set afterwards, bounds should not be reset so that regions can be aligned on their top-left-corner");
    Parameter[] parameters = new Parameter[]{watershedMap, fitMargin, morphoRadius}; //trimUpperPixels, resetBounds
    public static boolean debug = false;
    public static int debugLabel =0;
    public boolean verbose = false;
    
    @Override
    public String getToolTipText() {
        return "Fits an existing region to edges. "
                + "<br /> it first performs a watershed partitioning in a window around each region defined by <em>Fit margin</em>. "
                + "<br / >Partition  whose seeds are not included in the microchannel regions are removed ";
    }
    
    public FitRegionsToEdges setResetBounds(boolean resetBounds) {
        this.resetBounds.setSelected(resetBounds);
        return this;
    }
    public FitRegionsToEdges setTrimUpperPixels(int trimUpperPixels) {
        this.trimUpperPixels.setValue(trimUpperPixels);
        return this;
    }
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) {
        StructureObjectUtils.getAllChildren(parentTrack, structureIdx).parallelStream().forEach(mc -> {
            Image pf = mc.getParent().getPreFilteredImage(structureIdx);
            BoundingBox b = mc.getBounds();
            int margin  = fitMargin.getValue().intValue();
            int marginL = Math.min(margin, b.xMin());
            int marginR = Math.min(margin , pf.sizeX()-1 - b.xMax());
            int marginUp = Math.min(margin, b.yMin());
            BoundingBox cropBB = new SimpleBoundingBox(b.xMin()-marginL, b.xMax()+marginR, b.yMin()-marginUp, b.yMax(), b.zMin(), b.zMax());
            Image crop = pf.crop(cropBB);
            Image edge = watershedMap.filter(crop, new BlankMask(crop));
            fit(crop, edge, new int[]{marginL, marginR, marginUp}, mc.getRegion(), this.morphoRadius.getValue().doubleValue(), this.trimUpperPixels.getValue().intValue(), this.resetBounds.getSelected(), debug);
        });
        
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
    
    private static void fit(Image inputLocal, Image edgeMapLocal, int[] marginLRUp, Region object, double morphoRadius, int trimUpperPixelRadius, boolean resetMask, boolean verbose) {
        double innerMaskSlope = 0;
        boolean seedsInMaskAreForeground = false; // parameter ? 
        BoundingBox b = object.getBounds();
        if (verbose && object.getLabel()==debugLabel) {
            ImageWindowManagerFactory.showImage(inputLocal);
            ImageWindowManagerFactory.showImage(edgeMapLocal);
        }
        List<Region> seeds = new ArrayList<>(); // for watershed partition
        // seeds that are background: corners and if possible L&R sides
        Voxel cornerL = new Voxel(0, 0, 0);
        Voxel cornerR = new Voxel(edgeMapLocal.sizeX()-1, 0, 0);
        
        Set<Voxel> leftBck = new HashSet<>();
        Set<Voxel> rightBck = new HashSet<>();
        for (int y = 0;y<edgeMapLocal.sizeY(); ++y) {
            if (y==0 || marginLRUp[0]>0) leftBck.add(new Voxel(0, y, 0));
            if (y==0 || marginLRUp[1]>0) rightBck.add(new Voxel(edgeMapLocal.sizeX()-1, y, 0));
        }
        if (verbose && object.getLabel()==debugLabel) {
            logger.debug("cornerL: {}(hash:{}), contained: {}, R: {}(hash:{}) contained: {}", cornerL, cornerL.hashCode(), leftBck.contains(cornerL), cornerR, cornerR.hashCode(), rightBck.contains(cornerR));
        }
        seeds.add(new Region(leftBck, 1, object.is2D(), inputLocal.getScaleXY(), inputLocal.getScaleZ()));
        seeds.add(new Region(rightBck, 1, object.is2D(), inputLocal.getScaleXY(), inputLocal.getScaleZ()));
        
        // this mask will define seeds that are for sure in the foreground : arrow shape mask in the center of the image
        int innerMargin = 0;
        if (innerMargin*4>=b.sizeX()-2) innerMargin = Math.max(1, b.sizeX()/8);
        BoundingBox innerRegion = new SimpleBoundingBox(innerMargin+marginLRUp[0], inputLocal.sizeX()-1-innerMargin-marginLRUp[1],marginLRUp[2], inputLocal.sizeY()-1, 0, inputLocal.sizeZ()-1);
        if (debug) logger.debug("crop inner-margin: {}, inner region: {} mask: {}", innerMargin, innerRegion, edgeMapLocal.getBoundingBox() );
        ImageByte mask = new ImageByte("", edgeMapLocal); 
        ImageOperations.fill(mask, 1, innerRegion);
        // roughly remove upper l&r angle from inner mask
        double x0 = marginLRUp[0];
        double x1=  innerRegion.xMean();
        double x20 = x1;
        double x21 = edgeMapLocal.sizeX()-1-marginLRUp[0];
        double y0 = marginLRUp[2]+innerRegion.sizeX() * innerMaskSlope; 
        double y1 = marginLRUp[2];
        double y20 = y1;
        double y21 = y0;
        double a1  = (y1-y0)/(x1-x0);
        double a2 = (y21-y20)/(x21-x20);
        
        for (int x = (int)x0; x<=x21; ++x) {
            for (int y = (int)y1; y<=y0; ++y) {
                if (y-y0<a1*(x-x0)) mask.setPixel(x, y, 0, 0);
                if ((y-y20)<a2*(x-x20)) mask.setPixel(x, y, 0, 0);
            }
        }
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(mask.duplicate("innnerMask"));
        
        ImageByte maxL = Filters.localExtrema(edgeMapLocal, null, false, null, Filters.getNeighborhood(1, 1, edgeMapLocal)).resetOffset();
        List<Region> allSeeds= ImageLabeller.labelImageList(maxL);
        Set<Voxel> foregroundVox = new HashSet<>();
        Iterator<Region> it = allSeeds.iterator();
        while (it.hasNext()) {
            Region n = it.next();
            for (Voxel v : n.getVoxels()) {
                if (leftBck.contains(v) || rightBck.contains(v)) {
                    it.remove();
                    break;
                }
                if (mask.insideMask(v.x, v.y, v.z)) {
                    foregroundVox.addAll(n.getVoxels());
                    it.remove();
                    break;
                }
            }
        }
        seeds.add(new Region(foregroundVox, 1, object.is2D(), object.getScaleXY(), object.getScaleZ()));
        // remaining seeds can be either from background OR from foreground
        seeds.addAll(allSeeds); 
        if (verbose && object.getLabel()==debugLabel) {
            Stream.concat(leftBck.stream(), rightBck.stream()).forEach(v->maxL.setPixel(v.x, v.y, v.z, 1));
            foregroundVox.stream().forEach(v->maxL.setPixel(v.x, v.y, v.z, 2));
            allSeeds.stream().forEach(r->r.getVoxels().stream().forEach(v->maxL.setPixel(v.x, v.y, v.z, 3)));
            ImageWindowManagerFactory.showImage(maxL.setName("Seeds 1=bck, 2=fore, 3=?"));
        }
        RegionPopulation partition = WatershedTransform.watershed(edgeMapLocal, null, seeds, null);
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(partition.getLabelMap().setName("partition"));
        
        if (seedsInMaskAreForeground) {
            Voxel foreVox = foregroundVox.stream().findAny().get();
            Region fore = partition.getRegions().stream().filter(r->r.getVoxels().contains(foreVox)).findAny().get();
            partition.getRegions().removeIf(o->o!=fore);
        } else { // merge regions either to foreground either to background
            Region bck1 = partition.getRegions().stream().filter(r->r.getVoxels().contains(cornerL)).findAny().get();
            Region bck2 = partition.getRegions().stream().filter(r->r.getVoxels().contains(cornerR)).findAny().orElseThrow(()->new RuntimeException("No background 2 object found for region: "+object.getLabel()));
            if ((bck1!=bck2 && partition.getRegions().size()>3) || (bck1==bck2 && partition.getRegions().size()>2)) { // some seeds were not merge either with bck or foreground -> decide with merge sort algorithm on edge value
                if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(partition.getLabelMap().duplicate("beofre merge"));
                SplitAndMergeRegionCriterion sm  = new SplitAndMergeRegionCriterion(edgeMapLocal, inputLocal, Double.POSITIVE_INFINITY, SplitAndMergeRegionCriterion.InterfaceValue.DIFF_MEDIAN_BTWN_REGIONS);
                //sm.setTestMode(verbose && object.getLabel()==debugLabel); // TODO ADD A MISC TEST VALUE THAT CHECKS IF OBJECTS OVERLAY
                sm.addForbidFusionForegroundBackground(r->r==bck1||r==bck2, r->!Collections.disjoint(r.getVoxels(), foregroundVox));
                if (bck1!=bck2) sm.addForbidFusion(i->(i.getE1()==bck1&&i.getE2()==bck2) || (i.getE1()==bck1&&i.getE2()==bck2)); // to be able to know how many region we want in the end. somtimes bck1 & bck2 can't merge
                partition = sm.merge(partition, sm.objectNumberLimitCondition(bck1==bck2 ? 2 :3)); // keep 3 regions = background on both sides & foreground
            }
            if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(partition.getLabelMap().duplicate("after ws transf"));
            partition.getRegions().removeIf(o->o.contains(cornerL) || o.contains(cornerR)); // remove background
        }
        partition.relabel(true);
        
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(partition.getLabelMap().duplicate("after ws transf & delete"));
        partition.translate(inputLocal, true);
        ImageInteger mcMask = partition.getLabelMap();
        // CLOSE & OPEN 
        if (morphoRadius<marginLRUp[2] && morphoRadius<marginLRUp[0] && morphoRadius<marginLRUp[1]) Filters.binaryClose(mcMask, mcMask, Filters.getNeighborhood(morphoRadius, mcMask), false);
        else mcMask=Filters.binaryCloseExtend(mcMask, Filters.getNeighborhood(morphoRadius,mcMask), false);
        Filters.binaryOpen(mcMask, mcMask, Filters.getNeighborhood(morphoRadius,mcMask), false);
        
        trimUpperPixels(mcMask, trimUpperPixelRadius); // avoid strong top border artefact 
        //ImageOperations.andWithOffset(mcMask, object.getMask(), mcMask);
        // trim xLeft & xRight to mean value 
        double[] xLMean = new double[2];
        double[] xRMean = new double[2];
        final ImageInteger regionMask =mcMask;
        ImageMask.loop(partition.getLabelMap(), (x, y, z)-> {
            if (y<=regionMask.sizeX()) return; // do not take into acount head
            if (x==0 || !regionMask.insideMask(x-1, y, z)) {
                xLMean[0]+=x;
                xLMean[1]++;
            }
            if (x==regionMask.sizeX()-1 || !regionMask.insideMask(x+1, y, z)) {
                xRMean[0]+=x;
                xRMean[1]++;
            }
        });
        xLMean[0]/=xLMean[1];
        xRMean[0]/=xRMean[1];
        ImageMask.loop(mcMask, (x, y, z)-> {if (x<xLMean[0] || x>xRMean[0]) regionMask.setPixel(x, y, z, 0);});
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(mcMask.duplicate("after trim L&R"));
        
        // and could also be or. In case of microchannels -> and
        object.and(mcMask);
        if (resetMask) object.resetMask(); // If average mask filter is used: no reset so that all image have same upper-left-corner
        
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(object.getMaskAsImageInteger().duplicate("after fit "));
        //if (debug && object.getLabel()==1) ImageWindowManagerFactory.showImage(object.getMask().duplicate("mask after remove"));
    }
    public static void trimUpperPixels(ImageInteger mask, int radius) {
        if (radius<=0) return;
        List<Voxel> toErase = new ArrayList<>();
        ImageMask.loop(mask, (x, y, z)->{
            for (int dy = 1; dy<=radius; ++dy) {
                if (y<dy || !mask.insideMask(x, y-dy, z)) {
                    toErase.add(new Voxel(x, y, z));
                    break;
                }
            }
        });
        for (Voxel v : toErase) mask.setPixel(v.x, v.y, v.z, 0);
    }

    

    
}
