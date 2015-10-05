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
import configuration.parameters.PluginParameter;
import dataStructure.objects.BlankStructureObject;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObjectProcessing;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import plugins.Segmenter;
import plugins.Thresholder;

/**
 *
 * @author jollion
 */
public class SimpleThresholder implements Segmenter {
    PluginParameter<Thresholder> threshold;
    
    public SimpleThresholder() {
        threshold = new PluginParameter<Thresholder>("Threshold", Thresholder.class, false);
    }
    
    public SimpleThresholder(Thresholder thresholder) {
        this.threshold= new PluginParameter<Thresholder>("Threshold", Thresholder.class, thresholder, false);
    }
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing structureObject) {
        ImageByte mask = new ImageByte("mask", input);
        Thresholder t =  threshold.getPlugin();
        double thresh = t.runThresholder(input, structureObject);
        byte[][] pixels = mask.getPixelArray();
        for (int z = 0; z<input.getSizeZ(); ++z) {
            for (int xy = 0; xy<input.getSizeXY(); ++xy) {
                if (input.getPixel(xy, z)>=thresh) pixels[z][xy]=1;
            }
        }
        Object3D[] objects = ImageLabeller.labelImage(mask);
        logger.trace("simple thresholder: image: {} number of objects: {}", input.getName(), objects.length);
        return new ObjectPopulation(new ArrayList<Object3D>(Arrays.asList(objects)), input);
    }
    
    public static ObjectPopulation run(Image input, Thresholder thresholder, StructureObjectProcessing structureObject) {
        double thresh = thresholder.runThresholder(input, structureObject);
        return run(input, thresh); 
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask, Thresholder thresholder) {
        double thresh = thresholder.runThresholder(input, new BlankStructureObject(mask));
        return run(input, thresh); 
    }
    
    public static ObjectPopulation run(Image input, double threhsold) {
        ImageInteger mask = ImageOperations.threshold(input, threhsold, true, false, false, null);
        Object3D[] objects = ImageLabeller.labelImage(mask);
        return new ObjectPopulation(new ArrayList<Object3D>(Arrays.asList(objects)), input);
    }

    public Parameter[] getParameters() {
        return new Parameter[]{threshold};
    }

    public boolean does3D() {
        return true;
    }
    
}
