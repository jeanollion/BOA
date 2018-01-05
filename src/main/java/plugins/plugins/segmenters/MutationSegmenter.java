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
import image.ImageMask2D;
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
import java.util.function.Function;
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
import processing.Filters.LocalMax;
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
import processing.neighborhood.EllipsoidalNeighborhood;
import processing.neighborhood.EllipsoidalSubVoxNeighborhood;
import processing.neighborhood.Neighborhood;
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
    NumberParameter thresholdHigh = new NumberParameter("Threshold for Seeds", 2, 2.25);
    //PluginParameter<Thresholder> thresholdLow = new PluginParameter<Thresholder>("Threshold for propagation", Thresholder.class, new ObjectCountThresholder(20), false);
    NumberParameter thresholdLow = new NumberParameter("Threshold for propagation", 2, 1.63);
    NumberParameter intensityThreshold = new NumberParameter("Intensity Threshold for Seeds", 2, 1.8); // 
    boolean planeByPlane = false;
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
    
    /*public ObjectPopulation run(Image input, StructureObjectProcessing parent, double[] scale, int minSpotSize, double thresholdHigh , double thresholdLow, double intensityThreshold, List<Image> intermediateImages) {
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
    }*/
    private static class ProcessingVariables {
        Image input;
        Image[] lap;
        Image smooth;
        boolean lapScaled, smoothScaled;
        double[] ms;
        double smoothScale;
        public void initPV(Image input, ImageMask mask, double smoothScale) {
            this.input=input;
            this.smoothScale=smoothScale;
            //BackgroundFit.debug=debug;
            ms = new double[2];
            //double thld = BackgroundFit.backgroundFitHalf(input, mask, 2, ms);
            //final double thld= Double.POSITIVE_INFINITY;
            //final double t = thld;
            //ms = ImageOperations.getMeanAndSigmaWithOffset(input, mask, v->v<=t);
            //if (ms[2]==0) thld = BackgroundThresholder.runThresholder(input, mask, 3, 3, 2, ms);
            
            double thld = BackgroundThresholder.runThresholder(input, mask, 3, 3, 2, Double.MAX_VALUE, ms);
            
            if (debug) logger.debug("scaling thld: {} mean & sigma: {}", thld, ms); //if (debug) 
        }
        public Image getScaledInput() {
            return ImageOperations.affineOperation2WithOffset(input, null, 1/ms[1], -ms[0]).setName("Scaled Input");
        }
        protected Image getSmoothedMap() {
            if (smooth==null) throw new RuntimeException("Smooth map not initialized");
            if (!smoothScaled) {
                ImageOperations.affineOperation2WithOffset(smooth, smooth, smoothScale/ms[1], -ms[0]);
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
    public ObjectPopulation run(Image input, StructureObjectProcessing parent, double[] scale, int minSpotSize, double thresholdSeeds, double thresholdPropagation, double intensityThreshold, List<Image> intermediateImages) {
        Arrays.sort(scale);
        ImageMask parentMask = parent.getMask().getSizeZ()!=input.getSizeZ() ? new ImageMask2D(parent.getMask()) : parent.getMask();
        this.pv.initPV(input, parentMask, smoothScale.getValue().doubleValue()) ;
        if (pv.smooth==null || pv.lap==null) setMaps(computeMaps(input, input));
        // TODO: test is Use Scale is taken into acount.
        
        Image smooth = pv.getSmoothedMap();
        Image[] lapSPZ = Image.mergeImagesInZ(Arrays.asList(pv.getLaplacianMap())).toArray(new Image[0]); // in case there are several z
        double[] radii = new double[scale.length];
        for (int z = 0; z<radii.length; ++z) radii[z] = Math.max(1, scale[z]); //-0.5
        Neighborhood n = radii.length>1 ? processing.neighborhood.ConicalNeighborhood.generateScaleSpaceNeighborhood(radii, false) : new CylindricalNeighborhood(radii[0], 1, false);
        //Neighborhood n = new CylindricalNeighborhood(1.5, lap.length, false);
        //Neighborhood n = new CylindricalNeighborhood(1.5, 1, false);
        
        // 4D local max
        ImageByte[] seedsSPZ = new ImageByte[lapSPZ.length];
        LocalMax[] lmZ = new LocalMax[lapSPZ.length];
        for (int z = 0; z<lapSPZ.length; ++z) {
            lmZ[z] = new LocalMax();
            lmZ[z].setUp(lapSPZ[z], n);
            seedsSPZ[z] = new ImageByte("", lapSPZ[z]);
        }
        for (int zz = 0; zz<lapSPZ.length; ++zz) {
            final int z = zz;
            lapSPZ[z].getBoundingBox().translateToOrigin().loop((x, y, sp)->{
                float currentValue = lapSPZ[z].getPixel(x, y, sp);
                if (parentMask.insideMask(x, y, z) && smooth.getPixel(x, y, z)>=intensityThreshold && currentValue>=thresholdSeeds) { // check pixel is over thresholds
                    if ( (z==0 || (z>0 && seedsSPZ[z-1].getPixel(x, y, sp)==0)) && lmZ[z].hasValueNoOver(currentValue, x, y, sp)) { // check if 1) was not already checked at previous plane [make it not parallelizable] && if is local max on this z plane
                        boolean lm = true;
                        if (z>0) lm = lmZ[z-1].hasValueNoOver(currentValue, x, y, sp); // check if local max on previous z plane
                        if (lm && z<lapSPZ.length-1) lm = lmZ[z+1].hasValueNoOver(currentValue, x, y, sp); // check if local max on next z plane
                        //logger.debug("candidate seed: x:{}, y:{}, z:{},value: {} local max ? {}, no value sup below:{} , no value sup over:{}", x, y, z, currentValue, lm, z>0?lmZ[z-1].hasValueNoOver(currentValue, x, y, sp):true, z<lapSPZ.length-1?lmZ[z+1].hasValueNoOver(currentValue, x, y, sp):true);
                        if (lm) seedsSPZ[z].setPixel(x, y, sp, 1);
                    }
                }
            });
        }
        
        ImageByte[] seedMaps = arrangeSpAndZPlanes(seedsSPZ, planeByPlane).toArray(new ImageByte[0]);
        Image[] wsMap = arrangeSpAndZPlanes(lapSPZ, planeByPlane).toArray(new Image[0]);
        ObjectPopulation[] pops =  MultiScaleWatershedTransform.watershed(wsMap, parentMask, seedMaps, true, new MultiScaleWatershedTransform.ThresholdPropagationOnWatershedMap(thresholdPropagation), new MultiScaleWatershedTransform.SizeFusionCriterion(minSpotSize));
        //ObjectPopulation pop =  watershed(lap, parent.getMask(), seedPop.getObjects(), true, new ThresholdPropagationOnWatershedMap(thresholdPropagation), new SizeFusionCriterion(minSpotSize), false);
        SubPixelLocalizator.debug=debug;
        for (int i = 0; i<pops.length; ++i) { // TODO voir si en 3D pas mieux avec gaussian
            int z = i/scale.length;
            SubPixelLocalizator.setSubPixelCenter(wsMap[i], pops[i].getObjects(), true); // lap -> better in case of close objects
            for (Object3D o : pops[i].getObjects()) { // quality criterion : sqrt (smooth * lap)
                if (o.getQuality()==0) { // localizator didnt work
                    double[] center = o.getMassCenter(wsMap[i], false);
                    if (center[0]>wsMap[i].getSizeX()-1) center[0] = wsMap[i].getSizeX()-1;
                    if (center[1]>wsMap[i].getSizeY()-1) center[1] = wsMap[i].getSizeY()-1;
                    o.setCenter(center);
                    //o.setQuality(lap.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0));
                }
                double zz = o.getCenter().length>2?o.getCenter()[2]:z;
                o.setQuality(Math.sqrt(wsMap[i].getPixel(o.getCenter()[0], o.getCenter()[1], zz) * smooth.getPixel(o.getCenter()[0], o.getCenter()[1], zz)));
                if (planeByPlane && lapSPZ.length>1) { // keep track of z coordinate
                    o.setCenter(new double[]{o.getCenter()[0], o.getCenter()[1], 0}); // adding z dimention
                    o.translate(0, 0, z);
                }  
            }
        }
        ObjectPopulation pop = MultiScaleWatershedTransform.combine(pops, input);
        if (debug) {
            logger.debug("Parent: {}: Q: {}", parent, Utils.toStringList(pop.getObjects(), o->""+o.getQuality()));
            logger.debug("Parent: {}: C: {}", parent ,Utils.toStringList(pop.getObjects(), o->""+Utils.toStringArray(o.getCenter())));
        }
        pop.filter(new ObjectPopulation.RemoveFlatObjects(false));
        pop.filter(new ObjectPopulation.Size().setMin(minSpotSize));
        if (testParam!=null) {
            ImageWindowManagerFactory.showImage(TypeConverter.toByteMask(parent.getMask(), null, 1));
            ImageWindowManagerFactory.showImage(input);
            ImageWindowManagerFactory.showImage(pv.getScaledInput());
            boolean showLap = testParam.equals(this.scale.getName()) || testParam.equals(this.thresholdHigh.getName()) || testParam.equals(this.thresholdLow.getName());
            if (showLap) {
                ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("LaplacianMap scale space ", new Image[][]{lapSPZ});
                ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("LaplacianMap scale space ", new Image[][]{seedsSPZ});
            }
            if (testParam.equals(this.scale.getName()) || testParam.equals(this.intensityThreshold.getName())) ImageWindowManagerFactory.showImage(pv.getSmoothedMap().setName("IntensityMap"));
            logger.debug("Quality: {}", Utils.toStringList(pop.getObjects(), o->o.getQuality()+""));
            ImageWindowManagerFactory.showImage(pop.getLabelMap().setName("segmented image"));
            
            
        }
        if (intermediateImages!=null) {
            if (planeByPlane) {
                for (int z = 0; z<seedsSPZ.length; ++z) {
                    intermediateImages.add(seedsSPZ[z].setName("seed scale space z="+z));
                    intermediateImages.add(lapSPZ[z].setName("Laplacian scale space z="+z));
                }
            } else {
                for (int sp = 0; sp<wsMap.length; ++sp) {
                    intermediateImages.add(seedMaps[sp].setName("seed sp"+sp));
                    intermediateImages.add(wsMap[sp].setName("Laplacian sp"+sp));
                }
            }
            intermediateImages.add(pv.getScaledInput());
            intermediateImages.add(smooth.setName("gaussian"));
            
        }
        return pop;
    }
    
    private static <T extends Image> List<T> arrangeSpAndZPlanes(T[] spZ, boolean ZbyZ) {
        if (ZbyZ) {
            List<T> res = new ArrayList<>(spZ.length * spZ[0].getSizeZ());
            for (int z = 0; z<spZ.length; ++z) {
                for (int sp = 0; sp<spZ[z].getSizeZ(); ++sp) {
                    res.add(spZ[z].getZPlane(sp));
                }
            }
            return res;
        } else return Image.mergeImagesInZ(Arrays.asList(spZ));
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
    public void setQuality(List<Object3D> objects, BoundingBox offset, Image input, ImageMask parentMask) {
        if (objects.isEmpty()) return;
        if (offset==null) offset = new BoundingBox();
        this.pv.initPV(input, parentMask, smoothScale.getValue().doubleValue()) ;
        for (Object3D o : objects) {
            double[] center = o.getCenter();
            if (center==null) throw new IllegalArgumentException("No center for object: "+o);
            double smooth = pv.getSmoothedMap().getPixel(center[0]-offset.getxMin(), center[1]-offset.getyMin(), center.length>2?center[2]-offset.getzMin():0);
            List<Double> lapValues = new ArrayList<>(pv.getLaplacianMap().length);
            for (Image lap : pv.getLaplacianMap()) lapValues.add((double)lap.getPixel(center[0]-offset.getxMin(), center[1]-offset.getyMin(), center.length>2?center[2]-offset.getzMin():0));
            o.setQuality(Math.sqrt(smooth * Collections.max(lapValues)));
            //logger.debug("object: {} smooth: {} lap: {} q: {}", o.getCenter(), smooth, Collections.max(lapValues), o.getQuality());
        }
    }
    @Override
    public Image[] computeMaps(Image rawSource, Image filteredSource) {
        double[] scale = getScale();
        double smoothScale = this.smoothScale.getValue().doubleValue();
        Image[] maps = new Image[scale.length+1];
        Function<Image, Image> gaussF = f->ImageFeatures.gaussianSmooth(f, smoothScale, false).setName("gaussian: "+smoothScale);
        maps[0] = planeByPlane ? ImageOperations.applyPlaneByPlane(filteredSource, gaussF) : gaussF.apply(filteredSource); //
        for (int i = 0; i<scale.length; ++i) {
            final int ii = i;
            Function<Image, Image> lapF = f->ImageFeatures.getLaplacian(f, scale[ii], true, false).setName("laplacian: "+scale[ii]);
            maps[i+1] = ImageOperations.applyPlaneByPlane(filteredSource, lapF); //  : lapF.apply(filteredSource); if too few images laplacian is not relevent in 3D. TODO: put a condition on slice number, and check laplacian values
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
        Image smooth = ImageFeatures.gaussianSmoothScaleIndep(scaledInput, smoothScale.getValue().doubleValue(), smoothScale.getValue().doubleValue(), false).setName("gaussian: "+scale.getArrayDouble()[0]); // scale indep ? 
        ObjectPopulation pop =  watershed(lap, parent.getMask(), seedObjects, true, new ThresholdPropagationOnWatershedMap(this.thresholdLow.getValue().doubleValue()), new SizeFusionCriterion(minSpotSize.getValue().intValue()), false);
        SubPixelLocalizator.setSubPixelCenter(smooth, pop.getObjects(), true);
        for (Object3D o : pop.getObjects()) { // quality criterion : smooth * lap
            o.setQuality(Math.sqrt(smooth.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0) * lap.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0)));
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
