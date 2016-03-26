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
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import measurement.BasicMeasurements;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackers.ObjectIdxTracker;
import processing.EDT;
import processing.Filters;
import processing.FitEllipse;
import processing.ImageFeatures;
import processing.WatershedTransform;
import processing.localthickness.LocalThickness;
import processing.mergeRegions.InterfaceCollection;
import processing.mergeRegions.RegionCollection;
import utils.ArrayUtil;
import utils.ThreadRunner;
import utils.Utils;
import static utils.Utils.plotProfile;

/**
 *
 * @author jollion
 */
public class BacteriaTrans implements SegmenterSplitAndMerge {
    public static boolean debug = false;
    
    // configuration-related attributes
    NumberParameter openRadius = new BoundedNumberParameter("Open Radius", 1, 0, 0, null);
    NumberParameter splitThreshold = new BoundedNumberParameter("Split Threshold", 4, 0.12, 0, 1);
    NumberParameter minSize = new BoundedNumberParameter("Minimum size", 0, 100, 50, null);
    NumberParameter contactLimit = new BoundedNumberParameter("Contact Threshold with X border", 0, 10, 0, null);
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 3, 1, 5);
    NumberParameter dogScale = new BoundedNumberParameter("DoG scale", 0, 40, 5, null);
    NumberParameter hessianScale = new BoundedNumberParameter("Hessian scale", 1, 4, 1, 6);
    NumberParameter hessianThresholdFactor = new BoundedNumberParameter("Hessian threshold factor", 1, 1, 0, 5);
    NumberParameter thresholdForEmptyChannel = new BoundedNumberParameter("Threshold for empty channel", 1, 2, 0, null);
    Parameter[] parameters = new Parameter[]{splitThreshold, minSize, contactLimit, smoothScale, dogScale, hessianScale, hessianThresholdFactor, thresholdForEmptyChannel, openRadius};
    
    //segmentation-related attributes (kept for split and merge methods)
    Image distanceMap;
    ImageByte splitMask;
    double splitThresholdValue; 
    
    public BacteriaTrans setSplitThreshold(double splitThreshold) {
        this.splitThreshold.setValue(splitThreshold);
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
    public BacteriaTrans setHessianScale(double hessianScale) {
        this.hessianScale.setValue(hessianScale);
        return this;
    }
    public BacteriaTrans setHessianThresholdFactor(double hessianThresholdFactor) {
        this.hessianThresholdFactor.setValue(hessianThresholdFactor);
        return this;
    }
    public BacteriaTrans setOpenRadius(double openRadius) {
        this.openRadius.setValue(openRadius);
        return this;
    }
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        double fusionThreshold = splitThreshold.getValue().doubleValue();
        return run(input, parent.getMask(), fusionThreshold, minSize.getValue().intValue(), contactLimit.getValue().intValue(), smoothScale.getValue().doubleValue(), dogScale.getValue().doubleValue(), hessianScale.getValue().doubleValue(), hessianThresholdFactor.getValue().doubleValue(), thresholdForEmptyChannel.getValue().doubleValue(), openRadius.getValue().doubleValue(), this);
    }
    
    @Override
    public String toString() {
        return "Bacteria Fluo: " + Utils.toStringArray(parameters);
    }   
    
    public static ObjectPopulation run(Image input, ImageMask mask, double fusionThreshold, int minSize, int contactLimit, double smoothScale, double dogScale, double hessianScale, double hessianThresholdFactor, double thresholdForEmptyChannel, double openRadius, BacteriaTrans instance) {
        double thicknessThreshold = 5;
        final double relativeThicknessThreshold = 0.6;
        ImageDisplayer disp=debug?new IJImageDisplayer():null;
        //double hessianThresholdFacto = 1;
        
        ImageFloat dog = ImageFeatures.differenceOfGaussians(input, 0, dogScale, 1, false).setName("DoG");
        double threshold = IJAutoThresholder.runThresholder(dog, mask, null, AutoThresholder.Method.Otsu, 0);
        if (debug) logger.debug("threshold: {}", threshold);
        //ImageOperations.affineOperation(dog, dog, -1, threshold);
        
        //Image smoothed = Filters.median(input, input, Filters.getNeighborhood(1.5, 1.5, input)).setName("Smoothed");
        //Image smoothed = ImageFeatures.gaussianSmooth(input, 1.5, 1.5, false);
        
        //double t0 = IJAutoThresholder.runThresholder(intensityMap, mask, null, AutoThresholder.Method.Otsu, 0);
        
        
        //threshold=0;
        // criterion for empty channel: 
        double[] musigmaOver = getMeanAndSigma(dog, mask, threshold, true);
        double[] musigmaUnder = getMeanAndSigma(dog, mask, threshold, false);
        if (musigmaOver[2]==0 || musigmaUnder[2]==0) return new ObjectPopulation(input);
        else {            
            if (musigmaOver[0] - musigmaUnder[0]<thresholdForEmptyChannel) return new ObjectPopulation(input);
        }
        ObjectPopulation pop1 = SimpleThresholder.runUnder(dog, threshold);
        if (openRadius>=1) {
            for (Object3D o : pop1.getObjects()) {
                ImageInteger m = Filters.binaryOpen(o.getMask(), null, Filters.getNeighborhood(openRadius, openRadius, o.getMask()));
                o.setMask(m);
            }
            pop1.relabel();
            pop1 = new ObjectPopulation(pop1.getLabelImage(), false);
        }
        pop1.filter(new ObjectPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        pop1.filter(new ObjectPopulation.Size().setMin(minSize)); // remove small objects
        if (debug) {
            disp.showImage(pop1.getLabelImage().duplicate("first seg"));
            
        }
        
        /*
        segmentation based on thickness threshold
        1) analyse Y profil of max|X values of distance map -> search for means under threshold
        2) watershed on distance map with one seed at each segment (max)
        */
        ImageFloat edm = EDT.transform(pop1.getLabelImage(), true, 1, input.getScaleZ()/input.getScaleXY(), 1);
        
        WatershedTransform.FusionCriterion relativeThickness = new WatershedTransform.FusionCriterion() {
            WatershedTransform instance;
            public void setUp(WatershedTransform instance) {this.instance=instance;}
            public boolean checkFusionCriteria(WatershedTransform.Spot s1, WatershedTransform.Spot s2, Voxel currentVoxel) {
                double max1 = -Double.MAX_VALUE;
                double max2 = -Double.MAX_VALUE;
                for (Voxel v : s1.voxels) if (v.value>max1) max1 = v.value;
                for (Voxel v : s2.voxels) if (v.value>max2) max2 = v.value;
                double norm = Math.min(max1, max2);
                return currentVoxel.value/norm>relativeThicknessThreshold;
            }
        };
        ObjectPopulation res = WatershedTransform.watershed(edm, pop1.getLabelImage(), true, null, new WatershedTransform.MultipleFusionCriteria(new WatershedTransform.SizeFusionCriterion(minSize), relativeThickness)); //new WatershedTransform.ThresholdFusionOnWatershedMap(thicknessThreshold)
        
        
        
        if (debug) {
            disp.showImage(edm);
            disp.showImage(res.getLabelImage().setName("watershed EDM"));
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
        
        if (instance!=null) {
            instance.distanceMap=edm;
            instance.splitThresholdValue=relativeThicknessThreshold;
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

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    @Override public double split(Object3D o, List<Object3D> result) {
        if (distanceMap==null) throw new Error("Segment method have to be called before split method in order to initialize images");
        if (splitMask==null) splitMask = new ImageByte("split mask", distanceMap);
        o.draw(splitMask, 1);
        ObjectPopulation pop = WatershedObjectSplitter.split(distanceMap, splitMask, true);
        o.draw(splitMask, 0);
        if (pop==null || pop.getObjects().isEmpty() || pop.getObjects().size()==1) return Double.NaN;
        ArrayList<Object3D> remove = new ArrayList<Object3D>(pop.getObjects().size());
        pop.filter(new ObjectPopulation.Thickness().setX(2).setY(2), remove); // remove thin objects
        pop.filter(new ObjectPopulation.Size().setMin(minSize.getValue().intValue()), remove); // remove small objects
        if (pop.getObjects().size()<=1) return Double.NaN;
        else {
            if (!remove.isEmpty()) {
                logger.warn("BacteriaFluo split: small objects removed need to merge them");
                //return Double.NaN;
            }
            
            Object3D o1 = pop.getObjects().get(0);
            Object3D o2 = pop.getObjects().get(1);
            result.add(o1);
            result.add(o2);
            
            return splitThresholdValue-getInterfaceValue(getInterface(o1, o2))/getNomalizationValue(distanceMap, o1.getVoxels(), o2.getVoxels());
        }
    }

    @Override public double computeMergeCost(List<Object3D> objects) {
        if (distanceMap==null) throw new Error("Segment method have to be called before merge method in order to initialize images");
        if (splitMask==null) splitMask = new ImageByte("split mask", distanceMap);
        if (objects.isEmpty() || objects.size()==1) return 0;
        Iterator<Object3D> it = objects.iterator();
        Object3D ref  = objects.get(0);
        double maxCost = Double.MIN_VALUE;
        while (it.hasNext()) { //first round : remove objects not connected with ref & compute interactions with ref objects
            Object3D n = it.next();
            if (n!=ref) {
                ArrayList<Voxel> inter = getInterface(ref, n);
                if (inter.isEmpty()) it.remove();
                else {
                    double c = getInterfaceValue(inter) / getNomalizationValue(distanceMap, ref.getVoxels(), n.getVoxels());
                    if (c>maxCost) maxCost = c;
                }
            }
        }
        for (int i = 2; i<objects.size()-1; ++i) { // second round compute other interactions
            for (int j = i+1; j<objects.size(); ++j) {
                ArrayList<Voxel> inter = getInterface(objects.get(i), objects.get(j));
                if (!inter.isEmpty()) {
                    double c = getInterfaceValue(inter) / getNomalizationValue(distanceMap, objects.get(i).getVoxels(), objects.get(j).getVoxels());
                    if (c>maxCost) maxCost = c;
                }
            }
        }
        if (maxCost==Double.MIN_VALUE) return Double.NaN;
        return maxCost-splitThresholdValue;
    }
    private double getInterfaceValue(ArrayList<Voxel> inter) {
        return BasicMeasurements.getMeanValue(inter, distanceMap, false);
    }
    
    private ArrayList<Voxel> getInterface(Object3D o1, Object3D o2) {
        o1.draw(splitMask, o1.getLabel());
        o2.draw(splitMask, o2.getLabel());
        ArrayList<Voxel> inter = InterfaceCollection.getInteface(o1, o2, splitMask);
        o1.draw(splitMask, 0);
        o2.draw(splitMask, 0);
        return inter;
    }
}
