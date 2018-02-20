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
package boa.plugins.plugins.post_filters;

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
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageLabeller;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.List;
import boa.plugins.PostFilter;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.WatershedTransform;
import boa.plugins.plugins.pre_filters.ImageFeature;
import boa.plugins.plugins.segmenters.EdgeDetector;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class FitMicrochannelHeadToEdges implements PostFilter {
     protected PreFilterSequence watershedMap = new PreFilterSequence("Watershed Map").add(new ImageFeature().setFeature(ImageFeature.Feature.StructureMax).setScale(1.5).setSmoothScale(1.5)).setToolTipText("Watershed map, separation between regions are at area of maximal intensity of this map");
    
    public static boolean debug = false;
    public static int debugLabel = 1;
    @Override
    public RegionPopulation runPostFilter(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        Image edge = watershedMap.filter(parent.getRawImage(childStructureIdx), parent.getMask());
        fitHead(edge, childPopulation);
        return childPopulation;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{watershedMap};
    }
    public static void fitHead(Image edgeMap, RegionPopulation inputPop) {
        if (debug) ImageWindowManagerFactory.showImage(edgeMap);
        for (Region o : inputPop.getRegions()) cutHead(edgeMap, 3, o);
        inputPop.redrawLabelMap(true);
        if (debug && !inputPop.getRegions().isEmpty()) logger.debug("object mask type: {}", inputPop.getRegions().get(0).getMask().getClass().getSimpleName());
    }
    
    private static void cutHead(Image edgeMap, int margin, Region object) {
        BoundingBox b = object.getBounds();
        BoundingBox head = new BoundingBox(b.getxMin()-margin, b.getxMax()+margin, b.getyMin()-margin, b.getyMin()+b.getSizeX(), b.getzMin(), b.getzMax());
        Image edgeMapLocal = edgeMap.crop(head);
        List<Region> seeds = new ArrayList<>(3);
        int label = 0;
        double scaleXY = edgeMap.getScaleXY();
        double scaleZ = edgeMap.getScaleZ();
        Voxel corner1 = new Voxel(0, 0, 0);
        Voxel corner2 = new Voxel(edgeMapLocal.getSizeX()-1, 0, 0);
        seeds.add(new Region(corner1, ++label, object.is2D(), (float)scaleXY, (float)scaleZ));
        seeds.add(new Region(corner2, ++label, object.is2D(), (float)scaleXY, (float)scaleZ));
        // add all local min within innerHead
        int innerMargin = margin*2;
        if (innerMargin*2>=b.getSizeX()-2) innerMargin = Math.max(1, b.getSizeX()/4);
        BoundingBox innerHead = new BoundingBox(innerMargin, head.getSizeX()-1-innerMargin,innerMargin, head.getSizeY()-1-innerMargin, 0, head.getSizeZ()-1);
        ImageByte maxL = Filters.localExtrema(edgeMapLocal, null, false, innerHead.getImageProperties(1, 1), Filters.getNeighborhood(1.5, 1.5, edgeMapLocal)).resetOffset();
        //if (debug && object.getLabel()==1) ImageWindowManagerFactory.showImage(maxL.duplicate("inner seeds before and"));
        //ImageOperations.andWithOffset(maxL, innerHead.getImageProperties(1, 1), maxL);
        if (debug && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(maxL.duplicate("inner seeds after and"));
        seeds.addAll(ImageLabeller.labelImageList(maxL));
        //seeds.add(new Region(new Voxel((gradLocal.getSizeX()-1)/2, (gradLocal.getSizeY()-1)/2, 0), ++label, (float)scaleXY, (float)scaleZ));
        RegionPopulation pop = WatershedTransform.watershed(edgeMapLocal, null, seeds, false, null, null, false);
        if (debug && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("after ws transf"));
        pop.getRegions().removeIf(o->!o.contains(corner1)&&!o.contains(corner2));
        pop.relabel(true);
        if (debug && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("after ws transf & delete"));
        pop.translate(head, true);
        object.andNot(pop.getLabelMap());
        //object.resetMask(); // no reset so that average mask 
        if (debug && object.getLabel()==debugLabel) ImageWindowManagerFactory.showImage(object.getMaskAsImageInteger().duplicate("after remove head"));
        //if (debug && object.getLabel()==1) ImageWindowManagerFactory.showImage(object.getMask().duplicate("mask after remove"));
    }
}
