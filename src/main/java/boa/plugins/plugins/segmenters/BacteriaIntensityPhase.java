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

import boa.data_structure.RegionPopulation;
import boa.data_structure.RegionPopulation.Border;
import boa.data_structure.RegionPopulation.ContactBorderMask;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.TypeConverter;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.measurement.BasicMeasurements;
import boa.measurement.GeometricalMeasurements;
import boa.plugins.TrackParametrizable;
import boa.plugins.plugins.pre_filters.Sigma;
import boa.utils.DoubleStatistics;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;


/**
 * Bacteria segmentation within microchannels, for phas images
 * @author jollion
 */
public class BacteriaIntensityPhase extends BacteriaIntensity implements TrackParametrizable<BacteriaIntensityPhase> {
    public BacteriaIntensityPhase() {
        this.splitThreshold.setValue(0.1);
        this.minSize.setValue(100);
        this.hessianScale.setValue(3);
        localThresholdFactor.setToolTipText("Factor defining the local threshold. Lower value of this factor will yield in smaller cells. T = mean_w - sigma_w * (this factor), with mean_w = weigthed mean of raw pahse image weighted by edge image, sigma_w = sigma weighted by edge image. ");
        localThresholdFactor.setValue(1);
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
    
    @Override public SplitAndMergeHessian initializeSplitAndMerge(Image input, ImageMask foregroundMask) {
        SplitAndMergeHessian sam = super.initializeSplitAndMerge(input, foregroundMask);
        //double sd1 = localNormalization?0:1.5*ImageOperations.getMeanAndSigma(sam.getHessian(), foregroundMask)[1];
        //double sd = localNormalization?0:ImageOperations.getMeanAndSigma(sam.getHessian(), foregroundMask, v->v<sd1 && v>-sd1)[1];
        sam.setInterfaceValue(i-> {
            Collection<Voxel> voxels = i.getVoxels();
            if (voxels.isEmpty()) return Double.NaN;
            else {
                Image hessian = sam.getHessian();
                double hessSum = 0;
                for (Voxel v : voxels) hessSum+=hessian.getPixel(v.x, v.y, v.z);
                double val = hessSum/voxels.size();
                // normalize using mean value (compare with max of mean or max of median
                double mean = Stream.concat(i.getE1().getVoxels().stream(), i.getE2().getVoxels().stream()).mapToDouble(v->(double)input.getPixel(v.x, v.y, v.z)).average().getAsDouble();
                val/=mean;
                return val;
            }
        });
        return sam;
    }
    
    
    @Override
    protected EdgeDetector initEdgeDetector(StructureObjectProcessing parent, int structureIdx) {
        EdgeDetector seg = super.initEdgeDetector(parent, structureIdx);
        seg.seedRadius.setValue(1.5);
        return seg;
    }
    @Override 
    protected RegionPopulation filterRegionAfterSplitByHessian(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        return filterBorderArtefacts(parent, structureIdx, pop);
    }
    @Override
    protected RegionPopulation filterRegionsAfterEdgeDetector(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        return filterBorderArtefacts(parent, structureIdx, pop);
    }
    /**
     * Filter Border Artefacts using: 1) a criterion on contact on sides and low X-thickness for side artefacts, a criterion on contact with closed-end of microchannel and local-thickness
     * @param parent
     * @param structureIdx
     * @param pop
     * @return 
     */
    protected RegionPopulation filterBorderArtefacts(StructureObjectProcessing parent, int structureIdx, RegionPopulation pop) {
        boolean verbose = testMode;
        double globThld = this.globalThreshold;
        Image intensity = parent.getPreFilteredImage(structureIdx);
        if (intensity==null) throw new IllegalArgumentException("no prefiltered image");
        // filter border artefacts: thin objects in X direction, contact on one side of the image 
        ContactBorderMask contactLeft = new ContactBorderMask(1, parent.getMask(), Border.Xl);
        ContactBorderMask contactRight = new ContactBorderMask(1, parent.getMask(), Border.Xr);
        double thicknessLimit = parent.getMask().getSizeX() * 0.4; // 0.33?
        double thicknessLimit2 = parent.getMask().getSizeX() * 0.25; 
        double thickYLimit = 15;
        pop.filter(r->{
            int cL = contactLeft.getContact(r);
            int cR = contactRight.getContact(r);
            if (cL==0 && cR ==0) return true;
            int c = Math.max(cL, cR);
            double thickX = GeometricalMeasurements.meanThicknessX(r);
            if (thickX>thicknessLimit) return true;
            double thickY = GeometricalMeasurements.meanThicknessY(r);
            if (verbose) logger.debug("filter after seg: thickX: {} contact: {}/{}", thickX, c, thickY*0.75);
            if (c < thickY*0.75) return true;
            if (thickY>thickYLimit && thickX<thicknessLimit2) return false; // long and thin objects are always border artifacts
            return BasicMeasurements.getQuantileValue(r, intensity, 0.5)[0]>globThld; // avoid removing foreground
        });
        ContactBorderMask contactUp = new ContactBorderMask(1, parent.getMask(), Border.YUp);
        ContactBorderMask contactUpLR = new ContactBorderMask(1, parent.getMask(), Border.XYup);
        // remove the artefact at the top of the channel
        pop.filter(r->{
            int cUp = contactUp.getContact(r); // consider only objects in contact with the top of the parent mask
            if (cUp<=2) return true;
            cUp = contactUpLR.getContact(r);
            if (cUp<r.getVoxels().size()/5) return true;
            double thickness = GeometricalMeasurements.localThickness(r);
            if (verbose) logger.debug("upper artefact: contact: {}/{} thickness: {}", cUp, r.getVoxels().size(), thickness);
            return thickness>thicknessLimit;
        });
        
        return pop;
    }
    
    @Override
    protected RegionPopulation localThreshold(Image input, RegionPopulation pop, StructureObjectProcessing parent, int structureIdx, boolean callFromSplit) {
        double dilRadius = callFromSplit ? 0 : 2;
        Image smooth = ImageFeatures.gaussianSmooth(parent.getRawImage(structureIdx), smoothScale.getValue().doubleValue(), false);
        Image edgeMap = Sigma.filter(smooth, 3, 1, 3, 1);
        if (splitVerbose) ImageWindowManagerFactory.showImage(edgeMap.setName("local threshold edge map"));
        ImageMask mask = parent.getMask();
        pop.localThresholdEdges(smooth, edgeMap, localThresholdFactor.getValue().doubleValue(), false, false, dilRadius, mask);
        if (splitVerbose) ImageWindowManagerFactory.showImage(pop.getLabelMap().duplicate("after localThreshold"));
        pop.smoothRegions(2, true, mask);
        return pop;
    }
    
    
    // apply to segmenter from whole track information (will be set prior to all other methods=
    
    boolean isVoid = false;
    double globalThreshold = Double.NaN;
    @Override
    public ApplyToSegmenter<BacteriaIntensityPhase> run(int structureIdx, List<StructureObject> parentTrack) {
        Set<StructureObject> voidMC = new HashSet<>();
        double[] minAndGlobalThld = TrackParametrizable.getVoidMicrochannels(structureIdx, parentTrack, 0.4, voidMC);
        return (p, s) -> {
            if (voidMC.contains(p)) s.isVoid=true; 
            s.minThld=minAndGlobalThld[0];
            s.globalThreshold = minAndGlobalThld[1];
        };
    }
}
