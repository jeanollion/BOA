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
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PostFilterSequence;
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
import java.util.ArrayList;
import java.util.List;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import plugins.Segmenter;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.measurements.objectFeatures.IntensityRatio;
import plugins.plugins.measurements.objectFeatures.SNR;
import plugins.plugins.postFilters.FeatureFilter;
import processing.Filters;
import processing.ImageFeatures;
import processing.MultiScaleWatershedTransform;
import processing.WatershedTransform;
import processing.WatershedTransform.MonotonalPropagation;
import processing.WatershedTransform.MultiplePropagationCriteria;
import processing.WatershedTransform.SizeFusionCriterion;
import processing.WatershedTransform.ThresholdPropagationOnWatershedMap;
import static processing.WatershedTransform.watershed;
import processing.neighborhood.ConditionalNeighborhoodZ;
import processing.neighborhood.CylindricalNeighborhood;
import processing.neighborhood.EllipsoidalNeighborhood;

/**
 *
 * @author jollion
 */
public class MutationSegmenterScaleSpace implements Segmenter, ManualSegmenter, ObjectSplitter {
    public static boolean debug = false;
    public static boolean displayImages = false;
    NumberParameter minSpotSize = new BoundedNumberParameter("Min. Spot Size (Voxels)", 0, 5, 1, null);
    NumberParameter thresholdHigh = new BoundedNumberParameter("Threshold for Seeds", 3, 2.5, 1, null);
    NumberParameter thresholdLow = new BoundedNumberParameter("Threshold for propagation", 3, 1.5, 0, null);
    NumberParameter intensityThreshold = new BoundedNumberParameter("Intensity Threshold for Seeds", 2, 115, 0, null);
    PostFilterSequence postFilters = new PostFilterSequence("Post-Filters").add(new FeatureFilter(new SNR().setBackgroundObjectStructureIdx(1), 0.75, true, true));
    Parameter[] parameters = new Parameter[]{minSpotSize, thresholdHigh,  thresholdLow, intensityThreshold, postFilters};
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        ObjectPopulation res= run(input, parent.getMask(), minSpotSize.getValue().intValue(), intensityThreshold.getValue().doubleValue(), thresholdHigh.getValue().doubleValue(), thresholdLow.getValue().doubleValue(), null);
        return postFilters.filter(res, structureIdx, (StructureObject)parent);
    }
    public PostFilterSequence getPostFilters() {return postFilters;}
    
    public MutationSegmenterScaleSpace setThresholdSeeds(double threshold) {
        this.thresholdHigh.setValue(threshold);
        return this;
    }
    
    public static ObjectPopulation run(Image input, ImageMask mask, int minSpotSize, double thresholdSeedsIntensity, double thresholdHigh , double thresholdLow, ArrayList<Image> intermediateImages) {
        if (input.getSizeZ()>1) {
            /*
            // tester sur average, max, ou plan par plan
            ArrayList<Image> planes = input.splitZPlanes();
            ArrayList<ObjectPopulation> populations = new ArrayList<ObjectPopulation>(planes.size());
            for (Image plane : planes) {
                ObjectPopulation obj = runPlaneHybrid(plane, mask, minSpotSize, thresholdHigh, thresholdLow, intermediateImages);
                //if (true) return obj;
                if (obj!=null && !obj.getObjects().isEmpty()) populations.add(obj);
            }
            if (populations.isEmpty()) return new ObjectPopulation(new ArrayList<Object3D>(0), planes.get(0));
            // combine: 
            ObjectPopulation pop = populations.remove(populations.size()-1);
            pop.combine(populations);
            return pop;
            */
            throw new Error("MutationSegmenter: should be run only a 2D image");
        } else return runPlaneHybrid(input, mask, minSpotSize, thresholdSeedsIntensity, thresholdHigh, thresholdLow, intermediateImages);
    }
    
    /*public static ObjectPopulation runPlane(Image input, ImageMask mask, int minSpotSize, double thresholdSeeds, double thresholdPropagation, ArrayList<Image> intermediateImages) {
        if (input.getSizeZ()>1) throw new Error("MutationSegmenter: should be run on a 2D image");
        double[] radii = new double[]{2, 2.5, 3, 3.5, 4.5, 7};
        int maxScaleIdx=radii.length-1-2;
        double scale =2;
        Image scaleSpace = ImageFeatures.getScaleSpaceHessianMaxNorm(input, radii, ImageFeatures.gaussianSmooth(input, 2, 2, false), 100);
        //Image scaleSpace = ImageFeatures.getScaleSpaceLaplacian(input, new double[]{scale, scale+2, scale+4});
        ImageByte seedsSP = Filters.localExtrema(scaleSpace, null, true, thresholdSeeds, new CylindricalNeighborhood(scale, 1, false) ).setName("seedsSP"); // new CircularNeighborhoodPerSlice(radii, scaleSpace.getSizeZ(), false) //scaleSpace.getSizeZ()
        if (intermediateImages!=null) {
            //intermediateImages.add(input);
            intermediateImages.add(scaleSpace);
            intermediateImages.add(seedsSP);
            intermediateImages.add(ImageOperations.maxZProjection(seedsSP, 0, maxScaleIdx).setName("all seeds"));
            //intermediateImages.add(ImageFeatures.getScaleSpaceHessianDet(input, new double[]{scale-0.5, scale, scale+0.5, scale+1.5, scale+2.5, scale+3.5}));
        }
        
        ObjectPopulation pop =  MultiScaleWatershedTransform.watershed(scaleSpace.splitZPlanes(0, maxScaleIdx).toArray(new Image[0]), mask, seedsSP.splitZPlanes(0, maxScaleIdx).toArray(new ImageByte[0]), true, new MultiScaleWatershedTransform.MultiplePropagationCriteria(new MultiScaleWatershedTransform.ThresholdPropagationOnWatershedMap(thresholdPropagation), new MultiScaleWatershedTransform.MonotonalPropagation()), new MultiScaleWatershedTransform.SizeFusionCriterion(1));
        
        pop.filter(new ObjectPopulation.RemoveFlatObjects(input));
        pop.filter(new ObjectPopulation.Size().setMin(minSpotSize));
        return pop;
    }*/
    
    /*
    public static ObjectPopulation runPlaneMono(Image input, ImageMask mask, int minSpotSize, double thresholdSeeds, double thresholdPropagation, ArrayList<Image> intermediateImages) {
        if (input.getSizeZ()>1) throw new Error("MutationSegmenter: should be run on a 2D image");
        double[] radii = new double[]{2, 2.5, 3, 3.5, 4.5, 7};
        int wsPlane=1;
        int maxScaleIdx=radii.length-1-2;
        Image scaleSpace = ImageFeatures.getScaleSpaceHessianMaxNorm(input, radii, ImageFeatures.gaussianSmooth(input, 2, 2, false), 100); 
        Image watershedMap = scaleSpace.getZPlane(wsPlane);
        ImageByte seedsSP = Filters.localExtrema(scaleSpace, null, true, thresholdSeeds, new CylindricalNeighborhood(2, 2, false) ).setName("seedsSP"); //
        //ImageByte seeds = ImageOperations.maxZProjection(seedsSP, 0, 3).setName("seeds"); // select only seeds from plane 1 2 & 3 (exclude big objects)
        ImageByte seeds = seedsSP.getZPlane(wsPlane).setName("seeds");
        for (int i = 0;i<=maxScaleIdx; ++i) if (i!=wsPlane) combineSeeds(seedsSP.getZPlane(i), seeds, watershedMap, radii[i]);
        
        if (intermediateImages!=null) {
            //intermediateImages.add(input);
            intermediateImages.add(scaleSpace);
            intermediateImages.add(seedsSP);// sur le plan 1 -> combinaison des plans 0 et 2..
            intermediateImages.add(seeds);
        }
        
        ObjectPopulation seedPop = new ObjectPopulation(seeds, false);
        ObjectPopulation pop =  watershed(watershedMap, mask, seedPop.getObjects(), true, new MultiplePropagationCriteria(new ThresholdPropagationOnWatershedMap(thresholdPropagation), new MonotonalPropagation()), new SizeFusionCriterion(1));
        
        pop.filter(new ObjectPopulation.RemoveFlatObjects(input));
        pop.filter(new ObjectPopulation.Size().setMin(minSpotSize));
        return pop;
    }*/
    
    public static ObjectPopulation runPlaneHybrid(Image input, ImageMask mask, int minSpotSize, double thresholdSeedsIntensity, double thresholdSeeds, double thresholdPropagation, ArrayList<Image> intermediateImages) {
        if (input.getSizeZ()>1) throw new Error("MutationSegmenter: should be run on a 2D image");
        double[] radii = new double[]{2, 2.5, 3, 3.5, 6, 7}; // 5,7 ??
        int maxScaleIdx=radii.length-1-2;
        int maxScaleWSIdx=1;
        Image smooth = ImageFeatures.gaussianSmooth(input, 2, 2, false);
        Image scaleSpace = getScaleSpace(input, smooth, radii); 
        ImageByte seedsSP = getSeedsScaleSpace(scaleSpace, thresholdSeeds, 1.5, maxScaleIdx);
        //new IJImageDisplayer().showImage(seedsSP.duplicate("before filter intensity"));
        //new IJImageDisplayer().showImage(smooth.duplicate("smoothed"));
        // filter by intensity : remove seeds with low intensity
        for (int z = 0; z<seedsSP.getSizeZ(); ++z) {
            for (int xy = 0; xy<seedsSP.getSizeXY(); ++xy) {
                if (seedsSP.insideMask(xy, z) && smooth.getPixel(xy, 0)<thresholdSeedsIntensity) seedsSP.setPixel(xy, z, 0);
            }
        }
        Image[] wsMaps = scaleSpace.splitZPlanes(0, maxScaleWSIdx).toArray(new Image[0]);
        ImageByte[] seedMaps = seedsSP.splitZPlanes(0, maxScaleWSIdx).toArray(new ImageByte[0]); //remove seeds from 2 last radii
        
        // combine seeds: project seeds from higher radii to radius @maxScaleWSIdx  
        for (int i = maxScaleWSIdx+1; i<=maxScaleIdx; ++i) combineSeeds(seedsSP.getZPlane(i), seedMaps[maxScaleWSIdx], wsMaps[maxScaleWSIdx], radii[i]);
        //for (int i = 0; i<maxScaleWSIdx; ++i) removeSeeds(seedMaps[maxScaleWSIdx], seedMaps[i], 1.5);
        if (intermediateImages!=null) {
            //intermediateImages.add(input);
            intermediateImages.add(scaleSpace);
            intermediateImages.add(seedsSP);
        }
        
        
        ObjectPopulation pop =  MultiScaleWatershedTransform.watershed(wsMaps, mask, seedMaps, true, new MultiScaleWatershedTransform.MultiplePropagationCriteria(new MultiScaleWatershedTransform.ThresholdPropagationOnWatershedMap(thresholdPropagation)), new MultiScaleWatershedTransform.SizeFusionCriterion(minSpotSize));// minSpotSize->1 //, new MultiScaleWatershedTransform.MonotonalPropagation()
        
        pop.filter(new ObjectPopulation.RemoveFlatObjects(input));
        pop.filter(new ObjectPopulation.Size().setMin(minSpotSize));
        return pop;
    }
    
    private static Image getScaleSpace(Image input, Image norm, double[] radii) {
        //return ImageFeatures.getScaleSpaceHessianMax(input, radii);
        //return ImageFeatures.getScaleSpaceHessianMax(input, radii);
        return ImageFeatures.getScaleSpaceHessianMaxNorm(input, radii, norm, 100); // ou scale = 3
    }
    
    private static ImageByte getSeedsScaleSpace(Image scaleSpace, double thresholdSeeds, double radius, int maxScaleIdx) {
        return Filters.localExtrema(scaleSpace, null, true, thresholdSeeds, new ConditionalNeighborhoodZ(new CylindricalNeighborhood(radius, 1, false)).setNeighborhood(maxScaleIdx, new CylindricalNeighborhood(radius, 1, 2, false)) ).setName("seedsSP"); //new CylindricalNeighborhood(2, 1, false) //
    }
    
    private static void combineSeeds(ImageByte input, ImageByte output, Image watershedMap, double scale) { // seeds from other plane do not necessaryly correspond to a local max in the current plane
        ObjectPopulation seedPop0 = new ObjectPopulation(input, false);
        EllipsoidalNeighborhood n = new EllipsoidalNeighborhood(scale, false);
        for (Object3D o : seedPop0.getObjects()) {
            for (Voxel v : o.getVoxels()) {
                n.setPixelsByIndex(v, watershedMap);
                float max = n.getPixelValues()[0];
                int idxMax = 0;
                for (int i = 1; i<n.getValueCount(); ++i) if (n.getPixelValues()[i]>max) {
                    max=n.getPixelValues()[i];
                    idxMax = i;
                }
                output.setPixel(v.x+n.dx[idxMax], v.y+n.dy[idxMax], 0, 1);
            }
        }
    }
    
    private static void removeSeeds(ImageByte template, ImageByte toRemove, double scale) { // seeds from other plane do not necessaryly correspond to a local max in the current plane
        EllipsoidalNeighborhood n = new EllipsoidalNeighborhood(scale, false);
        for (int z = 0; z<template.getSizeZ(); ++z) {
            for (int y=0; y<template.getSizeY(); ++y) {
                for (int x=0; x<template.getSizeX(); ++x) {
                    if (toRemove.getPixelInt(x, y, z)>0) {
                        n.setPixels(x, y, z, template);
                        for (float v : n.getPixelValues()) {
                            if (v>0) {
                                toRemove.setPixel(x, y, z, 0);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
    

    public Parameter[] getParameters() {
        return parameters;
    }
    
    // manual segmenter implementation
    
    private boolean verboseManualSeg;
    public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }

    public ObjectPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        Image smooth = ImageFeatures.gaussianSmooth(input, 2, 2, false);
        Image scaleSpace = getScaleSpace(input, smooth, new double[]{2.5}).setName("WatershedMap from: "+input.getName());
        List<Object3D> seedObjects = ObjectFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getScaleXY(), input.getScaleZ());
        ObjectPopulation pop =  WatershedTransform.watershed(scaleSpace, segmentationMask, seedObjects, true, new WatershedTransform.ThresholdPropagationOnWatershedMap(thresholdLow.getValue().doubleValue()), new WatershedTransform.SizeFusionCriterion(minSpotSize.getValue().intValue()));
        
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixelWithOffset(seed[0], seed[1], seed[2], 1);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(seedMap);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(scaleSpace);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pop.getLabelMap().setName("segmented from: "+input.getName()));
        }
        
        return pop;
    }
    // object splitter implementation
    public ObjectPopulation splitObject(Image input, Object3D object) {
        return WatershedObjectSplitter.splitInTwo(input, object.getMask(), true, this.minSpotSize.getValue().intValue(), manualSplitVerbose);
    }
    boolean manualSplitVerbose;
    public void setSplitVerboseMode(boolean verbose) {
        manualSplitVerbose=verbose;
    }
      
}
