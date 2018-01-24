/*
 * Copyright (C) 2015 jollion
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
package boa.image.processing;

import boa.image.processing.ImageFeatures;
import boa.image.processing.ImageOperations;
import boa.image.processing.Filters;
import boa.test_utils.TestUtils;
import static boa.test_utils.TestUtils.logger;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.IJImageWrapper;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.io.ImageIOCoordinates;
import boa.image.ImageInt;
import boa.image.ImageInteger;
import boa.image.io.ImageReader;
import boa.image.ImageShort;
import org.junit.Test;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;

/**
 *
 * @author jollion
 */
public class TestFilters {
    public static void main(String[] args) {
        new TestFilters().testScale();
    }
    
    public void testScale() {
        String path = "/home/jollion/Documents/LJP/DataLJP/testsub60/imagesTest.ome.tiff";
        Image source = ImageReader.openImage(path, new ImageIOCoordinates(0, 0, 0, new BoundingBox(17, 78, 242, 613, 0, 0)));
        double[] ms = ImageOperations.getMeanAndSigma(source, null, null);
        double scale = 2.5;
        //Image smoothThenScale = ImageFeatures.gaussianSmooth(source, scale, scale, false).setName("gaussian then scale");
        Image lap = ImageFeatures.getLaplacian(source, scale, true, false).setName("laplacian");
        //ImageOperations.affineOperation2WithOffset(smoothThenScale, smoothThenScale, 1/ms[1], -ms[0]);
        Image lapThenScale = ImageOperations.affineOperation2WithOffset(lap, null, 1/ms[1], 0);
        
        ImageOperations.affineOperation2WithOffset(source, source, 1/ms[1], -ms[0]);
        //Image scaleTheSmooth = ImageFeatures.gaussianSmooth(source, scale, scale, false).setName("scale the gaussian");
        Image scaleThenLap = ImageFeatures.getLaplacian(source, scale, true, false).setName("scale then laplacian");
        
        //ImageWindowManagerFactory.showImage(smoothThenScale);
        ImageWindowManagerFactory.showImage(lap);
        ImageWindowManagerFactory.showImage(lapThenScale);
        //ImageWindowManagerFactory.showImage(scaleTheSmooth);
        ImageWindowManagerFactory.showImage(scaleThenLap);
    }
    
    @Test
    public void testMean() {
        //logger.info("byte value: 61.4 {} 61.5 {} 61.6 {} 255 {}, 255.5 {}, 256 {}", new Float(61.4).byteValue(), new Float(61.5).byteValue()&0xff, new Float(61.6).byteValue()&0xff, new Float(255).byteValue()&0xff, new Float(255.5).byteValue()&0xff, new Float(256).byteValue()&0xff);
        Neighborhood n = new EllipsoidalNeighborhood(7, 4, false);
        Image test = TestUtils.generateRandomImage(20, 20, 20, new ImageByte("", 0, 0, 0));
        long t1 = System.currentTimeMillis();
        Prefs.setThreads(1);
        Image resIJ = IJImageWrapper.wrap(new ImagePlus("", ij.plugin.Filters3D.filter(IJImageWrapper.getImagePlus(test).getImageStack(), ij.plugin.Filters3D.MEAN, 7, 7, 4)));
        long t2 = System.currentTimeMillis();
        Image res = Filters.mean(test, test, n);
        long t3 = System.currentTimeMillis();
        logger.info("processing time IJ: {} lib: {}", (t2-t1), t3-t2);
        TestUtils.assertImage(resIJ, res, 0);
        
        test = TestUtils.generateRandomImage(20, 20, 20, new ImageShort("", 0, 0, 0));
        resIJ = IJImageWrapper.wrap(new ImagePlus("", ij.plugin.Filters3D.filter(IJImageWrapper.getImagePlus(test).getImageStack(), ij.plugin.Filters3D.MEAN, 7, 7, 4)));
        res = Filters.mean(test, test, n);
        TestUtils.assertImage(resIJ, res, 0);
        
        test = TestUtils.generateRandomImage(20, 20, 20, new ImageFloat("", 0, 0, 0));
        resIJ = IJImageWrapper.wrap(new ImagePlus("", ij.plugin.Filters3D.filter(IJImageWrapper.getImagePlus(test).getImageStack(), ij.plugin.Filters3D.MEAN, 7, 7, 4)));
        res = Filters.mean(test, test, n);
        TestUtils.assertImage(resIJ, res, 0);
        
        
        // test 2D
        n = new EllipsoidalNeighborhood(1, false);
        test = TestUtils.generateRandomImage(100, 100, 1, new ImageByte("", 0, 0, 0));
        resIJ=test.duplicate("res IJ");
        t1 = System.currentTimeMillis();
        new ij.plugin.filter.RankFilters().rank(IJImageWrapper.getImagePlus(resIJ).getProcessor(), 0.5, ij.plugin.filter.RankFilters.MEAN);
        t2 = System.currentTimeMillis();
        res = Filters.mean(test, test, n);
        t3 = System.currentTimeMillis();
        logger.info("2D processing time IJ: {} lib: {}", (t2-t1), t3-t2);
        //Utils.assertImage(resIJ, res, 0); 
        // values and kernels are diferents in imageJ!!
    }
    
    @Test
    public void testMedian() {
        Neighborhood n = new EllipsoidalNeighborhood(6, 6, false);
        Image test = TestUtils.generateRandomImage(20, 20, 20, new ImageByte("", 0, 0, 0));
        long t1 = System.currentTimeMillis();
        Prefs.setThreads(1);
        Image resIJ = IJImageWrapper.wrap(new ImagePlus("", ij.plugin.Filters3D.filter(IJImageWrapper.getImagePlus(test).getImageStack(), ij.plugin.Filters3D.MEDIAN, 6,6,6)));
        long t2 = System.currentTimeMillis();
        Image res = Filters.median(test, test, n);
        long t3 = System.currentTimeMillis();
        logger.info("processing time IJ: {} lib: {}", (t2-t1), t3-t2);
        
        TestUtils.assertImage(resIJ, res, 0);
        
        test = TestUtils.generateRandomImage(20, 20, 20, new ImageShort("", 0, 0, 0));
        resIJ = IJImageWrapper.wrap(new ImagePlus("", ij.plugin.Filters3D.filter(IJImageWrapper.getImagePlus(test).getImageStack(), ij.plugin.Filters3D.MEDIAN, 6,6,6)));
        res = Filters.median(test, test, n);
        TestUtils.assertImage(resIJ, res, 0);
        
        test = TestUtils.generateRandomImage(20, 20, 20, new ImageFloat("", 0, 0, 0));
        resIJ = IJImageWrapper.wrap(new ImagePlus("", ij.plugin.Filters3D.filter(IJImageWrapper.getImagePlus(test).getImageStack(), ij.plugin.Filters3D.MEDIAN, 6,6,6)));
        res = Filters.median(test, test, n);
        TestUtils.assertImage(resIJ, res, 0);
    }
}
