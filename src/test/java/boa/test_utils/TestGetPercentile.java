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
package boa.test_utils;

import static boa.test_utils.TestUtils.logger;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.processing.ImageOperations;
import boa.image.io.ImageReader;
import boa.image.TypeConverter;
import java.util.Arrays;
import org.apache.commons.lang.ArrayUtils;
import static boa.utils.Utils.toStringArray;

/**
 *
 * @author Jean Ollion
 */
public class TestGetPercentile {
    public static void main(String[] args) {
        ImageFloat im = TypeConverter.toFloat(ImageReader.openIJTif("/data/Images/MOP/image.tif"), null);
        Double[] per  = new Double[]{0.0001, 0.1, 0.5, 0.9, 0.9999};
        float[] pix = im.getPixelArray()[0];
        Arrays.sort(pix);
        logger.debug("sort: {}", toStringArray(per, d->d+":"+getPer(d, pix)));
        logger.debug("histo: {}", ImageOperations.getQuantiles(im, null, null, ArrayUtils.toPrimitive(per)));
    }
    public static double getPer(double per , float[] array) {
        double idx = per * array.length;
        double plus = idx - (int)idx;
        return array[(int)idx] * (1-plus) + array[(int)idx+1] * plus;
    }
}
