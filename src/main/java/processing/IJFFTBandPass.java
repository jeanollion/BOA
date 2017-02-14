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
package processing;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import image.IJImageWrapper;
import image.Image;
import image.ImageFloat;

/**
 *
 * @author jollion
 */
public class IJFFTBandPass {
    public static Image bandPass(Image input, double min, double max, int stripes, double stripeTolerance) {
        ImagePlus ip = IJImageWrapper.getImagePlus(input);
        FftBandPassFilter fftBandPassFilter = new FftBandPassFilter(ip, min, max, stripes, stripeTolerance);
        
        ImageProcessor imp=fftBandPassFilter.run(ip.getProcessor());
        return IJImageWrapper.wrap(new ImagePlus("FFT of "+input.getName(), imp));
    }
    public static Image suppressHorizontalStripes(Image input) {
        ImagePlus ip = IJImageWrapper.getImagePlus(input);
        FftBandPassFilter fftBandPassFilter = new FftBandPassFilter(ip, 0, 200, 1, 0);
        
        ImageProcessor imp=fftBandPassFilter.run(ip.getProcessor());
        return IJImageWrapper.wrap(new ImagePlus("FFT of "+input.getName(), imp));
    }
}
