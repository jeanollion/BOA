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
package boa.plugins.plugins.segmenters;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.GroupParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.RegionPopulation.ContactBorder;
import boa.data_structure.RegionPopulation.Filter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.process.AutoThresholder;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.image.ImageProperties;
import boa.image.processing.ObjectFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import boa.measurement.BasicMeasurements;
import boa.measurement.GeometricalMeasurements;
import net.imglib2.KDTree;
import net.imglib2.Point;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import boa.plugins.ManualSegmenter;
import boa.plugins.ObjectSplitter;
import static boa.plugins.Plugin.logger;
import boa.plugins.Segmenter;
import boa.plugins.SegmenterSplitAndMerge;
import boa.plugins.Thresholder;
import boa.plugins.OverridableThreshold;
import boa.plugins.ParameterSetup;
import boa.plugins.plugins.manual_segmentation.WatershedObjectSplitter;
import boa.plugins.plugins.post_filters.MicrochannelPhaseArtifacts;
import boa.plugins.plugins.pre_filter.IJSubtractBackground;
import boa.plugins.plugins.segmenters.BacteriaTrans.ProcessingVariables.InterfaceBT;
import boa.plugins.plugins.thresholders.ConstantValue;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.plugins.plugins.thresholders.LocalContrastThresholder;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import static boa.plugins.plugins.trackers.ObjectIdxTracker.getComparatorRegion;
import boa.image.processing.Curvature;
import boa.image.processing.EDT;
import boa.image.processing.FillHoles2D;
import boa.image.processing.Filters;
import boa.image.processing.FitEllipse;
import boa.image.processing.FitEllipse.EllipseFit2D;
import boa.image.processing.Curvature;
import static boa.image.processing.Curvature.computeCurvature;
import static boa.image.processing.Curvature.getCurvatureMask;
import boa.image.processing.ImageFeatures;
import boa.image.processing.WatershedTransform;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.utils.HashMapGetCreate;
import boa.utils.Utils;
import static boa.utils.Utils.plotProfile;
import boa.image.processing.clustering.ClusterCollection.InterfaceFactory;
import boa.image.processing.clustering.SimpleInterfaceVoxelSet;
import boa.image.processing.clustering.Interface;
import boa.image.processing.clustering.InterfaceImpl;
import boa.image.processing.clustering.InterfaceRegion;
import boa.image.processing.clustering.InterfaceRegionImpl;
import boa.image.processing.clustering.InterfaceVoxelSet;
import boa.image.processing.clustering.RegionCluster;
import boa.image.processing.clustering.RegionCluster.InterfaceVoxels;

/**
 *
 * @author jollion
 */
public class BacteriaTrans implements SegmenterSplitAndMerge, ManualSegmenter, ObjectSplitter, ParameterSetup, OverridableThreshold {
    public static boolean debug = false;
    
    // configuration-related attributes
    
    //NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 2, 1, 6);
    NumberParameter openRadius = new BoundedNumberParameter("Open Radius", 1, 2.5, 0, null).setToolTipText("For microchannel border aberration removal"); // 0-3
    NumberParameter closeRadius = new BoundedNumberParameter("Close Radius", 1, 4, 0, null); //3-5
    NumberParameter maxBorderArtefactThickness = new BoundedNumberParameter("Max Border Artefact Thickness", 0, 7, 1, null).setToolTipText("In pixels. For microchannel border aberration removal");;
    NumberParameter fillHolesBackgroundContactProportion = new BoundedNumberParameter("Fill holes background contact proportion", 2, 0.25, 0, 1);
    NumberParameter minSizePropagation = new BoundedNumberParameter("Minimum size (propagation)", 0, 5, 5, null); // too high -> bad separation, too low: objects shape too far away from bact -> random merging
    PluginParameter<Thresholder> threshold = new PluginParameter<>("Threshold (separation from background)", Thresholder.class, new LocalContrastThresholder() , false); // // // //new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu)
    NumberParameter thresholdContrast = new BoundedNumberParameter("Contrast Threshold (separation from background)", 4, 0.02, 0.001, 0.999).setToolTipText("CURRENTLY NOT USED"); //minFN=0.14 (150325/0/1/f=113/th=144) 0.199 (150324/0/0/tp44/th=318) / maxFP=0.071(141107/0/0/tp796/th=265)
    
    GroupParameter backgroundSeparation = new GroupParameter("Separation from background", threshold, thresholdContrast, openRadius, closeRadius, fillHolesBackgroundContactProportion, maxBorderArtefactThickness);
    
    NumberParameter relativeThicknessThreshold = new BoundedNumberParameter("Relative Thickness Threshold (lower: split more)", 2, 0.7, 0, 1);
    NumberParameter relativeThicknessMaxDistance = new BoundedNumberParameter("Max Distance for Relative Thickness normalization factor (calibrated)", 2, 1, 0, null);
    GroupParameter thicknessParameters = new GroupParameter("Constaint on thickness", relativeThicknessMaxDistance, relativeThicknessThreshold);
    
    NumberParameter curvatureThreshold = new BoundedNumberParameter("Curvature Threshold (lower merge more)", 2, -1, null, 0);
    NumberParameter curvatureThreshold2 = new BoundedNumberParameter("Curvature Threshold2 (lower merge more)", 2, -0.6, null, 0);
    NumberParameter curvatureScale = new BoundedNumberParameter("Curvature scale", 0, 6, 3, null);
    NumberParameter curvatureSearchRadius = new BoundedNumberParameter("Radius for min. search", 1, 2.5, 1, null);
    GroupParameter curvatureParameters = new GroupParameter("Constaint on curvature", curvatureScale, curvatureThreshold, curvatureThreshold2, curvatureSearchRadius);
    
    NumberParameter minSizeFusionCost = new BoundedNumberParameter("Minimum Object size (split & merge)", 0, 50, 5, null).setToolTipText("during object correction step: under this size objects will be merged with other object in contact"); 
    NumberParameter minSizeFusion = new BoundedNumberParameter("Minimum Object size (fusion)", 0, 50, 5, null).setToolTipText("under this size (pixels) objects will be merged with other object in contact");
    NumberParameter minSize = new BoundedNumberParameter("Minimum Object size", 0, 50, 5, null).setToolTipText("under this size (pixels) objects will be erased");
    NumberParameter minSizeChannelEnd = new BoundedNumberParameter("Minimum Object size (end of channel)", 0, 100, 5, null).setToolTipText("under this size (pixels) the last object of the channel will be erased");
    NumberParameter minXSize = new BoundedNumberParameter("Minimum Average of X-Thickness", 0, 4, 1, null).setToolTipText("objects with average thickness along X-axis under this value will be erased");
    GroupParameter objectParameters = new GroupParameter("Constaint on segmented Objects", minSize, minXSize, minSizeFusion, minSizeFusionCost, minSizeChannelEnd);
    
