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

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Histogram;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageMask;
import boa.image.MutableBoundingBox;
import boa.image.SimpleBoundingBox;
import boa.image.processing.RegionFactory;
import boa.image.processing.clustering.RegionCluster;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.plugins.ManualSegmenter;
import boa.plugins.ObjectSplitter;
import static boa.plugins.Plugin.logger;
import boa.plugins.SegmenterSplitAndMerge;
import boa.plugins.TestableProcessingPlugin;
import boa.plugins.ToolTip;
import boa.plugins.TrackParametrizable;
import boa.plugins.plugins.pre_filters.ImageFeature;
import boa.plugins.plugins.pre_filters.Sigma;
import static boa.plugins.plugins.segmenters.BacteriaFluo.getCost;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import boa.ui.GUI;
import boa.utils.Pair;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 * @author Jean Ollion
 * @param <T>
 */
public abstract class BacteriaIntensitySegmenter<T extends BacteriaIntensitySegmenter<T>> implements TrackParametrizable<T>, SegmenterSplitAndMerge, ManualSegmenter, ObjectSplitter, ToolTip, TestableProcessingPlugin {
    protected final int MIN_SIZE_PROPAGATION = 50;
    NumberParameter vcThldForVoidMC = new BoundedNumberParameter("Variation coefficient threshold", 3, 0.085, 0, null).setToolTipText("Parameter to look for void microchannels, at track pre-filter step: <br /> To assess if whole microchannel track is void: sigma / mu of raw images is computed on whole track, in the central line of each microchannel (1/3 of the width). If sigma / mu < this value, the whole track is considered to be void. <br />If the track is not void, a global otsu threshold is computed on the prefiltered signal. A channel is considered as void if its value of sigma / mu of raw signal is inferior to this threshold and is its mean value of prefiltered signal is superior to the global threshold");
    
