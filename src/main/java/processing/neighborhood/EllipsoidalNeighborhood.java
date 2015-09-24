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

import image.Image;

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
    
    int valueNumber;
    /**
     * 3D Elipsoidal Neighbourhood around a voxel
     * @param radius in pixel in the XY-axis
     * @param radiusZ in pixel in the Z-axis
     * an array of diplacement from the center
     */
    public EllipsoidalNeighborhood(double radius, double radiusZ, boolean excludeCenter) {
        this.radius=radius;
        this.radiusZ=radiusZ;
        is3D=true;
        double r = (double) radius / radiusZ;
        int rad = (int) (radius + 0.5f);
        int radZ = (int) (radiusZ + 0.5f);
        int[][] temp = new int[3][(2 * rad + 1) * (2 * rad + 1) * (2 * radZ + 1)];
        //float[] tempDist = new float[temp[0].length];
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
                        //tempDist[count] = (float) Math.sqrt(d2);
                        count++;
                    }
                }
            }
        }

        //distances = new float[count];
        //System.arraycopy(tempDist, 0, distances, 0, count);

        /*
         * Integer[] order = new Integer[distances.length]; for (int i = 0; i <
         * order.length; i++) order[i]=i; Arrays.sort(order, new
         * ComparatorDistances()); Arrays.sort(distances); for (int i = 0;
         * i<count; i++) { vois[0][i]=temp[0][order[i]];
         * vois[1][i]=temp[1][order[i]]; vois[2][i]=temp[2][order[i]]; }
         *
         */
        dx= new int[count];
        dy= new int[count];
        dz= new int[count];
        values=new float[count];
        System.arraycopy(temp[0], 0, dx, 0, count);
        System.arraycopy(temp[1], 0, dy, 0, count);
        System.arraycopy(temp[2], 0, dz, 0, count);
    }
    /**
     * 2D Circular Neighbourhood around a voxel
     * @param radius in pixel in the XY-axis
     * an array of diplacement from the center. central point is excluded
     */
    public EllipsoidalNeighborhood(double radius, boolean excludeCenter) { //todo subclass to avoid the test...
        this.radius = radius;
        is3D=false;
        int rad = (int) (radius + 0.5f);
        int[][] temp = new int[2][(2 * rad + 1) * (2 * rad + 1)];
        //float[] tempDist = new float[temp[0].length];, final float[] dest
        int count = 0;
        double rad2 = radius * radius;
        for (int yy = -rad; yy <= rad; yy++) {
            for (int xx = -rad; xx <= rad; xx++) {
                float d2 = yy * yy + xx * xx;
                if (d2 <= rad2 && (!excludeCenter || d2 > 0)) {	//exclusion du point central
                    temp[0][count] = xx;
                    temp[1][count] = yy;
                    //tempDist[count] = (float) Math.sqrt(d2);
                    count++;
                }
            }
        }
        dx= new int[count];
        dy= new int[count];
        dz= new int[count];
        values=new float[count];
        System.arraycopy(temp[0], 0, dx, 0, count);
        System.arraycopy(temp[1], 0, dy, 0, count);
    }
    
    @Override public void setPixels(int x, int y, int z, Image image) {
        valueNumber=0;
        int xx, yy;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz)) values[valueNumber++]=image.getPixel(xx, yy, zz);
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, 0)) values[valueNumber++]=image.getPixel(xx, yy, 0);
            }
        }
    }
    
    @Override public int getSize() {return dx.length;}

    @Override public float[] getPixelValues() {
        return values;
    }

    @Override public int getValueNumber() {
        return valueNumber;
    }
    
}
