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
package boa.image.processing;

import boa.core.Task;
import boa.data_structure.dao.MasterDAO;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.io.ImageReader;
import boa.plugins.plugins.thresholders.BackgroundFit;
import boa.plugins.plugins.thresholders.HistogramAnalyzer;
import boa.plugins.plugins.transformations.SaturateHistogramHyperfluoBacteria;
import static boa.test_utils.TestUtils.logger;
import boa.utils.ArrayUtil;
import boa.utils.Utils;
import ij.ImageJ;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 *
 * @author Jean Ollion
 */
public class TestSaturateHistogram {
    public static void main(String[] args) {
        // open histo
        new ImageJ();
        
        String dbName = "MF1_180509";
        //String dbName = "fluo160501_uncorr_TestParam";
        //String dbName= "WT_180318_Fluo";
        int channelIdx = 0;
        int postition=0;
        MasterDAO mDAO = new Task(dbName).getDB();
        List<Image> images = new ArrayList<>();
        int maxF = mDAO.getExperiment().getPosition(postition).getFrameNumber(true);
        //int maxF = 10;
        for (int f = 0; f<maxF; ++f)  images.add(mDAO.getExperiment().getPosition(postition).getInputImages().getImage(channelIdx, f));
        Image im = Image.mergeZPlanes(images);
        ImageWindowManagerFactory.showImage(im);
        
        //HistogramAnalyzer ha = new HistogramAnalyzer(histo,5, true).setVerbose(true);
        //logger.debug("range: [{}] bck range: [{}-{}]({}), saturation: {}, thld: {}", histo.minAndMax, histo.getValueFromIdx(ha.getBackgroundRange().min), histo.getValueFromIdx(ha.getBackgroundRange().max), ha.getBackgroundRange().max, ha.getSaturationThreshold(5, 0.2), ha.getThresholdMultimodal());
        //ha.plot();
        
        //SaturateHistogramHyperfluoBacteria sat = new SaturateHistogramHyperfluoBacteria();
        //sat.computeConfigurationData(channelIdx, mDAO.getExperiment().getPosition(postition).getInputImages());
        long t0 = System.currentTimeMillis();
            //Histogram histo = HistogramFactory.getHistogram(images, 1, null, true);
            Histogram histo = HistogramFactory.getHistogram(()->Image.stream(images).parallel(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
            histo.plotIJ1("histo", true);
        long t1 = System.currentTimeMillis();
        logger.debug("get histo: {}ms", t1-t0);
    }
}
