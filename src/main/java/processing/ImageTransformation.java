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

import image.Image;
import image.ImageProperties;
import image.ImagescienceWrapper;
import imagescience.image.Coordinates;
import imagescience.image.Dimensions;
import imagescience.transform.Rotate;
import imagescience.transform.Turn;
import imagescience.transform.Translate;
import imagescience.transform.Embed;
/**
 *
 * @author jollion
 */
public class ImageTransformation {
    
    public static Image rotateXY(Image image, float angle) {
        if (angle%90==0) {
            return turn(image, (int)angle/90, 0, 0);
        } else return rotate(image, angle, 0, 0, 2, true, true);
    }
    
    public static Image rotate(Image image, float zAngle, float yAngle, float xAngle, int interpolation, boolean fit, boolean antialiasing) {
        return ImagescienceWrapper.wrap((new  Rotate()).run(ImagescienceWrapper.getImagescience(image), zAngle, yAngle, xAngle, interpolation, fit, antialiasing));
    }
    
    public static Image turn(Image image, int times90z, int times90y, int times90x) {
        return ImagescienceWrapper.wrap((new Turn()).run(ImagescienceWrapper.getImagescience(image), times90z, times90y, times90x));
    }
    
    public static Image translate(Image image, int xTrans, int yTrans, int zTrans, int interpolation) {
        return ImagescienceWrapper.wrap((new  Translate()).run(ImagescienceWrapper.getImagescience(image), xTrans, yTrans, zTrans, interpolation));
    }
    
    public static Image resize(Image image, ImageProperties newImage, int posX, int posY, int posZ) {
        Dimensions dim = new Dimensions(newImage.getSizeX(), newImage.getSizeY(), newImage.getSizeZ(), 1, 1);
        Coordinates pos = new Coordinates(posX, posY, posZ);
        return ImagescienceWrapper.wrap((new Embed()).run(ImagescienceWrapper.getImagescience(image), dim, pos, 0));
    }
}
