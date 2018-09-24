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
package boa.processing.test;

import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.io.ImageReader;
import boa.plugins.plugins.thresholders.BackgroundFit;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import static boa.test_utils.TestUtils.logger;
import java.util.List;

/**
 *
 * @author Jean Ollion
 */
public class BackgroundFitTest {
    public static void main(String[] args) {
        Image im = ImageReader.openIJTif("/data/Images/MOP/BackgroundFitTest2.tif");
        List<Image> ims = im.splitZPlanes();
        int idx = 0;
        /*for (Image i : ims ) {
            for (double bin = 1; bin<10; ++bin) {
                double[] ms = new double[2];
                Histogram histo = HistogramFactory.getHistogram(() -> i.stream(), bin);
                BackgroundFit.backgroundFit(histo, 1, ms);
                logger.debug("image: {}, binSize: {} (#{}), mean: {}, sigma: {}", idx, histo.binSize, histo.data.length, ms[0], ms[1]);
            }
            //++idx;
            return;
        }*/
        double[] ms = new double[2];
        Histogram histo = HistogramFactory.getHistogram(() -> im.stream(), HistogramFactory.BIN_SIZE_METHOD.BACKGROUND);
        histo.plotIJ1("whole image: bin: "+histo.binSize, true);
        double thld = BackgroundFit.backgroundFit(histo, 1, ms);
        double msBT[] = new double[2];
        BackgroundThresholder.debug=true;
        double thldBT = BackgroundThresholder.runThresholder(im, null, 3, 6, 3, Double.POSITIVE_INFINITY, msBT);
        logger.debug("image: {}, binSize: {} (#{}), mean: {}, sigma: {}, thld: {}, BT: mean: {}, sigma: {}, thld: {}", "all", histo.binSize, histo.data.length, ms[0], ms[1], thld, msBT[0], msBT[1], thldBT);
        histo = HistogramFactory.getHistogram(() -> ims.get(0).stream(), HistogramFactory.BIN_SIZE_METHOD.BACKGROUND);
        histo.plotIJ1("single image: bin: "+histo.binSize, true);
        thld = BackgroundFit.backgroundFit(histo, 1, ms);
        thldBT = BackgroundThresholder.runThresholder(ims.get(0), null, 3, 6, 3, Double.MAX_VALUE, msBT);
        logger.debug("image: {}, binSize: {} (#{}), mean: {}, sigma: {}, thld: {}, BT: mean: {}, sigma: {}, thld: {}", idx, histo.binSize, histo.data.length, ms[0], ms[1], thld, msBT[0], msBT[1], thldBT);
    }
}
