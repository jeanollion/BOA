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
package boa.processing.test;

import boa.core.Task;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.processing.ImageFeatures;
import boa.image.wrappers.ImgLib2ImageWrapper;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import static boa.test_utils.TestUtils.logger;
import boa.utils.Utils;
import ij.process.AutoThresholder;
import java.util.stream.Collectors;
import net.imglib2.img.Img;

/**
 *
 * @author Jean Ollion
 */
public class TestIJAutoThreshold {
    public static void main(String[] args) {
        String dbName = "WT_180504";
        MasterDAO db = new Task(dbName).getDB();
        ObjectDAO dao = MasterDAO.getDao(db, 0);
        Image image = dao.getRoot(0).getRawImage(0);
        Histogram histo256  = HistogramFactory.getHistogram(()->image.stream(), HistogramFactory.BIN_SIZE_METHOD.NBINS_256);
        Histogram histoAuto  = HistogramFactory.getHistogram(()->image.stream(), HistogramFactory.BIN_SIZE_METHOD.AUTO);
        long t0 = System.currentTimeMillis();
        double thld256 = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo256);
        long t1 = System.currentTimeMillis();
        double thld256IJ2 = IJAutoThresholder.runThresholderIJ2(AutoThresholder.Method.Otsu, histo256);
        long t2 = System.currentTimeMillis();
        double thldAutoIJ2 = IJAutoThresholder.runThresholderIJ2(AutoThresholder.Method.Otsu, histoAuto);
        long t3 = System.currentTimeMillis();
        logger.debug("IJ1: {} ({}ms), IJ2: {} ({}ms), IJ2 Auto: {} ({}ms)", thld256, t1-t0, thld256IJ2, t2-t1, thldAutoIJ2, t3-t2);
        
        /*logger.debug("Mem before load image: {}", Utils.getMemoryUsage());
        Image im = Image.mergeZPlanes(dao.getRoots().stream().map(r->r.getRawImage(0)).collect(Collectors.toList()));
        logger.debug("Mem after load image: {}", Utils.getMemoryUsage());
        long t0 = System.currentTimeMillis();
        Img wrap = ImgLib2ImageWrapper.getImage(im);
        long t1 = System.currentTimeMillis();
        logger.debug("Mem after wrap: {}", Utils.getMemoryUsage());
        logger.debug("time to wrap: {}", t1-t0);*/
    }
}
