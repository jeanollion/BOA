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

import static core.Processor.logger;
import image.Image;
import java.util.Arrays;
import java.util.Comparator;

/**
 *
 * @author jollion
 */
public class EllipsoidalNeighborhood implements Neighborhood {
    double radius;
    double radiusZ;
    boolean is3D;        
    public final int[] dx, dy, dz;
    float[] values;
    float[] distances;
    int valueCount;
    /**
     * 3D Elipsoidal Neighbourhood around a voxel
     * @param radius in pixel in the XY-axis
     * @param radiusZ in pixel in the Z-axis
     * @param excludeCenter if true, central point can excluded
     * return an array of diplacement from the center
     */
    public EllipsoidalNeighborhood(double radius, double radiusZ, boolean excludeCenter) {
        this.radius=radius;
        this.radiusZ=radiusZ;
        is3D=true;
        double r = (double) radius / radiusZ;
        int rad = (int) (radius + 0.5f);
        int radZ = (int) (radiusZ + 0.5f);
        int[][] temp = new int[3][(2 * rad + 1) * (2 * rad + 1) * (2 * radZ + 1)];
        final float[] tempDist = new float[temp[0].length];
        int count         = 0;
        double rad2 = radius * radius;
        for (int zz = -radZ; zz <= radZ; zz++) {
            for (int yy = -rad; yy <= rad; yy++) {
                for (int xx = -rad; xx <= rad; xx++) {
                    double d2 = zz * r * zz * r + yy * yy + xx * xx;
                    if (d2 <= rad2 && (!excludeCenter || d2 > 0)) {	//exclusion du point central
                        temp[0][count] = xx;
                        temp[1][count] = yy;
                        temp[2][count] = zz;
                        tempDist[count] = (float) Math.sqrt(d2);
                        count++;
                    }
                }
            }
        }

        
        Integer[] indicies = new Integer[count]; for (int i = 0; i <indicies.length; i++) indicies[i]=i;
        Comparator<Integer> compDistance = new Comparator<Integer>() {
            @Override public int compare(Integer arg0, Integer arg1) {
                return Float.compare(tempDist[arg0], tempDist[arg1]);
            }
        };
        Arrays.sort(indicies, compDistance);
        distances = new float[count];
        dx= new int[count];
        dy= new int[count];
        dz= new int[count];
        values=new float[count];
        for (int i = 0; i<count; ++i) {
            dx[i] = temp[0][indicies[i]];
            dy[i] = temp[1][indicies[i]];
            dz[i] = temp[2][indicies[i]];
            distances[i] = tempDist[indicies[i]];
        }
    }
    /**
     * 2D Circular Neighbourhood around a voxel
     * @param radius in pixel in the XY-axis
     * an array of diplacement from the center. 
     * @param excludeCenter if true, central point can excluded
     */
    public EllipsoidalNeighborhood(double radius, boolean excludeCenter) { 
        this.radius = radius;
        this.radiusZ=radius;
        is3D=false;
        int rad = (int) (radius + 0.5f);
        int[][] temp = new int[2][(2 * rad + 1) * (2 * rad + 1)];
        final float[] tempDist = new float[temp[0].length];
        int count = 0;
        double rad2 = radius * radius;
        for (int yy = -rad; yy <= rad; yy++) {
            for (int xx = -rad; xx <= rad; xx++) {
                float d2 = yy * yy + xx * xx;
                if (d2 <= rad2 && (!excludeCenter || d2 > 0)) {	//exclusion du point central
                    temp[0][count] = xx;
                    temp[1][count] = yy;
                    tempDist[count] = (float) Math.sqrt(d2);
                    count++;
                }
            }
        }
        Integer[] indicies = new Integer[count]; for (int i = 0; i <indicies.length; i++) indicies[i]=i;
        Comparator<Integer> compDistance = new Comparator<Integer>() {
            @Override public int compare(Integer arg0, Integer arg1) {
                return Float.compare(tempDist[arg0], tempDist[arg1]);
            }
        };
        Arrays.sort(indicies, compDistance);
        distances = new float[count];
        dx= new int[count];
        dy= new int[count];
        dz= new int[count];
        values=new float[count];
        for (int i = 0; i<count; ++i) {
            dx[i] = temp[0][indicies[i]];
            dy[i] = temp[1][indicies[i]];
            distances[i] = tempDist[indicies[i]];
        }
    }
    
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
                if (image.contains(xx, yy, 0)) values[valueCount++]=image.getPixel(xx, yy, 0);
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

    @Override public double getRadiusXY() {
        return radius;
    }

    @Override public double getRadiusZ() {
        return radiusZ;
    }
    
}
