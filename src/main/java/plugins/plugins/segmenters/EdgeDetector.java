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
import configuration.parameters.ChoiceParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PreFilterSequence;
import dataStructure.objects.Region;
import dataStructure.objects.RegionPopulation;
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.Voxel;
import ij.process.AutoThresholder;
import image.Histogram;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageProperties;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import measurement.BasicMeasurements;
import plugins.PreFilter;
import plugins.Segmenter;
import plugins.Thresholder;
import plugins.plugins.preFilter.ImageFeature;
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.Filters;
import processing.ImageFeatures;
import processing.WatershedTransform;

/**
 *
 * @author jollion
 */
public class EdgeDetector implements Segmenter {
    protected PreFilterSequence watershedMap = new PreFilterSequence("Watershed Map").add(new ImageFeature().setFeature(ImageFeature.Feature.GRAD).setScale(2));
    public PluginParameter<Thresholder> threshold = new PluginParameter("Threshold", Thresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false);
    ChoiceParameter thresholdMethod = new ChoiceParameter("Remove background method", new String[]{"Intensity Map", "Value Map", "Secondary Map"}, "Value Map", false);
    protected PreFilterSequence scondaryThresholdMap = new PreFilterSequence("Secondary Threshold Map").add(new ImageFeature().setFeature(ImageFeature.Feature.HessianMax).setScale(2));
    ConditionalParameter thresholdCond = new ConditionalParameter(thresholdMethod).setDefaultParameters(new Parameter[]{threshold}).setActionParameters("Secondary Map", new Parameter[]{scondaryThresholdMap});
    boolean testMode;
    
    // variables
    Image wsMap;
    ImageInteger seedMap;
    Image secondaryThresholdMap;
    Image watershedPriorityMap;
    public Image getWsMap(Image input, StructureObjectProcessing parent) {
        if (wsMap==null) wsMap = watershedMap.filter(input, parent);
        return wsMap;
    }

    public ImageInteger getSeedMap(Image input, StructureObjectProcessing parent) {
        if (seedMap==null) seedMap = Filters.localExtrema(getWsMap(input, parent), null, false, Filters.getNeighborhood(1, 1, getWsMap(input, parent)));
        return seedMap;
    }

    public EdgeDetector setWsMap(Image wsMap) {
        this.wsMap = wsMap;
        return this;
    }
    public EdgeDetector setWsPriorityMap(Image map) {
        this.watershedPriorityMap = map;
        return this;
    }
    public EdgeDetector setSeedMap(ImageInteger seedMap) {
        this.seedMap = seedMap;
        return this;
    }
    