    Parameter[] parameters = new Parameter[]{backgroundSeparation, thicknessParameters, curvatureParameters, objectParameters};
    private final static double maxMergeCostDistanceBB = 10; // distance in pixel for cost computation for merging small objects (during correction)
    private final static double maxMergeDistanceBB = 3; // distance in pixel for merging small objects during main process
    // ParameterSetup interface
    
    public BacteriaTrans setObjectParameters(double minSize, double minXSize) {
        this.minSize.setValue(minSize);
        this.minXSize.setValue(minXSize);
        return this;
    }
    
    @Override public boolean canBeTested(String p) {
        List canBeTested = new ArrayList<String>(){{add(curvatureThreshold.getName()); add(curvatureThreshold2.getName()); add(threshold.getName()); add(curvatureScale.getName()); add(relativeThicknessThreshold.getName());}};
        return canBeTested.contains(p);
    }
    public BacteriaTrans setThreshold(boa.plugins.SimpleThresholder t) {
        this.threshold.setPlugin(t);
        return this;
    }
    String testParameter;
    @Override public void setTestParameter(String p) {
        this.testParameter=p;
    }
    
    private static Image getCurvatureImage(RegionPopulation pop, int curvatureScale) {
        ImageFloat curv = new ImageFloat("Curvature map: "+curvatureScale, pop.getImageProperties()).resetOffset();
        pop.getObjects().stream().map((o) -> computeCurvature(o.getMask(), curvatureScale)).forEach((tree) -> { Curvature.drawOnCurvatureMask(curv, tree); });
        return curv;
    }
    // Overridable Threshold related interface

    double thresholdValue = Double.NaN;
    @Override public void setThresholdValue(double threhsold) {
        thresholdValue = threhsold;
    }
    @Override public Image getThresholdImage(Image input, int structureIdx, StructureObjectProcessing parent) {
        return getProcessingVariables(input, parent.getMask()).getIntensityMap(); // TODO for all methdos with these argument : check if pv exists and has same input & parent ...
    }
    ImageInteger mask=null;
    @Override public void setThresholdedImage(ImageInteger thresholdedImage) {
        mask = thresholdedImage;
    }
    //segmentation-related attributes (kept for split and merge methods)
    ProcessingVariables pv;
    
    public BacteriaTrans setRelativeThicknessThreshold(double relativeThicknessThreshold) {
        this.relativeThicknessThreshold.setValue(relativeThicknessThreshold);
        return this;
    }

    /*public BacteriaTrans setSmoothScale(double smoothScale) {
        this.smoothScale.setValue(smoothScale);
        return this;
    }*/

    public BacteriaTrans setOpenRadius(double openRadius) {
        this.openRadius.setValue(openRadius);
        return this;
    }

    
    @Override
    public String toString() {
        return "Bacteria Trans: " + Utils.toStringArray(parameters, p->p.toStringFull());
    }   
    
    public ProcessingVariables getProcessingVariables(Image input, ImageMask segmentationMask) {
        return new ProcessingVariables(input, segmentationMask,
                thresholdContrast.getValue().doubleValue(), 2, // contrast radius optimized = 2
                relativeThicknessThreshold.getValue().doubleValue(), relativeThicknessMaxDistance.getValue().doubleValue(), 
                openRadius.getValue().doubleValue(), closeRadius.getValue().doubleValue(), this.fillHolesBackgroundContactProportion.getValue().doubleValue(), this.maxBorderArtefactThickness.getValue().intValue(),
                minSize.getValue().intValue(), minXSize.getValue().intValue(), minSizePropagation.getValue().intValue(), minSizeFusion.getValue().intValue(),
                curvatureScale.getValue().intValue(), curvatureThreshold.getValue().doubleValue(), curvatureThreshold2.getValue().doubleValue(), curvatureSearchRadius.getValue().doubleValue());
    }
    
