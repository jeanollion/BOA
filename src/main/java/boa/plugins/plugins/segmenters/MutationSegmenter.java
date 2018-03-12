/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.segmenters;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.ArrayNumberParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.ImageMask2D;
import boa.image.processing.ImageOperations;
import boa.image.ImageShort;
import boa.image.processing.RegionFactory;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import jj2000.j2k.util.ArrayUtil;
import boa.measurement.BasicMeasurements;
import boa.plugins.ManualSegmenter;
import boa.plugins.ObjectSplitter;
import boa.plugins.ParameterSetup;
import static boa.plugins.Plugin.logger;
import boa.plugins.Segmenter;
import boa.plugins.UseMaps;
import boa.plugins.plugins.manual_segmentation.WatershedObjectSplitter;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.image.processing.Filters.LocalMax;
import boa.image.processing.ImageFeatures;
import boa.image.processing.MultiScaleWatershedTransform;
import boa.image.processing.SubPixelLocalizator;
import boa.image.processing.WatershedTransform.SizeFusionCriterion;
import boa.image.processing.WatershedTransform.ThresholdPropagationOnWatershedMap;
import static boa.image.processing.WatershedTransform.watershed;
import boa.image.processing.neighborhood.CylindricalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.plugins.ToolTip;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class MutationSegmenter implements Segmenter, UseMaps, ManualSegmenter, ObjectSplitter, ParameterSetup, ToolTip {
    public List<Image> intermediateImages;
    public static boolean debug = false;
    public static boolean displayImages = false;
    ArrayNumberParameter scale = new ArrayNumberParameter("Scale", 0, new BoundedNumberParameter("Scale", 1, 2, 1, 5)).setSorted(true);
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 2, 1, 5).setToolTipText("Scale (in pixels) for gaussian smooth");
    NumberParameter minSpotSize = new BoundedNumberParameter("Min. Spot Size (Voxels)", 0, 5, 1, null).setToolTipText("In pixels: spots under this size will be removed");
    NumberParameter thresholdHigh = new NumberParameter("Threshold for Seeds", 2, 2.25).setToolTipText("Higher value will increase false negative and decrease false positives.<br /> Laplacian Threshold for seed selection");
    NumberParameter thresholdLow = new NumberParameter("Threshold for propagation", 2, 1.63).setToolTipText("Lower value will yield in larger spots.<br /> Laplacian Threshold for watershed propagation: propagation stops at this value.");
    NumberParameter intensityThreshold = new NumberParameter("Intensity Threshold for Seeds", 2, 1.8).setToolTipText("Higher value will increase false negative and decrease false positives.<br /> Laplacian Threshold for seed selection"); 
    boolean planeByPlane = false;
    Parameter[] parameters = new Parameter[]{scale, smoothScale, minSpotSize, thresholdHigh,  thresholdLow, intensityThreshold};
    ProcessingVariables pv = new ProcessingVariables();
    protected String toolTip = "<b>Spot Detection</b>. <br /> "
            + "<ul><li>Input image is scaled by removing the mean value and dividing by the standard-deviation value of the background signal within the segmentation parent</li>"
            + "<li>Spots are detected using a seeded watershed algorithm in the laplacian transform.</li> "
            + "<li>Seeds are set on regional maxima of the laplacian transform, within the mask of the segmentation parent, with laplacian value superior to <em>Threshold for Seeds</em> and gaussian value superior to <em>Intensity Threshold for Seeds</em></li>"
            + "<li>If several scales are provided, the laplacian scale space will be computed (3D for 2D input, and 4D for 3D input) and the seeds will be 3D/4D local extrema in the scale space in order to determine at the same time their scale and spatial localization</li>"
            + "<li>Watershed propagation is done within the segmentation parent mask until laplacian values reach <em>Threshold for propagation</em></li>"
            + "<li>A quality parameter in computed as √(laplacian x gaussian) at the center of the spot</li><ul>";
    
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
    /**
     * See {@link #run(boa.image.Image, boa.data_structure.StructureObjectProcessing, double[], int, double, double, double, java.util.List) }
     * @param input
     * @param structureIdx
     * @param parent
     * @return 
     */
    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent, getScale(), minSpotSize.getValue().intValue(), thresholdHigh.getValue().doubleValue(), thresholdLow.getValue().doubleValue(), intensityThreshold.getValue().doubleValue(), intermediateImages);
    }

    /*public RegionPopulation run(Image input, StructureObjectProcessing parent, double[] scale, int minSpotSize, double thresholdHigh , double thresholdLow, double intensityThreshold, List<Image> intermediateImages) {
        if (input.getSizeZ()>1) {
            // tester sur average, max, ou plan par plan
            ArrayList<Image> planes = input.splitZPlanes();
            ArrayList<ObjectPopulation> populations = new ArrayList<ObjectPopulation>(planes.size());
            for (Image plane : planes) {
                RegionPopulation obj = runPlane(plane, parent, scale, minSpotSize, thresholdHigh, thresholdLow, intensityThreshold, intermediateImages);
                //if (true) return obj;
                if (obj!=null && !obj.getObjects().isEmpty()) populations.add(obj);
            }
            if (populations.isEmpty()) return new RegionPopulation(new ArrayList<Object3D>(0), planes.get(0));
            // combine: 
            RegionPopulation pop = populations.remove(populations.size()-1);
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
                if (!smooth.sameDimensions(input)) smooth = smooth.cropWithOffset(input.getBoundingBox()); // map was computed on parent that differs from segmentation parent
                ImageOperations.affineOperation2WithOffset(smooth, smooth, smoothScale/ms[1], -ms[0]);
                smoothScaled=true;
            }
            return smooth;
        }
        
        protected Image[] getLaplacianMap() {
            if (lap==null) throw new RuntimeException("Laplacian map not initialized");
            if (!lapScaled) {
                for (int i = 0; i<lap.length; ++i) {
                    if (!lap[i].sameDimensions(input)) lap[i] = lap[i].cropWithOffset(input.getBoundingBox()); // map was computed on parent that differs from segmentation parent
                    ImageOperations.affineOperation2WithOffset(lap[i], lap[i], 1/ms[1], 0);
                } // no additive coefficient
                lapScaled=true;
            }
            return lap;
        }
    }
    /**
     * Spots are detected using a seeded watershed algorithm in the laplacian transform
     * Input image is scaled by removing the mean value and dividing by the standard-deviation value of the background within the segmentation parent
     * Seeds are set on regional maxima of the laplacian transform, within the mask of {@param parent}, with laplacian value superior to {@param thresholdSeeds} and gaussian value superior to {@param intensityThreshold}
     * If several scales are provided, the laplacian scale space will be computed (3D for 2D input, and 4D for 3D input) and the seeds will be 3D/4D local extrema in the scale space in order to determine at the same time their scale and spatial localization
     * Watershed propagation is done within the mask of {@param parent} until laplacian values reach {@param thresholdPropagation}
     * A quality parameter in computed as √(laplacian x gaussian) at the center of the spot
     * @param input pre-diltered image from wich spots will be detected
     * @param parent segmentation parent
     * @param scale scale for laplacian filtering, corresponds to size of the objects to be detected, if several, objects will be detected in the scale space
     * @param minSpotSize under this size spots will be erased
     * @param thresholdSeeds minimal laplacian value to segment a spot
     * @param thresholdPropagation laplacian value at the border of spots
     * @param intensityThreshold minimal gaussian value to semgent a spot
     * @param intermediateImages for testing purpose
     * @return segmented spots
     */
    public RegionPopulation run(Image input, StructureObjectProcessing parent, double[] scale, int minSpotSize, double thresholdSeeds, double thresholdPropagation, double intensityThreshold, List<Image> intermediateImages) {
        Arrays.sort(scale);
        ImageMask parentMask = parent.getMask().getSizeZ()!=input.getSizeZ() ? new ImageMask2D(parent.getMask()) : parent.getMask();
        this.pv.initPV(input, parentMask, smoothScale.getValue().doubleValue()) ;
        if (pv.smooth==null || pv.lap==null) setMaps(computeMaps(input, input));
        
        Image smooth = pv.getSmoothedMap();
        Image[] lapSPZ = ((List<Image>)Image.mergeImagesInZ(Arrays.asList(pv.getLaplacianMap()))).toArray(new Image[0]); // in case there are several z
        double[] radii = new double[scale.length];
        for (int z = 0; z<radii.length; ++z) radii[z] = Math.max(1, scale[z]); //-0.5
        Neighborhood n = radii.length>1 ? boa.image.processing.neighborhood.ConicalNeighborhood.generateScaleSpaceNeighborhood(radii, false) : new CylindricalNeighborhood(radii[0], 1, false);
        //Neighborhood n = new CylindricalNeighborhood(1.5, lap.length, false);
        //Neighborhood n = new CylindricalNeighborhood(1.5, 1, false);
        
        // 4D local max
        ImageByte[] seedsSPZ = new ImageByte[lapSPZ.length];
        LocalMax[] lmZ = new LocalMax[lapSPZ.length];
        for (int z = 0; z<lapSPZ.length; ++z) {
            lmZ[z] = new LocalMax(new ImageMask2D(parent.getMask(), parent.getMask().getSizeZ()!=input.getSizeZ()?0:z));
            lmZ[z].setUp(lapSPZ[z], n);
            seedsSPZ[z] = new ImageByte("", lapSPZ[z]);
        }
        for (int zz = 0; zz<lapSPZ.length; ++zz) {
            final int z = zz;
            lapSPZ[z].getBoundingBox().translateToOrigin().loop((x, y, sp)->{
                float currentValue = lapSPZ[z].getPixel(x, y, sp);
                if (parentMask.insideMask(x, y, z) && smooth.getPixel(x, y, z)>=intensityThreshold && currentValue>=thresholdSeeds) { // check pixel is over thresholds
                    if ( (z==0 || (z>0 && seedsSPZ[z-1].getPixel(x, y, sp)==0)) && lmZ[z].hasNoValueOver(currentValue, x, y, sp)) { // check if 1) was not already checked at previous plane [make it not parallelizable] && if is local max on this z plane
                        boolean lm = true;
                        if (z>0) lm = lmZ[z-1].hasNoValueOver(currentValue, x, y, sp); // check if local max on previous z plane
                        if (lm && z<lapSPZ.length-1) lm = lmZ[z+1].hasNoValueOver(currentValue, x, y, sp); // check if local max on next z plane
                        //logger.debug("candidate seed: x:{}, y:{}, z:{},value: {} local max ? {}, no value sup below:{} , no value sup over:{}", x, y, z, currentValue, lm, z>0?lmZ[z-1].hasNoValueOver(currentValue, x, y, sp):true, z<lapSPZ.length-1?lmZ[z+1].hasNoValueOver(currentValue, x, y, sp):true);
                        if (lm) seedsSPZ[z].setPixel(x, y, sp, 1);
                    }
                }
            });
        }
        
        ImageByte[] seedMaps = arrangeSpAndZPlanes(seedsSPZ, planeByPlane).toArray(new ImageByte[0]);
        Image[] wsMap = ((List<Image>)arrangeSpAndZPlanes(lapSPZ, planeByPlane)).toArray(new Image[0]);
        RegionPopulation[] pops =  MultiScaleWatershedTransform.watershed(wsMap, parentMask, seedMaps, true, new MultiScaleWatershedTransform.ThresholdPropagationOnWatershedMap(thresholdPropagation), new MultiScaleWatershedTransform.SizeFusionCriterion(minSpotSize));
        //ObjectPopulation pop =  watershed(lap, parent.getMask(), seedPop.getObjects(), true, new ThresholdPropagationOnWatershedMap(thresholdPropagation), new SizeFusionCriterion(minSpotSize), false);
        SubPixelLocalizator.debug=debug;
        for (int i = 0; i<pops.length; ++i) { // TODO voir si en 3D pas mieux avec gaussian
            int z = i/scale.length;
            setCenterAndQuality(wsMap[i], smooth, pops[i], z);
            for (Region o : pops[i].getRegions()) {
                if (planeByPlane && lapSPZ.length>1) { // keep track of z coordinate
                    o.setCenter(new double[]{o.getCenter()[0], o.getCenter()[1], 0}); // adding z dimention
                    o.translate(0, 0, z);
                }  
            }
        }
        RegionPopulation pop = MultiScaleWatershedTransform.combine(pops, input);
        if (debug) {
            logger.debug("Parent: {}: Q: {}", parent, Utils.toStringList(pop.getRegions(), o->""+o.getQuality()));
            logger.debug("Parent: {}: C: {}", parent ,Utils.toStringList(pop.getRegions(), o->""+Utils.toStringArray(o.getCenter())));
        }
        pop.filter(new RegionPopulation.RemoveFlatObjects(false));
        pop.filter(new RegionPopulation.Size().setMin(minSpotSize));
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
            logger.debug("Quality: {}", Utils.toStringList(pop.getRegions(), o->o.getQuality()+""));
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
    
    private static void setCenterAndQuality(Image map, Image map2, RegionPopulation pop, int z) {
        SubPixelLocalizator.setSubPixelCenter(map, pop.getRegions(), true); // lap -> better in case of close objects
        for (Region o : pop.getRegions()) { // quality criterion : sqrt (smooth * lap)
            if (o.getQuality()==0) { // localizator didnt work
                double[] center = o.getMassCenter(map, false);
                if (center[0]>map.getSizeX()-1) center[0] = map.getSizeX()-1;
                if (center[1]>map.getSizeY()-1) center[1] = map.getSizeY()-1;
                if (center.length>=2 && center[2]>map.getSizeZ()-1) center[2] = map.getSizeZ()-1;
                o.setCenter(center);
            }
            double zz = o.getCenter().length>2?o.getCenter()[2]:z;
            //logger.debug("size : {} set quality: center: {} : z : {}, bounds: {}, is2D: {}", o.getSize(), o.getCenter(), z, wsMap[i].getBoundingBox().translateToOrigin(), o.is2D());
            if (zz>map.getSizeZ()-1) zz=map.getSizeZ()-1;
            o.setQuality(Math.sqrt(map.getPixel(o.getCenter()[0], o.getCenter()[1], zz) * map2.getPixel(o.getCenter()[0], o.getCenter()[1], zz)));
        }
    }
    private static <T extends Image<T>> List<T> arrangeSpAndZPlanes(T[] spZ, boolean ZbyZ) {
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
    
    public void printSubLoc(String name, Image locMap, Image smooth, Image lap, RegionPopulation pop, BoundingBox globBound) {
        BoundingBox b = locMap.getBoundingBox().translate(globBound.reverseOffset());
        List<Region> objects = pop.getRegions();
        
        for(Region o : objects) o.setCenter(o.getMassCenter(locMap, false));
        pop.translate(b, false);
        logger.debug("mass center: centers: {}", Utils.toStringList(objects, o -> Utils.toStringArray(o.getCenter())+" value: "+o.getQuality()));
        pop.translate(b.duplicate().reverseOffset(), false);
        
        for(Region o : objects) o.setCenter(o.getGeomCenter(false));
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
    public void setQuality(List<Region> objects, BoundingBox offset, Image input, ImageMask parentMask) {
        if (objects.isEmpty()) return;
        if (offset==null) offset = new BoundingBox();
        this.pv.initPV(input, parentMask, smoothScale.getValue().doubleValue()) ;
        for (Region o : objects) {
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
    public RegionPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        ImageMask parentMask = parent.getMask().getSizeZ()!=input.getSizeZ() ? new ImageMask2D(parent.getMask()) : parent.getMask();
        this.pv.initPV(input, parentMask, smoothScale.getValue().doubleValue()) ;
        if (pv.smooth==null || pv.lap==null) setMaps(computeMaps(input, input));
        else logger.debug("manual seg: maps already set!");
        List<Region> seedObjects = RegionFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getSizeZ()==1, input.getScaleXY(), input.getScaleZ());
        Image lap = pv.getLaplacianMap()[0]; // todo max in scale space for each seed? 
        Image smooth = pv.getSmoothedMap();
        RegionPopulation pop =  watershed(lap, parentMask, seedObjects, true, new ThresholdPropagationOnWatershedMap(this.thresholdLow.getValue().doubleValue()), new SizeFusionCriterion(minSpotSize.getValue().intValue()), false);
        setCenterAndQuality(lap, smooth, pop, 0);
        
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
    public RegionPopulation splitObject(StructureObject parent, int structureIdx, Region object) {
        Image input = parent.getPreFilteredImage(structureIdx);
        ImageFloat wsMap = ImageFeatures.getLaplacian(input, 1.5, false, false);
        return WatershedObjectSplitter.splitInTwo(wsMap, object.getMask(), true, true, manualSplitVerbose);
    }

    boolean manualSplitVerbose;
    @Override
    public void setSplitVerboseMode(boolean verbose) {
        manualSplitVerbose=verbose;
    }
    @Override
    public String getToolTipText() {
       return toolTip;
    }
}