    PreFilterSequence edgeMap = new PreFilterSequence("Edge Map").add(new ImageFeature().setFeature(ImageFeature.Feature.GAUSS).setScale(1.5), new Sigma(2).setMedianRadius(0)).setToolTipText("Filters used to define edge map used in first watershed step. <br />Max eigen value of Structure tensor is a good option<br />Median/Gaussian + Sigma is more suitable for noisy images (involve less derivation)<br />Gradient magnitude is another option. <br />Configuration Hint: tune this value using the intermediate images <em>Edge Map for Partitioning</em> and <em>Region Values after partitioning</em>");  // min scale = 1 (noisy signal:1.5), max = 2 min smooth scale = 1.5 (noisy / out of focus: 2) //new ImageFeature().setFeature(ImageFeature.Feature.StructureMax).setScale(1.5).setSmoothScale(2)
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 2, 0, 5).setToolTipText("Scale (pixels) for gaussian filtering for the local thresholding step");
    NumberParameter hessianScale = new BoundedNumberParameter("Hessian scale", 1, 4, 1, 6).setToolTipText("In pixels. Used in step 2). Lower value -> finner split, more sentitive to noise. Influences the value of split threshold parameter. <br />Configuration Hint: tune this value using the intermediate image <em>Hessian</em>");
    NumberParameter splitThreshold = new BoundedNumberParameter("Split Threshold", 4, 0.3, 0, null).setEmphasized(true).setToolTipText("At step 2) regions are merge if sum(hessian)|interface / sum(raw intensity)|interface < (this parameter). <br />Lower value splits more.  <br />Configuration Hint: Tune the value using intermediate image <em>Interface Values before merge by Hessian</em>, interface with a value over this threshold will not be merged");
    NumberParameter localThresholdFactor = new BoundedNumberParameter("Local Threshold Factor", 2, 1.25, 0, null);
    NumberParameter minSize = new BoundedNumberParameter("Minimum Region Size", 0, 100, 50, null).setToolTipText("Minimum Object Size in voxels");
    
    //segmentation-related attributes (kept for split and merge methods)
    EdgeDetector edgeDetector;
    SplitAndMergeHessian splitAndMerge;
    StructureObjectProcessing currentParent;
    
    // parameter from track parametrizable
    double globalBackgroundLevel;
    boolean isVoid;
    
    public T setSplitThreshold(double splitThreshold) {
        this.splitThreshold.setValue(splitThreshold);
        return (T)this;
    }
    public T setMinSize(int minSize) {
        this.minSize.setValue(minSize);
        return (T)this;
    }
    public T setSmoothScale(double smoothScale) {
        this.smoothScale.setValue(smoothScale);
        return (T)this;
    }
    public T setHessianScale(double hessianScale) {
        this.hessianScale.setValue(hessianScale);
        return (T)this;
    }
    public T setLocalThresholdFactor(double localThresholdFactor) {
        this.localThresholdFactor.setValue(localThresholdFactor);
        return (T)this;
    }
    protected SplitAndMergeHessian initializeSplitAndMerge(StructureObjectProcessing parent, int structureIdx, ImageMask foregroundMask) {
        //if (edgeDetector==null) edgeDetector = initEdgeDetector(parent, structureIdx); //call from split/merge/manualseg
        SplitAndMergeHessian res= new SplitAndMergeHessian(parent.getPreFilteredImage(structureIdx), splitThreshold.getValue().doubleValue(), hessianScale.getValue().doubleValue(), globalBackgroundLevel);
        //res.setWatershedMap(parent.getPreFilteredImage(structureIdx), false).setSeedCreationMap(parent.getPreFilteredImage(structureIdx), true);  // better results when using hessian as watershed map -> takes into 
        //if (stores!=null) res.setTestMode(TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent));
        return res;
    }
    /**
     * Initialize the EdgeDetector that will create a parittion of the image and filter the regions
     * @param parent
     * @param structureIdx
     * @return 
     */
    protected EdgeDetector initEdgeDetector(StructureObjectProcessing parent, int structureIdx) {
        EdgeDetector seg = new EdgeDetector().setIsDarkBackground(true);
        seg.minSizePropagation.setValue(0);
        seg.setPreFilters(edgeMap.get());
        seg.setThrehsoldingMethod(EdgeDetector.THLD_METHOD.NO_THRESHOLDING);
        //seg.setTestMode( TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent));
        return seg;
    }
    @Override public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        if (isVoid) return null;
        
        Consumer<Image> imageDisp = TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent);
        edgeDetector = initEdgeDetector(parent, structureIdx);
        RegionPopulation splitPop = edgeDetector.runSegmenter(input, structureIdx, parent);
        splitPop.smoothRegions(1, true, parent.getMask());
        if (stores!=null) imageDisp.accept(edgeDetector.getWsMap(parent.getPreFilteredImage(structureIdx), parent.getMask()).setName("Edge Map for Partitioning"));
        if (stores!=null) imageDisp.accept(EdgeDetector.generateRegionValueMap(splitPop, parent.getPreFilteredImage(structureIdx)).setName("Region Values After Partitioning"));
        splitPop = filterRegionsAfterEdgeDetector(parent, structureIdx, splitPop);
        if (stores!=null) imageDisp.accept(EdgeDetector.generateRegionValueMap(splitPop, parent.getPreFilteredImage(structureIdx)).setName("Region Values After Filtering of Paritions"));
        if (splitAndMerge==null || !parent.equals(currentParent)) {
            currentParent = parent;
            splitAndMerge = initializeSplitAndMerge(parent, structureIdx, parent.getMask());
        }
        RegionPopulation split = splitAndMerge.split(splitPop.getLabelMap(), MIN_SIZE_PROPAGATION);
        if (stores!=null) {
            imageDisp.accept(splitAndMerge.getHessian().setName("Hessian"));
            
        }
        split = filterRegionAfterSplitByHessian(parent, structureIdx, split);
        if (stores!=null)  {
            imageDisp.accept(EdgeDetector.generateRegionValueMap(split, input).setName("Region Values before merge by Hessian"));
            imageDisp.accept(splitAndMerge.drawInterfaceValues(split).setName("Interface values before merge by Hessian"));
        }
        RegionPopulation res = splitAndMerge.merge(split, null);
        res = localThreshold(input, res, parent, structureIdx, false);
        if (stores!=null)  imageDisp.accept(res.getLabelMap().duplicate("Region Labels after local threshold"));
        res.filter(new RegionPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        res.filter(new RegionPopulation.Size().setMin(minSize.getValue().intValue())); // remove small objects
        
        res.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        
        if (stores!=null) {
            int pSIdx = ((StructureObject)parent).getExperiment().getStructure(structureIdx).getParentStructure();
            stores.get(parent).addMisc("Display Thresholds", l->{
                if (l.stream().map(o->o.getParent(pSIdx)).anyMatch(o->o==parent)) {
                    displayAttributes();
                }
            });
        }
        return res;
    }
    protected abstract void displayAttributes();
    protected abstract RegionPopulation filterRegionsAfterEdgeDetector(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop);
    protected abstract RegionPopulation filterRegionAfterSplitByHessian(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop);
    protected abstract RegionPopulation localThreshold(Image input, RegionPopulation pop, StructureObjectProcessing parent, int structureIdx, boolean callFromSplit);

    
    // segmenter split and merge interface
    protected ImageByte tempSplitMask;
    private SplitAndMergeHessian.Interface getInterface(Region o1, Region o2) {
        o1.draw(tempSplitMask, o1.getLabel());
        o2.draw(tempSplitMask, o2.getLabel());
        SplitAndMergeHessian.Interface inter = RegionCluster.getInteface(o1, o2, tempSplitMask, splitAndMerge.getFactory());
        inter.updateInterface();
        o1.draw(tempSplitMask, 0);
        o2.draw(tempSplitMask, 0);
        return inter;
    }
    @Override 
    public double split(StructureObject parent, int structureIdx, Region o, List<Region> result) {
        result.clear();
        RegionPopulation pop =  splitObject(parent, structureIdx, o); // after this step pop is in same landmark as o
        if (pop.getRegions().size()<=1) return Double.POSITIVE_INFINITY;
        else {
            if (tempSplitMask==null) tempSplitMask = new ImageByte("split mask", parent.getMask());
            result.addAll(pop.getRegions());
            if (pop.getRegions().size()>2) return 0; //   objects could not be merged during step process means no contact (effect of local threshold)
            SplitAndMergeHessian.Interface inter = getInterface(result.get(0), result.get(1));
            //logger.debug("split @ {}-{}, inter size: {} value: {}/{}", parent, o.getLabel(), inter.getVoxels().size(), inter.value, splitAndMerge.splitThresholdValue);
            if (inter.getVoxels().size()<=1) return 0;
            double cost = getCost(inter.value, splitAndMerge.splitThresholdValue, true);
            return cost;
        }
        
    }

    @Override public double computeMergeCost(StructureObject parent, int structureIdx, List<Region> objects) {
        if (objects.isEmpty() || objects.size()==1) return 0;
        Image input = parent.getPreFilteredImage(structureIdx);
        RegionPopulation mergePop = new RegionPopulation(objects, objects.get(0).isAbsoluteLandMark() ? input : new BlankMask(input).resetOffset());
        mergePop.relabel(false); // ensure distinct labels , if not cluster cannot be found
        if (splitAndMerge==null || !parent.equals(currentParent)) {
            currentParent = parent;
            splitAndMerge = initializeSplitAndMerge(parent, structureIdx, parent.getMask());
        }
        RegionCluster c = new RegionCluster(mergePop, true, splitAndMerge.getFactory());
        List<Set<Region>> clusters = c.getClusters();
        if (clusters.size()>1) { // merge impossible : presence of disconnected objects
            if (stores!=null) logger.debug("merge impossible: {} disconnected clusters detected", clusters.size());
            return Double.POSITIVE_INFINITY;
        } 
        double maxCost = Double.NEGATIVE_INFINITY;
        Set<SplitAndMergeHessian.Interface> allInterfaces = c.getInterfaces(clusters.get(0));
        for (SplitAndMergeHessian.Interface i : allInterfaces) {
            i.updateInterface();
            if (i.value>maxCost) maxCost = i.value;
        }

        if (Double.isInfinite(maxCost)) return Double.POSITIVE_INFINITY;
        return getCost(maxCost, splitAndMerge.splitThresholdValue, false);
        
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
    
    // manual correction implementations
    private boolean verboseManualSeg;
    @Override public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }
    // object splitter interface
    boolean splitVerbose;
    @Override public void setSplitVerboseMode(boolean verbose) {
        this.splitVerbose=verbose;
    }
    @Override public RegionPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        
        List<Region> seedObjects = RegionFactory.createSeedObjectsFromSeeds(seedsXYZ, input.sizeZ()==1, input.getScaleXY(), input.getScaleZ());
        EdgeDetector seg = initEdgeDetector(parent, structureIdx);
        RegionPopulation pop = seg.run(input, segmentationMask);
        SplitAndMergeHessian splitAndMerge=initializeSplitAndMerge(parent, structureIdx, pop.getLabelMap());
        pop = splitAndMerge.merge(pop, null);
        pop.filter(o->{
            for(Region so : seedObjects ) if (o.intersect(so)) return true;
            return false;
        });
        pop.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        localThreshold(input, pop, parent, structureIdx, false);
        //RegionPopulation pop =  WatershedTransform.watershed(splitAndMerge.getHessian(), segmentationMask, seedObjects, false, new WatershedTransform.ThresholdPropagation(getNormalizedHessian(input), this.manualSegPropagationHessianThreshold.getValue().doubleValue(), false), new WatershedTransform.SizeFusionCriterion(this.minSize.getValue().intValue()), false);
        
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixel(seed[0], seed[1], seed[2], 1);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(seedMap);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(splitAndMerge.getHessian());
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pop.getLabelMap().setName("segmented from: "+input.getName()));
        }
        
        return pop;
    }
    
    // track parameterization 
    /**
     * Detected void microchannels
     * All microchannels are void if variation coefficient (sigma/mu) of raw sigal is inferior to the corresponding parameter value
     * If not, a global threshold is computed on all prefiltered images, if mean prefiltered value < global thld OR variation coefficient < parameter -> the microchannel is considered as void
     * @param structureIdx
     * @param parentTrack
     * @return void microchannels
     */
    protected Set<StructureObject> getVoidMicrochannels(int structureIdx, List<StructureObject> parentTrack) {
        double globalVoidThldSigmaMu = vcThldForVoidMC.getValue().doubleValue();
        // get sigma in the middle line of each MC
        double[] globalSum = new double[4];
        boolean phase = this instanceof BacteriaPhaseContrast; // min value only computed for fluo. 0 -> phase
        globalSum[3] = phase ? 0 : Double.POSITIVE_INFINITY;
        Function<StructureObject, float[]> compute = p->{
            Image imR = p.getRawImage(structureIdx);
            Image im = p.getPreFilteredImage(structureIdx);
            if (im==null) throw new RuntimeException("no prefiltered image");
            int xMargin = im.sizeX()/3;
            MutableBoundingBox bb= new MutableBoundingBox(im).resetOffset().extend(new SimpleBoundingBox(xMargin, -xMargin, im.sizeX(), -im.sizeY()/6, 0, 0)); // only central line to avoid border effects + remove open end -> sometimes optical aberration
            double[] sum = new double[2];
            double[] sumR = new double[4];
            sumR[3] = phase? 0 : Double.POSITIVE_INFINITY;
            BoundingBox.loop(bb, (x, y, z)-> {
                if (p.getMask().insideMask(x, y, z)) {
                    double v = im.getPixel(x, y, z);
                    sum[0]+=v;
                    sum[1]++;
                    v = imR.getPixel(x, y, z);
                    sumR[0]+=v;
                    sumR[1]+=v*v;
                    sumR[2]++;
                    if (!phase && sumR[3]>v) sumR[3] = v;
                }
            });
            synchronized(globalSum) {
                globalSum[0]+=sumR[0];
                globalSum[1]+=sumR[1];
                globalSum[2]+=sumR[2];
                if (!phase && globalSum[3]>sumR[3]) globalSum[3] = sumR[3];
            }
            double meanR = sumR[0]/sumR[2];
            double meanR2 = sumR[1]/sumR[2];
            return new float[]{(float)(sum[0]/sum[1]), (float)meanR, (float)Math.sqrt(meanR2 - meanR * meanR), (float)sumR[3]};
        };
        List<float[]> meanF_meanR_sigmaR_minR = parentTrack.stream().parallel().map(p->compute.apply(p)).collect(Collectors.toList());
        // 1) criterion for void microchannel track
        double globalMean = globalSum[0]/globalSum[2];
        double globalMin = globalSum[3];
        double globalSigma = Math.sqrt(globalSum[1]/globalSum[2] - globalMean * globalMean);
        if (globalSigma/(globalMean-globalMin)<globalVoidThldSigmaMu) {
            logger.debug("parent: {} sigma/mean: {}/{}-{}={} all channels considered void: {}", parentTrack.get(0), globalSigma, globalMean, globalMin, globalSigma/(globalMean-globalMin));
            return new HashSet<>(parentTrack);
        }
        // 2) criterion for void microchannels : low intensity value
        // intensity criterion based on global threshold (otsu for phase, backgroundFit(5) for fluo
        double globalThld = getGlobalThreshold(parentTrack, structureIdx);

        Set<StructureObject> outputVoidMicrochannels = IntStream.range(0, parentTrack.size())
                .filter(idx -> meanF_meanR_sigmaR_minR.get(idx)[0]<globalThld)  // test on mean value is because when mc is very full, sigma can be low enough
                .filter(idx -> meanF_meanR_sigmaR_minR.get(idx)[2]/(meanF_meanR_sigmaR_minR.get(idx)[1]-meanF_meanR_sigmaR_minR.get(idx)[3])<globalVoidThldSigmaMu) // test on sigma/mu value because when mc is nearly void, mean can be low engough
                .mapToObj(idx -> parentTrack.get(idx))
                .collect(Collectors.toSet());
        logger.debug("parent: {} global Sigma/Mean-Min {}/{}-{}={} global thld: {} void mc {}/{}", parentTrack.get(0), globalSigma,globalMean, globalMin, globalSigma/(globalMean-globalMin), globalThld,outputVoidMicrochannels.size(), parentTrack.size() );
        logger.debug("10 frames lower std/mu: {}", IntStream.range(0, parentTrack.size()).mapToObj(idx -> new Pair<>(parentTrack.get(idx).getFrame(), meanF_meanR_sigmaR_minR.get(idx)[2]/(meanF_meanR_sigmaR_minR.get(idx)[1]-meanF_meanR_sigmaR_minR.get(idx)[3]))).sorted((p1, p2)->Double.compare(p1.value, p2.value)).limit(10).collect(Collectors.toList()));
        logger.debug("10 frames upper std/mu: {}", IntStream.range(0, parentTrack.size()).mapToObj(idx -> new Pair<>(parentTrack.get(idx).getFrame(), meanF_meanR_sigmaR_minR.get(idx)[2]/(meanF_meanR_sigmaR_minR.get(idx)[1]-meanF_meanR_sigmaR_minR.get(idx)[3]))).sorted((p1, p2)->-Double.compare(p1.value, p2.value)).limit(10).collect(Collectors.toList()));
        
        //logger.debug("s/mu : {}", Utils.toStringList(meanF_meanR_sigmaR.subList(10, 15), f->"mu="+f[0]+" muR="+f[1]+"sR="+f[2]+ " sR/muR="+f[2]/f[1]));
        return outputVoidMicrochannels;
    }
    /**
     * 
     * @param parent
     * @param structureIdx
     * @return global threshold for void microchannel test
     */
    protected abstract double getGlobalThreshold(List<StructureObject> parent, int structureIdx);
    
    
    // testable processing plugin
    Map<StructureObject, TestDataStore> stores;
    @Override public void setTestDataStore(Map<StructureObject, TestDataStore> stores) {
        this.stores=  stores;
    }
}
