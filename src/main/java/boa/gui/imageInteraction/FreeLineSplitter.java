/*
 * Copyright (C) 2016 jollion
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

import configuration.parameters.Parameter;
import dataStructure.objects.Region;
import dataStructure.objects.RegionPopulation;
import dataStructure.objects.RegionPopulation.Filter;
import dataStructure.objects.StructureObject;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageLabeller;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;
import plugins.ObjectSplitter;
import processing.neighborhood.EllipsoidalNeighborhood;
import processing.neighborhood.Neighborhood;
import utils.Pair;

/**
 *
 * @author jollion
 */
public class FreeLineSplitter implements ObjectSplitter {
    final Map<Region, BoundingBox> offsetMap;
    final int[] xPoints, yPoints;
    public FreeLineSplitter(Collection<Pair<StructureObject, BoundingBox>> objects, int[] xPoints, int[] yPoints) {
        if (xPoints.length!=yPoints.length) throw new IllegalArgumentException("xPoints & yPoints should have same length");
        //logger.debug("xPoints: {}", xPoints);
        //logger.debug("yPoints: {}", yPoints);
        this.xPoints=xPoints;
        this.yPoints=yPoints;
        offsetMap = new HashMap<>(objects.size());
        for (Pair<StructureObject, BoundingBox> p : objects) {
            offsetMap.put(p.key.getObject(), p.value);
        }
    }
    @Override
    public RegionPopulation splitObject(Image input, Region object) {
        ImageInteger mask = object.getMask();
        ImageInteger splitMask = mask.duplicate("splitMask");
        BoundingBox off=offsetMap.get(object);
        if (off==null) {
            logger.debug("no offset found");
            return null;
        }
        int offX = off.getxMin();
        int offY = off.getyMin();
        for (int i = 0; i<xPoints.length; ++i) {
            int x = xPoints[i] - offX;
            int y = yPoints[i] - offY;
            if (splitMask.contains(x, y, 0)) splitMask.setPixel(x, y, 0, 0);
        }
        List<Region> objects = ImageLabeller.labelImageListLowConnectivity(splitMask);
        RegionPopulation res = new RegionPopulation(objects, input);
        res.filterAndMergeWithConnected(o->o.getSize()>1); // connect 1-pixels objects, artifacts of low connectivity labelling
        if (objects.size()>2) { // merge smaller & connected
            // islate bigger object and try to merge others
            Region biggest = Collections.max(objects, (o1, o2)->Integer.compare(o1.getSize(), o2.getSize()));
            List<Region> toMerge = new ArrayList<>(objects);
            toMerge.remove(biggest);
            RegionPopulation mergedPop =  new RegionPopulation(toMerge, input);
            mergedPop.mergeAllConnected();
            objects = mergedPop.getObjects();
            objects.add(biggest);
            res = new RegionPopulation(objects, input);
        }
        // relabel removed pixels
        if (objects.size()==2) {
            splitMask = res.getLabelMap();
            Neighborhood n = new EllipsoidalNeighborhood(1.5, true);
            for (int i = 0; i<xPoints.length; ++i) {
                int x = xPoints[i] - offX;
                int y = yPoints[i] - offY;
                if (splitMask.contains(x, y, 0) && mask.insideMask(x, y, 0) && !splitMask.insideMask(x, y, 0)) {
                    int l1Count = 0, l2Count=0;
                    n.setPixels(x, y, 0, splitMask);
                    for (float f : n.getPixelValues()) {
                        if (f==1) ++l1Count;
                        else if (f==2) ++l2Count;
                    }
                    splitMask.setPixel(x, y, 0, l1Count>=l2Count ? 1 : 2);
                }
            }
            res = new RegionPopulation(splitMask, true);
        }
        if (verbose) {
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(res.getLabelMap());
        } 
        if (objects.size()==2) return res;
        return null;
    }
    boolean verbose=false;
    @Override
    public void setSplitVerboseMode(boolean verbose) {
        this.verbose=verbose;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[0];
    }
    
}
