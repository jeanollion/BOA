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

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObjectProcessing;
import ij.process.AutoThresholder;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageMask;
import java.util.Iterator;
import measurement.BasicMeasurements;
import plugins.Segmenter;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.Filters;
import processing.ImageFeatures;
import processing.WatershedTransform;
import processing.neighborhood.EllipsoidalNeighborhood;

/**
 *
 * @author jollion
 */
public class BacteriesFluo2D implements Segmenter {
    NumberParameter size = new BoundedNumberParameter("Minimal Object Dimension", 0, 15, 1, null);
    final static double gradientScale = 1;
    final static double medianScale = 2; // a mettre dans les prefilters
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        double thld = IJAutoThresholder.runThresholder(parent.getParent().getRawImage(structureIdx), parent.getParent().getMask(), AutoThresholder.Method.Otsu);
        
        return run(input, parent.getMask(), size.getValue().doubleValue(), thld);
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask, double minObjectDimension, double thld) {
        //Image filtered = Filters.median(input, new ImageFloat("", 0, 0, 0), Filters.getNeighborhood(3, 3, input));
        Image filtered = ImageFeatures.gaussianSmooth(input, medianScale, medianScale, true);
        Image[] structure = ImageFeatures.structureTransform(input, medianScale, gradientScale);
        Image gradient = structure[0];
        //Image gradient = ImageFeatures.getGradientMagnitude(filtered, gradientScale, false);
        ImageByte seeds = Filters.localExtrema(gradient, null, false, Filters.getNeighborhood(minObjectDimension/2.0, 1, input));
        //ImageByte seeds = Filters.localExtrema(filtered, null, false, Filters.getNeighborhood(minObjectDimension/2.0, 1, input));
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(filtered.setName("filtered"));
        disp.showImage(structure[0].setName("structure 0"));
        disp.showImage(structure[1].setName("structure 1"));
        disp.showImage(seeds.setName("seeds"));
        ObjectPopulation pop = WatershedTransform.watershed(gradient, mask, seeds, false);
        Iterator<Object3D> it = pop.getObjects().iterator();
        while(it.hasNext()) {
            Object3D o = it.next();
            if (BasicMeasurements.getMeanValue(o, input)<thld) it.remove();
        }
        pop.relabel();
        return pop;
    }

    public Parameter[] getParameters() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public boolean does3D() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