    @Override public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        /*
            1) compute forground mask & check if there are objects inside
            2) watershed on DOG -> produce oversegmentation
            3) watershed on EDM using seeds from previous watershed in order to fit interfaces to morphology
            4) merge using criterion on curvature & relative thickness
            
        */
        pv = getProcessingVariables(input, parent.getMask());
        if (mask==null) {
            if (Double.isNaN(thresholdValue)) pv.threshold = this.threshold.instanciatePlugin().runThresholder(pv.getIntensityMap(), parent);
            else {
                if (debug) logger.debug("using pre-set threshold {}, for {}", thresholdValue, parent);
                pv.threshold=thresholdValue;
            }
        } else {
            if (debug) logger.debug("using pre-set mask");
            pv.thresh=mask;
        }
        if (debug) {
            new IJImageDisplayer().showImage(input.setName("input"));
            logger.debug("threshold: {}", pv.threshold);
        }
        RegionPopulation pop = pv.splitAndMergeObjects(pv.getSegmentationMask(), pv.minSizeFusion, 0, true,  debug);
        if (this.testParameter!=null) {
            if (testParameter.equals(relativeThicknessThreshold.getName())) {
                logger.debug("rel t test");
                ImageWindowManagerFactory.showImage(pv.getSegmentationMask().duplicate("before merging"));
                ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("after merging"));
            }
            else if (testParameter.equals(curvatureScale.getName())|| testParameter.equals(curvatureThreshold.getName()) || testParameter.equals(curvatureThreshold2.getName())) {
                logger.debug("cur test");
                ImageWindowManagerFactory.showImage(pv.getSegmentationMask().duplicate("segmentation mask"));
                ImageWindowManagerFactory.showImage(getCurvatureImage(new RegionPopulation(pv.getSegmentationMask(), true), curvatureScale.getValue().intValue()));
                ImageWindowManagerFactory.showImage(pv.splitSegmentationMask(pv.getSegmentationMask(), minSizePropagation.getValue().intValue()).getLabelMap().setName("after split"));
                ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("after merge"));
            } else if (testParameter.equals(threshold.getName())) {
                ImageWindowManagerFactory.showImage(pv.getIntensityMap().duplicate("before threshold. Value: "+pv.threshold));
                ImageWindowManagerFactory.showImage(pv.getSegmentationMask().duplicate("after threshold"));
            }
            return null;
        }
        
        
        //if (contactLimit.getValue().intValue()>0) pop.filter(new RegionPopulation.ContactBorder(contactLimit.getValue().intValue(), parent.getMask(), RegionPopulation.ContactBorder.Border.YDown));
        
        if (!pop.getObjects().isEmpty() && pop.getObjects().get(pop.getObjects().size()-1).getSize()<minSizeChannelEnd.getValue().intValue()) pop.getObjects().remove(pop.getObjects().size()-1); // remove small objects at the end of channel? // si plusieurs somme des tailles inférieurs?
        pop.filter(new RegionPopulation.Size().setMin(minSize.getValue().intValue()));
        return pop;
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
        // set tool tips
        //this.thresholdContrast.setToolTipText("Higher value will remove objects with low local contrast at borders");
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    // segmenter split and merge interface
    @Override public double split(Image input, Region o, List<Region> result) {
        //new IJImageDisplayer().showImage(o.getMask().duplicate("split mask"));
        RegionPopulation pop =  splitObject(input, o); // initialize pv
        pop.translate(o.getBounds().duplicate().reverseOffset(), false);
        if (pop.getObjects().size()<=1) return Double.POSITIVE_INFINITY;
        else {
            Region o1 = pop.getObjects().get(0);
            Region o2 = pop.getObjects().get(1);
            result.add(o1);
            result.add(o2);
            InterfaceBT inter = getInterface(o1, o2);
            
            inter.updateSortValue();
            double cost = getCost(inter.curvatureValue, curvatureThreshold.getValue().doubleValue(), false); 
            
            //new IJImageDisplayer().showImage(pop.getLabelMap().duplicate("split"));
            //new IJImageDisplayer().showImage(inter.getCurvatureMask());
            //logger.debug("split: #objects: {} intersize: {}, curvature {}, threshold: {}, cost: {}", pop.getObjects().size(), inter.voxels.size(), inter.curvatureValue, curvatureThreshold.getValue().doubleValue(), cost);
            pop.translate(o.getBounds(), true);
            return cost;
        }
        /*
        if (pv==null) throw new RuntimeException("Segment method have to be called before split method in order to initialize images");
        synchronized(pv) {
            o.draw(pv.getSplitMask(), 1);
            RegionPopulation pop = BacteriaTrans.getSeparatedObjects(pv, pv.getSplitMask(), minSizePropagation.getValue().intValue(), 2, true);
            new IJImageDisplayer().showImage(pop.getLabelMap().duplicate("split"));
            o.draw(pv.splitMask, 0);
            if (pop==null || pop.getObjects().isEmpty() || pop.getObjects().size()==1) return Double.POSITIVE_INFINITY;
            ArrayList<Object3D> remove = new ArrayList<Object3D>(pop.getObjects().size());
            pop.filter(new RegionPopulation.Thickness().setX(minXSize).setY(2), remove); // remove thin objects ?? merge? 
            pop.filter(new RegionPopulation.Size().setMin(minSizePropagation.getValue().intValue()), remove); // remove small objects ?? merge? 
            if (pop.getObjects().size()<=1) return Double.POSITIVE_INFINITY;
            else {
                if (!remove.isEmpty()) pop.mergeWithConnected(remove);
                Region o1 = pop.getObjects().get(0);
                Region o2 = pop.getObjects().get(1);
                result.add(o1);
                result.add(o2);
                InterfaceBT inter = getInterface(o1, o2);
                inter.updateSortValue();
                //logger.debug("split: intersize: {}, cost {}", inter.voxels.size(), inter.curvatureValue);
                return getCost(inter.curvatureValue, pv.curvatureThreshold, false); 
            }
            
        }*/
    }
    @Override public double computeMergeCost(Image input, List<Region> objects) { // si les objets ont un label identique -> bug -> check?
        if (objects.isEmpty() || objects.size()==1) return 0;
        double minSize= minSizeFusionCost.getValue().doubleValue();
        RegionPopulation mergePop = new RegionPopulation(objects, input, false);
        
        pv = getProcessingVariables(input, mergePop.getLabelMap());
        pv.segMask=mergePop.getLabelMap();
        double minCurv = Double.POSITIVE_INFINITY;
        RegionCluster c = new RegionCluster(mergePop, false, true, pv.getFactory());
        List<Set<Region>> clusters = c.getClusters();
        if (debug) {
            logger.debug("compute merge cost: {} objects in {} clusters, sizes: {}", objects.size(), clusters.size(), Utils.toStringList(objects, o-> o.getLabel()+"->"+o.getSize()));
            ImageWindowManagerFactory.showImage(mergePop.getLabelMap().duplicate("merge image"));
        }
        if (clusters.size()>1) { // merge impossible : presence of disconnected objects / except if small objects
            // if at least all clusters but one are small -> can merge without cost 
            int nSmall = 0;
            Set<Region> smallest = null;
            double minClustSize=Double.POSITIVE_INFINITY;
            for (Set<Region> cl : clusters) {
                double size = 0;
                for (Region o : cl) size+=o.getSize();
                if (size<=minSize) {
                    ++nSmall;
                    if (size<minClustSize) {
                        minClustSize = size;
                        smallest = cl;
                    }
                }
            }
            if (nSmall>=clusters.size()-1) { // check max distance
                double maxD = Double.NEGATIVE_INFINITY;
                for (Set<Region> set : clusters) {
                    if (set!=smallest) {
                        double d = getMinDistance(smallest, set);
                        if (d>maxD) maxD = d;
                    }
                }
                if (maxD<=maxMergeCostDistanceBB) return 0;
            }
            if (debug) logger.debug("merge impossible: {} disconnected clusters detected", clusters.size());
            return Double.POSITIVE_INFINITY;
        }
        pv.updateCurvature(clusters);
        Set<InterfaceBT> allInterfaces = c.getInterfaces(clusters.get(0));
        for (InterfaceBT i : allInterfaces) { // get the min curvature value = worst case
            i.updateSortValue();
            //logger.debug("interface: {}", i);
            if (Double.isInfinite(minCurv) && (i.getE1().getSize()<=minSize || i.getE2().getSize()<=minSize)) { // small objects can merge without cost
                minCurv=pv.curvatureThreshold;
            } else if (i.curvatureValue<minCurv) minCurv = i.curvatureValue;
        }
        if (minCurv==Double.POSITIVE_INFINITY || minCurv==Double.NaN) return Double.POSITIVE_INFINITY;
        return getCost(minCurv, pv.curvatureThreshold, true);
        /*
        if (pv==null) throw new RuntimeException("Segment method have to be called before merge method in order to initialize images");
        if (objects.isEmpty() || objects.size()==1) return 0;
        synchronized(pv) {
            double minCurv = Double.POSITIVE_INFINITY;
            RegionPopulation mergePop = new RegionPopulation(objects, pv.getSplitMask(), pv.getSplitMask(), false);
            //new IJImageDisplayer().showImage(mergePop.getLabelMap());
            RegionCluster c = new RegionCluster(mergePop, false, pv.getFactory());
            List<Set<Object3D>> clusters = c.getClusters();
            //logger.debug("compute merge cost: {} objects in {} clusters", objects.size(), clusters.size());
            if (clusters.size()>1) { // merge impossible : presence of disconnected objects
                if (debug) logger.debug("merge impossible: {} disconnected clusters detected", clusters.size());
                return Double.POSITIVE_INFINITY;
            } 
            Set<InterfaceBT> allInterfaces = c.getInterfaces(clusters.get(0));
            for (InterfaceBT i : allInterfaces) {
                i.updateSortValue();
                if (i.curvatureValue<minCurv) minCurv = i.curvatureValue;
            }
            //logger.debug("minCurv: {}", minCurv);
            if (minCurv==Double.POSITIVE_INFINITY || minCurv==Double.NaN) return Double.POSITIVE_INFINITY;
            return getCost(minCurv, pv.curvatureThreshold, true);
        }*/
    }
    private static double getMinDistance(Set<Region> cl1, Set<Region> cl2) {
        // getClosest objects from mass center
        if (cl1.isEmpty() || cl2.isEmpty()) return Double.POSITIVE_INFINITY;
        Region mo1 = null;
        Region mo2 = null;
        if (cl1.size()>1 || cl2.size()>1) {
        double minD = Double.POSITIVE_INFINITY;
            for (Region o1 : cl1) {
                for (Region o2 : cl2) {
                    if (o1!=o2) {
                        double d = GeometricalMeasurements.getDistance(o1, o2);
                        if (minD>d) {
                            minD = d;
                            mo1 = o1;
                            mo2 = o2;
                        }
                    }
                }
            } 
        } else {
            mo1 = cl1.iterator().next();
            mo2 = cl2.iterator().next();
        }
        return GeometricalMeasurements.getDistanceBB(mo1, mo2, false);
    }
    private InterfaceBT getInterface(Region o1, Region o2) {
        o1.draw(pv.getSplitMask(), o1.getLabel());
        o2.draw(pv.splitMask, o2.getLabel());
        InterfaceBT inter = RegionCluster.getInteface(o1, o2, pv.getSplitMask(), pv.getFactory());
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
    @Override public RegionPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        ProcessingVariables pv = getProcessingVariables(input, segmentationMask);
        pv.threshold = threshold.instanciatePlugin().runThresholder(pv.getIntensityMap(), parent);
        if (verboseManualSeg) logger.debug("threshold: {} (type: {})", pv.threshold, threshold.toJSONEntry().toJSONString());
        ImageOperations.and(segmentationMask, pv.getSegmentationMask(), pv.getSegmentationMask());
        RegionPopulation res = pv.splitSegmentationMask(pv.getSegmentationMask(), pv.minSizePropagation);
        List<Region> seedObjects = ObjectFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getSizeZ()==1, input.getScaleXY(), input.getScaleZ());
        for (Region o : seedObjects) o.draw(pv.getSegmentationMask(), 1, null); // to ensure points are included in mask
        RegionCluster<InterfaceBT> c = new RegionCluster(res, false, true, pv.getFactory());
        c.setFixedPoints(seedObjects);
        pv.updateCurvature(c.getClusters());
        c.mergeSort(false, 0, seedObjects.size());  
        Collections.sort(res.getObjects(), getComparatorRegion(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
        res.relabel(true);
        
        //ObjectPopulation pop = WatershedTransform.watershed(pv.getIntensityMap(), pv.getSegmentationMask(), seedObjects, false, null, null, true);
        
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixel(seed[0], seed[1], seed[2], 1);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(seedMap);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pv.getEDM().setName("EDM. Threshold: "+pv.threshold));
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pv.getIntensityMap());
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(res.getLabelMap().setName("segmented from: "+input.getName()));
        }
        
        return res;
    }

    // object splitter interface
    boolean splitVerbose;
    @Override
    public void setSplitVerboseMode(boolean verbose) {
        this.splitVerbose=verbose;
    }
    
    @Override
    public RegionPopulation splitObject(Image input, Region object) {
        ImageInteger mask = object.getMask();
        if (!input.sameSize(mask)) {
            input = input.crop(object.getBounds());
            //input = object.isAbsoluteLandMark()? input.cropWithOffset(object.getBounds()) : input.crop(object.getBounds());
            //mask = mask.crop(input.getBoundingBox()); // problem with crop & offsets when bb is larger & has an offset
        }
        pv = getProcessingVariables(input, mask);
        pv.splitVerbose=splitVerbose;
        pv.segMask=mask; // no need to compute threshold because split is performed within object's mask
        RegionPopulation pop = pv.splitAndMergeObjects(pv.segMask, minSizeFusionCost.getValue().intValue(), 2, false, splitVerbose);
        
        pop.translate(object.getBounds(), true);
        return pop;
    }
    
    protected static class ProcessingVariables {
        public boolean splitVerbose=false;
        private Image distanceMap;
        private ImageInteger segMask, thresh;
        final ImageMask mask;
        final Image input;
        //private Image smoothed;
        ImageByte splitMask;
        final double relativeThicknessThreshold, openRadius, fillHolesBckProp, closeRadius, relativeThicknessMaxDistance;//, smoothScale;// aspectRatioThreshold, angleThresholdRad; 
        final int maxBorderThickness;
        double threshold = Double.NaN;
        final int curvatureScale, minSizePropagation, minSize, minXSize;
        int minSizeFusion;
        final double curvatureSearchScale;
        final double curvatureThreshold, curvatureThreshold2;
        final double contrastThreshold, contrastRadius;
        RegionCluster.InterfaceFactory<Region, InterfaceBT> factory;
        protected final HashMap<Region, KDTree<Double>> curvatureMap = new HashMap<>();
        private double yLimLastObject = Double.NaN;
        private ProcessingVariables(Image input, ImageMask mask, double contrastThreshold, double contrastRadius, double splitThresholdValue, double relativeThicknessMaxDistance, double openRadius, double closeRadius, double fillHolesBckProp, int borderThickness, int minSize, int minXSize, int minSizePropagation, int minSizeFusion, int curvatureScale, double curvatureThreshold, double curvatureThreshold2, double curvatureSearchRadius) {
            this.input=input;
            this.mask=mask;
            this.contrastRadius=contrastRadius;
            this.contrastThreshold=contrastThreshold;
            this.relativeThicknessThreshold=splitThresholdValue;
            this.relativeThicknessMaxDistance=relativeThicknessMaxDistance;
            this.openRadius=openRadius; //Math.max(1, openRadius);
            this.minSizePropagation=minSizePropagation;
            this.minSizeFusion=minSizeFusion;
            this.curvatureScale = curvatureScale;
            curvatureSearchScale = curvatureSearchRadius;
            this.curvatureThreshold=curvatureThreshold;
            this.curvatureThreshold2=curvatureThreshold2;
            this.minSize=minSize;
            this.minXSize=minXSize;
            this.closeRadius=closeRadius;
            this.fillHolesBckProp=fillHolesBckProp;
            this.maxBorderThickness=borderThickness;
        }
        /*private Image getSmoothed() {
            if (smoothed==null) smoothed = ImageFeatures.gaussianSmooth(input, smoothScale, smoothScale, false);
            return smoothed;
        }*/
        private ImageByte getSplitMask() {
            if (splitMask==null) splitMask = new ImageByte("split mask", input);
            return splitMask;
        }
        public InterfaceFactory<Region, InterfaceBT> getFactory() {
            if (factory==null) factory = (Region e1, Region e2, Comparator<? super Region> elementComparator) -> new InterfaceBT(e1, e2);
            return factory;
        }

        private Image getIntensityMap() {
            return input;
            /*if (intensityMap==null) {
                //Image close = Filters.close(input, null, Filters.getNeighborhood(5, 5, input)).setName("open3");
                //new IJImageDisplayer().showImage(close);
                intensityMap = ImageFeatures.differenceOfGaussians(input, 0, dogScale, 1, false).setName("DoG");
            }
            //if (intensityMap==null) intensityMap=IJSubtractBackground.filter(input, dogScale, true, true, true, false).setName("subLight");
            return intensityMap;
            */
            //return input;
        }
        private RegionPopulation splitSegmentationMask(ImageInteger maskToSplit, int minSize) {
            ImageByte seeds = Filters.localExtrema(getEDM(), null, true, maskToSplit, Filters.getNeighborhood(3, 3, getEDM())); // TODO seed radius -> parameter ? 
            RegionPopulation res =  WatershedTransform.watershed(getEDM(), maskToSplit, ImageLabeller.labelImageList(seeds), true, null, new WatershedTransform.SizeFusionCriterion(minSize), true);
            if (res.getObjects().size()==1) { // relabel with low connectivity -> if not contour will fail
                List<Region> list = ImageLabeller.labelImageListLowConnectivity(maskToSplit);
                res = new RegionPopulation(list, maskToSplit);
            } 
            //res = new MicrochannelPhaseArtifacts().setThickness(maxBorderThickness).runPostFilter(null, -1, res); // filter to remove channel border artifacts
            return res;
            /*   
            RegionPopulation res = WatershedTransform.watershed(getIntensityMap(), maskToSplit, false, null, new WatershedTransform.SizeFusionCriterion(minSizePropagation), true);
            if (splitVerbose) logger.debug("splitMask: {}", res.getObjects().size());
            if (res.getObjects().size()>1) {
                res.setVoxelIntensities(getEDM()); // for getExtremaSeedList method called just afterwards. // offset of objects needs to be relative to EDM map because EDM offset is not taken into acount
                RegionPopulation res2 = WatershedTransform.watershed(getEDM(), maskToSplit, res.getExtremaSeedList(true), true, null, new WatershedTransform.SizeFusionCriterion(minSize), true);
                if (res2.getObjects().size()>1) return res2;
                else {
                    res =  WatershedTransform.watershed(getEDM(), maskToSplit, true, null, new WatershedTransform.SizeFusionCriterion(minSize), true);
                    res.setVoxelIntensities(getEDM());
                    return res;
                }
            } else {
                res =  WatershedTransform.watershed(getEDM(), maskToSplit, true, null, new WatershedTransform.SizeFusionCriterion(minSize), true);
                res.setVoxelIntensities(getEDM());
                return res;
            }
            */
        }
        private ImageInteger getSegmentationMask() {
            if (segMask == null) {
                if (thresh==null && Double.isNaN(threshold)) throw new RuntimeException("Threshold not set");
                IJImageDisplayer disp = debug?new IJImageDisplayer():null;
                if (thresh==null) thresh = ImageOperations.threshold(getIntensityMap(), threshold, false, false);
                if (debug) disp.showImage(thresh.duplicate("raw thresholded map"));
                ImageOperations.and(mask, thresh, thresh);
                thresh = Filters.open(thresh, thresh, Filters.getNeighborhood(1, 1, thresh)); // in order to avoid abberent cuvature values
                thresh = Filters.applyFilter(thresh, null, new RemoveThinBorder(), null); // aberation: borders of microchannels can be segemented -> fill holes would create aberations
                if (debug) disp.showImage(thresh.duplicate("after remove thin borders"));
                RegionPopulation pop1 = new RegionPopulation(thresh).setLabelImage(thresh, false, false); // high connectivity for fill holes to fill holes between close cells?
                //FillHoles2D.fillHoles(pop1); // before open in order to avoid digging holes close to borders / after having removed borders
                thresh = pop1.getLabelMap();
                // remove border artifacts
                ImageInteger open = Filters.binaryOpen(thresh, null, Filters.getNeighborhood(openRadius, openRadius, thresh));
                if (debug) disp.showImage(open.duplicate("Open Image"));
                ImageOperations.xor(open, thresh, open);
                if (debug) disp.showImage(open.duplicate("Open Image XOR: "));
                RegionPopulation openPop = new RegionPopulation(open, false);
                List<Region> toRemove = new ArrayList<>();
                openPop.filter(new RegionPopulation.ContactBorder(1, open, RegionPopulation.ContactBorder.Border.X), toRemove);
                openPop.filter(new RegionPopulation.ContactBorder(1, open, RegionPopulation.ContactBorder.Border.YUp), toRemove); // do not remove Ydown -> end of channel
                for (Region o : toRemove) o.draw(thresh, 0);
                if (debug) {
                    int label = 1;
                    ImageOperations.fill(open, 0, null);
                    for (Region o : toRemove) o.draw(open, label++);
                    disp.showImage(open.duplicate("Open Border Remove #: "+label));
                }
                if (debug) disp.showImage(thresh.duplicate("SEG MASK AFTER REMOVE OPEN BORDER"));
                FillHoles2D.debug=debug;
                FillHoles2D.fillHolesClosing(thresh, closeRadius, fillHolesBckProp, minSizeFusion);
                if (debug) disp.showImage(thresh.duplicate("SEG MASK AFTER Morpho"));
                pop1 = new RegionPopulation(thresh).setLabelImage(thresh, false, true);
                Collections.sort(pop1.getObjects(), getComparatorRegion(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
                pop1.filter(new RegionPopulation.Size().setMin(minSize)); // remove small objects
                pop1.filter(new RegionPopulation.MeanThickness().setX(minXSize)); // remove thin objects
                pop1.filter(new RegionPopulation.Thickness().setY(2));
                pop1.relabel(false);
                if (debug) disp.showImage(pop1.getLabelMap().duplicate("SEG MASK AFTER REMVOVE SMALL OBJECTS"));
                //pop1.filter(new ContrastIntensity(-contrastThreshold, contrastRadius, 0, false, getIntensityMap()));
                //pop1.filter(new LocalContrast(contrastThreshold, contrastRadius, contrastRadius, getIntensityMap()));
                //if (debug) disp.showImage(pop1.getLabelMap().duplicate("SEG MASK AFTER  REMOVE CONTRAST"));
                segMask = pop1.getLabelMap();
                thresh = null; // no need to keep
            }
            return segMask;
        }
        private Image getEDM() {
            if (distanceMap==null) {
                distanceMap = EDT.transform(getSegmentationMask(), true, 1, input.getScaleZ()/input.getScaleXY(), 1);
            }
            return distanceMap;
        }
        protected void updateCurvature(List<Set<Region>> clusters) { // need to be called in order to use curvature in InterfaceBT
            curvatureMap.clear();
            ImageByte clusterMap = new ImageByte("cluster map", segMask).resetOffset(); 
            Iterator<Set<Region>> it = clusters.iterator();
            while(it.hasNext()) {
                Set<Region> clust = it.next();
                for (Region o : clust) o.draw(clusterMap, 1);
                //Filters.binaryOpen(clusterMap, clusterMap, Filters.getNeighborhood(1, 1, clusterMap)); // avoid funny values // done at segmentation step
                KDTree<Double> curv = Curvature.computeCurvature(clusterMap, curvatureScale);
                /*if (debug) {
                    logger.debug("curvature map: {}", curv.size());
                    try {
                        Image c = Curvature.getCurvatureMask(clusterMap, curv);
                        if (c!=null) ImageWindowManagerFactory.showImage(c);
                    } catch(Exception e) {
                        logger.debug("error curv map show", e);
                    }
                }*/
                for (Region o : clust) {
                    curvatureMap.put(o, curv);
                    if (it.hasNext()) o.draw(clusterMap, 0);
                }
            }
        }
        
        protected RegionPopulation splitAndMergeObjects(ImageInteger segmentationMask, int minSize, int objectMergeLimit, boolean endOfChannel, boolean debug) {
            //if (BacteriaTrans.debug) debug=true;
            RegionPopulation res = splitSegmentationMask(segmentationMask, minSizePropagation);
            if (res.getObjects().isEmpty()) return res;
            if (debug) ImageWindowManagerFactory.showImage(getEDM());
            if (debug) {
                ImageWindowManagerFactory.showImage(res.getLabelMap().duplicate("labelMap - shape re-split"));
                ImageWindowManagerFactory.showImage(getCurvatureImage(new RegionPopulation(segmentationMask, true), curvatureScale));
            }
            if (endOfChannel && !res.getObjects().isEmpty()) yLimLastObject = res.getObjects().get(res.getObjects().size()-1).getBounds().getyMax();
            // merge using the criterion
            RegionCluster.verbose=debug;
            //res.setVoxelIntensities(pv.getEDM());// for merging // useless if watershed transform on EDM has been called just before
            RegionCluster<InterfaceBT> c = new RegionCluster(res, false, true, getFactory());
            updateCurvature(c.getClusters());
            if (minSize>0) c.mergeSmallObjects(minSize, objectMergeLimit, null);
            c.mergeSort(objectMergeLimit<=1, 0, objectMergeLimit);
            if (minSize>0) {
                BiFunction<Region, Set<Region>, Region> noInterfaceCase = (smallO, set) -> {
                    if (set.isEmpty()) return null;
                    Region closest = Collections.min(set, (o1, o2) -> Double.compare(o1.getBounds().getDistance(smallO.getBounds()), o2.getBounds().getDistance(smallO.getBounds())));
                    double d = GeometricalMeasurements.getDistanceBB(closest, smallO, false);
                    if (debug) logger.debug("merge small objects with no interface: min distance: {} to {} = {}", smallO.getLabel(), closest.getLabel(), d);
                    if (d<maxMergeDistanceBB) return closest;
                    else return null;
                }; 
                c.mergeSmallObjects(minSize, objectMergeLimit, noInterfaceCase);
            }
            Collections.sort(res.getObjects(), getComparatorRegion(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
            res.relabel(true);
            return res;
        }
        
        protected class InterfaceBT extends InterfaceRegionImpl<InterfaceBT> implements InterfaceVoxels<InterfaceBT> {
            double maxDistance=Double.NEGATIVE_INFINITY;
            double curvatureValue=Double.POSITIVE_INFINITY;
            double curvL=Double.NaN, curvR=Double.NaN;
            double relativeThickNess = Double.NEGATIVE_INFINITY;
            Voxel maxVoxel=null;
            double value=Double.NaN;
            private final Set<Voxel> borderVoxels = new HashSet<>(), borderVoxels2 = new HashSet<>();
            private final Set<Voxel> voxels = new HashSet<>();
            private final Neighborhood borderNeigh = new EllipsoidalNeighborhood(1.5, true);
            private ImageInteger joinedMask;
            public InterfaceBT(Region e1, Region e2) {
                super(e1, e2);
            }
            @Override public Collection<Voxel> getVoxels() {
                return voxels;
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
                //if (debug || ProcessingVariables.this.splitVerbose) logger.debug("interface: {}, contains curvature: {}", this, ProcessingVariables.this.curvatureMap.containsKey(e1));
                if (borderVoxels.isEmpty()) setBorderVoxels();
                return ProcessingVariables.this.curvatureMap.get(e1);
            }
            
            @Override public void updateSortValue() {
                if (voxels.size()<=-1) curvatureValue=Double.NEGATIVE_INFINITY; // when border is too small curvature may not be computable, but objects should not be merged
                else if (getCurvature()!=null) {
                    curvatureValue = getMeanOfMinCurvature();
                } //else logger.debug("no curvature found for: {}", this);
                if (Double.isNaN(curvatureValue)) curvatureValue = Double.NEGATIVE_INFINITY; // curvature cannot be computed for objects too small
                //else logger.debug("curvature null");
            }
            @Override
            public void performFusion() {
                super.performFusion();
            }
            @Override 
            public void fusionInterface(InterfaceBT otherInterface, Comparator<? super Region> elementComparator) {
                if (otherInterface.maxDistance>maxDistance) {
                    this.maxDistance=otherInterface.maxDistance;
                    this.maxVoxel=otherInterface.maxVoxel;
                }
                joinedMask=null;
                voxels.addAll(otherInterface.voxels);
                setBorderVoxels();
            }
            
            @Override
            public boolean checkFusion() {
                if (maxVoxel==null) return false;
                if (this.voxels.isEmpty()) return false;
                // criterion on size
                if ((this.e1.getSize()<minSizeFusion && (Double.isNaN(yLimLastObject) || e1.getBounds().getyMax()<yLimLastObject)) || (this.e2.getSize()<minSizeFusion&& (Double.isNaN(yLimLastObject) || e2.getBounds().getyMax()<yLimLastObject))) return true; // fusion of small objects, except for last objects
                
                // criterion on curvature
                // curvature has been computed @ upadateSortValue
                if (debug| ProcessingVariables.this.splitVerbose) logger.debug("interface: {}+{}, Mean curvature: {} ({} & {}), Threshold: {} & {}", e1.getLabel(), e2.getLabel(), curvatureValue, curvL, curvR, curvatureThreshold, curvatureThreshold2);
                if (curvatureValue<curvatureThreshold || (curvL<curvatureThreshold2 && curvR<curvatureThreshold2)) return false;
                //else if (true) return true;
                double max1 = Double.NEGATIVE_INFINITY;
                double max2 = Double.NEGATIVE_INFINITY;
                for (Voxel v : e1.getVoxels()) if (v.value>max1 && v.getDistance(maxVoxel, e1.getScaleXY(), e1.getScaleZ())<relativeThicknessMaxDistance) max1 = v.value;
                for (Voxel v : e2.getVoxels()) if (v.value>max2 && v.getDistance(maxVoxel, e1.getScaleXY(), e1.getScaleZ())<relativeThicknessMaxDistance) max2 = v.value;
                
                double norm = Math.min(max1, max2);
                value = maxDistance/norm;
                if (debug| ProcessingVariables.this.splitVerbose) logger.debug("Thickness criterioninterface: {}+{}, norm: {} maxInter: {}, criterion value: {} threshold: {} fusion: {}, scale: {}", e1.getLabel(), e2.getLabel(), norm, maxDistance,value, relativeThicknessThreshold, value>relativeThicknessThreshold, e1.getScaleXY() );
                return  value>relativeThicknessThreshold;
            }
            
            private double getMinCurvature(Collection<Voxel> voxels) { // returns negative infinity if no border
                if (voxels.isEmpty()) return Double.NEGATIVE_INFINITY;
                //RadiusNeighborSearchOnKDTree<Double> search = new RadiusNeighborSearchOnKDTree(getCurvature());
                NearestNeighborSearchOnKDTree<Double> search = new NearestNeighborSearchOnKDTree(getCurvature());
                
                double min = Double.POSITIVE_INFINITY;
                for (Voxel v : voxels) {
                    search.search(new Point(new int[]{v.x, v.y}));
                    double d = search.getSampler().get();
                    if (d<min) min = d;
                }
                
                /*double searchScale = curvatureSearchScale;
                double searchScaleLim = 2 * curvatureSearchScale;
                while(Double.isInfinite(min) && searchScale<searchScaleLim) { // curvature is smoothed thus when there are angles the neerest value might be far away. progressively increment search scale in order not to reach the other side too easily
                    for(Voxel v : voxels) {
                        
                        search.search(new Point(new int[]{v.x, v.y}), searchScale, true);
                        if (search.numNeighbors()>=1) min=search.getSampler(0).get();
                        //for (int i = 0; i<search.numNeighbors(); ++i) {
                        //    Double d = search.getSampler(i).get();
                        //    if (min>d) min = d;
                        //}
                    }
                    ++searchScale;
                }*/
                if (Double.isInfinite(min)) return Double.NEGATIVE_INFINITY;
                return min;
            }
            public double getMeanOfMinCurvature() {
                curvL=Double.NaN;
                curvR=Double.NaN;
                if (borderVoxels.isEmpty() && borderVoxels2.isEmpty()) {
                    if (debug| ProcessingVariables.this.splitVerbose) logger.debug("{} : NO BORDER VOXELS");
                    if (voxels.isEmpty()) return Double.NEGATIVE_INFINITY;
                    else return Double.POSITIVE_INFINITY;
                }
                else {    
                    if (borderVoxels2.isEmpty()) {
                        if (debug| ProcessingVariables.this.splitVerbose) logger.debug("{}, GET CURV: {}, borderVoxels: {}", this, getMinCurvature(borderVoxels), borderVoxels.size());
                        return getMinCurvature(borderVoxels);
                    }
                    else {
                        //logger.debug("mean of min: b1: {}, b2: {}", getMinCurvature(borderVoxels), getMinCurvature(borderVoxels2));
                        //return 0.5 * (getMinCurvature(borderVoxels)+ getMinCurvature(borderVoxels2));
                        double min1 = getMinCurvature(borderVoxels);
                        double min2 = getMinCurvature(borderVoxels2);
                        this.curvL=min1;
                        this.curvR=min2;
                        double res;
                        if ((Math.abs(min1-min2)>2*Math.abs(curvatureThreshold))) { // when one side has a curvature very different from the other -> hole -> do not take into acount // TODO: check generality of criterion. put parameter? 
                            res = Math.max(min1, min2);
                        } else res = 0.5 * (min1 + min2); 
                        if (debug | ProcessingVariables.this.splitVerbose) logger.debug("{}, GET CURV: {}&{} -> {} , borderVoxels: {}&{}", this, min1, min2, res, borderVoxels.size(), borderVoxels2.size());
                        return res;
                    }
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
                //v.value=(float)pixVal;
            }
            private void setBorderVoxels() {
                borderVoxels.clear();
                borderVoxels2.clear();
                if (voxels.isEmpty()) return;
                // add border voxel
                ImageInteger mask = getJoinedMask();
                Set<Voxel> allBorderVoxels = new HashSet<>();
                for (Voxel v : voxels) if (borderNeigh.hasNullValue(v.x-mask.getOffsetX(), v.y-mask.getOffsetY(), v.z-mask.getOffsetZ(), mask, true)) allBorderVoxels.add(v);
                if ((debug| ProcessingVariables.this.splitVerbose) && allBorderVoxels.isEmpty()) ImageWindowManagerFactory.showImage(mask.duplicate("joindedMask "+this));
                //logger.debug("all border voxels: {}", allBorderVoxels.size());
                populateBoderVoxel(allBorderVoxels);
            }
            
            private void populateBoderVoxel(Collection<Voxel> allBorderVoxels) {
                if (allBorderVoxels.isEmpty()) return;
                
                BoundingBox b = new BoundingBox();
                for (Voxel v : allBorderVoxels) b.expand(v);
                ImageByte mask = new ImageByte("", b.getImageProperties());
                for (Voxel v : allBorderVoxels) mask.setPixelWithOffset(v.x, v.y, v.z, 1);
                RegionPopulation pop = new RegionPopulation(mask, false);
                pop.translate(b, false);
                List<Region> l = pop.getObjects();
                if (l.isEmpty()) logger.error("interface: {}, no side found", this);
                else if (l.size()>=1) { // case of small borders -> only one distinct side
                    borderVoxels.addAll(l.get(0).getVoxels());   
                    if (l.size()==2) borderVoxels2.addAll(l.get(1).getVoxels());} 
                else {
                    if (debug) logger.error("interface: {}, #{} sides found!!, {}", this, l.size(), input.getName());
                }
            }

            @Override public int compareTo(InterfaceBT t) { // decreasingOrder of curvature value
                //return Double.compare(t.maxDistance, maxDistance);
                int c = Double.compare(t.curvatureValue, curvatureValue);
                if (c==0) return super.compareElements(t, RegionCluster.regionComparator); // consitency with equals method
                else return c;
            }
            
            @Override
            public String toString() {
                return "Interface: " + e1.getLabel()+"+"+e2.getLabel()+ " sortValue: "+curvatureValue;
            }  
        }
        
    }
    
    public static class ContrastIntensity implements Filter {
        final double threshold;
        final double dilRadiusXY, dilRadiusZ;
        final Image intensityMap;
        final boolean keepOverThreshold;
        Map<Region, Region> dilatedObjects;
        boolean computeOutsideMeanForEachObject = true;
        double meanOut;
        public ContrastIntensity(double threshold, double dilatationRadiusXY, double dilatationRadiusZ, boolean keepOverThreshold, Image intensityMap) {
            this.threshold = threshold;
            this.intensityMap = intensityMap;
            this.dilRadiusXY=dilatationRadiusXY;
            this.dilRadiusZ=dilatationRadiusZ;
            this.keepOverThreshold=keepOverThreshold;
        }
        
        public ContrastIntensity setComputeOutsideMeanForEachObject(boolean computeOutsideMeanForEachObject) {
            this.computeOutsideMeanForEachObject = computeOutsideMeanForEachObject;
            return this;
        }
        
        @Override
        public void init(RegionPopulation population) {
            if (!computeOutsideMeanForEachObject) {
                ImageByte res = ImageOperations.getDilatedMask(population.getLabelMap(), dilRadiusXY, dilRadiusZ, ImageOperations.not(population.getLabelMap(), new ImageByte("", 0, 0, 0)), true);
                new IJImageDisplayer().showImage(res.setName("dil mask"));
                Region dilO = new Region(res, 1, res.getSizeZ()==1);
                meanOut = BasicMeasurements.getMeanValue(dilO, intensityMap, true);
            } else dilatedObjects = population.getDilatedObjects(dilRadiusXY, dilRadiusZ, true);
        }

        @Override
        public boolean keepObject(Region object) {
            Region dup = object.duplicate();
            dup.erode(Filters.getNeighborhood(dilRadiusXY, dilRadiusZ, intensityMap));
            if (dup.getVoxels().isEmpty()) dup = object;
            double mean = BasicMeasurements.getMeanValue(dup, intensityMap, false);
            double meanOut = computeOutsideMeanForEachObject ? BasicMeasurements.getMeanValue(dilatedObjects.get(object), intensityMap, false) : this.meanOut;
            if (debug) logger.debug("object: {}, mean: {}, mean out: {}, diff: {}, thld: {}, keep? {}", object.getLabel(), mean, meanOut, mean-meanOut, threshold, mean-meanOut >= threshold == keepOverThreshold);
            return mean-meanOut >= threshold == keepOverThreshold;
        }
        
    }
    public static class LocalContrast implements Filter {
        final double threshold;
        final Image intensityMap;
        
        public LocalContrast(double threshold, double radiusXY, double radiusZ, Image intensityMap) {
            this.threshold = threshold;
            Image intensityNorm = intensityMap;
            //TODO voir si on peut simplement diviser la carte de contraste par le scale de normalisation
            this.intensityMap = LocalContrastThresholder.getLocalContrast(intensityNorm, radiusXY);
            if (debug) new IJImageDisplayer().showImage(this.intensityMap.setName("contrast map"));
        }
                
        @Override
        public void init(RegionPopulation population) {}

        @Override
        public boolean keepObject(Region object) {
            List<Voxel> contour = new ArrayList<>(object.getContour());
            ContactBorder borderFilter = new ContactBorder(1, intensityMap, ContactBorder.Border.XY);
            borderFilter.setTolerance(1);
            contour.removeIf(v->borderFilter.contact(v));
            double value = contour.size()<15? Double.NaN : BasicMeasurements.getMeanValue(contour, intensityMap, false);
            if (debug) logger.debug("Local Contrast filter: object: {}, contour {} (after border remove: {}), value: {}, thld: {}, keep? {}", object.getLabel(), object.getContour().size(), contour.size(), value, threshold, Double.isNaN(value)||value>threshold);
            return Double.isNaN(value)||value>threshold;
        }
        
    }
    private static class RemoveThinBorder extends boa.image.processing.Filters.Filter {
        int xLim, yLim;
        @Override public void setUp(Image image, Neighborhood neighborhood) {
            super.setUp(image, neighborhood);
            this.xLim=image.getSizeX()-1;
            this.yLim=image.getSizeY()-1;
        }
        @Override
        public float applyFilter(int x, int y, int z) {
            float pix = image.getPixel(x, y, z);
            if (pix==0) return 0;
            if (x==0 && xLim>1 && image.getPixel(x+1, y, z)==0) return 0;
            if (x==xLim && xLim>1 && image.getPixel(x-1, y, z)==0) return 0;
            if (y==0 && yLim>1 && image.getPixel(x, y+1, z)==0) return 0;
            if (y==yLim && yLim>1 && image.getPixel(x, y-1, z)==0) return 0;
            return pix;
        }
    }

}