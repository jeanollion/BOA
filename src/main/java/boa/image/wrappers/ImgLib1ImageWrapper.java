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
package boa.image.wrappers;

import boa.image.IJImageWrapper;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageShort;
import boa.image.TypeConverter;
import ij.ImagePlus;
import static boa.image.IJImageWrapper.getImagePlus;
import mpicbg.imglib.image.display.imagej.ImageJFunctions;
import mpicbg.imglib.type.numeric.RealType;

/**
 *
 * @author jollion
 */
public class ImgLib1ImageWrapper {
    
    public static < T extends RealType< T >> Image wrap(mpicbg.imglib.image.Image<T> image) {
        ImagePlus ip = ImageJFunctions.copyToImagePlus(image);
        return IJImageWrapper.wrap(ip);
    }
    
    public static  mpicbg.imglib.image.Image getImage(Image image) { //<T extends RealType<T>>
        image = TypeConverter.toCommonImageType(image);
        ImagePlus ip = IJImageWrapper.getImagePlus(image);
        if (image instanceof ImageFloat) return ImageJFunctions.wrapFloat(ip);
        else if (image instanceof ImageShort) return ImageJFunctions.wrapShort(ip);
        else if (image instanceof ImageByte) return ImageJFunctions.wrapByte(ip);
        else return null;
    }
}
