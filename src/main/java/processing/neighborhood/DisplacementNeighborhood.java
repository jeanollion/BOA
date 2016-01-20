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
package processing.neighborhood;

import dataStructure.objects.Voxel;
import image.Image;

/**
 *
 * @author jollion
 */
public abstract class DisplacementNeighborhood implements Neighborhood{
    public int[] dx, dy, dz;
    boolean is3D;        
    float[] values;
    float[] distances;
    int valueCount=0;
    
    @Override public void setPixels(Voxel v, Image image) {setPixels(v.x, v.y, v.z, image);}
    
    @Override public void setPixels(int x, int y, int z, Image image) {
        valueCount=0;
        int xx, yy;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz)) values[valueCount++]=image.getPixel(xx, yy, zz);
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z)) values[valueCount++]=image.getPixel(xx, yy, z);
            }
        }
    }
    public void setPixelsByIndex(Voxel v, Image image) {setPixelsByIndex(v.x, v.y, v.z, image);}
    public void setPixelsByIndex(int x, int y, int z, Image image) {
        valueCount=0;
        int xx, yy;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz)) {
                    values[i]=image.getPixel(xx, yy, zz);
                    valueCount++;
                } else values[i]=Float.NaN;
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z)) {
                    values[i]=image.getPixel(xx, yy, z);
                    valueCount++;
                } else values[i]=Float.NaN;
            }
        }
    }
    
    @Override public int getSize() {return dx.length;}

    @Override public float[] getPixelValues() {
        return values;
    }

    @Override public int getValueCount() {
        return valueCount;
    }
    @Override public float[] getDistancesToCenter() {
        return distances;
    }
    public boolean is3D() {
        return is3D;
    }
}
