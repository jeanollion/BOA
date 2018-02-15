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
package boa.measurement;

import boa.data_structure.Region;
import boa.data_structure.Voxel;
import boa.image.Image;
import boa.image.processing.localthickness.LocalThickness;
import java.util.ArrayList;
import java.util.List;
import boa.utils.ArrayUtil;
import java.util.Iterator;

/**
 *
 * @author jollion
 */
public class GeometricalMeasurements {
    
    public static double getVolume(Region o) {
        int count = o.getVoxels().size();
        if (!o.is2D()) return count * o.getScaleXY() * o.getScaleXY() * o.getScaleZ();
        else  return count * o.getScaleXY() * o.getScaleXY();
    }
    public static double getFeretMax(Region o) {
        double d2Max = 0;
        List<Voxel> list = new ArrayList<>(o.getContour());
        int voxCount = list.size();
        double scaleXY = o.getScaleXY();
        double scaleZ = o.getScaleZ();
        Iterator<Voxel> it = list.iterator();
        for (int i = 0; i<voxCount-1; ++i) {
            for (int j = i+1; j<voxCount; ++j) {
                double d2Temp = list.get(i).getDistanceSquare(list.get(j), scaleXY, scaleZ);
                if (d2Temp>d2Max) d2Max = d2Temp;
            }
        }
        return Math.sqrt(d2Max);
    }
    
    public static double getDistance(Region o1, Region o2) {
        return getDistance(o1.getGeomCenter(false), o2.getGeomCenter(false), o1.getScaleXY(), o1.getScaleZ());
    }
    public static double getDistanceSquare(Region o1, Region o2) {
        return getDistanceSquare(o1.getGeomCenter(false), o2.getGeomCenter(false), o1.getScaleXY(), o1.getScaleZ());
    }
    public static double getDistanceBB(Region o1, Region o2, boolean scaled) {
        double dMin = Double.POSITIVE_INFINITY;
        double sXY = scaled ? o1.getScaleXY():1;
        double sZ = scaled ? o1.getScaleZ():1;
        for (Voxel v1 : o1.getContour()) {
            for (Voxel v2 : o2.getContour()) {
                double d = v1.getDistanceSquare(v2, sXY, sZ);
                if (d<dMin) dMin =d;
            }
        }
        return Math.sqrt(dMin);
    }
    public static double getDistance(Region o1, Region o2, Image im1, Image im2) {
        return getDistance(o1.getMassCenter(im1, false), o2.getMassCenter(im2, false), o1.getScaleXY(), o1.getScaleZ());
    }
    
