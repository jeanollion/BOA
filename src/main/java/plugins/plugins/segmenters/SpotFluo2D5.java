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
package plugins.plugins.segmenters;

import configuration.parameters.Parameter;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObjectProcessing;
import image.Image;
import image.ImageMask;
import image.ImageOperations;
import java.util.ArrayList;
import plugins.Segmenter;
import processing.ImageFeatures;

/**
 *
 * @author jollion
 */
public class SpotFluo2D5 implements Segmenter {

    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent.getMask());
    }
    
    public static ObjectPopulation runPlane(Image input, ImageMask mask) {
        Image smoothed = ImageFeatures.gaussianSmooth(input, 1, 1, false);
        
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask) {
        // tester sur average, max, ou plan par plan
        /*ArrayList<Image> planes = input.splitZPlanes();
        for (Image plane : planes) {
            ObjectPopulation obj = runPlane(plane, mask);
        }
        */
        Image avg = ImageOperations.meanProjection(input, ImageOperations.Axis.Z, null);
    }

    public Parameter[] getParameters() {
        return new Parameter[0];
    }

    public boolean does3D() {
        return true;
    }
    
}
