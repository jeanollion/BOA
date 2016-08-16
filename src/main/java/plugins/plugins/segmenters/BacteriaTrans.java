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
import ij.gui.Roi;
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
import image.ImageProperties;
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
import net.imglib2.KDTree;
import net.imglib2.Point;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import plugins.ParameterSetup;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.Thresholder;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.segmenters.BacteriaTrans.ProcessingVariables.InterfaceBT;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackers.ObjectIdxTracker;
import processing.Curvature;
import processing.EDT;
import processing.FillHoles2D;
import processing.Filters;
import processing.FitEllipse;
import processing.FitEllipse.EllipseFit2D;
import processing.Curvature;
import static processing.Curvature.computeCurvature;
import static processing.Curvature.getCurvatureMask;
import processing.ImageFeatures;
import processing.WatershedTransform;
import processing.neighborhood.EllipsoidalNeighborhood;
import processing.neighborhood.Neighborhood;
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
public class BacteriaTrans implements SegmenterSplitAndMerge, ManualSegmenter, ObjectSplitter, ParameterSetup {
    public static boolean debug = false;
    
    // configuration-related attributes
    
    
    //NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 2, 1, 6);
    NumberParameter openRadius = new BoundedNumberParameter("Open Radius", 1, 4, 0, null);
    NumberParameter minSizePropagation = new BoundedNumberParameter("Minimum size (propagation)", 0, 50, 5, null);
    NumberParameter dogScale = new BoundedNumberParameter("DoG scale", 0, 10, 5, null);
    NumberParameter thresholdForEmptyChannel = new BoundedNumberParameter("Threshold for empty channel", 1, 2, 0, null);
    //PluginParameter<Thresholder> threshold = new PluginParameter<Thresholder>("DoG Threshold (separation from background)", Thresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false);
    PluginParameter<Thresholder> threshold = new PluginParameter<Thresholder>("DoG Threshold (separation from background)", Thresholder.class, new ConstantValue(50), false);
    GroupParameter backgroundSeparation = new GroupParameter("Separation from background", dogScale, threshold, thresholdForEmptyChannel, minSizePropagation, openRadius);
    
    NumberParameter relativeThicknessThreshold = new BoundedNumberParameter("Relative Thickness Threshold (lower: split more)", 2, 0.7, 0, 1);
    NumberParameter relativeThicknessMaxDistance = new BoundedNumberParameter("Max Distance for Relative Thickness normalization factor (calibrated)", 2, 1, 0, null);
    GroupParameter thicknessParameters = new GroupParameter("Constaint on thickness", relativeThicknessMaxDistance, relativeThicknessThreshold);
    
    NumberParameter curvatureThreshold = new BoundedNumberParameter("Curvature Threshold (lower merge more)", 2, 1, null, 0);
    NumberParameter curvatureScale = new BoundedNumberParameter("Curvature scale", 0, 6, 3, null);
    NumberParameter curvatureSearchRadius = new BoundedNumberParameter("Radius for min. search", 1, 2.5, 1, null);
    GroupParameter curvatureParameters = new GroupParameter("Constaint on curvature", curvatureScale, curvatureThreshold, curvatureSearchRadius);
    
    /*NumberParameter aspectRatioThreshold = new BoundedNumberParameter("Aspect Ratio Threshold", 2, 1.5, 1, null);
    NumberParameter angleThreshold = new BoundedNumberParameter("Angle Threshold", 1, 20, 0, 90);
    GroupParameter angleParameters = new GroupParameter("Constaint on angles", aspectRatioThreshold, angleThreshold);
    */
    NumberParameter contactLimit = new BoundedNumberParameter("Contact Threshold with X border", 0, 10, 0, null);
    NumberParameter minSize = new BoundedNumberParameter("Minimum Object size", 0, 250, 5, null);
    GroupParameter objectParameters = new GroupParameter("Constaint on segmented Objects", minSize, contactLimit);
    
    Parameter[] parameters = new Parameter[]{backgroundSeparation, thicknessParameters, curvatureParameters, objectParameters};
    
    @Override public boolean canBeTested(Parameter p) {
        List canBeTested = new ArrayList(){{add(threshold); add(curvatureScale); add(dogScale); add(relativeThicknessThreshold);}};
        return canBeTested.contains(p);
    }

