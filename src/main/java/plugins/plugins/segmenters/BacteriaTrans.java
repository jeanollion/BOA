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
import ij.IJ;
import ij.process.AutoThresholder;
import image.BlankMask;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageMask;
import image.ImageOperations;
import image.ObjectFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import measurement.BasicMeasurements;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.segmenters.BacteriaTrans.ProcessingVariables.InterfaceBT;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackers.ObjectIdxTracker;
import processing.EDT;
import processing.Filters;
import processing.FitEllipse;
import processing.ImageFeatures;
import processing.WatershedTransform;
import utils.Utils;
import static utils.Utils.plotProfile;
import utils.clustering.ClusterCollection.InterfaceFactory;
import utils.clustering.Interface;
import utils.clustering.InterfaceImpl;
import utils.clustering.InterfaceObject3D;
import utils.clustering.InterfaceObject3DImpl;
import utils.clustering.Object3DCluster;

/**
 *
 * @author jollion
 */
public class BacteriaTrans implements SegmenterSplitAndMerge, ManualSegmenter, ObjectSplitter {
    public static boolean debug = false;
    
    // configuration-related attributes
    NumberParameter openRadius = new BoundedNumberParameter("Open Radius", 1, 4, 0, null);
    NumberParameter minSize = new BoundedNumberParameter("Minimum size", 0, 150, 5, null);
    NumberParameter contactLimit = new BoundedNumberParameter("Contact Threshold with X border", 0, 10, 0, null);
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 3, 1, 5);
    NumberParameter dogScale = new BoundedNumberParameter("DoG scale", 0, 10, 5, null);
    NumberParameter thresholdForEmptyChannel = new BoundedNumberParameter("Threshold for empty channel", 1, 2, 0, null);
    NumberParameter relativeThicknessThreshold = new BoundedNumberParameter("Relative Thickness Threshold (lower: split more)", 2, 0.7, 0, 1);
    Parameter[] parameters = new Parameter[]{minSize, contactLimit, smoothScale, dogScale, thresholdForEmptyChannel, openRadius, relativeThicknessThreshold};
    
    //segmentation-related attributes (kept for split and merge methods)
    ProcessingVariables pv;
    
    public BacteriaTrans setRelativeThicknessThreshold(double relativeThicknessThreshold) {
        this.relativeThicknessThreshold.setValue(relativeThicknessThreshold);
        return this;
    }
    public BacteriaTrans setMinSize(int minSize) {
        this.minSize.setValue(minSize);
        return this;
    }
    public BacteriaTrans setSmoothScale(double smoothScale) {
        this.smoothScale.setValue(smoothScale);
        return this;
    }
    public BacteriaTrans setDogScale(int dogScale) {
        this.dogScale.setValue(dogScale);
        return this;
    }

    public BacteriaTrans setOpenRadius(double openRadius) {
        this.openRadius.setValue(openRadius);
        return this;
    }
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent.getMask(), minSize.getValue().intValue(), contactLimit.getValue().intValue(), smoothScale.getValue().doubleValue(), dogScale.getValue().doubleValue(), thresholdForEmptyChannel.getValue().doubleValue(), openRadius.getValue().doubleValue(), relativeThicknessThreshold.getValue().doubleValue(), this);
    }
    
    @Override
    public String toString() {
        return "Bacteria Fluo: " + Utils.toStringArray(parameters);
    }   
    
    public static ObjectPopulation run(Image input, ImageMask mask, int minSize, int contactLimit, double smoothScale, double dogScale, double thresholdForEmptyChannel, double openRadius, final double relativeThicknessThreshold, BacteriaTrans instance) {
        ImageDisplayer disp=debug?new IJImageDisplayer():null;
        //double hessianThresholdFacto = 1;
        ProcessingVariables pv = new ProcessingVariables(input, mask, relativeThicknessThreshold, dogScale, openRadius, minSize);
        Image dog = pv.getDOG();
        //double threshold = IJAutoThresholder.runThresholder(dog, mask, null, AutoThresholder.Method.Shanbhag, 0); // OTSU
        double threshold = 100;
        if (debug) logger.debug("threshold: {}", threshold);
        
        // criterion for empty channel: 
        double[] musigmaOver = getMeanAndSigma(dog, mask, threshold, true);
        double[] musigmaUnder = getMeanAndSigma(dog, mask, threshold, false);
        if (musigmaOver[2]==0 || musigmaUnder[2]==0) return new ObjectPopulation(input);
        else {            
            if (musigmaOver[0] - musigmaUnder[0]<thresholdForEmptyChannel) return new ObjectPopulation(input);
        }
        pv.threshold=threshold;
        ImageInteger segMask = pv.getSegmentationMask();
        Image edm = pv.getEDM();

        
        /*
        segmentation based on thickness threshold
        1) analyse Y profil of max|X values of distance map -> search for means under threshold
        2) watershed on distance map with one seed at each segment (max)
        */
        
        
        /*WatershedTransform.FusionCriterion relativeThickness = new WatershedTransform.FusionCriterion() {
            WatershedTransform instance;
            public void setUp(WatershedTransform instance) {this.instance=instance;}
            public boolean checkFusionCriteria(WatershedTransform.Spot s1, WatershedTransform.Spot s2, Voxel currentVoxel) {
                double max1 = Double.NEGATIVE_INFINITY;
                double max2 = Double.NEGATIVE_INFINITY;
                for (Voxel v : s1.voxels) if (v.value>max1) max1 = v.value;
                for (Voxel v : s2.voxels) if (v.value>max2) max2 = v.value;
                double norm = Math.min(max1, max2);
                return currentVoxel.value/norm>relativeThicknessThreshold;
            }
        };*/
        ObjectPopulation res = WatershedTransform.watershed(edm, segMask, true, null, new WatershedTransform.SizeFusionCriterion(minSize)); //new WatershedTransform.ThresholdFusionOnWatershedMap(thicknessThreshold) // relativeThickness
        if (debug) disp.showImage(res.getLabelMap().duplicate("watershed EDM"));
        
        // merge using the same criterion : max(EDM@frontière) / min(max(EDM@S1), max(EDM@S2)) > criterion
        Object3DCluster.verbose=debug;
        res.setVoxelIntensities(pv.getEDM());
        Object3DCluster.mergeSort(res,  pv.getFactory());
        
        if (debug) {
            disp.showImage(dog.setName("DOG"));
            disp.showImage(edm);
            disp.showImage(res.getLabelMap().setName("seg map after fusion"));
            //disp.showImage(cluster.drawInterfacesSortValue(sm));
            //disp.showImage(cluster.drawInterfaces());
            //FitEllipse.fitEllipse2D(pop1.getObjects().get(0));
            //FitEllipse.fitEllipse2D(pop1.getObjects().get(1));
        }
        // OTHER IDEAS TO TAKE INTO ACCOUNT INTENSITY WITHIN BACTERIA
        /* normalize image
        1) dilate image & compute mean & sigma outside
        2) compute mean & sigma inside
        3) Histogram transformation: mean outside = 0 / mean inside = 1
        */
        
        /*
        add thickness information:  * border size / mean thickness of the 2 objects -> transformation of the Hessian map
        */
        
        if (instance!=null) instance.pv=pv;
        return res;
    }
    
    
    
    public static double getNomalizationValue(Image edm, List<Voxel> v1, List<Voxel> v2) {
        double max1 = -Double.MAX_VALUE;
        double max2 = -Double.MAX_VALUE;
        for (Voxel v : v1) {
            double val = edm.getPixel(v.x, v.y, v.z);
            if (val>max1) max1 = v.value;
        }
        for (Voxel v : v2) {
            double val = edm.getPixel(v.x, v.y, v.z);
            if (val>max2) max2 = v.value;
        }
        return Math.min(max1, max2);
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
    // segmenter split and merge interface
    @Override public double split(Object3D o, List<Object3D> result) {
        if (pv==null) throw new Error("Segment method have to be called before split method in order to initialize images");
        o.draw(pv.getSplitMask(), 1);
        ObjectPopulation pop = WatershedObjectSplitter.splitInTwo(pv.getEDM(), pv.splitMask, true, minSize.getValue().intValue(), false);
        o.draw(pv.splitMask, 0);
        if (pop==null || pop.getObjects().isEmpty() || pop.getObjects().size()==1) return Double.NaN;
        ArrayList<Object3D> remove = new ArrayList<Object3D>(pop.getObjects().size());
        pop.filter(new ObjectPopulation.Thickness().setX(2).setY(2), remove); // remove thin objects ?? merge? 
        pop.filter(new ObjectPopulation.Size().setMin(minSize.getValue().intValue()), remove); // remove small objects ?? merge? 
        if (pop.getObjects().size()<=1) return Double.NaN;
        else {
            if (!remove.isEmpty()) pop.mergeWithConnected(remove);
            Object3D o1 = pop.getObjects().get(0);
            Object3D o2 = pop.getObjects().get(1);
            result.add(o1);
            result.add(o2);
            InterfaceBT inter = getInterface(o1, o2);
            double[] value = new double[1];
            inter.checkFusion(value);
            if (Double.isNaN(value[0])) return Double.NaN;
            return value[0]-pv.relativeThicknessThreshold; // split cost : if > threshold difficult to split, if not easy to split
        }
    }

    @Override public double computeMergeCost(List<Object3D> objects) {
        if (pv==null) throw new Error("Segment method have to be called before merge method in order to initialize images");
        if (objects.isEmpty() || objects.size()==1) return 0;
        Iterator<Object3D> it = objects.iterator();
        Object3D ref  = objects.get(0);
        double maxCost = Double.NEGATIVE_INFINITY;
        double[] value = new double[1];
        while (it.hasNext()) { // first round : remove objects not connected with ref & compute interactions with ref objects
            Object3D n = it.next();
            if (n!=ref) {
                InterfaceBT inter = getInterface(ref, n);
                inter.checkFusion(value);
                if (value[0]>maxCost) maxCost = value[0];
            }
        }
        for (int i = 2; i<objects.size()-1; ++i) { // second round compute other interactions
            for (int j = i+1; j<objects.size(); ++j) {
                InterfaceBT inter = getInterface(objects.get(i), objects.get(j));
                inter.checkFusion(value);
                if (value[0]>maxCost) maxCost = value[0];
            }
        }
        if (maxCost==Double.NEGATIVE_INFINITY || maxCost==Double.NaN) return Double.NaN;
        return maxCost-pv.relativeThicknessThreshold;
    }
    
    private InterfaceBT getInterface(Object3D o1, Object3D o2) {
        o1.draw(pv.getSplitMask(), o1.getLabel());
        o2.draw(pv.splitMask, o2.getLabel());
        InterfaceBT inter = Object3DCluster.getInteface(o1, o2, pv.getSplitMask(), pv.getFactory());
        o1.draw(pv.splitMask, 0);
        o2.draw(pv.splitMask, 0);
        return inter;
    }
    
    // manual semgneter interface
    // manual correction implementations
    private boolean verboseManualSeg;
    public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }
    @Override public ObjectPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        ProcessingVariables pv = new ProcessingVariables(input, segmentationMask, this.relativeThicknessThreshold.getValue().doubleValue(), this.dogScale.getValue().doubleValue(), this.openRadius.getValue().doubleValue(), this.minSize.getValue().intValue());
        pv.threshold=100d; // TODO variable ou auto
        List<Object3D> seedObjects = ObjectFactory.createObjectsFromSeeds(seedsXYZ, input.getScaleXY(), input.getScaleZ());
        ImageOperations.and(segmentationMask, pv.getSegmentationMask(), pv.getSegmentationMask());
        ObjectPopulation pop =  WatershedTransform.watershed(pv.getEDM(), pv.getSegmentationMask(), seedObjects, true, null, null);
        
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixel(seed[0], seed[1], seed[2], 1);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(seedMap);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pv.getEDM());
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pv.getDOG());
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pop.getLabelMap().setName("segmented from: "+input.getName()));
        }
        
        return pop;
    }

    // object splitter interface
    boolean splitVerbose;
    public void setSplitVerboseMode(boolean verbose) {
        this.splitVerbose=verbose;
    }
    
    public ObjectPopulation splitObject(Image input, Object3D object) {
        Image edm = EDT.transform(object.getMask(), true, 1, input.getScaleZ()/input.getScaleXY(), 1).setName("edm");
        return WatershedObjectSplitter.splitInTwo(edm, object.getMask(), true, minSize.getValue().intValue(), splitVerbose);
    }
    
    protected static class ProcessingVariables {
        private Image distanceMap;
        private ImageInteger segMask;
        final ImageMask mask;
        final Image input;
        private Image dog;
        ImageByte splitMask;
        final double relativeThicknessThreshold, dogScale, openRadius; 
        final int minSize;
        double threshold = Double.NaN;
        Object3DCluster.InterfaceFactory<Object3D, InterfaceBT> factory;
        private ProcessingVariables(Image input, ImageMask mask, double splitThresholdValue, double dogScale, double openRadius, int minSize) {
            this.input=input;
            this.mask=mask;
            this.relativeThicknessThreshold=splitThresholdValue;
            this.dogScale=dogScale;
            this.openRadius=openRadius;
            this.minSize=minSize;
        }
        
        private ImageByte getSplitMask() {
            if (splitMask==null) splitMask = new ImageByte("split mask", input);
            return splitMask;
        }
        public InterfaceFactory<Object3D, InterfaceBT> getFactory() {
            if (factory==null) {
                factory = new Object3DCluster.InterfaceFactory<Object3D, InterfaceBT>() {
                    public InterfaceBT create(Object3D e1, Object3D e2, Comparator<? super Object3D> elementComparator) {
                        return new InterfaceBT(e1, e2);
                    }
                    
                };
            }
            return factory;
        }

        private Image getDOG() {
            if (dog==null) dog = ImageFeatures.differenceOfGaussians(input, 0, dogScale, 1, false).setName("DoG");
            return dog;
        }
        
        private ImageInteger getSegmentationMask() {
            if (segMask == null) {
                if (Double.isNaN(threshold)) throw new Error("Threshold not set");
                ImageInteger thresh = ImageOperations.threshold(getDOG(), threshold, false, false);
                Filters.binaryOpen(thresh, thresh, Filters.getNeighborhood(openRadius, openRadius, thresh));
                IJImageDisplayer disp = debug?new IJImageDisplayer():null;
                if (debug) disp.showImage(thresh.duplicate("before close"));
                thresh = Filters.binaryClose(thresh, Filters.getNeighborhood(1, 1, thresh));
                if (debug) disp.showImage(thresh.duplicate("after close"));
                ImageOperations.and(mask, thresh, thresh);
                ObjectPopulation pop1 = new ObjectPopulation(thresh, false);
                pop1.filter(new ObjectPopulation.Thickness().setX(2).setY(2)); // remove thin objects
                pop1.filter(new ObjectPopulation.Size().setMin(minSize)); // remove small objects
                if (debug) disp.showImage(pop1.getLabelMap().duplicate("first seg"));
                segMask = pop1.getLabelMap();
            }
            return segMask;
        }
        private Image getEDM() {
            if (distanceMap==null) {
                distanceMap = EDT.transform(getSegmentationMask(), true, 1, input.getScaleZ()/input.getScaleXY(), 1);
            }
            return distanceMap;
        }
        
        protected class InterfaceBT extends InterfaceObject3DImpl {
            double maxDistance=Double.NEGATIVE_INFINITY;
            Voxel maxVoxel;

            public InterfaceBT(Object3D e1, Object3D e2) {
                super(e1, e2);
            }

            @Override public void updateSortValue() {}

            @Override 
            public void fusionInterface(Interface<Object3D> otherInterface, Comparator<? super Object3D> elementComparator) {
                fusionInterfaceSetElements(otherInterface, elementComparator);
                InterfaceBT other = (InterfaceBT) otherInterface;
                if (other.maxDistance>maxDistance) {
                    this.maxDistance=other.maxDistance;
                    this.maxVoxel=other.maxVoxel;
                }
            }

            @Override
            public boolean checkFusion(double[] outputValues) {
                if (Double.isInfinite(maxDistance)) {
                    if (outputValues!=null && outputValues.length>=1) outputValues[0] = Double.NaN;
                    return false;
                }
                double max1 = Double.NEGATIVE_INFINITY;
                double max2 = Double.NEGATIVE_INFINITY;
                for (Voxel v : e1.getVoxels()) if (v.value>max1 && v.getDistanceSquare(maxVoxel, e1.getScaleXY(), e1.getScaleZ())<maxDistance) max1 = v.value;
                for (Voxel v : e2.getVoxels()) if (v.value>max2 && v.getDistanceSquare(maxVoxel, e1.getScaleXY(), e1.getScaleZ())<maxDistance) max2 = v.value;

                double norm = Math.min(max1, max2);
                if (Object3DCluster.verbose) logger.debug("interface: {}+{}, norm: {} maxInter: {}, criterion: {} threshold: {} fusion: {}", e1.getLabel(), e2.getLabel(), norm, maxDistance,maxDistance/norm, relativeThicknessThreshold, maxDistance/norm>relativeThicknessThreshold );
                if (outputValues!=null && outputValues.length>=1) outputValues[0] = maxDistance/norm;
                return maxDistance/norm>relativeThicknessThreshold;
            }

            @Override
            public void addPair(Voxel v1, Voxel v2) {
                addVoxel(getEDM(), v1);
                addVoxel(getEDM(), v2);
            }
            private void addVoxel(Image image, Voxel v) {
                double value =image.getPixel(v.x, v.y, v.z);
                if (value>maxDistance) {
                    maxDistance = value;
                    maxVoxel = v;
                }
            }

            public int compareTo(Interface<Object3D> t) {
                return Double.compare(maxDistance, ((InterfaceBT)t).maxDistance);
            }
        }
        
    }
    
    
}
