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
import boa.image.Image;
import boa.image.io.ImageReader;
import boa.plugins.plugins.thresholders.HistogramAnalyzer;
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
public class AnalyseMultimodalHistogram {
    public static void main(String[] args) {
        // open histo
        new ImageJ();
        /*
        String dbName = "MF1_180509";
        int postition=3, frame=122;
        MasterDAO mDAO = new Task(dbName).getDB();
        List<Image> images = new ArrayList<>();
        for (int f = 0; f<mDAO.getExperiment().getPosition(postition).getFrameNumber(true); ++f)  images.add(mDAO.getExperiment().getPosition(postition).getInputImages().getImage(0, f));
        Histogram histo = Histogram.getHisto256(images, null);
        ImageWindowManagerFactory.showImage(Image.mergeZPlanes(images));
        */
        
        Image im = ImageReader.openIJTif("/data/Images/MOP/SaturationHyperFluo3.tif");
        List<Image> images = im.splitZPlanes();
        
        
        int fInterval = 1000;
        for (int f = 0; f<images.size(); f+=fInterval) {
            Image subIm = Image.mergeZPlanes(images.subList(f, Math.min(images.size(), f+fInterval)));
            ImageWindowManagerFactory.showImage(subIm);
            Histogram histo = subIm.getHisto256(null);
            HistogramAnalyzer ha = new HistogramAnalyzer(histo, true).setVerbose(true);
            logger.debug("range: [{}] bck range: [{}-{}]({}), saturation: {}, thld: {}", histo.minAndMax, histo.getValueFromIdx(ha.getBackgroundRange().min), histo.getValueFromIdx(ha.getBackgroundRange().max), ha.getBackgroundRange().max, ha.getSaturationThreshold(5, 0.2), ha.getThresholdMultimodal());
            ha.plot();
        }
        
        
        
        
        
        
        
    }
}