    @Override public void test(Parameter p, Image input, int structureIdx, StructureObjectProcessing parent) {
        debug=false;
        ImageDisplayer disp = ImageWindowManagerFactory.instanciateDisplayer();
        pv = getProcessingVariables(input, parent.getMask());
        pv.threshold = this.threshold.instanciatePlugin().runThresholder(pv.getDOG(), parent);
        if (p == relativeThicknessThreshold) {
            logger.debug("rel t test");
            ObjectPopulation pop = getSeparatedObjects(pv, pv.getSegmentationMask(), minSize.getValue().intValue(), 0, false);
            disp.showImage(pv.getSegmentationMask().duplicate("before merging"));
            disp.showImage(pop.getLabelMap().duplicate("after merging"));
        }
        else if (p==curvatureScale) {
            logger.debug("cur test");
            disp.showImage(pv.getSegmentationMask().duplicate("segmentation mask"));
            
            ObjectPopulation popSplit = getSeparatedObjects(pv, pv.getSegmentationMask(), minSize.getValue().intValue(), 0, false);
            disp.showImage(getCurvatureImage(new ObjectPopulation(pv.getSegmentationMask(), true), curvatureScale.getValue().intValue()));
            disp.showImage(pv.splitSegmentationMask(pv.getSegmentationMask()).getLabelMap().setName("after split"));
            disp.showImage(popSplit.getLabelMap().duplicate("after merge"));
        } else if (p==threshold) {
            disp.showImage(pv.getDOG().duplicate("before threshold. Value: "+pv.threshold));
            disp.showImage(pv.getSegmentationMask().duplicate("after threshold"));
        } else if (p==dogScale) {
            disp.showImage(input.duplicate("input"));
            disp.showImage(pv.getDOG().duplicate("DoG: Scale: "+dogScale.getValue().doubleValue()));
        }
    }
    
    private static Image getCurvatureImage(ObjectPopulation pop, int curvatureScale) {
        ImageFloat curv = new ImageFloat("Curvature map: "+curvatureScale, pop.getImageProperties()).resetOffset();
        pop.getObjects().stream().map((o) -> computeCurvature(o.getMask(), curvatureScale)).forEach((tree) -> { Curvature.drawOnCurvatureMask(curv, tree); });
        return curv;
    }
    
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

    
    @Override
    public String toString() {
        return "Bacteria Trans: " + Utils.toStringArray(parameters);
    }   
    
    public ProcessingVariables getProcessingVariables(Image input, ImageMask segmentationMask) {
        return new ProcessingVariables(input, segmentationMask, 
                relativeThicknessThreshold.getValue().doubleValue(), relativeThicknessMaxDistance.getValue().doubleValue(), 
                dogScale.getValue().doubleValue(), openRadius.getValue().doubleValue(), minSizePropagation.getValue().intValue(), minSize.getValue().intValue(),
                curvatureScale.getValue().intValue(), curvatureThreshold.getValue().doubleValue(), curvatureSearchRadius.getValue().doubleValue());
    }
    
