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
package processing;

import boa.gui.imageInteraction.IJImageDisplayer;
import static core.Processor.logger;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.Voxel;
import image.BlankMask;
import image.Image;
import image.ImageByte;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Iterator;
import measurement.BasicMeasurements;
import processing.mergeRegions.RegionCollection;

/**
 *
 * @author jollion
 */
public class FitEdges {
    
    public static Object3D run(Image image, Image fitImage, ImageMask mask, double regionThreshold) { // attention aux offsets de l'object
        // run watershed on fit image
        if (mask==null) mask = new BlankMask(image);
        ImageByte seeds = Filters.localExtrema(fitImage, null, false, Filters.getNeighborhood(1, 1, image));
        //new IJImageDisplayer().showImage(seeds);
        ObjectPopulation pop = WatershedTransform.watershed(fitImage, mask, seeds, false);
        Iterator<Object3D> it = pop.getObjects().iterator();
        int count=0;
        while(it.hasNext()) {
            Object3D o = it.next();
            logger.debug("fit edges: object: {} value: {}, thld: {}", count++, BasicMeasurements.getMeanValue(o, image, false), regionThreshold);
            if (BasicMeasurements.getMeanValue(o, image, false)<regionThreshold) it.remove();
        }
        if (pop.getObjects().isEmpty()) return null;
        Object3D o = pop.getObjects().get(0);
        for (int i = 1; i<pop.getObjects().size(); ++i) o.getVoxels().addAll(pop.getObjects().get(i).getVoxels());
        return o;
    }
}
