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



/**
 *
 * @author jollion
 */
public class ImagescienceWrapper {
    public static imagescience.image.Image getImagescience(Image image) {
        ImagePlus ip = IJImageWrapper.getImagePlus(image);
        return imagescience.image.Image.wrap(ip);
    }
    
    public static Image wrap(imagescience.image.Image image) {
        
        return IJImageWrapper.wrap(image.imageplus());
    }
    
}
