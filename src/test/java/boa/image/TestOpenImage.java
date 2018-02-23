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
package boa.image;

import static boa.test_utils.TestUtils.logger;
import boa.image.Image;
import boa.image.io.ImageIOCoordinates;
import boa.image.io.ImageReader;
import org.junit.Test;

/**
 *
 * @author jollion
 */
public class TestOpenImage {
    public static void main(String[] args) {
        new TestOpenImage().testDim();
    }
    //@Test
    public void testDim() {
        long t0 = System.currentTimeMillis();
        ImageReader r = new ImageReader("/data/Images/Phase/121202_6300_mutd5_lb/mg6300mutd5_LB_lr62replic2_oil37.nd2");
        long t1 = System.currentTimeMillis();
        logger.info("reader creation time: {}", t1-t0);
        int[][] stc = r.getSTCXYZNumbers();
        long t2 = System.currentTimeMillis();
        logger.info("number of series: {} time points, channel {}. time {}ms", stc.length, stc,t2-t1);
        double[] scale =  r.getScaleXYZ(1);
        long t3 = System.currentTimeMillis();
        logger.info("scaleXYZ: {}, time: {}ms",scale, t3-t2);
        
        Image im = r.openImage(new ImageIOCoordinates(0, 0, 2));
        long t4 = System.currentTimeMillis();
        logger.info("X: "+im.sizeX()+ " Y: "+im.sizeY()+ " Z: time: {}ms"+im.sizeZ(), t4-t3);
        //>5min .. 60 series 985 timePoints 1 channel
    }
}
