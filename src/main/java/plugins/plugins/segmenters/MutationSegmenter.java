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
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.ArrayNumberParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.ParameterUtils;
import configuration.parameters.PluginParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.ObjectPopulation.MeanIntensity;
import dataStructure.objects.ObjectPopulation.Or;
import dataStructure.objects.ObjectPopulation.Overlap;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageOperations;
import image.ImageShort;
import image.ObjectFactory;
import image.TypeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import jj2000.j2k.util.ArrayUtil;
import measurement.BasicMeasurements;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import plugins.ParameterSetup;
import static plugins.Plugin.logger;
import plugins.Segmenter;
import plugins.Thresholder;
import plugins.UseMaps;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.thresholders.BackgroundFit;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.thresholders.ObjectCountThresholder;
import processing.Filters;
import processing.gaussianFit.GaussianFit;
import processing.IJFFTBandPass;
import processing.ImageFeatures;
import processing.LoG;
import processing.MultiScaleWatershedTransform;
import processing.SubPixelLocalizator;
import processing.WatershedTransform;
import processing.WatershedTransform.MonotonalPropagation;
import processing.WatershedTransform.MultiplePropagationCriteria;
import processing.WatershedTransform.SizeFusionCriterion;
import processing.WatershedTransform.ThresholdPropagation;
import processing.WatershedTransform.ThresholdPropagationOnWatershedMap;
import static processing.WatershedTransform.watershed;
import processing.neighborhood.ConditionalNeighborhoodZ;
import processing.neighborhood.CylindricalNeighborhood;
import processing.neighborhood.EllipsoidalSubVoxNeighborhood;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class MutationSegmenter implements Segmenter, UseMaps, ManualSegmenter, ObjectSplitter, ParameterSetup {
    public List<Image> intermediateImages;
    public static boolean debug = false;
    public static boolean displayImages = false;
    ArrayNumberParameter scale = new ArrayNumberParameter("Scale", 0, new BoundedNumberParameter("Scale", 1, 2, 1, 5)).setSorted(true);
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 2, 1, 5);
    NumberParameter minSpotSize = new BoundedNumberParameter("Min. Spot Size (Voxels)", 0, 5, 1, null);
    NumberParameter thresholdHigh = new NumberParameter("Threshold for Seeds", 2, 0.6);
    //PluginParameter<Thresholder> thresholdLow = new PluginParameter<Thresholder>("Threshold for propagation", Thresholder.class, new ObjectCountThresholder(20), false);
    NumberParameter thresholdLow = new NumberParameter("Threshold for propagation", 2, 0.5);
    NumberParameter intensityThreshold = new NumberParameter("Intensity Threshold for Seeds", 2, 0.35);
    Parameter[] parameters = new Parameter[]{scale, smoothScale, minSpotSize, thresholdHigh,  thresholdLow, intensityThreshold};
    ProcessingVariables pv = new ProcessingVariables();
    
    public MutationSegmenter() {}
    
    public MutationSegmenter(double thresholdSeeds, double thresholdPropagation, double thresholdIntensity) {
        this.intensityThreshold.setValue(thresholdIntensity);
        this.thresholdHigh.setValue(thresholdSeeds);
        this.thresholdLow.setValue(thresholdPropagation);
    }
    
    public MutationSegmenter setThresholdSeeds(double threshold) {
        this.thresholdHigh.setValue(threshold);
        return this;
    }
    
    public MutationSegmenter setThresholdPropagation(double threshold) {
        //this.thresholdLow.setPlugin(new ConstantValue(threshold));
        this.thresholdLow.setValue(threshold);
        return this;
    }
    
    public MutationSegmenter setIntensityThreshold(double threshold) {
        this.intensityThreshold.setValue(threshold);
        return this;
    }
    
    public MutationSegmenter setScale(double... scale) {
        this.scale.setValue(scale);
        return this;
    }
    public double[] getScale() {
        double[] res = scale.getArrayDouble();
        List<Double> res2 = Utils.toList(res); 
        Utils.removeDuplicates(res2, true);
        if (res2.size()<res.length) return Utils.toDoubleArray(res2, false);
        else return res;
    }
    // ParameterSetup implementation
    @Override
    public boolean canBeTested(String p) {
        return new ArrayList<String>(){{add(scale.getName());add(intensityThreshold.getName()); add(thresholdHigh.getName()); add(thresholdLow.getName());}}.contains(p);
    }
    String testParam;
    @Override 
    public void setTestParameter(String p) {
        testParam = p;
    }
    
    @Override
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent, getScale(), minSpotSize.getValue().intValue(), thresholdHigh.getValue().doubleValue(), thresholdLow.getValue().doubleValue(), intensityThreshold.getValue().doubleValue(), intermediateImages);
    }
    
    public ObjectPopulation run(Image input, StructureObjectProcessing parent, double[] scale, int minSpotSize, double thresholdHigh , double thresholdLow, double intensityThreshold, List<Image> intermediateImages) {
        if (input.getSizeZ()>1) {
            // tester sur average, max, ou plan par plan
            ArrayList<Image> planes = input.splitZPlanes();
            ArrayList<ObjectPopulation> populations = new ArrayList<ObjectPopulation>(planes.size());
            for (Image plane : planes) {
                ObjectPopulation obj = runPlane(plane, parent, scale, minSpotSize, thresholdHigh, thresholdLow, intensityThreshold, intermediateImages);
                //if (true) return obj;
                if (obj!=null && !obj.getObjects().isEmpty()) populations.add(obj);
            }
            if (populations.isEmpty()) return new ObjectPopulation(new ArrayList<Object3D>(0), planes.get(0));
            // combine: 
            ObjectPopulation pop = populations.remove(populations.size()-1);
            pop.combine(populations);
            return pop;
        } else return runPlane(input, parent, scale, minSpotSize, thresholdHigh, thresholdLow, intensityThreshold, intermediateImages);
    }
    private static class ProcessingVariables {
        Image input;
        Image[] lap;
        Image smooth;
        boolean lapScaled, smoothScaled;
        double[] ms;
        public void initPV(Image input, ImageMask mask) {
            this.input=input;
            //BackgroundFit.debug=debug;
            ms = new double[2];
            //double thld = BackgroundFit.backgroundFitHalf(input, mask, 2, ms);
            //final double thld= Double.POSITIVE_INFINITY;
            //final double t = thld;
            //ms = ImageOperations.getMeanAndSigmaWithOffset(input, mask, v->v<=t);
            //if (ms[2]==0) thld = BackgroundThresholder.runThresholder(input, mask, 3, 3, 2, ms);
            
            double thld = BackgroundThresholder.runThresholder(input, mask, 3, 3, 2, ms);
            
            if (debug) logger.debug("scaling thld: {} mean & sigma: {}", thld, ms); //if (debug) 
        }
        public Image getScaledInput() {
            return ImageOperations.affineOperation2WithOffset(input, null, 1/ms[1], -ms[0]).setName("Scaled Input");
        }
        protected Image getSmoothedMap() {
            if (smooth==null) throw new RuntimeException("Smooth map not initialized");
            if (!smoothScaled) {
                ImageOperations.affineOperation2WithOffset(smooth, smooth, 1/ms[1], -ms[0]);
                smoothScaled=true;
            }
            return smooth;
        }
        
        protected Image[] getLaplacianMap() {
            if (lap==null) throw new RuntimeException("Laplacian map not initialized");
            if (!lapScaled) {
                for (int i = 0; i<lap.length; ++i) ImageOperations.affineOperation2WithOffset(lap[i], lap[i], 1/ms[1], 0); // no additive coefficient
                lapScaled=true;
            }
            return lap;
        }
    }
    public ObjectPopulation runPlane(Image input, StructureObjectProcessing parent, double[] scale, int minSpotSize, double thresholdSeeds, double thresholdPropagation, double intensityThreshold, List<Image> intermediateImages) {
        if (input.getSizeZ()>1) throw new RuntimeException("MutationSegmenter: should be run on a 2D image");
        //Arrays.sort(scale);
        this.pv.initPV(input, parent.getMask()) ;
        if (pv.smooth==null || pv.lap==null) setMaps(computeMaps(input, input));
        // TODO: test is Use Scale is taken into acount.
        //Image smooth = ImageFeatures.gaussianSmooth(sub, scale, scale, false);
        //Image lap = ImageFeatures.getLaplacian(sub, scale, true, false).setName("laplacian: "+scale);
        
        Image smooth = pv.getSmoothedMap();
        Image[] lap = pv.getLaplacianMap();
        Image lapSP = Image.mergeZPlanes(Arrays.asList(lap));
        
        ImageByte seedsSP = Filters.localExtrema(lapSP, null, true, thresholdSeeds, new CylindricalNeighborhood(1.5, 1, false)).setName("seedsSP"); // TODO: also exclude big sizes ?
        //ImageByte seeds = Filters.localExtrema(lap, null, true, thresholdSeeds, Filters.getNeighborhood(scale, scale, input));
        for (int z = 0; z<seedsSP.getSizeZ(); ++z) { // filter for smooth value
            for (int xy = 0; xy<seedsSP.getSizeXY(); ++xy) {
                if (seedsSP.insideMask(xy, z) && smooth.getPixel(xy, 0)<intensityThreshold) seedsSP.setPixel(xy, z, 0);
            }
        }
        if (intermediateImages!=null) {
            intermediateImages.add(lapSP.setName("lap scale sapce"));
            intermediateImages.add(seedsSP.setName("seed scale sapce"));
            intermediateImages.add(pv.getScaledInput());
        }
        ImageByte[] seedMaps = seedsSP.splitZPlanes().toArray(new ImageByte[0]);
        ObjectPopulation[] pops =  MultiScaleWatershedTransform.watershed(lap, parent.getMask(), seedMaps, true, new MultiScaleWatershedTransform.ThresholdPropagationOnWatershedMap(thresholdPropagation), new MultiScaleWatershedTransform.SizeFusionCriterion(minSpotSize));
        //ObjectPopulation seedPop = new ObjectPopulation(seeds, false);
        //ObjectPopulation pop =  watershed(lap, parent.getMask(), seedPop.getObjects(), true, new ThresholdPropagationOnWatershedMap(thresholdPropagation), new SizeFusionCriterion(minSpotSize), false);
        SubPixelLocalizator.debug=debug;
        for (int i = 0; i<lap.length; ++i) {
            SubPixelLocalizator.setSubPixelCenter(lap[i], pops[i].getObjects(), true); // lap -> better in case of close objects
            for (Object3D o : pops[i].getObjects()) { // quality criterion : smooth * lap
                if (o.getQuality()==0) { // localizator didnt work
                    double[] center = o.getMassCenter(lap[i], false);
                    if (center[0]>lap[i].getSizeX()-1) center[0] = lap[i].getSizeX()-1;
                    if (center[1]>lap[i].getSizeY()-1) center[1] = lap[i].getSizeY()-1;
                    o.setCenter(center);
                    //o.setQuality(lap.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0));
                }
                o.setQuality(Math.sqrt(lap[i].getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0) * smooth.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0)));
            }
        }
        ObjectPopulation pop = MultiScaleWatershedTransform.combine(pops);
        if (debug) {
            logger.debug("Parent: {}: Q: {}", parent, Utils.toStringList(pop.getObjects(), o->""+o.getQuality()));
            logger.debug("Parent: {}: C: {}", parent ,Utils.toStringList(pop.getObjects(), o->""+Utils.toStringArray(o.getCenter())));
        }
        pop.filter(new ObjectPopulation.RemoveFlatObjects(input));
        pop.filter(new ObjectPopulation.Size().setMin(minSpotSize));
        if (testParam!=null) {
            ImageWindowManagerFactory.showImage(TypeConverter.toByteMask(parent.getMask(), null, 1));
            ImageWindowManagerFactory.showImage(input);
            ImageWindowManagerFactory.showImage(pv.getScaledInput());
            boolean showLap = testParam.equals(this.scale.getName()) || testParam.equals(this.thresholdHigh.getName()) || testParam.equals(this.thresholdLow.getName());
            if (showLap) {
                ImageWindowManagerFactory.showImage(lapSP.setName("LaplacianMap scale space ("+thresholdHigh.getName()+";"+thresholdLow.getName()+")"));
                ImageWindowManagerFactory.showImage(seedsSP.setName("Seeds scale space"));
            }
            if (testParam.equals(this.scale.getName()) || testParam.equals(this.intensityThreshold.getName())) ImageWindowManagerFactory.showImage(pv.getSmoothedMap().setName("IntensityMap"));
            logger.debug("Quality: {}", Utils.toStringList(pop.getObjects(), o->o.getQuality()+""));
            ImageWindowManagerFactory.showImage(pop.getLabelMap().setName("segmented image"));
        }
        
        return pop;
    }
    
    public void printSubLoc(String name, Image locMap, Image smooth, Image lap, ObjectPopulation pop, BoundingBox globBound) {
        BoundingBox b = locMap.getBoundingBox().translate(globBound.reverseOffset());
        List<Object3D> objects = pop.getObjects();
        
        for(Object3D o : objects) o.setCenter(o.getMassCenter(locMap, false));
        pop.translate(b, false);
        logger.debug("mass center: centers: {}", Utils.toStringList(objects, o -> Utils.toStringArray(o.getCenter())+" value: "+o.getQuality()));
        pop.translate(b.duplicate().reverseOffset(), false);
        
        for(Object3D o : objects) o.setCenter(o.getGeomCenter(false));
        pop.translate(b, false);
        logger.debug("geom center {}: centers: {}", name, Utils.toStringList(objects, o -> Utils.toStringArray(o.getCenter())+" value: "+o.getQuality()));
        pop.translate(b.duplicate().reverseOffset(), false);
        
        
        SubPixelLocalizator.setSubPixelCenter(locMap, objects, true);
        pop.translate(b, false);
        logger.debug("locMap: {}, centers: {}", name, Utils.toStringList(objects, o -> Utils.toStringArray(o.getCenter())+" value: "+o.getQuality()));
        pop.translate(b.duplicate().reverseOffset(), false);
        logger.debug("smooth values: {}", Utils.toStringList(objects, o->""+smooth.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0)) );
        logger.debug("lap values: {}", Utils.toStringList(objects, o->""+lap.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0)) );
        
        
        
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    @Override
    public Image[] computeMaps(Image rawSource, Image filteredSource) {
        double[] scale = getScale();
        double smoothScale = this.smoothScale.getValue().doubleValue();
        Image[] maps = new Image[scale.length+1];
        maps[0] = ImageFeatures.gaussianSmooth(filteredSource, smoothScale, smoothScale, false).setName("gaussian: "+smoothScale);
        for (int i = 0; i<scale.length; ++i) {
            maps[i+1] = ImageFeatures.getLaplacian(filteredSource, scale[i], true, false).setName("laplacian: "+scale[i]);
        }
        return maps;
    }
    
    @Override
    public void setMaps(Image[] maps) {
        if (maps==null) return;
        double[] scale = getScale();
        if (maps.length!=scale.length+1) throw new IllegalArgumentException("Maps should be of length "+scale.length+1+" and contain smooth & laplacian of gaussian for each scale");
        this.pv.smooth=maps[0];
        this.pv.lap=new Image[scale.length];
        for (int i = 0; i<scale.length; ++i) pv.lap[i] = maps[i+1];
    }
    

    protected boolean verboseManualSeg;
    public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }
    
    @Override
    public ObjectPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        List<Object3D> seedObjects = ObjectFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getScaleXY(), input.getScaleZ());
        final double thld = BackgroundFit.backgroundFitHalf(input, parent.getMask(), 2, null);
        double[] ms = ImageOperations.getMeanAndSigmaWithOffset(input, parent.getMask(), v->v<=thld);
        if (ms[2]==0) ms = ImageOperations.getMeanAndSigmaWithOffset(input, parent.getMask(), v -> true);
        if (verboseManualSeg) logger.debug("thld: {} mean & sigma: {}", thld, ms);
        Image scaledInput = ImageOperations.affineOperation2WithOffset(input, null, 1/ms[1], -ms[0]);
        Image lap = ImageFeatures.getLaplacian(scaledInput, scale.getArrayDouble()[0], true, false).setName("laplacian: "+scale);
        Image smooth = ImageFeatures.gaussianSmooth(scaledInput, scale.getArrayDouble()[0], scale.getArrayDouble()[0], false).setName("gaussian: "+scale.getArrayDouble()[0]);
        ObjectPopulation pop =  watershed(lap, parent.getMask(), seedObjects, true, new ThresholdPropagationOnWatershedMap(this.thresholdLow.getValue().doubleValue()), new SizeFusionCriterion(minSpotSize.getValue().intValue()), false);
        SubPixelLocalizator.setSubPixelCenter(smooth, pop.getObjects(), true);
        for (Object3D o : pop.getObjects()) { // quality criterion : smooth * lap
            o.setQuality(Math.sqrt(o.getQuality() * lap.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0)));
        }
        
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixel(seed[0], seed[1], seed[2], 1);
            ImageWindowManagerFactory.showImage(seedMap);
            ImageWindowManagerFactory.showImage(lap.setName("Laplacian (watershedMap). Scale: "+scale.getArrayDouble()[0]));
            ImageWindowManagerFactory.showImage(smooth.setName("Smmothed Scale: "+scale.getArrayDouble()[0]));
            ImageWindowManagerFactory.showImage(pop.getLabelMap().setName("segmented from: "+input.getName()));
        }
        return pop;
    }

    @Override
    public ObjectPopulation splitObject(Image input, Object3D object) {
        ImageFloat wsMap = ImageFeatures.getLaplacian(input, 1.5, false, false);
        return WatershedObjectSplitter.splitInTwo(wsMap, object.getMask(), true, true, manualSplitVerbose);
    }

    boolean manualSplitVerbose;
    @Override
    public void setSplitVerboseMode(boolean verbose) {
        manualSplitVerbose=verbose;
    }
}
