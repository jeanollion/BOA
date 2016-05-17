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
import configuration.parameters.GroupParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.Voxel;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
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
import plugins.Thresholder;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.segmenters.BacteriaTrans.ProcessingVariables.InterfaceBT;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackers.ObjectIdxTracker;
import processing.EDT;
import processing.FillHoles2D;
import processing.Filters;
import processing.FitEllipse;
import processing.FitEllipse.EllipseFit2D;
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
    
    
    //NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 2, 1, 6);
    NumberParameter openRadius = new BoundedNumberParameter("Open Radius", 1, 4, 0, null);
    NumberParameter minSizePropagation = new BoundedNumberParameter("Minimum size (propagation)", 0, 50, 5, null);
    NumberParameter dogScale = new BoundedNumberParameter("DoG scale", 0, 10, 5, null);
    NumberParameter thresholdForEmptyChannel = new BoundedNumberParameter("Threshold for empty channel", 1, 2, 0, null);
    PluginParameter<Thresholder> threshold = new PluginParameter<Thresholder>("DoG Threshold (separation from background)", Thresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false);
    GroupParameter backgroundSeparation = new GroupParameter("Separation from background", dogScale, threshold, thresholdForEmptyChannel, minSizePropagation, openRadius);
    
    NumberParameter relativeThicknessThreshold = new BoundedNumberParameter("Relative Thickness Threshold (lower: split more)", 2, 0.7, 0, 1);
    NumberParameter relativeThicknessMaxDistance = new BoundedNumberParameter("Max Distance for Relative Thickness normalization factor (calibrated)", 2, 1, 0, null);
    GroupParameter thicknessParameters = new GroupParameter("Constaint on thickness", relativeThicknessMaxDistance, relativeThicknessThreshold);
    
    NumberParameter aspectRatioThreshold = new BoundedNumberParameter("Aspect Ratio Threshold", 2, 1.5, 1, null);
    NumberParameter angleThreshold = new BoundedNumberParameter("Angle Threshold", 1, 20, 0, 90);
    GroupParameter angleParameters = new GroupParameter("Constaint on angles", aspectRatioThreshold, angleThreshold);
    
    NumberParameter contactLimit = new BoundedNumberParameter("Contact Threshold with X border", 0, 10, 0, null);
    NumberParameter minSize = new BoundedNumberParameter("Minimum Object size", 0, 150, 5, null);
    GroupParameter objectParameters = new GroupParameter("Constaint on segmented Objects", minSize, contactLimit);
    
    Parameter[] parameters = new Parameter[]{backgroundSeparation, thicknessParameters, angleParameters, objectParameters};
    
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
    /*public BacteriaTrans setSmoothScale(double smoothScale) {
        this.smoothScale.setValue(smoothScale);
        return this;
    }*/
    public BacteriaTrans setDogScale(int dogScale) {
        this.dogScale.setValue(dogScale);
        return this;
    }

    public BacteriaTrans setOpenRadius(double openRadius) {
        this.openRadius.setValue(openRadius);
        return this;
    }
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent, this.threshold.instanciatePlugin(), minSizePropagation.getValue().intValue(), minSize.getValue().intValue(), contactLimit.getValue().intValue(), 2, dogScale.getValue().doubleValue(), thresholdForEmptyChannel.getValue().doubleValue(), openRadius.getValue().doubleValue(), relativeThicknessThreshold.getValue().doubleValue(),relativeThicknessMaxDistance.getValue().doubleValue(), aspectRatioThreshold.getValue().doubleValue(), angleThreshold.getValue().doubleValue(), this);
    }
    
    @Override
    public String toString() {
        return "Bacteria Fluo: " + Utils.toStringArray(parameters);
    }   
    
    public static ObjectPopulation run(Image input, StructureObjectProcessing parent, Thresholder thresholder, int minSizePropagation, int minSize, int contactLimit, double smoothScale, double dogScale, double thresholdForEmptyChannel, double openRadius, final double relativeThicknessThreshold, double relativeThicknessMaxDistance, double aspectRatioThreshold, double angleThreshold, BacteriaTrans instance) {
        IJImageDisplayer disp=debug?new IJImageDisplayer():null;
        //double hessianThresholdFacto = 1;
        ImageMask mask = parent.getMask();
        ProcessingVariables pv = new ProcessingVariables(input, mask, relativeThicknessThreshold, relativeThicknessMaxDistance, dogScale, openRadius, smoothScale, aspectRatioThreshold, angleThreshold);
        pv.threshold = thresholder.runThresholder(pv.getDOG(), parent);
        if (debug) logger.debug("threshold: {}", pv.threshold);
        // criterion for empty channel: 
        double[] musigmaOver = getMeanAndSigma(pv.getDOG(), mask, pv.threshold, true);
        double[] musigmaUnder = getMeanAndSigma(pv.getDOG(), mask, pv.threshold, false);
        if (musigmaOver[2]==0 || musigmaUnder[2]==0) return new ObjectPopulation(input);
        else {            
            if (musigmaOver[0] - musigmaUnder[0]<thresholdForEmptyChannel) return new ObjectPopulation(input);
        }
        
        ObjectPopulation res = WatershedTransform.watershed(pv.getDOG(), pv.getSegmentationMask(), false, null, new WatershedTransform.SizeFusionCriterion(minSizePropagation));
        if (debug) disp.showImage(res.getLabelMap().duplicate("watershed EDM"));
        res.setVoxelIntensities(pv.getEDM()); // for merging & shape-re-split
        res = WatershedTransform.watershed(pv.getEDM(), pv.getSegmentationMask(), res.getExtremaSeedList(true), true, null, new WatershedTransform.SizeFusionCriterion(minSizePropagation));
        if (debug) {
            ImagePlus ip = disp.showImage(res.getLabelMap().duplicate("watershed EDM - shape re-split"));
            Overlay ov = new Overlay();
            EllipseFit2D prev=null;
            for (Object3D o : res.getObjects()) {
                EllipseFit2D el = FitEllipse.fitEllipse2D(o);
                ov.add(el.getAxisRoi());
                if (prev!=null) ov.add(el.getCenterRoi(prev));
                prev = el;
            }
            ip.setOverlay(ov);
            ip.updateAndDraw();
        }
        
        // merge using the criterion : max(EDM@frontière) / min(max(EDM@S1), max(EDM@S2)) > criterion
        Object3DCluster.verbose=debug;
        res.setVoxelIntensities(pv.getEDM());// for merging // useless if watershed transform on EDM has been called just before
        Object3DCluster.mergeSort(res,  pv.getFactory());
        res.filter(new ObjectPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        res.filter(new ObjectPopulation.Size().setMin(minSize)); // remove small objects
        if (debug) {
            disp.showImage(pv.getDOG().setName("DOG"));
            disp.showImage(pv.getEDM());
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
        if (instance!=null) instance.pv=pv; // for further use : SegmenterSplitAndMerge
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
        ObjectPopulation pop = WatershedObjectSplitter.splitInTwo(pv.getSmoothed(), pv.splitMask, false, minSize.getValue().intValue(), false); // TODO minSize Propagation
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
            inter.checkFusion();
            if (Double.isNaN(inter.value)) return Double.NaN;
            return inter.value-pv.relativeThicknessThreshold; // split cost : if > threshold difficult to split, if not easy to split
        }
    }

    @Override public double computeMergeCost(List<Object3D> objects) {
        if (pv==null) throw new Error("Segment method have to be called before merge method in order to initialize images");
        if (objects.isEmpty() || objects.size()==1) return 0;
        Iterator<Object3D> it = objects.iterator();
        Object3D ref  = objects.get(0);
        double maxCost = Double.NEGATIVE_INFINITY;
        while (it.hasNext()) { // first round : remove objects not connected with ref & compute interactions with ref objects
            Object3D n = it.next();
            if (n!=ref) {
                InterfaceBT inter = getInterface(ref, n);
                inter.checkFusion();
                if (inter.value>maxCost) maxCost = inter.value;
            }
        }
        for (int i = 2; i<objects.size()-1; ++i) { // second round compute other interactions
            for (int j = i+1; j<objects.size(); ++j) {
                InterfaceBT inter = getInterface(objects.get(i), objects.get(j));
                inter.checkFusion();
                if (inter.value>maxCost) maxCost = inter.value;
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
        ProcessingVariables pv = new ProcessingVariables(input, segmentationMask, relativeThicknessThreshold.getValue().doubleValue(), relativeThicknessMaxDistance.getValue().doubleValue(), this.dogScale.getValue().doubleValue(), this.openRadius.getValue().doubleValue(), aspectRatioThreshold.getValue().doubleValue(), angleThreshold.getValue().doubleValue(), 2);
        pv.threshold = threshold.instanciatePlugin().runThresholder(pv.getDOG(), parent);
        //pv.threshold=100d; // TODO variable ou auto
        List<Object3D> seedObjects = ObjectFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getScaleXY(), input.getScaleZ());
        ImageOperations.and(segmentationMask, pv.getSegmentationMask(), pv.getSegmentationMask());
        ObjectPopulation pop = WatershedTransform.watershed(pv.getSmoothed(), pv.getSegmentationMask(), seedObjects, false, null, null);
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
        ProcessingVariables pv = new ProcessingVariables(input, object.getMask(), relativeThicknessThreshold.getValue().doubleValue(), relativeThicknessMaxDistance.getValue().doubleValue(), this.dogScale.getValue().doubleValue(), this.openRadius.getValue().doubleValue(), aspectRatioThreshold.getValue().doubleValue(), angleThreshold.getValue().doubleValue(), 2);
        return WatershedObjectSplitter.splitInTwo(pv.getSmoothed(), object.getMask(), false, minSize.getValue().intValue(), splitVerbose); // TODO minSize Propagation?
    }
    
    protected static class ProcessingVariables {
        private Image distanceMap;
        private ImageInteger segMask;
        final ImageMask mask;
        final Image input;
        private Image dog;
        private Image smoothed;
        ImageByte splitMask;
        final double relativeThicknessThreshold, dogScale, openRadius, relativeThicknessMaxDistance, smoothScale, aspectRatioThreshold, angleThresholdRad; 
        double threshold = Double.NaN;
        Object3DCluster.InterfaceFactory<Object3D, InterfaceBT> factory;
        private ProcessingVariables(Image input, ImageMask mask, double splitThresholdValue, double relativeThicknessMaxDistance, double dogScale, double openRadius, double smoothScale, double aspectRatioThreshold, double angleThresholdDeg) {
            this.input=input;
            this.mask=mask;
            this.relativeThicknessThreshold=splitThresholdValue;
            this.relativeThicknessMaxDistance=relativeThicknessMaxDistance;
            this.smoothScale=smoothScale;
            this.dogScale=dogScale;
            this.openRadius=openRadius;
            this.aspectRatioThreshold=aspectRatioThreshold;
            this.angleThresholdRad = angleThresholdDeg * Math.PI / 180d ;
        }
        private Image getSmoothed() {
            if (smoothed==null) smoothed = ImageFeatures.gaussianSmooth(input, smoothScale, smoothScale, false);
            return smoothed;
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
                //if (debug) disp.showImage(thresh.duplicate("before close"));
                //thresh = Filters.binaryClose(thresh, Filters.getNeighborhood(1, 1, thresh));
                //if (debug) disp.showImage(thresh.duplicate("after close"));
                ImageOperations.and(mask, thresh, thresh);
                ObjectPopulation pop1 = new ObjectPopulation(thresh, false);
                pop1.filter(new ObjectPopulation.Thickness().setX(2).setY(2)); // remove thin objects
                FillHoles2D.fillHoles(pop1);
                if (debug) disp.showImage(pop1.getLabelMap().duplicate("SEG MASK"));
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
            Voxel maxVoxel=null;
            double value=Double.NaN;
            private FitEllipse.EllipseFit2D ell1, ell2; // lazy loading
            public InterfaceBT(Object3D e1, Object3D e2) {
                super(e1, e2);
            }
            public FitEllipse.EllipseFit2D getEllipseFit1() {
                if (ell1==null) ell1 = FitEllipse.fitEllipse2D(e1);
                return ell1;
            }
            public FitEllipse.EllipseFit2D getEllipseFit2() {
                if (ell2==null) ell2 = FitEllipse.fitEllipse2D(e2);
                return ell2;
            }
            @Override public void updateSortValue() {}
            @Override
            public void performFusion() {
                super.performFusion();
                ell1=null;
                ell2=null;
            }
            @Override 
            public void fusionInterface(Interface<Object3D> otherInterface, Comparator<? super Object3D> elementComparator) {
                fusionInterfaceSetElements(otherInterface, elementComparator);
                InterfaceBT other = (InterfaceBT) otherInterface;
                if (other.maxDistance>maxDistance) {
                    this.maxDistance=other.maxDistance;
                    this.maxVoxel=other.maxVoxel;
                }
                ell1=null;
                ell2=null;
            }

            @Override
            public boolean checkFusion() {
                if (maxVoxel==null) return false;

                // criterion angle between two 
                // if aspect ratio is no elevated, angle is not taken into account
                // look @ angles between major axis and center-center
                // if both angles are opposed, it could be one single curved object, thus if angles are in same direction their sum is considered (penalty) 
                double[] al12 = getEllipseFit1().getAlignement(getEllipseFit2());
                double ar1 = getEllipseFit1().getAspectRatio();
                double al1 = ar1>aspectRatioThreshold ? al12[0] : 0;
                double ar2 = getEllipseFit2().getAspectRatio();
                double al2 = ar2>aspectRatioThreshold ? al12[1] : 0;
                double al = (al1*al2>0) ? Math.abs(al1+al2) : Math.max(Math.abs(al1), Math.abs(al2));
                if (Object3DCluster.verbose) logger.debug("interface: {}+{}, Final Alignement: {}, AspectRatio1: {} Alignement1: {}, AspectRatio1: {} Alignement1: {}", e1.getLabel(), e2.getLabel(), al*180d/Math.PI, ar1, al1 * 180d/Math.PI, ar2, al2 * 180d/Math.PI);
                if (al>angleThresholdRad) return false;
                
                
                double max1 = Double.NEGATIVE_INFINITY;
                double max2 = Double.NEGATIVE_INFINITY;
                for (Voxel v : e1.getVoxels()) if (v.value>max1 && v.getDistance(maxVoxel, e1.getScaleXY(), e1.getScaleZ())<relativeThicknessMaxDistance) max1 = v.value;
                for (Voxel v : e2.getVoxels()) if (v.value>max2 && v.getDistance(maxVoxel, e1.getScaleXY(), e1.getScaleZ())<relativeThicknessMaxDistance) max2 = v.value;
                
                double norm = Math.min(max1, max2);
                value = maxDistance/norm;
                if (Object3DCluster.verbose) logger.debug("interface: {}+{}, norm: {} maxInter: {}, criterion: {} threshold: {} fusion: {}, scale: {}", e1.getLabel(), e2.getLabel(), norm, maxDistance,value, relativeThicknessThreshold, value>relativeThicknessThreshold, e1.getScaleXY() );
                return  value>relativeThicknessThreshold;
            }

            @Override
            public void addPair(Voxel v1, Voxel v2) {
                addVoxel(getEDM(), v1);
                addVoxel(getEDM(), v2);
            }
            private void addVoxel(Image image, Voxel v) {
                double pixVal =image.getPixel(v.x, v.y, v.z);
                if (pixVal>maxDistance) {
                    maxDistance = pixVal;
                    maxVoxel = v;
                }
            }

            public int compareTo(Interface<Object3D> t) {
                return Double.compare(((InterfaceBT)t).maxDistance, maxDistance);
            }
        }
        
    }
    
    
}
