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
package processing.neighborhood;

import dataStructure.objects.Voxel;
import image.Image;

/**
 *
 * @author jollion
 */
public interface Neighborhood {
    /**
     * Copy pixels in the neighborhood
     * @param x X-axis coordinate of the center of the neighborhood
     * @param y Y-axis coordinate of the center of the neighborhood
     * @param z Z-axis coordinate of the center of the neighborhood (0 for 2D case)
     * @param image image to copy pixels values from
     */
    public void setPixels(int x, int y, int z, Image image);
    public void setPixels(Voxel v, Image image);
    public int getSize();
    public float[] getPixelValues();
    public float getMin(int x, int y, int z, Image image);
    public float getMax(int x, int y, int z, Image image);
    public float[] getDistancesToCenter();
    public int getValueCount();
    public boolean is3D();
    // float[] getCoefficientValue();
}
