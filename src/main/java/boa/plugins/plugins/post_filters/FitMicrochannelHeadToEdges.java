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
package boa.plugins.plugins.post_filters;

import boa.configuration.parameters.BooleanParameter;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
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
import boa.image.processing.WatershedTransform;
import boa.image.processing.split_merge.SplitAndMergeEdge;
import boa.plugins.plugins.pre_filters.ImageFeature;
import boa.plugins.plugins.pre_filters.Sigma;
import boa.plugins.plugins.segmenters.EdgeDetector;
import boa.utils.Utils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 *
 * @author jollion
 */
public class FitMicrochannelHeadToEdges implements PostFilter {
    protected PreFilterSequence watershedMap = new PreFilterSequence("Watershed Map").add(new Sigma(3).setMedianRadius(2)).setToolTipText("Watershed map, separation between regions are at area of maximal intensity of this map"); //new ImageFeature().setFeature(ImageFeature.Feature.StructureMax).setScale(1.5).setSmoothScale(1.5)
    BooleanParameter onlyHead = new BooleanParameter("Fit only head", false);
    BoundedNumberParameter trimUpperPixels = new BoundedNumberParameter("Trim Upper Pixels", 0, 2, 0, null).setToolTipText("Erase Pixels of a region if they are closer than this value to the background in the upper y-direction");
    BooleanParameter resetBounds = new BooleanParameter("Reset Bounds", true).setToolTipText("Whether bounds should be reset or not. If average mask track-post-filter is set afterwards, bounds should not be reset so that regions can be aligned on their top-left-corner");
    Parameter[] parameters = new Parameter[]{watershedMap, onlyHead, trimUpperPixels, resetBounds};
    public static boolean debug = false;
    public static int debugLabel = 10;
    public boolean verbose = false;
    public FitMicrochannelHeadToEdges setResetBounds(boolean resetBounds) {
        this.resetBounds.setSelected(resetBounds);
        return this;
    }
    public FitMicrochannelHeadToEdges setOnlyHead(boolean onlyHead) {
        this.onlyHead.setSelected(onlyHead);
        return this;
    }
    