    @Override public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        /*
            1) compute forground mask & check if there are objects inside
            2) watershed on DOG -> produce oversegmentation
            3) watershed on EDM using seeds from previous watershed in order to fit interfaces to morphology
            4) merge using criterion on curvature & relative thickness
            
        */
        double thresholdForEmptyChannel = this.thresholdForEmptyChannel.getValue().doubleValue();
        pv = getProcessingVariables(input, parent.getMask());
        //double hessianThresholdFacto = 1;
        ImageMask mask = parent.getMask();
        pv.threshold = this.threshold.instanciatePlugin().runThresholder(pv.getDOG(), parent);
        if (debug) {
            new IJImageDisplayer().showImage(input.setName("input"));
            logger.debug("threshold: {}", pv.threshold);
        }
        // criterion for empty channel: 
        double[] musigmaOver = getMeanAndSigma(pv.getDOG(), mask, pv.threshold, true);
        double[] musigmaUnder = getMeanAndSigma(pv.getDOG(), mask, pv.threshold, false);
        if (musigmaOver[2]==0 || musigmaUnder[2]==0) return new ObjectPopulation(input);
        else {            
            if (musigmaOver[0] - musigmaUnder[0]<thresholdForEmptyChannel) return new ObjectPopulation(input);
        }
        return getSeparatedObjects(pv, pv.getSegmentationMask(), minSize.getValue().intValue(), 0, debug);
    }
    
    protected static ObjectPopulation getSeparatedObjects(ProcessingVariables pv, ImageInteger segmentationMask, int minSize, int objectMergeLimit, boolean debug) {
        //if (BacteriaTrans.debug) debug=true;
        IJImageDisplayer disp=debug?new IJImageDisplayer():null;
        ObjectPopulation res = pv.splitSegmentationMask(segmentationMask);
        if (debug) {
            disp.showImage(pv.getEDM());
        }
        if (debug) {
            ImagePlus ip = disp.showImage(res.getLabelMap().duplicate("labelMap - shape re-split"));
            disp.showImage(getCurvatureImage(new ObjectPopulation(segmentationMask, true), pv.curvatureScale));
            //display ellipses..
            /*Overlay ov = new Overlay();
            EllipseFit2D prev=null;
            for (Object3D o : res.getObjects()) {
                EllipseFit2D el = FitEllipse.fitEllipse2D(o);
                ov.add(el.getAxisRoi());
                if (prev!=null) ov.add(el.getCenterRoi(prev));
                prev = el;
            }
            ip.setOverlay(ov);
            ip.updateAndDraw();*/
        }
        
        // merge using the criterion
        Object3DCluster.verbose=debug;
        //res.setVoxelIntensities(pv.getEDM());// for merging // useless if watershed transform on EDM has been called just before
        Object3DCluster.mergeSort(res,  pv.getFactory(), objectMergeLimit<=1, 0, objectMergeLimit);
        
        res.filterAndMergeWithConnected(new ObjectPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        res.filterAndMergeWithConnected(new ObjectPopulation.Size().setMin(minSize)); // remove small objects
        if (debug) {
            disp.showImage(pv.getDOG().setName("DOG"));
            //disp.showImage(pv.getEDM());
            //disp.showImage(res.getLabelMap().setName("seg map after fusion"));
            //disp.showImage(cluster.drawInterfacesSortValue(sm));
            //disp.showImage(cluster.drawInterfaces());
            //FitEllipse.fitEllipse2D(pop1.getObjects().get(0));
            //FitEllipse.fitEllipse2D(pop1.getObjects().get(1));
        }
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
    
    public static double getCost(double value, double threshold, boolean valueShouldBeBelowThresholdForAPositiveCost)  {
        if (valueShouldBeBelowThresholdForAPositiveCost) {
            if (value>=threshold) return 0;
            else return (threshold-value);
        } else {
            if (value<=threshold) return 0;
            else return (value-threshold);
        }
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    // segmenter split and merge interface
    @Override public double split(Object3D o, List<Object3D> result) {
        if (pv==null) throw new Error("Segment method have to be called before split method in order to initialize images");
        synchronized(pv) {
            o.draw(pv.getSplitMask(), 1);
            ObjectPopulation pop = BacteriaTrans.getSeparatedObjects(pv, pv.getSplitMask(), minSize.getValue().intValue(), 2, false);
            o.draw(pv.splitMask, 0);
            if (pop==null || pop.getObjects().isEmpty() || pop.getObjects().size()==1) return Double.POSITIVE_INFINITY;
            ArrayList<Object3D> remove = new ArrayList<Object3D>(pop.getObjects().size());
            pop.filter(new ObjectPopulation.Thickness().setX(2).setY(2), remove); // remove thin objects ?? merge? 
            pop.filter(new ObjectPopulation.Size().setMin(minSize.getValue().intValue()), remove); // remove small objects ?? merge? 
            if (pop.getObjects().size()<=1) return Double.POSITIVE_INFINITY;
            else {
                if (!remove.isEmpty()) pop.mergeWithConnected(remove);
                Object3D o1 = pop.getObjects().get(0);
                Object3D o2 = pop.getObjects().get(1);
                result.add(o1);
                result.add(o2);
                InterfaceBT inter = getInterface(o1, o2);
                inter.updateSortValue();
                //logger.debug("split: intersize: {}, cost {}", inter.voxels.size(), inter.curvatureValue);
                return getCost(inter.curvatureValue, pv.curvatureThreshold, false); // TODO faire un min avec la courbure
            }
            
        }
    }

    @Override public double computeMergeCost(List<Object3D> objects) {
        if (pv==null) throw new Error("Segment method have to be called before merge method in order to initialize images");
        if (objects.isEmpty() || objects.size()==1) return 0;
        synchronized(pv) {
            Iterator<Object3D> it = objects.iterator();
            Object3D ref  = objects.get(0);
            double minCurv = Double.POSITIVE_INFINITY;
            while (it.hasNext()) { // first round : remove objects not connected with ref & compute interactions with ref objects
                Object3D n = it.next();
                if (n!=ref) {
                    InterfaceBT inter = getInterface(ref, n);
                    if (!inter.voxels.isEmpty()) {
                        inter.updateSortValue();
                        if (inter.curvatureValue<minCurv) minCurv = inter.curvatureValue;
                    }
                }
            }
            for (int i = 2; i<objects.size()-1; ++i) { // second round compute other interactions
                for (int j = i+1; j<objects.size(); ++j) {
                    InterfaceBT inter = getInterface(objects.get(i), objects.get(j));
                    if (!inter.voxels.isEmpty()) {
                        inter.updateSortValue();
                        if (inter.curvatureValue<minCurv) minCurv = inter.curvatureValue;
                    }
                }
            }
            if (minCurv==Double.POSITIVE_INFINITY || minCurv==Double.NaN) return Double.POSITIVE_INFINITY;
            return getCost(minCurv, pv.curvatureThreshold, true);
            //return minCurv-pv.relativeThicknessThreshold;
        }
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
    @Override public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }
    @Override public ObjectPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        ProcessingVariables pv = getProcessingVariables(input, segmentationMask);
        pv.threshold = threshold.instanciatePlugin().runThresholder(pv.getDOG(), parent);
        //pv.threshold=100d; // TODO variable ou auto
        List<Object3D> seedObjects = ObjectFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getScaleXY(), input.getScaleZ());
        ImageOperations.and(segmentationMask, pv.getSegmentationMask(), pv.getSegmentationMask());
        ObjectPopulation pop = WatershedTransform.watershed(pv.getDOG(), pv.getSegmentationMask(), seedObjects, false, null, null);
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
    @Override
    public void setSplitVerboseMode(boolean verbose) {
        this.splitVerbose=verbose;
    }
    
    @Override
    public ObjectPopulation splitObject(Image input, Object3D object) {
        ProcessingVariables pv = getProcessingVariables(input, object.getMask());
        pv.segMask=object.getMask(); // no need to compute threshold because split is performed within object's mask
        return BacteriaTrans.getSeparatedObjects(pv, pv.segMask, minSize.getValue().intValue(), 2, splitVerbose);
    }
    
    protected static class ProcessingVariables {
        private Image distanceMap;
        private ImageInteger segMask;
        final ImageMask mask;
        final Image input;
        private Image dog;
        //private Image smoothed;
        ImageByte splitMask;
        final double relativeThicknessThreshold, dogScale, openRadius, relativeThicknessMaxDistance;//, smoothScale;// aspectRatioThreshold, angleThresholdRad; 
        double threshold = Double.NaN;
        final int curvatureScale, minSizePropagation, minSize;
        final double curvatureSearchScale;
        final double curvatureThreshold;
        Object3DCluster.InterfaceFactory<Object3D, InterfaceBT> factory;
        private ProcessingVariables(Image input, ImageMask mask, double splitThresholdValue, double relativeThicknessMaxDistance, double dogScale, double openRadius, int minSizePropagation, int minSize, int curvatureScale, double curvatureThreshold, double curvatureSearchRadius) {
            this.input=input;
            this.mask=mask;
            this.relativeThicknessThreshold=splitThresholdValue;
            this.relativeThicknessMaxDistance=relativeThicknessMaxDistance;
            //this.smoothScale=smoothScale;
            this.dogScale=dogScale;
            this.openRadius=openRadius;
            this.minSizePropagation=minSizePropagation;
            this.minSize=minSize;
            //this.aspectRatioThreshold=aspectRatioThreshold;
            //this.angleThresholdRad = angleThresholdDeg * Math.PI / 180d ;
            this.curvatureScale = curvatureScale;
            curvatureSearchScale = curvatureSearchRadius;
            this.curvatureThreshold=curvatureThreshold;
        }
        /*private Image getSmoothed() {
            if (smoothed==null) smoothed = ImageFeatures.gaussianSmooth(input, smoothScale, smoothScale, false);
            return smoothed;
        }*/
        private ImageByte getSplitMask() {
            if (splitMask==null) splitMask = new ImageByte("split mask", input);
            return splitMask;
        }
        public InterfaceFactory<Object3D, InterfaceBT> getFactory() {
            if (factory==null) factory = (Object3D e1, Object3D e2, Comparator<? super Object3D> elementComparator) -> new InterfaceBT(e1, e2);
            return factory;
        }

        private Image getDOG() {
            if (dog==null) dog = ImageFeatures.differenceOfGaussians(input, 0, dogScale, 1, false).setName("DoG");
            return dog;
        }
        private ObjectPopulation splitSegmentationMask(ImageInteger maskToSplit) {
            ObjectPopulation res = WatershedTransform.watershed(getDOG(), maskToSplit, false, null, new WatershedTransform.SizeFusionCriterion(minSizePropagation));
            res.setVoxelIntensities(getEDM()); // for getExtremaSeedList method called just afterwards
            return WatershedTransform.watershed(getEDM(), maskToSplit, res.getExtremaSeedList(true), true, null, new WatershedTransform.SizeFusionCriterion(minSizePropagation));
        }
        private ImageInteger getSegmentationMask() {
            if (segMask == null) {
                if (Double.isNaN(threshold)) throw new Error("Threshold not set");
                ImageInteger thresh = ImageOperations.threshold(getDOG(), threshold, false, false);
                Filters.binaryOpen(thresh, thresh, Filters.getNeighborhood(openRadius, openRadius, thresh));
                //IJImageDisplayer disp = debug?new IJImageDisplayer():null;
                //if (debug) disp.showImage(thresh.duplicate("before close"));
                thresh = Filters.binaryClose(thresh, Filters.getNeighborhood(1, 1, thresh));
                //if (debug) disp.showImage(thresh.duplicate("after close"));
                ImageOperations.and(mask, thresh, thresh);
                ObjectPopulation pop1 = new ObjectPopulation(thresh, false);
                pop1.filterAndMergeWithConnected(new ObjectPopulation.Thickness().setX(2).setY(2)); // remove thin objects
                FillHoles2D.fillHoles(pop1);
                //if (debug) disp.showImage(pop1.getLabelMap().duplicate("SEG MASK"));
                
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
        
        protected class InterfaceBT extends InterfaceObject3DImpl<InterfaceBT> {
            double maxDistance=Double.NEGATIVE_INFINITY;
            double curvatureValue=Double.POSITIVE_INFINITY;
            double relativeThickNess = Double.NEGATIVE_INFINITY;
            Voxel maxVoxel=null;
            double value=Double.NaN;
            //private FitEllipse.EllipseFit2D ell1, ell2; // lazy loading
            private KDTree<Double> curvature;
            private final Set<Voxel> borderVoxels, borderVoxels2;
            private final Set<Voxel> voxels;
            private final Neighborhood borderNeigh;
            private ImageInteger joinedMask;
            public InterfaceBT(Object3D e1, Object3D e2) {
                super(e1, e2);
                borderVoxels = new HashSet<Voxel>();
                borderVoxels2 = new HashSet<Voxel>();
                borderNeigh = new EllipsoidalNeighborhood(1.5, true);
                voxels = new HashSet<Voxel>();
            }
            private ImageInteger getJoinedMask() {
                if (joinedMask==null) {
                    // getJoinedMask of 2 objects
                    ImageInteger m1 = e1.getMask();
                    ImageInteger m2 = e2.getMask();
                    BoundingBox joinBox = m1.getBoundingBox(); 
                    joinBox.expand(m2.getBoundingBox());
                    ImageByte mask = new ImageByte("joinedMask:"+e1.getLabel()+"+"+e2.getLabel(), joinBox.getImageProperties()).setCalibration(m1);
                    ImageOperations.pasteImage(m1, mask, m1.getBoundingBox().translate(mask.getBoundingBox().reverseOffset()));
                    ImageOperations.orWithOffset(m2, mask, mask);
                    joinedMask = mask;
                }
                return joinedMask;
            }
            public KDTree<Double> getCurvature() {
                if (curvature==null) {
                    setBorderVoxels();
                    if (curvatureValue!=Double.NEGATIVE_INFINITY) curvature = Curvature.computeCurvature(getJoinedMask(), curvatureScale);
                    if (debug && ((e1.getLabel()==1 && e2.getLabel()==2))) {
                        ImageInteger m = getJoinedMask().duplicate("joinedMask:"+e1.getLabel()+"+"+e2.getLabel()+" (2)");
                        for (Voxel v : voxels) m.setPixelWithOffset(v.x, v.y, v.z, 2);
                        for (Voxel v : borderVoxels) m.setPixelWithOffset(v.x, v.y, v.z, 3);
                        for (Voxel v : borderVoxels2) m.setPixelWithOffset(v.x, v.y, v.z, 4);
                        new IJImageDisplayer().showImage(m);
                        Curvature.displayCurvature(m, curvatureScale);
                    }
                }
                return curvature;
            }
            /*public FitEllipse.EllipseFit2D getEllipseFit1() {
                if (ell1==null) ell1 = FitEllipse.fitEllipse2D(e1);
                return ell1;
            }
            public FitEllipse.EllipseFit2D getEllipseFit2() {
                if (ell2==null) ell2 = FitEllipse.fitEllipse2D(e2);
                return ell2;
            }*/
            @Override public void updateSortValue() {
                if (voxels.isEmpty()) curvatureValue=Double.POSITIVE_INFINITY;
                else if (getCurvature()!=null) curvatureValue = getMeanOfMinCurvature(); 
            }
            @Override
            public void performFusion() {
                super.performFusion();
            }
            @Override 
            public void fusionInterface(InterfaceBT otherInterface, Comparator<? super Object3D> elementComparator) {
                //fusionInterfaceSetElements(otherInterface, elementComparator);
                if (otherInterface.maxDistance>maxDistance) {
                    this.maxDistance=otherInterface.maxDistance;
                    this.maxVoxel=otherInterface.maxVoxel;
                }
                //ell1=null;
                //ell2=null;
                curvature = null;
                joinedMask=null;
                voxels.addAll(otherInterface.voxels);
                // border voxels will be set at next creation of curvature
            }

            @Override
            public boolean checkFusion() {
                if (maxVoxel==null) return false;
                if (this.voxels.isEmpty()) return false;
                // criterion on size
                if (this.e1.getSize()<minSize || this.e2.getSize()<minSize) return true;
                
                // criterion angle between two fitted ellipses
                // if aspect ratio is no elevated, angle is not taken into account
                // look @ angles between major axis and center-center
                // if both angles are opposed, it could be one single curved object, thus if angles are in same direction their sum is considered (penalty) 
                /*double[] al12 = getEllipseFit1().getAlignement(getEllipseFit2());
                double ar1 = getEllipseFit1().getAspectRatio();
                double al1 = ar1>aspectRatioThreshold ? al12[0] : 0;
                double ar2 = getEllipseFit2().getAspectRatio();
                double al2 = ar2>aspectRatioThreshold ? al12[1] : 0;
                double al = (al1*al2>0) ? Math.abs(al1+al2) : Math.max(Math.abs(al1), Math.abs(al2));
                if (Object3DCluster.verbose) logger.debug("interface: {}+{}, Final Alignement: {}, AspectRatio1: {} Alignement1: {}, AspectRatio1: {} Alignement1: {}", e1.getLabel(), e2.getLabel(), al*180d/Math.PI, ar1, al1 * 180d/Math.PI, ar2, al2 * 180d/Math.PI);
                if (al>angleThresholdRad) return false;
                */
                // criterion on curvature
                // curvature has been computed @ upadateSortValue
                if (debug) logger.debug("interface: {}+{}, Mean curvature: {}, Threshold: {}", e1.getLabel(), e2.getLabel(), curvatureValue, curvatureThreshold);
                if (curvatureValue<curvatureThreshold) return false;
                
                double max1 = Double.NEGATIVE_INFINITY;
                double max2 = Double.NEGATIVE_INFINITY;
                for (Voxel v : e1.getVoxels()) if (v.value>max1 && v.getDistance(maxVoxel, e1.getScaleXY(), e1.getScaleZ())<relativeThicknessMaxDistance) max1 = v.value;
                for (Voxel v : e2.getVoxels()) if (v.value>max2 && v.getDistance(maxVoxel, e1.getScaleXY(), e1.getScaleZ())<relativeThicknessMaxDistance) max2 = v.value;
                
                double norm = Math.min(max1, max2);
                value = maxDistance/norm;
                if (Object3DCluster.verbose) logger.debug("interface: {}+{}, norm: {} maxInter: {}, criterion: {} threshold: {} fusion: {}, scale: {}", e1.getLabel(), e2.getLabel(), norm, maxDistance,value, relativeThicknessThreshold, value>relativeThicknessThreshold, e1.getScaleXY() );
                return  value>relativeThicknessThreshold;
            }
            
            /*public double getMeanCurvature() {
                if (borderVoxels.isEmpty()) return 0;
                double mean = 0;
                getCurvature();
                for(Voxel v : borderVoxels) {
                    curvature.search(new Point(new int[]{v.x, v.y}));
                    if (curvature.getDistance()<=2) mean+=curvature.getSampler().get(); // distance? // min de chaque coté? -> faire 2 clusters..
                
                }
                return mean/=borderVoxels.size();
            }*/
            private double getMinCurvature(Collection<Voxel> voxels) { // returns positive infinity if no border
                RadiusNeighborSearchOnKDTree<Double> search = new RadiusNeighborSearchOnKDTree(getCurvature());
                if (voxels.isEmpty()) return 0;
                double min = Double.POSITIVE_INFINITY;
                getCurvature();
                for(Voxel v : voxels) {
                    search.search(new Point(new int[]{v.x, v.y}), curvatureSearchScale, false);
                    for (int i = 0; i<search.numNeighbors(); ++i) {
                        Double d = search.getSampler(i).get();
                        if (min>d) min = d;
                    }
                }
                //if (Double.isInfinite(min)) return Double.NaN;
                return min;
            }
            public double getMeanOfMinCurvature() {
                
                if (borderVoxels.isEmpty() && borderVoxels2.isEmpty()) return 0;
                else {    
                    if (borderVoxels2.isEmpty()) return getMinCurvature(borderVoxels);
                    else return 0.5 * (getMinCurvature(borderVoxels)+ getMinCurvature(borderVoxels2));
                }
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
                voxels.add(v);
            }
            private void setBorderVoxels() {
                borderVoxels.clear();
                borderVoxels2.clear();
                if (voxels.isEmpty()) return;
                // add border voxel
                ImageInteger mask = getJoinedMask();
                for (Voxel v : voxels) if (borderNeigh.hasNullValue(v.x-mask.getOffsetX(), v.y-mask.getOffsetY(), v.z-mask.getOffsetZ(), mask, true)) borderVoxels.add(v);
                populateBoderVoxel(borderVoxels);
            }
            
            private void populateBoderVoxel(Collection<Voxel> allBorderVoxels) {
                borderVoxels2.clear();
                if (allBorderVoxels.isEmpty()) return;
                else if (allBorderVoxels.size() >= Math.min(e1.getVoxels().size(), e2.getVoxels().size())/2) {
                    curvatureValue=Double.NEGATIVE_INFINITY;
                    return;
                }
                BoundingBox b = new BoundingBox();
                for (Voxel v : allBorderVoxels) b.expand(v);
                ImageByte mask = new ImageByte("", b.getImageProperties());
                for (Voxel v : allBorderVoxels) mask.setPixelWithOffset(v.x, v.y, v.z, 1);
                ObjectPopulation pop = new ObjectPopulation(mask, false);
                pop.translate(b, false);
                List<Object3D> l = pop.getObjects();
                borderVoxels.clear();
                if (l.isEmpty()) logger.error("interface: {}, no side found", this);
                else {
                    if (l.size()>=1) borderVoxels.addAll(l.get(0).getVoxels());
                    if (l.size()>=2) borderVoxels2.addAll(l.get(1).getVoxels());
                    if (l.size()>=3) logger.error("interface: {}, #{} sides found!!, {}", this, l.size(), input.getName());
                }
            }

            @Override public int compareTo(InterfaceBT t) { // decreasingOrder of curvature value
                //return Double.compare(t.maxDistance, maxDistance);
                int c = Double.compare(t.curvatureValue, curvatureValue);
                if (c==0) return super.compareElements(t, Object3DCluster.object3DComparator); // consitency with equals method
                else return c;
            }
            
            @Override
            public String toString() {
                return "Interface: " + e1.getLabel()+"+"+e2.getLabel()+ " sortValue: "+curvatureValue;
            }  
        }
        
    }
    
    
}
