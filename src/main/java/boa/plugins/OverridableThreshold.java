/*
 * Copyright (C) 2016 jollion
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
package boa.plugins;

import boa.data_structure.StructureObjectProcessing;
import boa.image.Image;
import boa.image.ImageInteger;

/**
 *
 * @author jollion
 */
public interface OverridableThreshold {
    @FunctionalInterface public static interface ThresholdByPixel { public void getThreshold(int x, int y, int z);}
    
    public void setThresholdValue(double threshold);
    //public void setThresholdFunction(ThresholdByPixel thresholdFunction);
    public Image getThresholdImage(Image input, int structureIdx, StructureObjectProcessing parent);
    public void setThresholdedImage(ImageInteger thresholdedImage);
    /*public static ImageInteger thresholdImage(Image image, ThresholdByPixel thresholdFunction) {
        
    }*/
}
