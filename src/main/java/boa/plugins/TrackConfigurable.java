/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins;

import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.gui.image_interaction.KymographX;
import boa.image.BlankMask;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.HistogramFactory.BIN_SIZE_METHOD;
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
import boa.utils.Utils;
import ij.process.AutoThresholder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 *
 * @author Jean Ollion
 * @param <P> segmenter type
 */
public interface TrackConfigurable<P extends Plugin> {
    /**
     * Interface Allowing to configure a plugin using information from whole parent track
     * @param <P> type of plugin to be configured
     */
    @FunctionalInterface public static interface TrackConfigurer<P> { 
        /**
         * Parametrizes the {@param segmenter}
         * This method may be called asynchronously with different pairs of {@param parent}/{@param segmenter}
         * @param parent parent object from the parent track used to create the {@link boa.plugins.TrackParametrizable.TrackConfigurer apply to segmenter object} See: {@link #getTrackConfigurer(int, java.util.List, boa.plugins.Segmenter, java.util.concurrent.ExecutorService) }. This is not necessary the segmentation parent that will be used as argument in {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
         * @param plugin Segmenter instance that will be configured, prior to call the method {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
         */
        public void apply(StructureObject parent, P plugin);
    }
    /**
     * 
     * @param structureIdx index of the structure to be segmented via call to {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
     * @param parentTrack parent track (elements are parent of structure {@param structureIdx}
     * @return ApplyToSegmenter object that will configure Segmenter instances before call to {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
     */
    public TrackConfigurer run(int structureIdx, List<StructureObject> parentTrack);
    
    // + static helpers methods
    public static <P extends Plugin> TrackConfigurer<P> getTrackConfigurer(int structureIdx, List<StructureObject> parentTrack, P plugin) {
        if (plugin instanceof TrackConfigurable) {
            TrackConfigurable tp = (TrackConfigurable)plugin;
            List<StructureObject> pT = StructureObjectUtils.getTrack(parentTrack.get(0).getTrackHead());
            pT.removeIf(p->p.getPreFilteredImage(structureIdx)==null);
            if (pT.isEmpty()) throw new RuntimeException("NO prefiltered images set");
            return tp.run(structureIdx, pT);
        }
        return null;
    }
    
    
    public static double getGlobalThreshold(int structureIdx, List<StructureObject> parentTrack, SimpleThresholder thlder) {
        Map<Image, ImageMask> maskMap = parentTrack.stream().collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask()));
        if (thlder instanceof ThresholderHisto) {
            Histogram hist = HistogramFactory.getHistogram(()->Image.stream(maskMap, true).parallel(), BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
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
    
    
}
