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
package image;

import ij.ImagePlus;
import static image.IJImageWrapper.getImagePlus;
import java.util.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.histogram.Integer1dBinMapper;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.ops.operation.iterableinterval.unary.MakeHistogram;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;

/**
 *
 * @author jollion
 */
public class ImgLib2ImageWrapper {
    
    public static Image wrap(RandomAccessibleInterval img) {
        ImagePlus ip = ImageJFunctions.wrap(img, "");
        return IJImageWrapper.wrap(ip);
    }
    
    public static <T extends RealType<T>> Img<T> getImage(Image image) {
        return ImagePlusAdapter.wrapReal(IJImageWrapper.getImagePlus(image));
    }
    /*public static <T extends RealType<T>> Histogram1d<T> getHistogram(Img<T> img) {
        if (img.firstElement() instanceof IntegerType) {
            ((Img<IntegerType>)img)
        }
        //TODO : utiliser ops qui construit l'histogram!
        Histogram1d<IntegerType> hist=new Histogram1d<T>(new Integer1dBinMapper<T>(0,255,false)); 
        RandomAccess<LongType> raHist=hist.randomAccess();
        new MakeHistogram<T>((int)hist.getBinCount()).compute(input,hist);
    }*/
}