    public static double getDistance(double[] c1, double[] c2, double scaleXY, double scaleZ) {
        return Math.sqrt(Math.pow((c1[0]-c2[0])*scaleXY, 2) + Math.pow((c1[1]-c2[1])*scaleXY, 2) + Math.pow((c1[2]-c2[2])*scaleZ, 2));
    }
    public static double getDistanceSquare(double[] c1, double[] c2, double scaleXY, double scaleZ) {
        return Math.pow((c1[0]-c2[0])*scaleXY, 2) + Math.pow((c1[1]-c2[1])*scaleXY, 2) + Math.pow((c1[2]-c2[2])*scaleZ, 2);
    }
    public static double localThickness(Region object) {
        Image ltMap = LocalThickness.localThickness(object.getMask(), object.is2D()?1:object.getScaleXY()/object.getScaleZ(), true, 1);
        return BasicMeasurements.getQuantileValue(object, ltMap, 0.5)[0];
    }
    public static double meanThicknessZ(Region object) {
        double mean = 0;
        double count = 0;
        for (int y = 0; y < object.getBounds().getSizeY(); ++y) {
            for (int x = 0; x < object.getBounds().getSizeX(); ++x) {
                int min = -1;
                int max = -1;
                for (int z = 0; z < object.getBounds().getSizeZ(); ++z) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = z;
                        break;
                    }
                }
                for (int z = object.getBounds().getSizeZ() - 1; z >= 0; --z) {
                    if (object.getMask().insideMask(x, y, z)) {
                        max = z;
                        break;
                    }
                }
                if (min >= 0) {
                    mean += max - min + 1;
                    ++count;
                }
            }
        }
        if (count > 0) {
            mean /= count;
        }
        return mean;
    }
    public static double medianThicknessZ(Region object) {
        List<Integer> values = new ArrayList<>();
        for (int y = 0; y < object.getBounds().getSizeY(); ++y) {
            for (int x = 0; x < object.getBounds().getSizeX(); ++x) {
                int min = -1;
                int max = -1;
                for (int z = 0; z < object.getBounds().getSizeZ(); ++z) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = z;
                        break;
                    }
                }
                for (int z = object.getBounds().getSizeZ() - 1; z >= 0; --z) {
                    if (object.getMask().insideMask(x, y, z)) {
                        max = z;
                        break;
                    }
                }
                if (min >= 0) values.add(max - min + 1);
            }
        }
        return ArrayUtil.medianInt(values);
    }
    public static double meanThicknessY(Region object) {
        double mean = 0;
        double count = 0;
        for (int z = 0; z < object.getBounds().getSizeZ(); ++z) {
            for (int x = 0; x < object.getBounds().getSizeX(); ++x) {
                int min = -1;
                int max = -1;
                for (int y = 0; y < object.getBounds().getSizeY(); ++y) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = y;
                        break;
                    }
                }
                for (int y = object.getBounds().getSizeY() - 1; y >= 0; --y) {
                    if (object.getMask().insideMask(x, y, z)) {
                        max = y;
                        break;
                    }
                }
                if (min >= 0) {
                    mean += max - min + 1;
                    ++count;
                }
            }
        }
        if (count > 0) {
            mean /= count;
        }
        return mean;
    }
    public static double medianThicknessY(Region object) {
        List<Integer> values = new ArrayList<>();
        for (int z = 0; z < object.getBounds().getSizeZ(); ++z) {
            for (int x = 0; x < object.getBounds().getSizeX(); ++x) {
                int min = -1;
                int max = -1;
                for (int y = 0; y < object.getBounds().getSizeY(); ++y) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = y;
                        break;
                    }
                }
                for (int y = object.getBounds().getSizeY() - 1; y >= 0; --y) {
                    if (object.getMask().insideMask(x, y, z)) {
                        max = y;
                        break;
                    }
                }
                if (min >= 0) values.add(max - min + 1);
            }
        }
        return ArrayUtil.medianInt(values);
    }

    public static double meanThicknessX(Region object) {
        double mean = 0;
        double count = 0;
        for (int z = 0; z < object.getBounds().getSizeZ(); ++z) {
            for (int y = 0; y < object.getBounds().getSizeY(); ++y) {
                int min = -1;
                int max = -1;
                for (int x = 0; x < object.getBounds().getSizeX(); ++x) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = x;
                        break;
                    }
                }
                for (int x = object.getBounds().getSizeX() - 1; x >= 0; --x) {
                    if (object.getMask().insideMask(x, y, z)) {
                        max = x;
                        break;
                    }
                }
                if (min >= 0) {
                    mean += max - min + 1;
                    ++count;
                }
            }
        }
        if (count > 0) {
            mean /= count;
        }
        return mean;
    }
    public static double medianThicknessX(Region object) {
        List<Integer> values = new ArrayList<>();
        for (int z = 0; z < object.getBounds().getSizeZ(); ++z) {
            for (int y = 0; y < object.getBounds().getSizeY(); ++y) {
                int min = -1;
                int max = -1;
                for (int x = 0; x < object.getBounds().getSizeX(); ++x) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = x;
                        break;
                    }
                }
                for (int x = object.getBounds().getSizeX() - 1; x >= 0; --x) {
                    if (object.getMask().insideMask(x, y, z)) {
                        max = x;
                        break;
                    }
                }
                if (min >= 0) values.add(max - min + 1);
            }
        }
        return ArrayUtil.medianInt(values);
    }
}
