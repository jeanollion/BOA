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
package boa.plugins;

import boa.data_structure.StructureObject;
import boa.image.BlankMask;
import boa.image.Histogram;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.TypeConverter;
import boa.image.processing.ImageOperations;
import static boa.plugins.Plugin.logger;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import ij.process.AutoThresholder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 * @param <S> segmenter type
 */
public interface TrackParametrizable<S extends Segmenter> {
    @FunctionalInterface public static interface ApplyToSegmenter<S> { 
        /**
         * Parametrizes the {@param segmenter}
         * This method may be called asynchronously with different pairs of {@param parent}/{@param segmenter}
         * @param parent parent object from the parent track used to create the {@link boa.plugins.TrackParametrizable.ApplyToSegmenter apply to segmenter object} See: {@link #getApplyToSegmenter(int, java.util.List, boa.plugins.Segmenter, java.util.concurrent.ExecutorService) }. This is not necessary the segmentation parent that will be used as argument in {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
         * @param segmenter Segmenter instance that will be parametrized, prior to call the method {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
         */
        public void apply(StructureObject parent, S segmenter);
    }
    /**
     * 
     * @param structureIdx index of the structure to be segmented via call to {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
     * @param parentTrack parent track (elements are parent of structure {@param structureIdx}
     * @return ApplyToSegmenter object that will parametrize Segmenter instances before call to {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
     */
    public ApplyToSegmenter run(int structureIdx, List<StructureObject> parentTrack);
    
    // + static helpers methods
    public static <S extends Segmenter> ApplyToSegmenter<S> getApplyToSegmenter(int structureIdx, List<StructureObject> parentTrack, S segmenter, ExecutorService executor) {
        if (segmenter instanceof TrackParametrizable) {
            TrackParametrizable tp = (TrackParametrizable)segmenter;
            if (executor!=null && tp instanceof MultiThreaded) ((MultiThreaded)tp).setExecutor(executor);
            return tp.run(structureIdx, parentTrack);
        }
        return null;
    }
    
    
    public static double getGlobalThreshold(int structureIdx, List<StructureObject> parentTrack, SimpleThresholder thlder) {
        Map<Image, ImageMask> maskMap = parentTrack.stream().collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask()));
        if (thlder instanceof ThresholderHisto) {
            Histogram hist = Histogram.getHisto256(maskMap, null);
            return ((ThresholderHisto)thlder).runThresholderHisto(hist);
        } else {
            Supplier<Pair<List<Image>, List<ImageInteger>>> supplier = ()->new Pair<>(new ArrayList<>(), new ArrayList<>());
            BiConsumer<Pair<List<Image>, List<ImageInteger>>, Map.Entry<Image, ImageMask>> accumulator =  (p, e)->{
                p.key.add(e.getKey());
                if (!(e.getValue() instanceof BlankMask)) p.value.add((ImageInteger)TypeConverter.toCommonImageType(e.getValue()));
            };
            BiConsumer<Pair<List<Image>, List<ImageInteger>>, Pair<List<Image>, List<ImageInteger>>> combiner = (p1, p2) -> {p1.key.addAll(p2.key);p1.value.addAll(p2.value);};
            Pair<List<Image>, List<ImageInteger>> globalImagesList = maskMap.entrySet().stream().collect( supplier,  accumulator,  combiner);
            Image globalImage = (Image)Image.mergeImagesInZ(globalImagesList.key);
            ImageMask globalMask = globalImagesList.value.isEmpty() ? new BlankMask(globalImage) : (ImageInteger)Image.mergeImagesInZ(globalImagesList.value);
            return thlder.runSimpleThresholder(globalImage, globalMask);
        }
    }
    /**
     * Detected whether all microchannels are void, only part of it or none.
     * @param structureIdx
     * @param parentTrack
     * @param thldForVoidMC 
     * @param outputVoidMicrochannels will add the void microchannels inside this set
     * @return [the minimal threshold if some channels are void or positive infinity if all channels are void ; the global threshold in non-empty microchannels]
     */
    public static double[] getVoidMicrochannels(int structureIdx, List<StructureObject> parentTrack, double thldForVoidMC, Set<StructureObject> outputVoidMicrochannels) {
        double bimodalThld = 0.4d;
        float[] thlds = new float[parentTrack.size()];
        int idx = 0;
        SimpleThresholder thlder = new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu);
        for ( StructureObject p : parentTrack) thlds[idx++] = (float)thlder.runSimpleThresholder(p.getPreFilteredImage(structureIdx), p.getMask());
        
        
        ImageFloat thldsIm = new ImageFloat("", thlds.length, thlds);
        double thld = thlder.runSimpleThresholder(thldsIm, null);
        double[] ms = ImageOperations.getMeanAndSigma(thldsIm, null);
        double[] msUnder = ImageOperations.getMeanAndSigma(thldsIm, null, v->v<thld);
        double[] msOver = ImageOperations.getMeanAndSigma(thldsIm, null, v->v>thld);
        logger.debug("test void microchannel otsu: thld: {} under: {} over: {}, all: {}, test: {}< {}", thld, msUnder, msOver, ms, msUnder[0]+msUnder[1], msOver[0]);
        
        // detect if cells get out or in with difference with previous channel
        /*float[] thldsDiff = new float[preFilteredImages.size()-1];
        for (int i =0; i<thldsDiff.length;++i) thldsDiff[i] = Math.abs(thlds[i+1] - thlds[i]);
        ImageFloat thldsDiffIm = new ImageFloat("", thldsDiff.length, thldsDiff);
        double[] msDiff = ImageOperations.getMeanAndSigma(thldsDiffIm, null);
        double maxDiff = thldsDiff[ArrayUtil.max(thldsDiff)];
        logger.debug("max diff: {} ms diff : {}", maxDiff, msDiff);
        */
        
        // test bimodal: normed difference of mean of 2 classes
        double diff = (msOver[0]-msUnder[0]) / thld;
        logger.debug("test void microchannel otsu: bimodal: diff: {}  bimodal: {}, minValue: {}", diff, diff>bimodalThld, thld);
        if (diff>bimodalThld) { // bimodal
            idx = 0;
            for (StructureObject s : parentTrack) if (thlds[idx++]<thld) outputVoidMicrochannels.add(s);
        }
        if (thld<thldForVoidMC) outputVoidMicrochannels.addAll(parentTrack);
        if (outputVoidMicrochannels.size()==parentTrack.size()) return new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        //return Double.NaN;
        // get global otsu thld for images with foreground
        Map<Image, ImageMask> imageMapMask = parentTrack.stream().filter(p->!outputVoidMicrochannels.contains(p)).collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask()));
        Histogram histo = Histogram.getHisto256(imageMapMask, null);
        double globalThld = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo);
        logger.debug("global otsu thld: {}", globalThld);
        return new double[]{outputVoidMicrochannels.isEmpty() ? Double.NaN : thld, globalThld};
    }
    
}
