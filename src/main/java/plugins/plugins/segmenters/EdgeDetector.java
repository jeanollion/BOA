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
package plugins.plugins.segmenters;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PreFilterSequence;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.Voxel;
import ij.process.AutoThresholder;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageMask;
import java.util.Map;
import java.util.stream.Collectors;
import measurement.BasicMeasurements;
import plugins.PreFilter;
import plugins.Segmenter;
import plugins.Thresholder;
import plugins.plugins.preFilter.ImageFeature;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.Filters;
import processing.WatershedTransform;

/**
 *
 * @author jollion
 */
public class EdgeDetector implements Segmenter {
    protected PreFilterSequence watershedMap = new PreFilterSequence("Watershed Map").add(new ImageFeature().setFeature(ImageFeature.Feature.GRAD).setScale(2));
    public PluginParameter<Thresholder> threshold = new PluginParameter("Threshold", Thresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false);
    BooleanParameter applyThresholderOnValueMap = new BooleanParameter("Apply Threshold on value map", true);
    boolean testMode;
    public EdgeDetector setPreFilters(PreFilter... prefilters) {
        this.watershedMap.removeAllElements();;
        this.watershedMap.add(prefilters);
        return this;
    }
    public EdgeDetector setThresholder(Thresholder thlder) {
        this.threshold.setPlugin(thlder);
        return this;
    }
    public EdgeDetector setApplyThresholdOnValueMap(boolean applyOnValueMap) {
        this.applyThresholderOnValueMap.setSelected(applyOnValueMap);
        return this;
    }
    @Override
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        Image wsMap = watershedMap.filter(input, parent);
        return runOnWsMap(input, wsMap, parent);
    }
    public ObjectPopulation runOnWsMap(Image input, Image wsMap, StructureObjectProcessing parent) {
        ImageByte seeds = Filters.localExtrema(wsMap, null, false, Filters.getNeighborhood(1, 1, wsMap));
        ObjectPopulation allRegions = WatershedTransform.watershed(wsMap, parent.getMask(), seeds, false, true);
        if (testMode) {
            ImageWindowManagerFactory.showImage(allRegions.getLabelMap().duplicate("Segmented Regions"));
            ImageWindowManagerFactory.showImage(seeds.setName("Seeds"));
            ImageWindowManagerFactory.showImage(wsMap.setName("Watershed Map"));
        }
        if (this.applyThresholderOnValueMap.getSelected()) {
            Map<Object3D, Double>[] values = new Map[1];
            Image valueMap = generateRegionValueMap(allRegions, input, values);
            double thld = threshold.instanciatePlugin().runThresholder(valueMap, parent);
            if (testMode) ImageWindowManagerFactory.showImage(valueMap.setName("Intensity value Map. Threshold: "+thld));
            values[0].entrySet().removeIf(e->e.getValue()>=thld);
            allRegions.getObjects().removeAll(values[0].keySet());
            allRegions.relabel(true);
        } else {
            double thld = threshold.instanciatePlugin().runThresholder(input, parent);
            if (testMode) ImageWindowManagerFactory.showImage(generateRegionValueMap(allRegions, input).setName("Intensity value Map. Threshold: "+thld));
            allRegions.filter(new ObjectPopulation.MeanIntensity(thld, true, input));
        }
        
        
        
        return allRegions;
    }
    public static Image generateRegionValueMap(ObjectPopulation pop, Image image) {
        return generateRegionValueMap(pop, image, null);
    }
    public static Image generateRegionValueMap(ObjectPopulation pop, Image image, Map<Object3D, Double>[] values) {
        Map<Object3D, Double> objectValues = pop.getObjects().stream().collect(Collectors.toMap(o->o, o->BasicMeasurements.getMeanValue(o, image, false)));
        if (values!=null) values[0] = objectValues;
        Image valueMap = new ImageFloat("Value per region", image);
        for (Map.Entry<Object3D, Double> e : objectValues.entrySet()) {
            for (Voxel v : e.getKey().getVoxels()) valueMap.setPixel(v.x, v.y, v.z, e.getValue());
        }
        return valueMap;
    }
    protected double getValue(Object3D o, Image input) {
        return BasicMeasurements.getMeanValue(o, input, false);
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{watershedMap, threshold, applyThresholderOnValueMap};
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }
    
}
