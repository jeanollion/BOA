/*
 * Copyright (C) 2017 jollion
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
package TestUtils;

import static TestUtils.TestUtils.logger;
import image.Image;
import image.ImageFloat;
import image.ImageOperations;
import image.ImageReader;
import image.TypeConverter;
import java.util.Arrays;
import org.apache.commons.lang.ArrayUtils;
import static utils.Utils.toStringArray;

/**
 *
 * @author jollion
 */
public class TestGetPercentile {
    public static void main(String[] args) {
        ImageFloat im = TypeConverter.toFloat(ImageReader.openIJTif("/data/Images/MOP/image.tif"), null);
        Double[] per  = new Double[]{0.0001, 0.1, 0.5, 0.9, 0.9999};
        float[] pix = im.getPixelArray()[0];
        Arrays.sort(pix);
        logger.debug("sort: {}", toStringArray(per, d->d+":"+getPer(d, pix)));
        logger.debug("histo: {}", ImageOperations.getPercentile(im, null, null, ArrayUtils.toPrimitive(per)));
    }
    public static double getPer(double per , float[] array) {
        double idx = per * array.length;
        double plus = idx - (int)idx;
        return array[(int)idx] * (1-plus) + array[(int)idx+1] * plus;
    }
}
