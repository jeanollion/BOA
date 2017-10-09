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

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import image.Image;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageOperations;
import image.ImageReader;
import processing.Filters;
import static processing.Filters.applyFilter;
import processing.neighborhood.Neighborhood;

/**
 *
 * @author jollion
 */
public class TestOpen {
    public static void main(String[] args) {
        
        ImageInteger im = (ImageInteger)ImageReader.openIJTif("/data/Images/MOP/ThldPlaneF268bis.tif");
        Neighborhood n = Filters.getNeighborhood(2.5, 1, im);
        ImageInteger min = Filters.binaryMin(im, null, n, true);
        
        ImageInteger open = Filters.binaryMax(min, null, n, false, false);
        Image xor = ImageOperations.xor(open, im, null);
        ImageWindowManagerFactory.showImage(im);
        ImageWindowManagerFactory.showImage(min);
        ImageWindowManagerFactory.showImage(open);
        ImageWindowManagerFactory.showImage(xor);
    }
}