    @Override
    public RegionPopulation runPostFilter(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        Image edge = watershedMap.filter(parent.getRawImage(childStructureIdx), parent.getMask());
        FitMicrochannelHeadToEdges.fit(edge, childPopulation, onlyHead.getSelected(), this.trimUpperPixels.getValue().intValue(), resetBounds.getSelected(), debug||verbose);
        return childPopulation;
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    public static void fit(Image edgeMap, RegionPopulation inputPop, boolean onlyHead, int trimUpperPixelRadius, boolean resetMask, boolean verbose) {
        if (verbose) ImageWindowManagerFactory.showImage(edgeMap);
        for (Region o : inputPop.getRegions()) {
            if (onlyHead) fitHead(edgeMap, 3, o, trimUpperPixelRadius, resetMask, verbose);
            else fitWhole(edgeMap, 3, o, trimUpperPixelRadius, resetMask, verbose);
        }
        inputPop.redrawLabelMap(true);
        if (verbose && !inputPop.getRegions().isEmpty()) logger.debug("object mask type: {}", inputPop.getRegions().get(0).getMask().getClass().getSimpleName());
    }
    
    private static void fitHead(Image edgeMap, int margin, Region object, int trimUpperPixelRadius, boolean resetMask, boolean verbose) {
        BoundingBox b = object.getBounds();
        BoundingBox head = new SimpleBoundingBox(b.xMin()-margin, b.xMax()+margin, b.yMin()-margin, b.yMin()+b.sizeX(), b.zMin(), b.zMax());
        Image edgeMapLocal = edgeMap.crop(head);
        List<Region> seeds = new ArrayList<>();
        int label = 0;
        double scaleXY = edgeMap.getScaleXY();
        double scaleZ = edgeMap.getScaleZ();
        Voxel corner1 = new Voxel(0, 0, 0);
        Voxel corner2 = new Voxel(edgeMapLocal.sizeX()-1, 0, 0);
        seeds.add(new Region(corner1, ++label, object.is2D(), (float)scaleXY, (float)scaleZ));
        seeds.add(new Region(corner2, ++label, object.is2D(), (float)scaleXY, (float)scaleZ));
        // add all local min within innerHead
        int innerMargin = margin*2;
        if (innerMargin*2>=b.sizeX()-2) innerMargin = Math.max(1, b.sizeX()/4);
        BoundingBox innerHead = new SimpleBoundingBox(innerMargin, head.sizeX()-1-innerMargin,innerMargin, head.sizeY()-1-innerMargin, 0, head.sizeZ()-1);
        ImageByte mask = new ImageByte("", edgeMapLocal);
        ImageOperations.fill(mask, 1, innerHead);
        ImageByte maxL = Filters.localExtrema(edgeMapLocal, null, false, mask, Filters.getNeighborhood(1.5, 1.5, edgeMapLocal)).resetOffset();
        //if (debug && object.getLabel()==1) ImageWindowManagerFactory.showImage(maxL.duplicate("inner seeds before and"));
        //ImageOperations.andWithOffset(maxL, innerHead.getImageProperties(1, 1), maxL);
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(maxL.duplicate("inner seeds after and"));
        seeds.addAll(ImageLabeller.labelImageList(maxL));
        //seeds.add(new Region(new Voxel((gradLocal.getSizeX()-1)/2, (gradLocal.getSizeY()-1)/2, 0), ++label, (float)scaleXY, (float)scaleZ));
        RegionPopulation pop = WatershedTransform.watershed(edgeMapLocal, null, seeds, false, null, new WatershedTransform.SizeFusionCriterion(5), false);
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("after ws transf"));
        pop.getRegions().removeIf(o->!o.contains(corner1)&&!o.contains(corner2));
        pop.relabel(true);
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("after ws transf & delete"));
        pop.translate(head, true);
        object.andNot(pop.getLabelMap());
        trimUpperPixels(object, trimUpperPixelRadius);
        if (resetMask) object.resetMask(); // no reset so that all image have same upper-left-corner -> for average mask
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(object.getMaskAsImageInteger().duplicate("after remove head"));
        //if (debug && object.getLabel()==1) ImageWindowManagerFactory.showImage(object.getMask().duplicate("mask after remove"));
    }
    private static void fitWhole(Image edgeMap, int margin, Region object, int trimUpperPixelRadius, boolean resetMask, boolean verbose) {
        double innerMaskSlope = 0.5;
        BoundingBox b = object.getBounds();
        int marginL = Math.min(margin, b.xMin());
        int marginR = Math.min(margin , edgeMap.sizeX()-1 - b.xMax());
        int marginUp = Math.min(margin, b.yMin());
        BoundingBox cut = new SimpleBoundingBox(b.xMin()-marginL, b.xMax()+marginR, b.yMin()-marginUp, b.yMax(), b.zMin(), b.zMax());
        Image edgeMapLocal = edgeMap.crop(cut);
        List<Region> seeds = new ArrayList<>(); // for watershed partition
        
        // seeds that are background: corners and if possible L&R sides
        Voxel cornerL = new Voxel(0, 0, 0);
        Voxel cornerR = new Voxel(edgeMapLocal.sizeX()-1, 0, 0);
        Set<Voxel> leftBck = new HashSet<>();
        Set<Voxel> rightBck = new HashSet<>();
        for (int y = 0;y<edgeMapLocal.sizeY(); ++y) {
            if (y==0 || marginL>0) leftBck.add(new Voxel(0, y, 0));
            if (y==0 || marginR>0) rightBck.add(new Voxel(edgeMapLocal.sizeX()-1, y, 0));
        }
        seeds.add(new Region(leftBck, 1, object.is2D(), edgeMap.getScaleXY(), edgeMap.getScaleZ()));
        seeds.add(new Region(rightBck, 1, object.is2D(), edgeMap.getScaleXY(), edgeMap.getScaleZ()));
        
        // this mask will define seeds that are for sure in the foreground : arrow shape mask in the center of the image
        int innerMargin = margin;
        if (innerMargin*4>=b.sizeX()-2) innerMargin = Math.max(1, b.sizeX()/8);
        BoundingBox innerRegion = new SimpleBoundingBox(innerMargin+marginL, cut.sizeX()-1-innerMargin-marginR,innerMargin+marginUp, cut.sizeY()-1, 0, cut.sizeZ()-1);
        
        ImageByte mask = new ImageByte("", edgeMapLocal); 
        ImageOperations.fill(mask, 1, innerRegion);
        // roughly remove upper l&r angle from inner mask
        double x0 = marginL;
        double x1=  innerRegion.xMean();
        double x20 = x1;
        double x21 = edgeMapLocal.sizeX()-1-marginL;
        double y0 = innerMargin+marginUp+innerRegion.sizeX() * innerMaskSlope; 
        double y1 = innerMargin+marginUp;
        double y20 = y1;
        double y21 = y0;
        double a1  = (y1-y0)/(x1-x0);
        double a2 = (y21-y20)/(x21-x20);
        
        for (int x = (int)x0; x<=x21; ++x) {
            for (int y = (int)y1; y<=y0; ++y) {
                if (y-y0<=a1*(x-x0)) mask.setPixel(x, y, 0, 0);
                if ((y-y20)<=a2*(x-x20)) mask.setPixel(x, y, 0, 0);
            }
        }
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(mask.duplicate("innnerMask"));
        
        ImageByte maxL = Filters.localExtrema(edgeMapLocal, null, false, null, Filters.getNeighborhood(1.5, 1.5, edgeMapLocal)).resetOffset();
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
        
        RegionPopulation pop = WatershedTransform.watershed(edgeMapLocal, null, seeds, false, null, new WatershedTransform.SizeFusionCriterion(5), false);
        Region bck1 = pop.getRegions().stream().filter(r->r.getVoxels().contains(cornerL)).findFirst().get();
        Region bck2 = pop.getRegions().stream().filter(r->r.getVoxels().contains(cornerR)).findFirst().get();
        
        if ((bck1!=bck2 && pop.getRegions().size()>3) || (bck1==bck2 && pop.getRegions().size()>2)) { // some seeds were not merge either with bck or foreground -> decide with merge sort algorithm on edge value
            SplitAndMergeEdge sm  = new SplitAndMergeEdge(edgeMapLocal, edgeMapLocal, Double.POSITIVE_INFINITY, false);
            sm.addForbidFusionForegroundBackground(r->r==bck1||r==bck2, r->!Collections.disjoint(r.getVoxels(), foregroundVox));
            if (bck1!=bck2) sm.addForbidFusion(i->(i.getE1()==bck1&&i.getE2()==bck2) || (i.getE1()==bck1&&i.getE2()==bck2)); // to be able to know how many region we want in the end. somtimes bck1 & bck2 can't merge
            pop = sm.merge(pop, bck1==bck2 ? 2 :3); // keep 3 regions = background on both sides & foreground
        }
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("after ws transf"));
        pop.getRegions().removeIf(o->!o.contains(cornerL)&&!o.contains(cornerR)); // remove background
        pop.relabel(true);
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("after ws transf & delete"));
        pop.translate(cut, true);
        object.andNot(pop.getLabelMap());
        
        trimUpperPixels(object, trimUpperPixelRadius); // avoid strong top border artefact 
        
        // trim xLeft & xRight to mean value 
        double[] xLMean = new double[2];
        double[] xRMean = new double[2];
        ImageInteger regionMask = object.getMaskAsImageInteger();
        ImageMask.loop(object.getMask(), (x, y, z)-> {
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
        ImageMask.loop(object.getMask(), (x, y, z)-> {if (x<xLMean[0] || x>xRMean[0]) regionMask.setPixel(x, y, z, 0);});
        
        if (resetMask) object.resetMask(); // If average mask filter is used: no reset so that all image have same upper-left-corner
        if (verbose && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(object.getMaskAsImageInteger().duplicate("after remove head"));
        //if (debug && object.getLabel()==1) ImageWindowManagerFactory.showImage(object.getMask().duplicate("mask after remove"));
    }
    public static void trimUpperPixels(Region region, int radius) {
        if (radius<=0) return;
        ImageInteger mask = region.getMaskAsImageInteger();
        List<Voxel> toErase = new ArrayList<>();
        ImageMask.loop(mask, (x, y, z)->{
            for (int dy = 1; dy<=radius; ++dy) {
                if (y<dy || !mask.insideMask(x, y-dy, z)) toErase.add(new Voxel(x, y, z));
            }
        });
        for (Voxel v : toErase) mask.setPixel(v.x, v.y, v.z, 0);
    }
}
