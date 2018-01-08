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
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.StructureObjectUtils;
import dataStructure.objects.Voxel;
import ij.process.AutoThresholder;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageOperations;
import image.ObjectFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import measurement.BasicMeasurements;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import plugins.ParameterSetup;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.plugins.preFilter.ImageFeature;
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.Filters;
import processing.ImageFeatures;
import processing.SplitAndMerge;
import processing.WatershedTransform;
import utils.ArrayUtil;
import utils.HashMapGetCreate;
import utils.Utils;
import utils.clustering.Object3DCluster;

/**
 *
 * @author jollion
 */
public class BacteriaFluo implements SegmenterSplitAndMerge, ManualSegmenter, ObjectSplitter, ParameterSetup {
    public static boolean debug = false;
    
    // configuration-related attributes
    NumberParameter openRadius = new BoundedNumberParameter("Open Radius", 1, 0, 0, null);
    NumberParameter splitThreshold = new BoundedNumberParameter("Split Threshold", 4, 0.3, 0, 1); // TODO was 0.12 before change of scale (hess *= sqrt(2pi)-> *2.5 // verifier si toujours ok
    NumberParameter minSize = new BoundedNumberParameter("Minimum size", 0, 100, 50, null);
    NumberParameter minSizePropagation = new BoundedNumberParameter("Minimum size (propagation)", 0, 50, 1, null);
    NumberParameter contactLimit = new BoundedNumberParameter("Contact Threshold with X border", 0, 10, 0, null);
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 2, 0, 5).setToolTipText("Scale for median filtering (remove high frequency noise). If value <1 no smooth is applied");
    NumberParameter dogScale = new BoundedNumberParameter("DoG scale", 0, 40, 0, null).setToolTipText("Scale for low frequency filtering. If value ==0 no filter is applied ");
    NumberParameter hessianScale = new BoundedNumberParameter("Hessian scale", 1, 4, 1, 6);
    NumberParameter hessianThresholdFactor = new BoundedNumberParameter("Hessian threshold factor", 1, 1, 0, 5);
    NumberParameter thresholdForEmptyChannel = new BoundedNumberParameter("Threshold for empty channel", 3, 2, 0, null);
    NumberParameter manualSegPropagationHessianThreshold = new BoundedNumberParameter("Manual Segmentation: Propagation NormedHessian Threshold", 3, 0.2, 0, null);
    
    Parameter[] parameters = new Parameter[]{splitThreshold, minSize, contactLimit, smoothScale, dogScale, hessianScale, hessianThresholdFactor, thresholdForEmptyChannel, openRadius, manualSegPropagationHessianThreshold};
    
    //segmentation-related attributes (kept for split and merge methods)
    SplitAndMerge splitAndMerge;
    Image smoothed, DoG, normalizedHessian;
    
    public BacteriaFluo setSplitThreshold(double splitThreshold) {
        this.splitThreshold.setValue(splitThreshold);
        return this;
    }
    public BacteriaFluo setMinSize(int minSize) {
        this.minSize.setValue(minSize);
        return this;
    }
    public BacteriaFluo setContactLimit(int contactLimit) {
        this.contactLimit.setValue(contactLimit);
        return this;
    }
    public BacteriaFluo setSmoothScale(double smoothScale) {
        this.smoothScale.setValue(smoothScale);
        return this;
    }
    public BacteriaFluo setDogScale(int dogScale) {
        this.dogScale.setValue(dogScale);
        return this;
    }
    public BacteriaFluo setHessianScale(double hessianScale) {
        this.hessianScale.setValue(hessianScale);
        return this;
    }
    public BacteriaFluo setHessianThresholdFactor(double hessianThresholdFactor) {
        this.hessianThresholdFactor.setValue(hessianThresholdFactor);
        return this;
    }
    public BacteriaFluo setOpenRadius(double openRadius) {
        this.openRadius.setValue(openRadius);
        return this;
    }
    
    @Override
    public String toString() {
        return "Bacteria Fluo: " + Utils.toStringArray(parameters);
    }   
    private SplitAndMerge initializeSplitAndMerge(Image input) {
        SplitAndMerge res =  new SplitAndMerge(input, splitThreshold.getValue().doubleValue(), hessianScale.getValue().doubleValue());
        res.setTestMode(debug);
        return res;
    }
    private Image getSmoothed(Image input) {
        if (smoothed==null) {
            if (smoothScale.getValue().doubleValue()>=1) smoothed = ImageFeatures.gaussianSmooth(input, smoothScale.getValue().doubleValue(), false);
        }
        return smoothed;
    }
    private Image getDoG(Image input) {
        if (DoG == null) {
            Image dog = input;
            Image dest = null;
            if (dogScale.getValue().doubleValue()>0) {
                dog = ImageFeatures.differenceOfGaussians(input, 0, dogScale.getValue().doubleValue(), 1, false).setName("DoG");
                dest = dog;
            }
            //Image dog = BandPass.filter(rawIntensityMap, 0, dogScale, 0, 0);
            this.DoG = dog;
            if (smoothScale.getValue().doubleValue()>0) {
                this.DoG = ImageFeatures.gaussianSmooth(dog, smoothScale.getValue().doubleValue(), dog==dest);
                //this.DoG= Filters.median(dog, dest, Filters.getNeighborhood(smoothScale.getValue().doubleValue(), 1, dog)).setName(dog.getName()+"+Smoothed");
            }
        }
        return DoG;
    }
    public Image getNormalizedHessian(Image input) {
        if (normalizedHessian==null) {
            Image gauss = ImageFeatures.gaussianSmooth(input, smoothScale.getValue().doubleValue(), false);
            normalizedHessian=ImageOperations.divide(splitAndMerge.getHessian(), gauss, null).setName("NormalizedHessian");
        } 
        return normalizedHessian;
    }
    
    @Override public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        splitAndMerge = initializeSplitAndMerge(input);
        double threshold = dogScale.getValue().doubleValue()==0 ? IJAutoThresholder.runThresholder(getDoG(input), parent.getMask(), null, AutoThresholder.Method.Otsu, 0) : 0;
        //if (debug) disp.showImage(getIntensityMap(input).duplicate("intensityMap"));
        // criterion for empty channel: // TODO revoir si besoin de intensity map (DOG + median )ou simple smooth ok
        double[] musigmaOver = getMeanAndSigma(getDoG(input), parent.getMask(), threshold, true);
        double[] musigmaUnder = getMeanAndSigma(getDoG(input), parent.getMask(), threshold, false);
        if (debug) logger.debug("test empty channel: thld: {} mean over: {} mean under: {}, crit: {} thld: {}", threshold, musigmaOver[0], musigmaUnder[0], musigmaOver[0] - musigmaUnder[0], thresholdForEmptyChannel.getValue().doubleValue());
        if (musigmaOver[2]==0 || musigmaUnder[2]==0) {
            if (debug) logger.debug("no pixel {} thld", musigmaOver[2]==0?"over":"under");
            return new ObjectPopulation(input);
        }
        else {            
            if (musigmaOver[0] - musigmaUnder[0]<thresholdForEmptyChannel.getValue().doubleValue()) return new ObjectPopulation(input);
        }
        /*
        
        ObjectPopulation pop1 = SimpleThresholder.run(pv.getIntensityMap(), threshold, parent.getMask());
        double openRadius = this.openRadius.getValue().doubleValue();
        if (openRadius>=1) {
            for (Object3D o : pop1.getObjects()) {
                ImageInteger m = Filters.binaryOpen(o.getMask(), null, Filters.getNeighborhood(openRadius, openRadius, o.getMask()));
                o.setMask(m);
            }
            pop1.relabel();
            pop1 = new ObjectPopulation(pop1.getLabelMap(), false);
        }
        pop1.filter(new ObjectPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        pop1.filter(new ObjectPopulation.Size().setMin(minSize.getValue().intValue())); // remove small objects
        
        
        if (debug) logger.debug("threhsold: {}", threshold);
        pop1.filter(new ObjectPopulation.MeanIntensity(threshold, true, pv.getIntensityMap()));
        if (debug) disp.showImage(pop1.getLabelMap().duplicate("first seg"));
        
        ObjectPopulation res = getSeparatedObjects(pop1.getLabelMap(), pv, minSizePropagation.getValue().intValue(), minSize.getValue().intValue(), 0, debug);
        if (res!=null) {
            if (contactLimit.getValue().intValue()>0 && res.getObjects().size()>1) res.filter(new ObjectPopulation.ContactBorder(contactLimit.getValue().intValue(), parent.getMask(), ObjectPopulation.ContactBorder.Border.YDown));
            res.relabel(true);
        }
        */
        EdgeDetector seg = new EdgeDetector(); // keep defaults parameters ? 
        seg.setTestMode(debug);
        //seg.setPreFilters(new ImageFeature().setFeature(ImageFeature.Feature.GRAD).setScale(2));
        seg.setPreFilters(new ImageFeature().setFeature(ImageFeature.Feature.StructureMax).setScale(2).setSmoothScale(2)); // min scale = 1.5 min smooth scale = 2
        seg.setApplyThresholdOnValueMap(true);
        seg.setThresholder(new BackgroundThresholder(4, 4, 1).setStartingValue(new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu)));
        //seg.setThresholder(new ConstantValue(50));
        ObjectPopulation splitPop = seg.runSegmenter(input, structureIdx, parent);
        if (false) { // when done on gradient -> intermediate regions are kept -> remove using hessian value
            double thresholdHess = IJAutoThresholder.runThresholder(splitAndMerge.getHessian(), parent.getMask(), AutoThresholder.Method.Otsu);
            if (debug) ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(splitPop, splitAndMerge.getHessian()).setName("Hessian Value Map. Threshold: "+thresholdHess));
            splitPop.filter(new ObjectPopulation.MeanIntensity(thresholdHess, false, splitAndMerge.getHessian()));
        }
        
        splitAndMerge = initializeSplitAndMerge(input);
        ObjectPopulation res = splitAndMerge.splitAndMerge(splitPop.getLabelMap(), minSizePropagation.getValue().intValue(), minSize.getValue().intValue(), 0);
        
        // second run: watershed with all seeds except those included in objects: only one per object
        
        /*ObjectPopulation allSeeds = new ObjectPopulation(seg.getSeedMap(input, parent), false);
        logger.debug("SEEDS BEFORE REMOVE: {}", allSeeds.getObjects().size());
        for (Object3D o : res.getObjects()) {
            List<Object3D> includedSeeds = o.getIncludedObjects(allSeeds.getObjects());
            //logger.debug("seeds for: {} =#{}", o.getLabel(), includedSeeds.size());
            if (includedSeeds.isEmpty()) continue;
            Object3D min = Collections.max(includedSeeds, (o1, o2)->Double.compare(BasicMeasurements.getMeanValue(o1, getSmoothed(input), false), BasicMeasurements.getMeanValue(o2, getSmoothed(input), false)));
            includedSeeds.remove(min);
            allSeeds.getObjects().removeAll(includedSeeds);
        }
        logger.debug("SEEDS AFTER REMOVE: {}", allSeeds.getObjects().size());
        allSeeds.relabel(true);
        seg.setSeedMap(allSeeds.getLabelMap());
        res = seg.runSegmenter(input, structureIdx, parent);
        if (debug) ImageWindowManagerFactory.showImage(res.getLabelMap().duplicate("After second ws"));
        */
        boolean localThreshold = true;
        if (localThreshold) {
            // TODO LOG TO FILE NE FCT PLUS!!
            // local threshold on each cell // TODO: MOP: teser la robustesse des differentes methodes en mesurant le sigma du growth rate
            Image erodeMap = getSmoothed(input);
            Object3DCluster<SplitAndMerge.Interface> inter = splitAndMerge.getInterfaces(res, false);
            for (Object3D o : res.getObjects()) {
                Set<Voxel> contour = new HashSet<>(o.getContour());
                Set<SplitAndMerge.Interface> inters = inter.getInterfaces(o);
                Set<Voxel> interVox = new HashSet<>();
                for (SplitAndMerge.Interface i : inters) interVox.addAll(i.getVoxels());
                interVox.retainAll(contour);
                contour.removeAll(interVox);
                double thld = ArrayUtil.quantile(Utils.transform(contour, v->(double)erodeMap.getPixel(v.x, v.y, v.z)), 0.075); // TODO set quantile as parameter
                o.erodeContours(erodeMap, thld, true, interVox);
                double thld2 = ArrayUtil.quantile(Utils.transform(contour, v->(double)erodeMap.getPixel(v.x, v.y, v.z)), 0.025); // case of first and last cells
                o.erodeContours(erodeMap, thld2, true, contour);
            }
            res.redrawLabelMap(true);
            res = new ObjectPopulation(res.getLabelMap(), true); // update bounds of objects
            if (debug) ImageWindowManagerFactory.showImage(res.getLabelMap().duplicate("After local threshold"));
        
        }
        res.filter(new ObjectPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        res.filter(new ObjectPopulation.Size().setMin(minSize.getValue().intValue())); // remove small objects
        
        if (testParameter!=null) {
            logger.debug("testParameter: {}", testParameter);
            if (splitThreshold.getName().equals(testParameter)) {
                Image hess = splitAndMerge.getHessian().duplicate("Split map");
                hess = ImageOperations.divide(hess, input, null);
                ImageWindowManagerFactory.showImage(res.getLabelMap().setName("Segmentation with splitThreshold: "+splitThreshold.getValue().doubleValue()));
                ImageOperations.trim(hess, res.getLabelMap(), hess);
                ImageWindowManagerFactory.showImage(hess);
            }
        }
        return res;
    }
    
    
    
    public static double[] getMeanAndSigma(Image image, ImageMask mask, double thld, boolean overThreshold) {
        double mean = 0;
        double count = 0;
        double values2 = 0;
        double value;
        for (int z = 0; z < image.getSizeZ(); ++z) {
            for (int xy = 0; xy < image.getSizeXY(); ++xy) {
                if (mask.insideMask(xy, z)) {
                    value = image.getPixel(xy, z);
                    if ((overThreshold && value>=thld) || (!overThreshold && value <= thld)) {
                        mean += value;
                        count++;
                        values2 += value * value;
                    }
                }
            }
        }
        if (count != 0) {
            mean /= count;
            values2 /= count;
            return new double[]{mean, Math.sqrt(values2 - mean * mean), count};
        } else {
            return new double[]{0, 0, 0};
        }
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    @Override public double split(Image input, Object3D o, List<Object3D> result) {
        ObjectPopulation pop =  splitObject(input, o); // init processing variables
        pop.translate(o.getBounds().duplicate().reverseOffset(), false);
        if (pop.getObjects().size()<=1) return Double.POSITIVE_INFINITY;
        else {
            if (pop.getObjects().size()>2) pop.mergeWithConnected(pop.getObjects().subList(2, pop.getObjects().size()));
            Object3D o1 = pop.getObjects().get(0);
            Object3D o2 = pop.getObjects().get(1);
            result.add(o1);
            result.add(o2);
            SplitAndMerge.Interface inter = getInterface(o1, o2);
            double cost = BacteriaTrans.getCost(inter.value, splitAndMerge.splitThresholdValue, true);
            pop.translate(o.getBounds(), true);
            return cost;
        }
        /*if (pv==null) throw new RuntimeException("Segment method have to be called before split method in order to initialize maps");
        synchronized(pv) {
            o.draw(pv.getSplitMask(), 1);
            //ObjectPopulation pop = WatershedObjectSplitter.splitInTwo(pv.intensityMap, pv.splitMask, false, minSize.getValue().intValue(), false);
            ObjectPopulation pop = getSeparatedObjects(pv.getSplitMask(), pv, minSizePropagation.getValue().intValue(), minSize.getValue().intValue(), 2, false);
            o.draw(pv.getSplitMask(), 0);
            if (pop==null || pop.getObjects().isEmpty() || pop.getObjects().size()==1) return Double.POSITIVE_INFINITY;
            ArrayList<Object3D> remove = new ArrayList<Object3D>(pop.getObjects().size());
            pop.filter(new ObjectPopulation.Thickness().setX(2).setY(2), remove); // remove thin objects
            pop.filter(new ObjectPopulation.Size().setMin(minSize.getValue().intValue()), remove); // remove small objects
            if (pop.getObjects().size()<=1) return Double.POSITIVE_INFINITY;
            else {
                if (!remove.isEmpty()) pop.mergeWithConnected(remove);
                if (pop.getObjects().size()>2) pop.mergeWithConnected(pop.getObjects().subList(2, pop.getObjects().size()));
                Object3D o1 = pop.getObjects().get(0);
                Object3D o2 = pop.getObjects().get(1);
                result.add(o1);
                result.add(o2);
                ProcessingVariables.InterfaceBF inter = getInterface(o1, o2);
                return BacteriaTrans.getCost(inter.value, pv.splitThresholdValue, true);
            }
        }*/
    }

    @Override public double computeMergeCost(Image input, List<Object3D> objects) {
        if (objects.isEmpty() || objects.size()==1) return 0;
        ObjectPopulation mergePop = new ObjectPopulation(objects, input, false);
        splitAndMerge = this.initializeSplitAndMerge(input);
        Object3DCluster c = new Object3DCluster(mergePop, false, true, splitAndMerge.getFactory());
        List<Set<Object3D>> clusters = c.getClusters();
        double maxCost = Double.NEGATIVE_INFINITY;
        //logger.debug("compute merge cost: {} objects in {} clusters", objects.size(), clusters.size());
        if (clusters.size()>1) { // merge impossible : presence of disconnected objects
            if (debug) logger.debug("merge impossible: {} disconnected clusters detected", clusters.size());
            return Double.POSITIVE_INFINITY;
        } 
        Set<SplitAndMerge.Interface> allInterfaces = c.getInterfaces(clusters.get(0));
        for (SplitAndMerge.Interface i : allInterfaces) {
            i.updateSortValue();
            if (i.value>maxCost) maxCost = i.value;
        }

        if (maxCost==Double.MIN_VALUE) return Double.POSITIVE_INFINITY;
        return BacteriaTrans.getCost(maxCost, splitAndMerge.splitThresholdValue, false);
        /*
        if (pv==null) throw new RuntimeException("Segment method have to be called before merge method in order to initialize images");
        if (objects.isEmpty() || objects.size()==1) return 0;
        synchronized(pv) {
            double maxCost = Double.NEGATIVE_INFINITY;
            ObjectPopulation mergePop = new ObjectPopulation(objects, pv.getSplitMask(), pv.getSplitMask(), false);
            Object3DCluster c = new Object3DCluster(mergePop, false, pv.getFactory());
            List<Set<Object3D>> clusters = c.getClusters();
            logger.debug("compute merge cost: {} objects in {} clusters", objects.size(), clusters.size());
            if (clusters.size()>1) { // merge impossible : presence of disconnected objects
                if (debug) logger.debug("merge impossible: {} disconnected clusters detected", clusters.size());
                return Double.POSITIVE_INFINITY;
            } 
            Set<ProcessingVariables.InterfaceBF> allInterfaces = c.getInterfaces(clusters.get(0));
            for (ProcessingVariables.InterfaceBF i : allInterfaces) {
                i.updateSortValue();
                if (i.value>maxCost) maxCost = i.value;
            }
            
            if (maxCost==Double.MIN_VALUE) return Double.POSITIVE_INFINITY;
            return BacteriaTrans.getCost(maxCost, pv.splitThresholdValue, false);
        }*/
    }
    
    private SplitAndMerge.Interface getInterface(Object3D o1, Object3D o2) {
        o1.draw(splitAndMerge.getSplitMask(), o1.getLabel());
        o2.draw(splitAndMerge.getSplitMask(), o2.getLabel());
        SplitAndMerge.Interface inter = Object3DCluster.getInteface(o1, o2, splitAndMerge.tempSplitMask, splitAndMerge.getFactory());
        inter.updateSortValue();
        o1.draw(splitAndMerge.getSplitMask(), 0);
        o2.draw(splitAndMerge.getSplitMask(), 0);
        return inter;
    }
    
    // manual correction implementations
    private boolean verboseManualSeg;
    @Override public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }

    @Override public ObjectPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        if (splitAndMerge==null) splitAndMerge=initializeSplitAndMerge(input);
        List<Object3D> seedObjects = ObjectFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getScaleXY(), input.getScaleZ());
        ObjectPopulation pop =  WatershedTransform.watershed(splitAndMerge.getHessian(), segmentationMask, seedObjects, false, new WatershedTransform.ThresholdPropagation(getNormalizedHessian(input), this.manualSegPropagationHessianThreshold.getValue().doubleValue(), false), new WatershedTransform.SizeFusionCriterion(this.minSize.getValue().intValue()), false);
        
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixel(seed[0], seed[1], seed[2], 1);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(seedMap);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(splitAndMerge.getHessian());
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(getNormalizedHessian(input).setName("NormalizedHessian: for propagation limit"));
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pop.getLabelMap().setName("segmented from: "+input.getName()));
        }
        
        return pop;
    }
    
    // object splitter interface
    boolean splitVerbose;
    @Override public void setSplitVerboseMode(boolean verbose) {
        this.splitVerbose=verbose;
    }
    
    @Override public ObjectPopulation splitObject(Image input, Object3D object) {
        if (!input.sameSize(object.getMask())) {
            input = input.crop(object.getBounds());
            //mask = mask.crop(input.getBoundingBox()); // problem with crop & offsets when bb is larger & has an offset
        }
        // avoid border effects: dilate image
        int ext = (int)this.hessianScale.getValue().doubleValue()+1;
        BoundingBox extent = new BoundingBox(-ext, ext, -ext, ext, 0, 0);
       
        Image inExt = input.extend(extent);
        ImageInteger maskExt = object.getMask().extend(extent);
        splitAndMerge = initializeSplitAndMerge(inExt);
        splitAndMerge.setTestMode(splitVerbose);
        // ici -> bug avec offsets? 
        //logger.debug("in off: {}, object off: {}, inExt off: {}, maskExt off: {}", input.getBoundingBox(), object.getMask().getBoundingBox(), inExt.getBoundingBox(), maskExt.getBoundingBox());
        ObjectPopulation res = splitAndMerge.splitAndMerge(maskExt, minSizePropagation.getValue().intValue(), minSize.getValue().intValue(), 2);
        extent = new BoundingBox(ext, -ext, ext, -ext, 0, 0);
        ImageInteger labels = res.getLabelMap().extend(extent);
        ObjectPopulation pop= new ObjectPopulation(labels, true);
        pop.translate(object.getBounds(), true);
        return pop;
    }

    // ParameterSetup Implementation
    
    @Override public boolean canBeTested(String p) {
        List canBeTested = new ArrayList(){{add(splitThreshold); add(dogScale);}};
        return canBeTested.contains(p);
    }
    String testParameter;
    @Override public void setTestParameter(String p) {
        this.testParameter=p;
    }
    
}
