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
package boa.gui.imageInteraction;

import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.RegionPopulation.Filter;
import boa.data_structure.StructureObject;
import boa.image.BoundingBox;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.Offset;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;
import boa.plugins.ObjectSplitter;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.utils.Pair;

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
            offsetMap.put(p.key.getRegion(), p.value);
        }
    }
    @Override
    public RegionPopulation splitObject(StructureObject parent, int structureIdx, Region object) {
        Image input = parent.getPreFilteredImage(structureIdx);
        ImageMask mask = object.getMask();
        ImageInteger splitMask = mask instanceof ImageInteger ? ((ImageInteger)mask).duplicate("splitMask") : TypeConverter.toImageInteger(mask, null);
        Offset off=offsetMap.get(object);
        if (off==null) {
            logger.debug("no offset found");
            return null;
        }
        int offX = off.xMin();
        int offY = off.yMin();
        for (int i = 0; i<xPoints.length; ++i) {
            int x = xPoints[i] - offX;
            int y = yPoints[i] - offY;
            if (splitMask.contains(x, y, 0)) splitMask.setPixel(x, y, 0, 0);
        }
        List<Region> objects = ImageLabeller.labelImageListLowConnectivity(splitMask);
        RegionPopulation res = new RegionPopulation(objects, input);
        res.filterAndMergeWithConnected(o->o.size()>1); // connect 1-pixels objects, artifacts of low connectivity labelling
        if (objects.size()>2) { // merge smaller & connected
            // islate bigger object and try to merge others
            Region biggest = Collections.max(objects, (o1, o2)->Integer.compare(o1.size(), o2.size()));
            List<Region> toMerge = new ArrayList<>(objects);
            toMerge.remove(biggest);
            RegionPopulation mergedPop =  new RegionPopulation(toMerge, input);
            mergedPop.mergeAllConnected();
            objects = mergedPop.getRegions();
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
                    n.setPixels(x, y, 0, splitMask, null);
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
