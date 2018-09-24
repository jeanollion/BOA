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
package boa.image.processing;

import boa.core.Processor;
import boa.core.Task;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.ImageMask;
import boa.plugins.PluginFactory;
import boa.plugins.plugins.thresholders.BackgroundFit;
import static boa.test_utils.TestUtils.logger;
import boa.utils.ArrayUtil;
import ij.ImageJ;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
/**
 *
 * @author Jean Ollion
 */
public class TestPhaseThresholdingMethod {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        String dbName = "WT_150616";
        int pIdx= 0;
        MasterDAO db = new Task(dbName).getDB();
        ObjectDAO dao = db.getDao(db.getExperiment().getPosition(pIdx).getName());
        List<StructureObject> roots = Processor.getOrCreateRootTrack(dao);
        Map<StructureObject, List<StructureObject>> allMC = StructureObjectUtils.getAllTracks(roots, 0);
        allMC.values().stream().sorted((l1, l2)->Integer.compare(l1.get(0).getIdx(),l2.get(0).getIdx())).forEach(t -> {
            db.getExperiment().getStructure(1).getProcessingScheme().getTrackPreFilters(true).filter(1, t);
            Image im = ImageWindowManagerFactory.getImageManager().getImageTrackObjectInterface(t, 1).setDisplayPreFilteredImages(true).generatemage(1, false);
            ImageWindowManagerFactory.showImage(im);
            Set<Image> seen = new HashSet<>();
            for (StructureObject o : t) {
                if (seen.contains(o.getPreFilteredImage(1))) logger.debug("dup image: {} within {}", o.getPreFilteredImage(1), o, seen.stream().filter(i -> i.equals(o.getPreFilteredImage(1))).findAny().orElse(null));
                else seen.add(o.getPreFilteredImage(1));
            }
            logger.debug("set size: {}, track size: {}", seen.size(), t.size());
            Map<Image, ImageMask> map = t.stream().collect(Collectors.toMap(oo -> oo.getPreFilteredImage(1), o-> o.getMask()));
            Histogram histo = HistogramFactory.getHistogram(()->Image.stream(map, true), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
            double thld = getPhaseThld(histo, true, 2.5);
            histo.plotIJ1("mc:"+t.get(0).getIdx()+ " thld:"+thld, true);
            Image thlded = ImageOperations.threshold(im, thld, true, true);
            ImageWindowManagerFactory.showImage(im);
            ImageWindowManagerFactory.showImage(thlded);
        });
    }
    public static double getPhaseThld(Histogram histo, boolean inverted, double sigmaFactor) {
        BackgroundFit.fillZeros(histo.data);
        float[] smoothed = BackgroundFit.smooth(histo.data, 5);
        List<Integer> peaks = ArrayUtil.getRegionalExtrema(smoothed, 5, true);
        Comparator<Integer> peakComp = (i1, i2)->Float.compare(smoothed[i1], smoothed[i2]);
        int mainPeak = peaks.stream().max(peakComp).orElse(-1);
        if (mainPeak==-1) return Double.NaN;
        // filter low peaks
        double thld = smoothed[mainPeak] * 0.25;
        peaks.removeIf(i->smoothed[i]<thld);
        peaks.stream().sorted(peakComp).limit(2); //sort &  keep only 2 main peaks 
        double mode = inverted ? Collections.max(peaks) : Collections.min(peaks);
        // todo for more precise estimation -> fit modal value in a pre-defined range
        // get sigma of distribution using halft with of distribution
        double halfWidthIdx = BackgroundFit.getHalfWidthIdx(histo, mode, !inverted);
        double sigma = Math.abs(histo.getValueFromIdx(mode) - histo.getValueFromIdx(halfWidthIdx)) / Math.sqrt(2*Math.log(2));
        logger.debug("mode: {}, half: {}, sigma: {}", histo.getValueFromIdx(mode), histo.getValueFromIdx(halfWidthIdx), sigma);
        if (inverted) return histo.getValueFromIdx(mode) - sigma * sigmaFactor;
        else return histo.getValueFromIdx(mode) + sigma * sigmaFactor;
    }
}
