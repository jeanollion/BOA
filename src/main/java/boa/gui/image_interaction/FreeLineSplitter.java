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
package boa.gui.image_interaction;

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
import boa.image.SimpleOffset;
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
import boa.utils.Utils;
import java.util.stream.IntStream;

/**
 *
 * @author Jean Ollion
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
        objects.forEach((p) -> {
            offsetMap.put(p.key.getRegion(), p.value);
        });
    }
    @Override
    public RegionPopulation splitObject(StructureObject parent, int structureIdx, Region object) {
        ImageMask mask = object.getMask();
        ImageInteger splitMask = mask instanceof ImageInteger ? ((ImageInteger)mask).duplicate("splitMask") : TypeConverter.toImageInteger(mask, null);
        Offset off=offsetMap.get(object);
        if (off==null) {
            logger.debug("no offset found");
            return null;
        }
        int offX = off.xMin();
        int offY = off.yMin();
        boolean[] removedPixel = new boolean[xPoints.length];
        for (int i = 0; i<xPoints.length; ++i) {
            int x = xPoints[i] - offX;
            int y = yPoints[i] - offY;
            if (splitMask.contains(x, y, 0) && splitMask.insideMask(x, y, 0)) {
                splitMask.setPixel(x, y, 0, 0);
                removedPixel[i] = true;
            }
        }
        List<Region> objects = ImageLabeller.labelImageListLowConnectivity(splitMask);
        RegionPopulation res = new RegionPopulation(objects, splitMask);
        res.filterAndMergeWithConnected(o->o.size()>1); // connect 1-pixels objects, artifacts of low connectivity labelling
        if (objects.size()>2) { // merge smaller & connected
            // islate bigger object and try to merge others
            Region biggest = Collections.max(objects, (o1, o2)->Integer.compare(o1.size(), o2.size()));
            List<Region> toMerge = new ArrayList<>(objects);
            toMerge.remove(biggest);
            RegionPopulation mergedPop =  new RegionPopulation(toMerge, splitMask);
            mergedPop.mergeAllConnected();
            objects = mergedPop.getRegions();
            objects.add(biggest);
            res = new RegionPopulation(objects, splitMask);
        }
        // relabel removed pixels
        if (objects.size()==2) {
            ImageInteger popMask = res.getLabelMap();
            Neighborhood n = new EllipsoidalNeighborhood(1.5, true);
            IntStream.range(0, xPoints.length).filter(i->removedPixel[i]).forEach(i -> {
                int x = xPoints[i] - offX;
                int y = yPoints[i] - offY;
                int l1Count = 0, l2Count=0;
                n.setPixels(x, y, 0, popMask, null);
                for (float f : n.getPixelValues()) {
                    if (f==1) ++l1Count;
                    else if (f==2) ++l2Count;
                }
                popMask.setPixel(x, y, 0, l1Count>=l2Count ? 1 : 2);
            });
            res = new RegionPopulation(popMask, true);
        }
        if (verbose) {
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(res.getLabelMap());
        } 
        res.translate(object.getBounds(), object.isAbsoluteLandMark());
        if (objects.size()==2) {
            logger.debug("freeline splitter absolute landmark : {}, off: {}", res.isAbsoluteLandmark(), Utils.toStringList(res.getRegions(), r -> new SimpleOffset(r.getBounds())));
            return res;
        }
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
