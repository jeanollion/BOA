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
import dataStructure.objects.Voxel;
import image.Image;
import image.ImageByte;
import image.ImageProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 *
 * @author jollion
 */
public class CylindricalNeighborhood extends DisplacementNeighborhood {
    double radius;
    double radiusZUp, radiusZDown;
    
    /**
     * 3D Cylindrical Neighbourhood around a voxel, with Z-axis as height
     * @param radiusXY in pixel in the XY-axis
     * @param heightZ height of the cilinder in pixel in the Z-axis
     * @param excludeCenter if true, central point can excluded
     * return an array of diplacement from the center
     */
    public CylindricalNeighborhood(double radiusXY, double heightZ, boolean excludeCenter) {
        this(radiusXY, heightZ, heightZ, excludeCenter);
    }
    public CylindricalNeighborhood(double radiusXY, double heightZDown, double heightZUp, boolean excludeCenter) {
        this.radius=radiusXY;
        this.radiusZUp=heightZUp;
        this.radiusZDown=heightZDown;
        is3D=radiusZUp>0 || radiusZDown>0;
        int rad = (int) (radius + 0.5d);
        int radZUp = (int) (radiusZUp + 0.5d);
        int radZDown = (int) (radiusZDown + 0.5d);
        int[][] temp = new int[3][(2 * rad + 1) * (2 * rad + 1) * (radZUp + radZDown + 1)];
        final float[] tempDist = new float[temp[0].length];
        int count         = 0;
        double rad2 = radius * radius;
        for (int yy = -rad; yy <= rad; yy++) {
            for (int xx = -rad; xx <= rad; xx++) {
                double d2 = yy * yy + xx * xx;
                if (d2 <= rad2) {
                    for (int zz = -radZDown; zz <= radZUp; zz++) {
                        if (!excludeCenter || d2 > 0 || zz!=0) {//exclusion du point central
                            temp[0][count] = xx;
                            temp[1][count] = yy;
                            temp[2][count] = zz;
                            tempDist[count] = (float) Math.sqrt(d2+zz*zz);
                            count++;
                        }
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
    
    @Override public String toString() {
        if (this.is3D) {
            String res = "3D CylindricalNeighborhood: XY:"+this.radius+" Z (down):"+radiusZDown+" Z (up):"+radiusZUp+" [";
            for (int i = 0; i<dx.length; ++i) res+="dx:"+dx[i]+",dy:"+dy[i]+",dz:"+dz[i]+";";
            return res+"]";
        } else {
            String res = "Cylindrical Neighborhood2D: radius:"+radius+ " [";
            for (int i = 0; i<dx.length; ++i) res+="dx:"+dx[i]+",dy:"+dy[i]+";";
            return res+"]";
        }
        
    }
    
    
    public ImageByte drawNeighborhood(ImageByte output) {
        int centerXY, centerZ;
        if (output == null) {
            int radXY = (int)(this.radius+0.5);
            int radZUp = (int)(this.radiusZUp+0.5);
            int radZDown = (int)(this.radiusZDown+0.5);
            centerXY=radXY;
            centerZ=radZDown;
            if (is3D) output = new ImageByte("3D CylindricalNeighborhood: XY:"+this.radius+" Z (down):"+radZDown+" Z (up):"+radZUp, radXY*2+1, radXY*2+1, (radZUp+radZDown)+1);
            else output = new ImageByte("2D CylindricalNeighborhood: XY:"+this.radius, radXY*2+1, radXY*2+1, 1);
        } else {
            centerXY = output.getSizeX()/2+1;
            centerZ = output.getSizeZ()/2+1;
        }
        if (is3D) for (int i = 0; i<this.dx.length;++i) {
            //logger.debug("set pix: x: {}/{}, y: {}/{}, z: {}/{}", centerXY+dx[i], output.getSizeX()-1, centerXY+dy[i], output.getSizeY()-1,  centerZ+dz[i], output.getSizeZ()-1);
            output.setPixel(centerXY+dx[i], centerXY+dy[i], centerZ+dz[i], 1);
        }
        else for (int i = 0; i<this.dx.length;++i) output.setPixel(centerXY+dx[i], centerXY+dy[i], 0, 1);
        return output;
    }
    
}
