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
package plugins.plugins.ObjectSplitter;

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import image.Image;
import image.ImageByte;
import image.ImageLabeller;
import image.ImageMask;
import plugins.ObjectSplitter;
import processing.Filters;
import processing.ImageFeatures;
import processing.WatershedTransform;

/**
 *
 * @author jollion
 */
public class WatershedObjectSplitter implements ObjectSplitter {
    //BoundedNumberParameter numberOfObjects = new BoundedNumberParameter("Maximum growth rate", 2, 1.5, 1, 2);
    public ObjectPopulation splitObject(Image input, Object3D object) {
        return split(input, object.getMask());
    }
    
    public static ObjectPopulation split(Image input, ImageMask mask) {
        // TODO smooth in prefilters..
        Image smoothed = ImageFeatures.gaussianSmooth(input, 2, 2, false);
        
        ImageByte localMax = Filters.localExtrema(smoothed, null, true, Filters.getNeighborhood(1, 1, input));
        Object3D[] seeds = ImageLabeller.labelImage(localMax);
        if (seeds.length<2) {
            logger.warn("Object splitter : less than 2 seeds found");
            return null;
        }
        else {
            WatershedTransform wt = new WatershedTransform(smoothed, mask, seeds, true, null, null);
            WatershedTransform.NumberFusionCriterion fusionCriterion = wt.new NumberFusionCriterion(2);
            wt.setFusionCriterion(fusionCriterion);
            wt.run();
            return wt.getObjectPopulation();
        }
    }

    public Parameter[] getParameters() {
        return new Parameter[0];
    }

    public boolean does3D() {
        return true;
    }
    
}
