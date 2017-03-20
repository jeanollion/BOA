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
import dataStructure.objects.Voxel;
import ij.process.AutoThresholder;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
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
import measurement.BasicMeasurements;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import plugins.ParameterSetup;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackers.ObjectIdxTracker;
import processing.Filters;
import processing.ImageFeatures;
import processing.WatershedTransform;
import utils.Utils;
import utils.clustering.ClusterCollection.InterfaceFactory;
import utils.clustering.Interface;
import utils.clustering.InterfaceObject3DImpl;
import utils.clustering.Object3DCluster;
import utils.clustering.Object3DCluster.InterfaceVoxels;

/**
 *
 * @author jollion
 */
public class BacteriaFluo implements SegmenterSplitAndMerge, ManualSegmenter, ObjectSplitter, ParameterSetup {
    public static boolean debug = false;
    
    // configuration-related attributes
    NumberParameter openRadius = new BoundedNumberParameter("Open Radius", 1, 0, 0, null);
    NumberParameter splitThreshold = new BoundedNumberParameter("Split Threshold", 4, 0.12, 0, 1);
    NumberParameter minSize = new BoundedNumberParameter("Minimum size", 0, 100, 50, null);
    NumberParameter minSizePropagation = new BoundedNumberParameter("Minimum size (propagation)", 0, 50, 1, null);
    NumberParameter contactLimit = new BoundedNumberParameter("Contact Threshold with X border", 0, 10, 0, null);
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 3, 1, 5);
    NumberParameter dogScale = new BoundedNumberParameter("DoG scale", 0, 40, 5, null);
    NumberParameter hessianScale = new BoundedNumberParameter("Hessian scale", 1, 4, 1, 6);
    NumberParameter hessianThresholdFactor = new BoundedNumberParameter("Hessian threshold factor", 1, 1, 0, 5);
    NumberParameter thresholdForEmptyChannel = new BoundedNumberParameter("Threshold for empty channel", 1, 2, 0, null);
    NumberParameter manualSegPropagationHessianThreshold = new BoundedNumberParameter("Manual Segmentation: Propagation NormedHessian Threshold", 3, 0.2, 0, null);
    
    Parameter[] parameters = new Parameter[]{splitThreshold, minSize, contactLimit, smoothScale, dogScale, hessianScale, hessianThresholdFactor, thresholdForEmptyChannel, openRadius, manualSegPropagationHessianThreshold};
    
    //segmentation-related attributes (kept for split and merge methods)
    ProcessingVariables pv;
    
    public BacteriaFluo setSplitThreshold(double splitThreshold) {
        this.splitThreshold.setValue(splitThreshold);
        return this;
    }
    public BacteriaFluo setMinSize(int minSize) {
        this.minSize.setValue(minSize);
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
    private ProcessingVariables initializeVariables(Image input) {
        return new ProcessingVariables(input, this.splitThreshold.getValue().doubleValue(), dogScale.getValue().doubleValue(), smoothScale.getValue().doubleValue(), hessianScale.getValue().doubleValue());
    }
    
    @Override public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        pv = initializeVariables(input);
        ImageDisplayer disp=debug?new IJImageDisplayer():null;
        double threshold = IJAutoThresholder.runThresholder(pv.getIntensityMap(), parent.getMask(), null, AutoThresholder.Method.Otsu, 0);
        
        // criterion for empty channel: 
        double[] musigmaOver = getMeanAndSigma(pv.getIntensityMap(), parent.getMask(), 0, true);
        double[] musigmaUnder = getMeanAndSigma(pv.getIntensityMap(), parent.getMask(), 0, false);
        if (musigmaOver[2]==0 || musigmaUnder[2]==0) return new ObjectPopulation(input);
        else {            
            if (musigmaOver[0] - musigmaUnder[0]<thresholdForEmptyChannel.getValue().doubleValue()) return new ObjectPopulation(input);
        }
        ObjectPopulation pop1 = SimpleThresholder.run(pv.getIntensityMap(), 0);
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
        
        
        //pop1.keepOnlyLargestObject(); // for testing purpose -> TODO = loop
        /*ObjectPopulation res=null;
        for (Object3D maskObject : pop1.getObjects()) {
            maskObject.draw(watershedMask, 1);
            double[] meanAndSigma = getMeanAndSigma(hessian, watershedMask, 0, false); // mean & sigma < 0
            //logger.debug("hessian mean: {}, sigma: {}, hessian thld: {}", meanAndSigma[0],meanAndSigma[1], sigmaCoeff * meanAndSigma[1]);
            ImageInteger seedMap = ImageOperations.threshold(hessian, hessianThresholdFactor * meanAndSigma[1], false, false, false, null);
            seedMap = ImageOperations.and(watershedMask, seedMap, seedMap).setName("seeds");
            //disp.showImage(seedMap);
            ObjectPopulation popWS = WatershedTransform.watershed(hessian, watershedMask, seedMap, false, null, new WatershedTransform.SizeFusionCriterion(minSize));
            popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
            if (debug) disp.showImage(popWS.getLabelMap().duplicate("before local threshold & merging"));
            //popWS.localThreshold(dogNoTrim, 0, localThresholdMargin, 0);
            //if (debug) disp.showImage(popWS.getLabelImage().duplicate("after local threhsold / before merging"));
            
            // fusion
            //RegionCollection.verbose=debug;
            //ObjectPopulation localPop= RegionCollection.mergeHessianBacteria(popWS, input, hessian, fusionThreshold, true);
            Object3DCluster.verbose=debug;
            popWS.setVoxelIntensities(hessian);
            Object3DCluster.mergeSort(popWS, pv.getFactory());
            if (res==null) res= popWS;
            else res.addObjects(popWS.getObjects());
            //if (debug) disp.showImage(localPop.getLabelImage().setName("after merging"));
            maskObject.draw(watershedMask, 0);
        }*/
        ObjectPopulation res = getSeparatedObjects(pop1.getLabelMap(), pv, minSizePropagation.getValue().intValue(), minSize.getValue().intValue(), 0, debug);
        if (res!=null) {
            if (contactLimit.getValue().intValue()>0) res.filter(new ObjectPopulation.ContactBorder(contactLimit.getValue().intValue(), parent.getMask(), ObjectPopulation.ContactBorder.Border.YDown));
            res.relabel(true);
        }
        return res;
    }
    
    protected static ObjectPopulation getSeparatedObjects(ImageInteger segmentationMask, ProcessingVariables pv, int minSizePropagation, int minSize, int objectMergeLimit, boolean debug) {
        ObjectPopulation popWS = WatershedTransform.watershed(pv.getHessian(), segmentationMask, false, null, new WatershedTransform.SizeFusionCriterion(minSizePropagation), false);
        if (debug) popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        ImageDisplayer disp=debug?new IJImageDisplayer():null;
        
        if (debug) {
            disp.showImage(pv.getIntensityMap());
            disp.showImage(pv.getHessian());
            disp.showImage(popWS.getLabelMap().duplicate("seg map before merge"));
        }
        Object3DCluster.verbose=debug;
        Object3DCluster.mergeSort(popWS,  pv.getFactory(), objectMergeLimit<=1, 0, objectMergeLimit);
        //if (debug) disp.showImage(popWS.getLabelMap().duplicate("seg map after merge"));
        popWS.filterAndMergeWithConnected(new ObjectPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        popWS.filterAndMergeWithConnected(new ObjectPopulation.Size().setMin(minSize)); // remove small objects
        popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        if (debug) disp.showImage(popWS.getLabelMap().duplicate("seg map"));
        return popWS;
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
            ProcessingVariables.InterfaceBF inter = getInterface(o1, o2);
            double cost = BacteriaTrans.getCost(inter.value, pv.splitThresholdValue, true);
            pop.translate(o.getBounds(), true);
            return cost;
        }
        /*if (pv==null) throw new Error("Segment method have to be called before split method in order to initialize maps");
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
        pv = this.initializeVariables(input);
        Object3DCluster c = new Object3DCluster(mergePop, false, true, pv.getFactory());
        List<Set<Object3D>> clusters = c.getClusters();
        double maxCost = Double.NEGATIVE_INFINITY;
        //logger.debug("compute merge cost: {} objects in {} clusters", objects.size(), clusters.size());
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
        /*
        if (pv==null) throw new Error("Segment method have to be called before merge method in order to initialize images");
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
    
    private ProcessingVariables.InterfaceBF getInterface(Object3D o1, Object3D o2) {
        o1.draw(pv.getSplitMask(), o1.getLabel());
        o2.draw(pv.getSplitMask(), o2.getLabel());
        ProcessingVariables.InterfaceBF inter = Object3DCluster.getInteface(o1, o2, pv.splitMask, pv.getFactory());
        inter.updateSortValue();
        o1.draw(pv.getSplitMask(), 0);
        o2.draw(pv.getSplitMask(), 0);
        return inter;
    }
    
    // manual correction implementations
    private boolean verboseManualSeg;
    @Override public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }

    @Override public ObjectPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        if (pv==null) pv=initializeVariables(input);
        List<Object3D> seedObjects = ObjectFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getScaleXY(), input.getScaleZ());
        ObjectPopulation pop =  WatershedTransform.watershed(pv.getHessian(), segmentationMask, seedObjects, false, new WatershedTransform.ThresholdPropagation(pv.getNormalizedHessian(), this.manualSegPropagationHessianThreshold.getValue().doubleValue(), false), new WatershedTransform.SizeFusionCriterion(this.minSize.getValue().intValue()), false);
        
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixel(seed[0], seed[1], seed[2], 1);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(seedMap);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pv.hessian);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pv.getNormalizedHessian().setName("NormalizedHessian: for propagation limit"));
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
        pv = initializeVariables(inExt);
        // ici -> bug avec offsets? 
        //logger.debug("in off: {}, object off: {}, inExt off: {}, maskExt off: {}", input.getBoundingBox(), object.getMask().getBoundingBox(), inExt.getBoundingBox(), maskExt.getBoundingBox());
        ObjectPopulation res = getSeparatedObjects(maskExt, pv, minSizePropagation.getValue().intValue(), minSize.getValue().intValue(), 2, splitVerbose);
        extent = new BoundingBox(ext, -ext, ext, -ext, 0, 0);
        ImageInteger labels = res.getLabelMap().extend(extent);
        ObjectPopulation pop= new ObjectPopulation(labels, true);
        pop.translate(object.getBounds(), true);
        return pop;
    }

    // ParameterSetup Implementation
    
    @Override public boolean canBeTested(Parameter p) {
        List canBeTested = new ArrayList(){{add(splitThreshold); add(dogScale);}};
        return canBeTested.contains(p);
    }

    @Override public void test(Parameter p, Image input, int structureIdx, StructureObjectProcessing parent) {
        if (p==splitThreshold) {
            logger.debug("test split threshold");
        } else if (p==dogScale) {
            logger.debug("dogScale");
        }
    }
    
    private  static class ProcessingVariables {
        Image hessian;
        final Image rawIntensityMap;
        Image intensityMap;
        Image normalizedHessian;
        ImageByte splitMask;
        final double splitThresholdValue, smoothScale, dogScale, hessianScale;
        InterfaceFactory<Object3D, InterfaceBF> factory;
        private ProcessingVariables(Image input, double splitThreshold, double dogScale, double smoothScale, double hessianScale) {
            rawIntensityMap=input;
            splitThresholdValue=splitThreshold;
            this.smoothScale=smoothScale;
            this.dogScale=dogScale;
            this.hessianScale=hessianScale;
        }
        
        public Image getIntensityMap() {
            if (intensityMap == null) {
                ImageFloat dog = ImageFeatures.differenceOfGaussians(rawIntensityMap, 0, dogScale, 1, false).setName("DoG");
                intensityMap= Filters.median(dog, dog, Filters.getNeighborhood(smoothScale, smoothScale, dog)).setName("DoG+Smoothed");
            }
            return intensityMap;
        }
        public Image getHessian() {
            if (hessian ==null) hessian=ImageFeatures.getHessian(rawIntensityMap, hessianScale, false)[0].setName("hessian");
            return hessian;
        }
        
        public ImageByte getSplitMask() {
            if (splitMask==null) splitMask = new ImageByte("split mask", rawIntensityMap);
            return splitMask;
        }
        private Image getNormalizedHessian() {
            if (normalizedHessian==null) {
                Image gauss = ImageFeatures.gaussianSmooth(rawIntensityMap, smoothScale, smoothScale*rawIntensityMap.getScaleXY()/rawIntensityMap.getScaleZ(), false);
                normalizedHessian=ImageOperations.divide(getHessian(), gauss, null).setName("NormalizedHessian");
            } 
            return normalizedHessian;
        }
        
        public InterfaceFactory<Object3D, InterfaceBF> getFactory() {
            if (factory==null) {
                factory = new InterfaceFactory<Object3D, InterfaceBF>() {
                    public InterfaceBF create(Object3D e1, Object3D e2, Comparator<? super Object3D> elementComparator) {
                        return new InterfaceBF(e1, e2);
                    }
                };
            }
            return factory;
        }
        
        protected class InterfaceBF extends InterfaceObject3DImpl<InterfaceBF> implements InterfaceVoxels<InterfaceBF> {
            double value;
            Set<Voxel> voxels;
            public InterfaceBF(Object3D e1, Object3D e2) {
                super(e1, e2);
                voxels = new HashSet<Voxel>();
            }
            
            @Override public void updateSortValue() {
                if (voxels.isEmpty()) {
                    value = Double.NaN;
                } else {
                    double hessSum = 0, intensitySum = 0;
                    getHessian();
                    for (Voxel v : voxels) {
                        hessSum+=hessian.getPixel(v.x, v.y, v.z);
                        intensitySum += rawIntensityMap.getPixel(v.x, v.y, v.z);
                    }
                    value = hessSum / intensitySum;
                }
            }

            @Override 
            public void fusionInterface(InterfaceBF otherInterface, Comparator<? super Object3D> elementComparator) {
                //fusionInterfaceSetElements(otherInterface, elementComparator);
                InterfaceBF other = otherInterface;
                voxels.addAll(other.voxels); 
                value = Double.NaN;// updateSortValue will be called afterwards
            }

            @Override
            public boolean checkFusion() {
                // criterion = - hessian @ border / intensity @ border < threshold
                if (BacteriaFluo.debug) logger.debug("check fusion: {}+{}, size: {}, value: {}, threhsold: {}, fusion: {}", e1.getLabel(), e2.getLabel(), voxels.size(), value, splitThresholdValue, value<splitThresholdValue);
                return value<splitThresholdValue;
            }

            @Override
            public void addPair(Voxel v1, Voxel v2) {
               voxels.add(v1);
               voxels.add(v2);
            }
            
            @Override
            public int compareTo(InterfaceBF t) {
                int c = Double.compare(value, t.value); // increasing values
                if (c==0) return super.compareElements(t, Object3DCluster.object3DComparator);
                else return c;
            }

            public Collection<Voxel> getVoxels() {
                return voxels;
            }
            
            @Override
            public String toString() {
                return "Interface: " + e1.getLabel()+"+"+e2.getLabel()+ " sortValue: "+value;
            } 
        }
    }
}