    public EdgeDetector setSecondaryThresholdMap(Image secondaryThresholdMap) {
        this.secondaryThresholdMap = secondaryThresholdMap;
        if (secondaryThresholdMap!=null) this.thresholdMethod.setSelectedIndex(2);
        return this;
    }
    public Image getSecondaryThresholdMap(Image input, StructureObjectProcessing parent) {
        if (scondaryThresholdMap==null) {
            if (!scondaryThresholdMap.isEmpty()) {
                if (scondaryThresholdMap.sameContent(this.watershedMap)) secondaryThresholdMap = getWsMap(input, parent);
                else secondaryThresholdMap = scondaryThresholdMap.filter(input, parent);
            }
        }
        return secondaryThresholdMap; // todo test if prefilter differs from ws map to avoid processing 2 times same image
    }
    public Image getWsPriorityMap(Image input, StructureObjectProcessing parent) {
        if (this.watershedPriorityMap==null) watershedPriorityMap = ImageFeatures.gaussianSmooth(input, 2, false); // TODO parameter?
        return watershedPriorityMap;
    }
    public EdgeDetector setPreFilters(PreFilter... prefilters) {
        this.watershedMap.removeAllElements();
        this.watershedMap.add(prefilters);
        return this;
    }
    public EdgeDetector setThresholder(Thresholder thlder) {
        this.threshold.setPlugin(thlder);
        return this;
    }
    public EdgeDetector setThrehsoldingMethod(int method) {
        this.thresholdMethod.setSelectedIndex(method);
        return this;
    }
    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        WatershedTransform wt = new WatershedTransform(getWsMap(input, parent), parent.getMask(), Arrays.asList(ImageLabeller.labelImage(getSeedMap(input, parent))), false, null, null);
        wt.setLowConnectivity(false);
        //wt.setPriorityMap(getWsPriorityMap(input, parent));
        wt.run();
        RegionPopulation allRegions = wt.getObjectPopulation();
        if (testMode) {
            ImageWindowManagerFactory.showImage(allRegions.getLabelMap().duplicate("Segmented Regions"));
            ImageWindowManagerFactory.showImage(seedMap.setName("Seeds"));
            ImageWindowManagerFactory.showImage(wsMap.setName("Watershed Map"));
        }
        if (this.thresholdMethod.getSelectedIndex()==0) {
            double thld = threshold.instanciatePlugin().runThresholder(input, parent);
            if (testMode) ImageWindowManagerFactory.showImage(generateRegionValueMap(allRegions, input).setName("Intensity value Map. Threshold: "+thld));
            allRegions.filter(new RegionPopulation.MeanIntensity(thld, true, input));
        } else if (this.thresholdMethod.getSelectedIndex()==1) { // thld on value map
            Map<Region, Double>[] values = new Map[1];
            Image valueMap = generateRegionValueMap(allRegions, input, values);
            double thld = threshold.instanciatePlugin().runThresholder(valueMap , parent);
            if (testMode) ImageWindowManagerFactory.showImage(valueMap.setName("Intensity value Map. Threshold: "+thld));
            values[0].entrySet().removeIf(e->e.getValue()>=thld);
            allRegions.getObjects().removeAll(values[0].keySet());
            allRegions.relabel(true);
        } else { // use of secondary map to select border regions and compute thld
            Map<Region, Double>[] values = new Map[1];
            Image valueMap = generateRegionValueMap(allRegions, input, values);
            double thld1 = IJAutoThresholder.runThresholder(valueMap, parent.getMask(), AutoThresholder.Method.Otsu);
            if (testMode) ImageWindowManagerFactory.showImage(valueMap.duplicate("Primary thld value map. Thld: "+thld1));
            Map<Region, Double>[] values2 = new Map[1];
            Image valueMap2 = generateRegionValueMap(allRegions, getSecondaryThresholdMap(input, parent), values2);
            double thld2 = IJAutoThresholder.runThresholder(valueMap2, parent.getMask(), AutoThresholder.Method.Otsu);
            // select objects under thld2 | above thld -> foreground, interface ou backgruond. Others are interface or border (majority) and set value to thld on valueMap 
            for (Region o : allRegions.getObjects()) {
                if (values[0].get(o)>=thld1 || values2[0].get(o)<thld2) o.draw(valueMap, thld1);
            }
            Histogram h = valueMap.getHisto256(parent.getMask());
            // search for largest segment with no values
            int sMax = 0, eMax = 0;
            for (int s = 0; s<254; ++s) {
                if (h.data[s]!=0) continue;
                int e = s;
                while(e<254 && h.data[e+1]==0) ++e;
                if (e-s>eMax-sMax) {
                    eMax = e;
                    sMax = s;
                }
            }
            double thld  = h.getValueFromIdx((eMax+sMax)/2.0);
            //double thld = BackgroundThresholder.runThresholder(valueMap, parent.getMask(), 4, 4, 1, thld1); // run background thlder with thld1 as limit to select border form interfaces
            if (testMode) {
                ImageWindowManagerFactory.showImage(valueMap2.setName("Secondary thld value map. Thld: "+thld2));
                ImageWindowManagerFactory.showImage(valueMap.setName("Value map. Thld: "+thld));
            }
            values[0].entrySet().removeIf(e->e.getValue()>=thld);
            allRegions.getObjects().removeAll(values[0].keySet());
            allRegions.relabel(true);
        }
        return allRegions;
    }

    public static Image generateRegionValueMap(RegionPopulation pop, Image image) {
        return generateRegionValueMap(pop, image, null);
    }
    public static Image generateRegionValueMap(RegionPopulation pop, Image image, Map<Region, Double>[] values) {
        Map<Region, Double> objectValues = pop.getObjects().stream().collect(Collectors.toMap(o->o, o->BasicMeasurements.getMeanValue(o, image, false)));
        if (values!=null) values[0] = objectValues;
        return generateRegionValueMap(image, objectValues);
    }
    private static Image generateRegionValueMap(ImageProperties image, Map<Region, Double> objectValues) {
        Image valueMap = new ImageFloat("Value per region", image);
        for (Map.Entry<Region, Double> e : objectValues.entrySet()) {
            for (Voxel v : e.getKey().getVoxels()) valueMap.setPixel(v.x, v.y, v.z, e.getValue());
        }
        return valueMap;
    }
    protected double getValue(Region o, Image input) {
        return BasicMeasurements.getMeanValue(o, input, false);
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{watershedMap, threshold, thresholdCond};
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }
    
}
