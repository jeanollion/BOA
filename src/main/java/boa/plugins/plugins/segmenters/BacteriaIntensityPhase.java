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

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.RegionPopulation.Border;
import boa.data_structure.RegionPopulation.ContactBorderMask;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.BoundingBox;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.MutableBoundingBox;
import boa.image.SimpleBoundingBox;
import boa.image.TypeConverter;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.ImageOperations;
import boa.image.processing.clustering.RegionCluster;
import boa.image.processing.split_merge.SplitAndMergeEdge;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.image.processing.split_merge.SplitAndMergeRegionCriterion;
import boa.measurement.BasicMeasurements;
import boa.measurement.GeometricalMeasurements;
import static boa.plugins.Plugin.logger;
import boa.plugins.TestableProcessingPlugin;
import boa.plugins.TrackParametrizable;
import boa.plugins.plugins.pre_filters.ImageFeature;
import boa.plugins.plugins.pre_filters.Sigma;
import static boa.plugins.plugins.segmenters.EdgeDetector.valueFunction;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.utils.ArrayUtil;
import boa.utils.DoubleStatistics;
import boa.utils.Utils;
import ij.process.AutoThresholder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;


/**
 * Bacteria segmentation within microchannels, for phas images
 * @author jollion
 */
public class BacteriaIntensityPhase extends BacteriaIntensity {
    BooleanParameter filterBorderArtefacts = new BooleanParameter("Filter border Artefacts", true);
    BooleanParameter performLocalThreshold = new BooleanParameter("Perform Local Threshold", true);
    BooleanParameter upperCellCorrection = new BooleanParameter("Upper Cell Correction", false).setToolTipText("If true: when the upper cell is touching the top of the microchannel, a different local threshold factor is applied to the upper half of the cell");
    NumberParameter upperCellLocalThresholdFactor = new BoundedNumberParameter("Upper cell local threshold factor", 2, 2, 0, null).setToolTipText("Local Threshold factor applied to the upper part of the cell");
    NumberParameter maxYCoordinate = new BoundedNumberParameter("Max yMin coordinate of upper cell", 0, 5, 0, null);
    ConditionalParameter cond = new ConditionalParameter(upperCellCorrection).setActionParameters("true", upperCellLocalThresholdFactor, maxYCoordinate);
    ConditionalParameter ltCond = new ConditionalParameter(performLocalThreshold).setActionParameters("true", localThresholdFactor, smoothScale, upperCellCorrection);
    
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{watershedMap, thldCond, splitThreshold , minSize, hessianScale, filterBorderArtefacts,ltCond, sigmaThldForVoidMC};
    }
    public BacteriaIntensityPhase() {
        this.splitThreshold.setValue(0.10); // 0.15 for scale = 3
        this.minSize.setValue(100);
        this.hessianScale.setValue(2);
        this.watershedMap.removeAll().add(new Sigma(3).setMedianRadius(2));
        thresholdMethod.setSelectedIndex(1);
        localThresholdFactor.setToolTipText("Factor defining the local threshold. <br />Lower value of this factor will yield in smaller cells. <br />Threshold = mean_w - sigma_w * (this factor), <br />with mean_w = weigthed mean of raw pahse image weighted by edge image, sigma_w = sigma weighted by edge image. ");
        localThresholdFactor.setValue(1);
        globalThresholder.setPlugin(new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu));
    }
    @Override public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        if (isVoid) return null;
        RegionPopulation pop = super.runSegmenter(input, structureIdx, parent);
        return filterBorderArtefacts(parent, structureIdx, pop);
    }
    final private String toolTip = "<b>Bacteria segmentation within microchannels</b><br />"
            + "Same algorithm as BacteriaIntensity with several changes:<br />"
            + "This algorithm is designed to work on inverted (foreground is bright) and normalized phase-contrast images, filtered with the Track-pre-filter: \"SubtractBackgroundMicrochannels\"<br />"
            + "<ol><li>Void microchannels are detected prior to segmentation step using information on the whole microchannel track."
            + "Otsu's threshold is applied on each frame (named tf) and and otsu's algorithm is applied on the distribution of thresholds (further named T)."
            + "To assess if the distribution is biomodal (ie some microchannels contain cells, other don't): mean value of tf over (Mo) and under (Mo) T are computed. <br />If (Mo-Mu)/T > 0.4 the distribution is considered bimodal."
            + "If T<0.4 all microchannels are considered as void.</li>"
            + "<li>Split/Merge criterion is value of hessian at interface between to regions normalized by the mean value of the pre-filtered image within all segmented regions</li>"
            + "<li>Local threshold step is performed on the raw images with a different value described in the \"local threshold factor parameter\"</li>"
            + "<li>High-intensity background objects resulting from border-effects & phase contrast imaging are removed based on thickness criterion & contact with border of microchannel</li></ol>";
    
    @Override public String getToolTipText() {return toolTip;}
    
    @Override public SplitAndMergeHessian initializeSplitAndMerge(StructureObjectProcessing parent, int structureIdx, ImageMask foregroundMask) {
        SplitAndMergeHessian sam = super.initializeSplitAndMerge(parent, structureIdx, foregroundMask);
        Image input = parent.getPreFilteredImage(structureIdx);
        setInterfaceValue(input, sam);
        return sam;
    }
    private void setInterfaceValue(Image input, SplitAndMergeHessian sam) {
        sam.setInterfaceValue(i-> {
            Collection<Voxel> voxels = i.getVoxels();
            if (voxels.isEmpty()) return Double.NaN;
            else {
                Image hessian = sam.getHessian();
                double val  =  voxels.stream().mapToDouble(v->hessian.getPixel(v.x, v.y, v.z)).average().getAsDouble();
                // normalize using mean value (compare with max of mean or max of median
                double mean = Stream.concat(i.getE1().getVoxels().stream(), i.getE2().getVoxels().stream()).mapToDouble(v->(double)input.getPixel(v.x, v.y, v.z)).average().getAsDouble();
                val/=mean;
                return val;
            }
        });
    }
    
    
    @Override
    protected EdgeDetector initEdgeDetector(StructureObjectProcessing parent, int structureIdx) {
        EdgeDetector seg = super.initEdgeDetector(parent, structureIdx);
        seg.minSizePropagation.setValue(0);
        seg.seedRadius.setValue(1.5);
        seg.setThrehsoldingMethod(EdgeDetector.THLD_METHOD.NO_THRESHOLDING);
        return seg;
    }
    @Override 
    protected RegionPopulation filterRegionAfterSplitByHessian(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        return pop;
        //return filterBorderArtefacts(parent, structureIdx, pop);
    }
    @Override
    protected RegionPopulation filterRegionsAfterEdgeDetector(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        if (pop.getRegions().isEmpty()) return pop;
        Map<Region, Double> values = pop.getRegions().stream().collect(Collectors.toMap(o->o, valueFunction(parent.getPreFilteredImage(structureIdx))));
        Consumer<Image> imageDisp = TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent);
        if (Double.isNaN(threshold)) { // if need to compute thld -> compute thld
            Image valueMap = EdgeDetector.generateRegionValueMap(parent.getPreFilteredImage(structureIdx), values);
            threshold = this.localThresholder.instanciatePlugin().runSimpleThresholder(valueMap, parent.getMask());
            if (stores!=null) imageDisp.accept(valueMap.setName("value map"));
        } else if (stores!=null) imageDisp.accept(EdgeDetector.generateRegionValueMap(parent.getPreFilteredImage(structureIdx), values).setName("value map"));
        if (!Double.isNaN(minThld)) threshold = Math.max(minThld, threshold);
        if (Double.isNaN(globalThreshold)) globalThreshold = threshold;
        // define 3 categories: background / forground / unknown
        // foreground -> high intensity, foreground -> low intensity -> merge them
        Function<Region, Integer> artefactFunc = getFilterBorderArtefacts(parent, structureIdx);
        if (stores!=null) {
            Map<Region, Double> valuesArt = pop.getRegions().stream().collect(Collectors.toMap(r->r, r->artefactFunc.apply(r)+2d));
            imageDisp.accept(EdgeDetector.generateRegionValueMap(parent.getPreFilteredImage(structureIdx), valuesArt).setName("artefact map"));
            imageDisp.accept(pop.getLabelMap().duplicate("region before artefact filter"));
        }
        Set<Region> backgroundL = pop.getRegions().stream().filter(r->values.get(r)<minThld || artefactFunc.apply(r)==-1).collect(Collectors.toSet());
        Set<Region> foregroundL = pop.getRegions().stream().filter(r->values.get(r)>Math.max(globalThreshold, threshold) && !backgroundL.contains(r) && artefactFunc.apply(r)==1).collect(Collectors.toSet());
        if (foregroundL.isEmpty()) {
            pop.getRegions().clear();
            pop.relabel(true);
            return pop;
        }
        pop.getRegions().removeAll(backgroundL);
        boolean relabeled = false;
        if (pop.getRegions().size()>foregroundL.size()) { // merge indetermined regions if their intensity is higher than foreground neighbord
            pop.getRegions().removeAll(foregroundL);
            pop.getRegions().addAll(0, foregroundL);
            pop.relabel(true);
            if (stores!=null) imageDisp.accept(pop.getLabelMap().duplicate("before merge undetermined regions with foregorund"));
            SplitAndMergeRegionCriterion sm = new SplitAndMergeRegionCriterion(null, parent.getPreFilteredImage(structureIdx), -Double.MIN_VALUE, SplitAndMergeRegionCriterion.InterfaceValue.ABSOLUTE_DIFF_MEDIAN_BTWN_REGIONS);
            //SplitAndMergeRegionCriterion sm = new SplitAndMergeRegionCriterion(null, parent.getRawImage(structureIdx), -Double.MIN_VALUE, SplitAndMergeRegionCriterion.InterfaceValue.ABSOLUTE_DIFF_MEDIAN_BTWN_REGIONS_INV); // TODO TRY WITH RAW INTENSITIES
            sm.addForbidFusion(i->foregroundL.contains(i.getE1())==foregroundL.contains(i.getE2()));
            sm.merge(pop, -1);
            if (stores!=null) imageDisp.accept(pop.getLabelMap().duplicate("after merge undetermined regions with foregorund foreground"));
            relabeled= true;
        }
        if (pop.getRegions().size()>foregroundL.size()) { // there are still undetermined regions
            pop.getRegions().removeAll(foregroundL);
            Region background = Region.merge(backgroundL);
            if (background!=null) {
                Region foreground = Region.merge(foregroundL);
                pop.getRegions().add(0, background); // fixed index so that same instance is conserved when merged
                pop.getRegions().add(1, foreground); // fixed index so that same instance is conserved when merged
                pop.relabel(false);
                if (stores!=null) {
                    /*pop.getRegions().removeAll(backgroundL);
                    pop.getRegions().addAll(0, backgroundL);
                    pop.getRegions().removeAll(foregroundL);
                    pop.getRegions().addAll(backgroundL.size(), foregroundL);
                    pop.relabel(false);*/
                    imageDisp.accept(pop.getLabelMap().duplicate("after fore & back fusion"));
                }
                SplitAndMergeEdge sm = new SplitAndMergeEdge(edgeDetector.getWsMap(parent.getPreFilteredImage(structureIdx), parent.getMask()), parent.getPreFilteredImage(structureIdx), 1, false);
                sm.setInterfaceValue(0.1, false);
                //SplitAndMergeRegionCriterion sm = new SplitAndMergeRegionCriterion(null, parent.getPreFilteredImage(structureIdx), Double.POSITIVE_INFINITY, SplitAndMergeRegionCriterion.InterfaceValue.DIFF_MEDIAN_BTWN_REGIONS);
                sm.allowMergeWithBackground(parent.getMask()); // helps to remove artefacts on the side but can remove head of mother cell
                sm.addForbidFusionForegroundBackground(r->r==background, r->r==foreground);
                //sm.addForbidFusionForegroundBackground(r->backgroundL.contains(r), r->foregroundL.contains(r));
                sm.setTestMode(imageDisp);
                sm.merge(pop, 2); // merge intertermined until 2 categories in the image
                pop.getRegions().remove(background);
                //pop.getRegions().removeAll(backgroundL);
                pop.relabel(true);
                relabeled = true;
            }
        }
        if (!relabeled) pop.relabel(true);
        
        if (stores!=null) imageDisp.accept(pop.getLabelMap().duplicate("after fore & back & intertermined fusion"));
        return pop;
    }
    /**
     * See {@link #getFilterBorderArtefacts(boa.data_structure.StructureObjectProcessing, int) }
     * @param parent
     * @param structureIdx
     * @param pop
     * @return 
     */
    protected RegionPopulation filterBorderArtefacts(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        Function<Region, Integer> artifactFunc  = getFilterBorderArtefacts(parent, structureIdx);
        pop.filter(r->artifactFunc.apply(r)>=0 && (Double.isNaN(minThld) || splitAndMerge.getMedianValues().getAndCreateIfNecessary(r)>minThld));
        return pop;
    }
    /**
     * Border Artefacts criterion 
     * 1) a criterion on contact on sides and low X-thickness for side artefacts, 
     * 2) a criterion on contact with closed-end of microchannel and local-thickness
     * @param parent
     * @param structureIdx
     * @return function that return 1 if the object is not a border artefact, -1 if it is one, 0 if it is not known
     */
    protected Function<Region, Integer> getFilterBorderArtefacts(StructureObjectProcessing parent, int structureIdx) {
        if (!filterBorderArtefacts.getSelected()) return r->1;
        boolean verbose = stores!=null;
        // filter border artefacts: thin objects in X direction, contact on one side of the image 
        ContactBorderMask contactLeft = new ContactBorderMask(1, parent.getMask(), Border.Xl);
        ContactBorderMask contactRight = new ContactBorderMask(1, parent.getMask(), Border.Xr);
        ContactBorderMask contactUp = new ContactBorderMask(1, parent.getMask(), Border.YUp);
        double thicknessLimitKeep = parent.getMask().sizeX() * 0.5;  // OVER this thickness objects are kept
        double thicknessLimitRemove = Math.max(4, parent.getMask().sizeX() * 0.25); // UNDER THIS VALUE other might be artefacts
        Function<Region, Integer> f1 = r->{
            int cL = contactLeft.getContact(r);
            int cR = contactRight.getContact(r);
            if (cL==0 && cR ==0) return 1;
            int cUp = contactUp.getContact(r);
            int c = Math.max(cL, cR);
            if (cUp>c) return 1;
            double thick = GeometricalMeasurements.maxThicknessX(r);
            if (thick>thicknessLimitKeep) return 1;
            double thickY = GeometricalMeasurements.maxThicknessY(r);
            if (verbose) logger.debug("R: {} artefact: thick: {}/{} (mean: {}) contact: {}/{} ", r.getLabel(), thick, thicknessLimitRemove, GeometricalMeasurements.meanThicknessX(r), c, thickY);
            if (c < thickY*0.5) return 1; // contact with either L or right should be enough
            if (thick<=thicknessLimitRemove && c>thickY*0.9) return -1; // thin objects stuck to the border 
            return 0;
            //return false;
            //return BasicMeasurements.getQuantileValue(r, intensity, 0.5)[0]>globThld; // avoid removing foreground
        };
        
        ContactBorderMask contactUpLR = new ContactBorderMask(1, parent.getMask(), Border.XYup);
        // remove the artefact at the top of the channel
        Function<Region, Integer> f2 = r->{
            int cUp = contactUp.getContact(r); // consider only objects in contact with the top of the parent mask
            if (cUp<=2) return 1;
            cUp = contactUpLR.getContact(r);
            if (verbose) logger.debug("R: {} upper artefact: contact: {}/{}", r.getLabel(), cUp, r.getVoxels().size());
            if (cUp<r.getVoxels().size()/12) return 1;
            double thickness = GeometricalMeasurements.getThickness(r);
            if (verbose) logger.debug("R: {} upper artefact: thickness: {}/{}", r.getLabel(), thickness, thicknessLimitRemove);
            if (thickness<thicknessLimitRemove) return -1;
            if (thickness>=thicknessLimitKeep) return 1;
            return 0;
        };
        return r->{
            int r1 = f1.apply(r);
            if (r1==-1 || r1==0) return r1;
            return f2.apply(r);
        };
    }
    
    @Override
    protected RegionPopulation localThreshold(Image input, RegionPopulation pop, StructureObjectProcessing parent, int structureIdx, boolean callFromSplit) {
        if (pop.getRegions().isEmpty()) return pop;
        if (!performLocalThreshold.getSelected()) return pop;
        double dilRadius = callFromSplit ? 0 : 2;
        Image smooth = smoothScale.getValue().doubleValue()<1 ? parent.getRawImage(structureIdx) : ImageFeatures.gaussianSmooth(parent.getRawImage(structureIdx), smoothScale.getValue().doubleValue(), false);
        Image edgeMap = Sigma.filter(parent.getRawImage(structureIdx), parent.getMask(), 3, 1, smoothScale.getValue().doubleValue(), 1);
        Consumer<Image> imageDisp = TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent);
        if (imageDisp!=null) { //| (callFromSplit && splitVerbose)
            imageDisp.accept(smooth.setName("local threshold intensity map"));
            imageDisp.accept(edgeMap.setName("local threshold edge map"));
        }
        ImageMask mask = parent.getMask();
        // different local threshold for middle part of upper cell when touches borders
        boolean differentialLF = false;
        if (upperCellCorrection.getSelected()) {
            Region upperCell = pop.getRegions().stream().min((r1, r2)->Integer.compare(r1.getBounds().yMin(), r2.getBounds().yMin())).get();
            if (upperCell.getBounds().yMin()<=maxYCoordinate.getValue().intValue()) {
                differentialLF = true;
                double yLim = upperCell.getGeomCenter(false).get(1)+upperCell.getBounds().sizeY()/3.0;
                pop.localThresholdEdges(smooth, edgeMap, localThresholdFactor.getValue().doubleValue(), false, false, dilRadius, mask, v->v.y<yLim); // local threshold for lower cells & half lower part of cell
                if (stores!=null) { //|| (callFromSplit && splitVerbose)
                    logger.debug("y lim: {}", yLim);
                    ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("after lower localThreshold"));
                }
                pop.localThresholdEdges(smooth, edgeMap, upperCellLocalThresholdFactor.getValue().doubleValue(), false, false, dilRadius, mask, v->v.y>yLim); // local threshold for half upper part of 1st cell                
            } 
        } 
        if (!differentialLF) pop.localThresholdEdges(smooth, edgeMap, localThresholdFactor.getValue().doubleValue(), false, false, dilRadius, mask, null);
        if (imageDisp!=null) imageDisp.accept(pop.getLabelMap().duplicate("after localThreshold"));
        pop.smoothRegions(2, true, mask);
        return pop;
    }
    
    @Override public RegionPopulation splitObject(StructureObject parent, int structureIdx, Region object) {
        Image input = parent.getPreFilteredImage(structureIdx);
        if (input==null) throw new IllegalArgumentException("No prefiltered image set");
        ImageInteger mask = object.isAbsoluteLandMark() ? object.getMaskAsImageInteger().cropWithOffset(input.getBoundingBox()) :object.getMaskAsImageInteger().cropWithOffset(input.getBoundingBox().resetOffset()); // extend mask to get the same size as the image
        if (splitAndMerge==null || !parent.equals(currentParent)) {
            currentParent = parent;
            splitAndMerge = initializeSplitAndMerge(parent, structureIdx,parent.getMask());
        }
        splitAndMerge.setTestMode(TestableProcessingPlugin.getAddTestImageConsumer(stores, (StructureObject)parent));
        splitAndMerge.setInterfaceValue(i->-(double)i.getVoxels().size()); // split @ smallest interface ? 
        RegionPopulation res = splitAndMerge.splitAndMerge(mask, object.size()/4, 2);
        setInterfaceValue(input, splitAndMerge); // for interface value computation
        //res =  localThreshold(input, res, parent, structureIdx, true); 
        if (object.isAbsoluteLandMark()) res.translate(parent.getBounds(), true);
        if (res.getRegions().size()>2) RegionCluster.mergeUntil(res, 2, 0); // merge most connected until 2 objects remain
        return res;
    }
    
    @Override
    protected double[] getGlobalMinAndGlobalThld(List<StructureObject> parentTrack, int structureIdx, Set<StructureObject> voidMC) {
        if (voidMC.size()==parentTrack.size()) return new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        // 1) get global otsu thld for images with foreground
        double globalThld = getGlobalOtsuThreshold(parentTrack.stream().filter(p->!voidMC.contains(p)), structureIdx);
        // 4) estimate a minimal threshold : middle point between mean value under global threshold and global threshold
        double[] sum = new double[3];
        double thld = globalThld;
        for (StructureObject p : parentTrack) {
            Image im = p.getPreFilteredImage(structureIdx);
            ImageMask.loop(p.getMask(), (x, y, z)-> {
                double v = im.getPixel(x, y, z);
                if (v<thld) {
                    sum[0]+=v;
                    sum[1]++;
                }
            });
        }
        double mean = sum[0]/sum[1];
        double minThreshold = (mean+globalThld)/2.0;
        logger.debug("parent: {} global threshold on images with forground: [{};{}]", parentTrack.get(0), minThreshold, globalThld);

        return new double[]{minThreshold, globalThld}; 
    }
    protected static double  getGlobalOtsuThreshold(Stream<StructureObject> parent, int structureIdx) { // TODO : remplacer par background fit. attention si image normalisée -> bin pas 1, sinon bin = 1. Calcult auto des bin ? ou en deux temps ? 
        Map<Image, ImageMask> imageMapMask = parent.collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask() )); 
        Histogram histo = HistogramFactory.getHistogram(()->Image.stream(imageMapMask, true).parallel(), 256);
        return IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo);
        
    }
}
