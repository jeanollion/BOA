/*
 * Copyright (C) 2018 jollion
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

import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.ImageOperations;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.measurement.BasicMeasurements;
import boa.plugins.TrackParametrizable;
import boa.plugins.plugins.pre_filters.Sigma;
import boa.utils.DoubleStatistics;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;


/**
 * Bacteria segmentation within microchannels, for phas images
 * @author jollion
 */
public class BacteriaIntensityPhase extends BacteriaIntensity implements TrackParametrizable<BacteriaIntensityPhase> {
    public BacteriaIntensityPhase() {
        this.splitThreshold.setValue(3.7);
        this.minSize.setValue(50);
        this.hessianScale.setValue(1.5);
        localThresholdFactor.setToolTipText("Factor defining the local threshold. Lower value of this factor will yield in smaller cells. T = mean_w - sigma_w * (this factor), with mean_w = weigthed mean of raw pahse image weighted by edge image, sigma_w = sigma weighted by edge image. ");
        localThresholdFactor.setValue(1);
    }
    @Override public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        if (isVoid) return null;
        return super.runSegmenter(input, structureIdx, parent);
    }
    final private String toolTip = "<html>Bacteria segmentation within microchannels, for phase images normalized and inverted (foreground is bright)</ br>"
            + "Same algorithm as BacteriaIntensity with minor changes:<br />"
            + "Split/Merge criterion is value of hessian at interface between to regions normalized by the std value of hessian within all segmented regions<br />"
            + "local threshold step is performed on the raw images</html>";
    
    @Override public String getToolTipText() {return toolTip;}
    boolean localNormalization = false; // testing
    @Override public SplitAndMergeHessian initializeSplitAndMerge(Image input, ImageMask foregroundMask) {
        SplitAndMergeHessian sam = super.initializeSplitAndMerge(input, foregroundMask);
        double sd1 = localNormalization?0:1.5*ImageOperations.getMeanAndSigma(sam.getHessian(), foregroundMask)[1];
        double sd = localNormalization?0:ImageOperations.getMeanAndSigma(sam.getHessian(), foregroundMask, v->v<sd1 && v>-sd1)[1];
        //double sd= localNormalization?0:ImageOperations.getMeanAndSigma(sam.getHessian(), foregroundMask)[1];
        //if (testMode) logger.debug("Hessian normalization first round sd: {} second round sd: {}", sd1/1.5d, sd);
        sam.setInterfaceValue(i-> {
            Collection<Voxel> voxels = i.getVoxels();
            if (voxels.isEmpty()) return Double.NaN;
            else {
                Image hessian = sam.getHessian();
                double hessSum = 0;
                for (Voxel v : voxels) hessSum+=hessian.getPixel(v.x, v.y, v.z);
                double val = hessSum/voxels.size();
                if (localNormalization) {
                    if (true) {
                        DoubleStatistics stats1= DoubleStatistics.getStats(i.getE1().getVoxels().stream().mapToDouble(v->(double)hessian.getPixel(v.x, v.y, v.z))); 
                        double sdLoc1 = Math.sqrt(stats1.getSumOfSquare()/stats1.getCount()); // mean is supposed to be zero
                        DoubleStatistics stats2= DoubleStatistics.getStats(i.getE2().getVoxels().stream().mapToDouble(v->(double)hessian.getPixel(v.x, v.y, v.z))); 
                        double sdLoc2 = Math.sqrt(stats2.getSumOfSquare()/stats2.getCount()); // mean is supposed to be zero
                        //double sdLoc = stats.getStandardDeviation();
                        return val/Math.max(sdLoc1, sdLoc2);
                    } else {
                        DoubleStatistics stats = DoubleStatistics.getStats(Stream.concat(i.getE1().getVoxels().stream(), i.getE2().getVoxels().stream()).mapToDouble(v->(double)hessian.getPixel(v.x, v.y, v.z))); // join view
                        double sdLoc = Math.sqrt(stats.getSumOfSquare()/stats.getCount()); // mean is supposed to be zero
                        //double sdLoc = stats.getStandardDeviation();
                        return val/sdLoc;
                    }
                } else {
                    return val/sd; // normalize by global hessian STD 
                }
            }
        });
        return sam;
    }
    
    boolean isVoid = false;
    @Override
    public ApplyToSegmenter<BacteriaIntensityPhase> run(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages) {
        Set<StructureObject> voidMC = new HashSet<>();
        double minThld = TrackParametrizable.getVoidMicrochannels(structureIdx, preFilteredImages, 0.4, voidMC);
        return (p, s) -> {
            if (voidMC.contains(p)) s.isVoid=true; 
            s.minThld=minThld;
        };
    }
    
    @Override
    protected RegionPopulation localThreshold(Image input, RegionPopulation pop, StructureObjectProcessing parent, int structureIdx) {
        double dilRadius = 2;
        Image smooth = ImageFeatures.gaussianSmooth(parent.getRawImage(structureIdx), smoothScale.getValue().doubleValue(), false);
        Image edgeMap = Sigma.filter(parent.getRawImage(structureIdx), 3, 3, 3, 3);
        pop.localThresholdEdges(smooth, edgeMap, localThresholdFactor.getValue().doubleValue(), false, false, dilRadius, parent.getMask());
        pop.smoothRegions(2, true, parent.getMask());
        return pop;
    }
}
