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
package boa.plugins.plugins.segmenters;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import ij.process.AutoThresholder;
import boa.image.Histogram;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.ImageProperties;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import boa.measurement.BasicMeasurements;
import boa.plugins.PreFilter;
import boa.plugins.Segmenter;
import boa.plugins.Thresholder;
import boa.plugins.plugins.pre_filter.ImageFeature;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.SplitAndMerge;
import boa.image.processing.WatershedTransform;
import boa.plugins.ToolTip;
import boa.utils.ArrayUtil;
import boa.plugins.SimpleThresholder;
/**
 *
 * @author jollion
 */
public class EdgeDetector implements Segmenter, ToolTip {
    protected PreFilterSequence watershedMap = new PreFilterSequence("Watershed Map").add(new ImageFeature().setFeature(ImageFeature.Feature.StructureMax).setScale(1.5).setSmoothScale(2)).setToolTipText("Watershed map, separation between regions are at area of maximal intensity of this map");
    public PluginParameter<SimpleThresholder> threshold = new PluginParameter("Threshold", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false).setToolTipText("Threshold method used to remove background regions");
    ChoiceParameter thresholdMethod = new ChoiceParameter("Remove background method", new String[]{"Intensity Map", "Value Map", "Secondary Map"}, "Value Map", false).setToolTipText("<html>Intensity Map: compute threshold on raw intensity map and removes regions whose median value is under the threhsold<br />Value Map: same as Intensity map but threshold is computed on an image where all pixels values are replaced by the median value of each region<br /><pre>Secondary Map: This method is designed to robustly threshold foreground objects and regions located between foreground objects. Does only work in case forground objects are of comparable intensities<br />1) Ostus's method is applied on on the image where pixels values are replaced by median value of eache region. <br />2) Ostus's method is applied on on the image where pixels values are replaced by median value of secondary map of each region. Typically using Hessian Max this allows to select regions in between two foreground objects or directly connected to foreground <br />3) A map with regions that are under threshold in 1) and over threshold in 2) ie regions that are not foreground but are either in between two objects or connected to one objects. The histogram of this map is computed and threshold is set in the middle of the largest histogram zone without objects</pre> </html>");
    protected PreFilterSequence scondaryThresholdMap = new PreFilterSequence("Secondary Threshold Map").add(new ImageFeature().setFeature(ImageFeature.Feature.HessianMax).setScale(2)).setToolTipText("A map used that allows to selected regions in between two foreground objects or directly connected to a foreground object");
    ConditionalParameter thresholdCond = new ConditionalParameter(thresholdMethod).setDefaultParameters(new Parameter[]{threshold}).setActionParameters("Secondary Map", new Parameter[]{scondaryThresholdMap});
    BooleanParameter darkBackground = new BooleanParameter("Dark Background", true);
    boolean testMode;
    
    // variables
    Image wsMap;
    ImageInteger seedMap;
    Image secondaryThresholdMap;
    Image watershedPriorityMap;
    String toolTip = "<html>Segment region at maximal values of the watershed map; <br />"
            + "1) Partition of the whole image using classical watershed seeded on all regional minima of the watershed map. <br />"
            + "2) Suppression of background regions depending on the selected metohd; <br />"
            + "</html>";
    
    @Override
    public String getToolTipText() {return toolTip;}

    public PreFilterSequence getWSMapSequence() {
        return this.watershedMap;
    }
    
    public Image getWsMap(Image input, ImageMask mask) {
        if (wsMap==null) wsMap = watershedMap.filter(input, mask);
        return wsMap;
    }

