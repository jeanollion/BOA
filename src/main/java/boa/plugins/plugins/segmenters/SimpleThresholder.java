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
package boa.plugins.plugins.segmenters;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObjectProcessing;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import boa.plugins.Segmenter;
import boa.plugins.Thresholder;
import boa.plugins.plugins.thresholders.ConstantValue;

/**
 *
 * @author jollion
 */
public class SimpleThresholder implements Segmenter {
    PluginParameter<Thresholder> threshold = new PluginParameter<>("Threshold", Thresholder.class, false);
    
    public SimpleThresholder() {

    }
    
    public SimpleThresholder(Thresholder thresholder) {
        this.threshold.setPlugin(thresholder);
    }
    
    public SimpleThresholder(double threshold) {
        this(new ConstantValue(threshold));
    }
    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing structureObject) {
        ImageByte mask = new ImageByte("mask", input);
        Thresholder t =  threshold.instanciatePlugin();
        double thresh = t.runThresholder(input, structureObject);
        byte[][] pixels = mask.getPixelArray();
        for (int z = 0; z<input.sizeZ(); ++z) {
            for (int xy = 0; xy<input.sizeXY(); ++xy) {
                if (input.getPixel(xy, z)>=thresh) pixels[z][xy]=1;
            }
        }
        Region[] objects = ImageLabeller.labelImage(mask);
        logger.trace("simple thresholder: image: {} number of objects: {}", input.getName(), objects.length);
        return  new RegionPopulation(new ArrayList<>(Arrays.asList(objects)), input);
        
    }
    
    public static RegionPopulation run(Image input, Thresholder thresholder, StructureObjectProcessing structureObject) {
        double thresh = thresholder.runThresholder(input, structureObject);
        return run(input, thresh, structureObject.getMask()); 
    }
    
    
    public static RegionPopulation run(Image input, double threhsold, ImageMask mask) {
        ImageInteger maskR = ImageOperations.threshold(input, threhsold, true, false, false, null);
        if (mask!=null) ImageOperations.and(maskR, mask, maskR);
        Region[] objects = ImageLabeller.labelImage(maskR);
        return new RegionPopulation(new ArrayList<>(Arrays.asList(objects)), input);
    }
    public static RegionPopulation runUnder(Image input, double threhsold, ImageMask mask) {
        ImageInteger maskR = ImageOperations.threshold(input, threhsold, false, false, false, null);
        if (mask!=null) ImageOperations.and(maskR, mask, maskR);
        Region[] objects = ImageLabeller.labelImage(maskR);
        return new RegionPopulation(new ArrayList<>(Arrays.asList(objects)), input);
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{threshold};
    }

}