    public ImageInteger getSeedMap(Image input,  ImageMask mask) {
        if (seedMap==null) seedMap = Filters.localExtrema(getWsMap(input, mask), null, false, mask, Filters.getNeighborhood(1, 1, getWsMap(input, mask)));
        return seedMap;
    }
    public EdgeDetector setIsDarkBackground(boolean dark) {
        this.darkBackground.setSelected(dark);
        return this;
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
    public Image getSecondaryThresholdMap(Image input,  ImageMask mask) {
        if (scondaryThresholdMap==null) {
            if (!scondaryThresholdMap.isEmpty()) {
                if (scondaryThresholdMap.sameContent(this.watershedMap)) secondaryThresholdMap = getWsMap(input, mask);
                else secondaryThresholdMap = scondaryThresholdMap.filter(input, mask);
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
    public EdgeDetector setThresholder(SimpleThresholder thlder) {
        this.threshold.setPlugin(thlder);
        return this;
    }
    public EdgeDetector setThrehsoldingMethod(int method) {
        this.thresholdMethod.setSelectedIndex(method);
        return this;
    }
    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent.getMask());
    }
    public RegionPopulation partitionImage(Image input, ImageMask mask) {
        WatershedTransform wt = new WatershedTransform(getWsMap(input, mask), mask, Arrays.asList(ImageLabeller.labelImage(getSeedMap(input, mask))), false, null, null);
        wt.setLowConnectivity(false);
        wt.run();
        return wt.getObjectPopulation();
    }
    public RegionPopulation run(Image input, ImageMask mask) {
        RegionPopulation allRegions = partitionImage(input, mask);
        
        /*
        // merge background regions : Do not solve the problem of 2 class of foreground intensities: when around foregrond region of highest intensity, background regions are of intensity comparable to lower foreground regions
        double thldMerge = IJAutoThresholder.runThresholder(input, parent.getMask(), AutoThresholder.Method.Triangle);
        if (testMode) logger.debug("thld: {}", thldMerge);
        SplitAndMerge sm = new SplitAndMerge(input, thldMerge, 1, s->{
            float[] values = new float[s.size()]; int i=0;
            for (Voxel v : s) values[i++] = input.getPixel(v.x, v.y, v.z);
            return ArrayUtil.quantiles(values, 0.5)[0];
        });
        sm.setTestMode(testMode);
        sm.merge(allRegions, 3, 0);
        */
        if (testMode) {
            ImageWindowManagerFactory.showImage(allRegions.getLabelMap().duplicate("Segmented Regions"));
            ImageWindowManagerFactory.showImage(seedMap.setName("Seeds"));
            ImageWindowManagerFactory.showImage(wsMap.setName("Watershed Map"));
        }
        if (this.thresholdMethod.getSelectedIndex()==0) {
            double thld = threshold.instanciatePlugin().runSimpleThresholder(input, mask);
            if (testMode) ImageWindowManagerFactory.showImage(generateRegionValueMap(allRegions, input).setName("Intensity value Map. Threshold: "+thld+" thldMethod: "+this.threshold.getPluginName()));
            allRegions.filter(new RegionPopulation.MedianIntensity(thld, darkBackground.getSelected(), input));
        } else if (this.thresholdMethod.getSelectedIndex()==1) { // thld on value map
            Map<Region, Double>[] values = new Map[1];
            Image valueMap = generateRegionValueMap(allRegions, input, values);
            double thld = threshold.instanciatePlugin().runSimpleThresholder(valueMap , mask);
            if (testMode) ImageWindowManagerFactory.showImage(valueMap.setName("Intensity value Map. Threshold: "+thld));
            if (darkBackground.getSelected()) values[0].entrySet().removeIf(e->e.getValue()>=thld);
            else values[0].entrySet().removeIf(e->e.getValue()<=thld);
            allRegions.getObjects().removeAll(values[0].keySet());
            allRegions.relabel(true);
        } else { // use of secondary map to select border regions and compute thld
            Map<Region, Double>[] values = new Map[1];
            Image valueMap = generateRegionValueMap(allRegions, input, values);
            double thld1 = IJAutoThresholder.runThresholder(valueMap, mask, AutoThresholder.Method.Otsu);
            if (testMode) ImageWindowManagerFactory.showImage(valueMap.duplicate("Primary thld value map. Thld: "+thld1));
            Map<Region, Double>[] values2 = new Map[1];
            Image valueMap2 = generateRegionValueMap(allRegions, getSecondaryThresholdMap(input, mask), values2);
            double thld2 = IJAutoThresholder.runThresholder(valueMap2, mask, AutoThresholder.Method.Otsu);
            // select objects under thld2 | above thld -> foreground, interface ou backgruond. Others are interface or border (majority) and set value to thld on valueMap 
            if (darkBackground.getSelected()) {
                for (Region o : allRegions.getObjects()) {
                    if (values[0].get(o)>=thld1 || values2[0].get(o)<thld2) o.draw(valueMap, thld1);
                }
            } else {
                for (Region o : allRegions.getObjects()) {
                    if (values[0].get(o)<=thld1 || values2[0].get(o)<thld2) o.draw(valueMap, thld1);
                }
            }
            Histogram h = valueMap.getHisto256(mask);
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
            if (darkBackground.getSelected()) values[0].entrySet().removeIf(e->e.getValue()>=thld);
            else values[0].entrySet().removeIf(e->e.getValue()<=thld);
            allRegions.getObjects().removeAll(values[0].keySet());
            allRegions.relabel(true);
        }
        return allRegions;
    }

    public static Image generateRegionValueMap(RegionPopulation pop, Image image) {
        return generateRegionValueMap(pop, image, null);
    }
    public static Image generateRegionValueMap(RegionPopulation pop, Image image, Map<Region, Double>[] values) {
        Function<Region, Double> valueFunction = valueFunction(image);
        //Function<Region, Double> valueFunction = o->BasicMeasurements.getMeanValue(o, image, false);
        Map<Region, Double> objectValues = pop.getObjects().stream().collect(Collectors.toMap(o->o, valueFunction));
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
    protected static Function<Region, Double> valueFunction(Image image) {
        return r->BasicMeasurements.getQuantileValue(r, image, false, 0.5)[0];
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{watershedMap, threshold, thresholdCond};
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    
    
}
