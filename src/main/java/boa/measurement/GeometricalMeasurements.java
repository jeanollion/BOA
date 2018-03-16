/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.measurement;

import boa.data_structure.Region;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.SimpleBoundingBox;
import boa.image.processing.EDT;
import boa.image.processing.Filters;
import boa.image.processing.bacteria_spine.BacteriaSpineFactory;
import boa.image.processing.localthickness.LocalThickness;
import java.util.ArrayList;
import java.util.List;
import boa.utils.ArrayUtil;
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.Vector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.DoubleStream;

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
        for (int i = 0; i<voxCount-1; ++i) {
            for (int j = i+1; j<voxCount; ++j) {
                double d2Temp = list.get(i).getDistanceSquare(list.get(j), scaleXY, scaleZ);
                if (d2Temp>d2Max) d2Max = d2Temp;
            }
        }
        return Math.sqrt(d2Max);
    }
    public static double getDistanceMapWidth(Region r) {
        ImageFloat edt = EDT.transform(r.getMask(), true, 1, r.getScaleZ()/r.getScaleXY(), 1);
        Filters.LocalMax lm = new Filters.LocalMax(r.getMask());
        lm.setUp(edt, Filters.getNeighborhood(1.5, edt));
        List<Double> localMax= new ArrayList<>();
        BoundingBox.loop(new SimpleBoundingBox(edt).resetOffset(), (x, y, z)-> {
            if (lm.applyFilter(x, y, z)>0) localMax.add((double)edt.getPixel(x, y, z));
        });
        return ArrayUtil.median(localMax) * 2;
    }
    
    public static double getSpineLength(Region r, boolean tryToFillHoles) {
        PointContainer2<?, Double>[] spine = BacteriaSpineFactory.createSpine(r, tryToFillHoles);
        return spine[spine.length-1].getContent2();
    }
    public static double[] getSpineLengthAndWidth(Region r, boolean tryToFillHoles) {
        PointContainer2<Vector, Double>[] spine = BacteriaSpineFactory.createSpine(r, tryToFillHoles);
        if (spine==null) return new double[]{Double.NaN, Double.NaN};
        double width = ArrayUtil.median(Arrays.stream(spine).mapToDouble(s->s.getContent1().norm()).toArray());
        double length = spine[spine.length-1].getContent2();
        return new double[]{length, width};
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
    
    public static double getDistance(Point c1, Point  c2, double scaleXY, double scaleZ) {
        return Math.sqrt(Math.pow((c1.get(0)-c2.get(0))*scaleXY, 2) + Math.pow((c1.get(1)-c2.get(1))*scaleXY, 2) + Math.pow((c1.getWithDimCheck(2)-c2.getWithDimCheck(2))*scaleZ, 2));
    }
    public static double getDistanceSquare(Point c1, Point c2, double scaleXY, double scaleZ) {
        return Math.pow((c1.get(0)-c2.get(0))*scaleXY, 2) + Math.pow((c1.get(1)-c2.get(1))*scaleXY, 2) + Math.pow((c1.getWithDimCheck(2)-c2.getWithDimCheck(2))*scaleZ, 2);
    }
    public static double localThickness(Region object) {
        Image ltMap = LocalThickness.localThickness(object.getMaskAsImageInteger(), object.is2D()?1:object.getScaleXY()/object.getScaleZ(), true, 1);
        DoubleStream stream = ltMap.stream(object.getMask(), true).sorted();
        return ArrayUtil.quantiles(stream.toArray(), 0.5)[0];
    }
    public static double meanThicknessZ(Region object) {
        double mean = 0;
        double count = 0;
        for (int y = 0; y < object.getBounds().sizeY(); ++y) {
            for (int x = 0; x < object.getBounds().sizeX(); ++x) {
                int min = -1;
                int max = -1;
                for (int z = 0; z < object.getBounds().sizeZ(); ++z) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = z;
                        break;
                    }
                }
                for (int z = object.getBounds().sizeZ() - 1; z >= 0; --z) {
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
        for (int y = 0; y < object.getBounds().sizeY(); ++y) {
            for (int x = 0; x < object.getBounds().sizeX(); ++x) {
                int min = -1;
                int max = -1;
                for (int z = 0; z < object.getBounds().sizeZ(); ++z) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = z;
                        break;
                    }
                }
                for (int z = object.getBounds().sizeZ() - 1; z >= 0; --z) {
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
        for (int z = 0; z < object.getBounds().sizeZ(); ++z) {
            for (int x = 0; x < object.getBounds().sizeX(); ++x) {
                int min = -1;
                int max = -1;
                for (int y = 0; y < object.getBounds().sizeY(); ++y) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = y;
                        break;
                    }
                }
                for (int y = object.getBounds().sizeY() - 1; y >= 0; --y) {
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
    public static double maxThicknessY(Region object) {
        double maxT = Double.NEGATIVE_INFINITY;
        for (int z = 0; z < object.getBounds().sizeZ(); ++z) {
            for (int x = 0; x < object.getBounds().sizeX(); ++x) {
                int min = -1;
                int max = -1;
                for (int y = 0; y < object.getBounds().sizeY(); ++y) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = y;
                        break;
                    }
                }
                for (int y = object.getBounds().sizeY() - 1; y >= 0; --y) {
                    if (object.getMask().insideMask(x, y, z)) {
                        max = y;
                        break;
                    }
                }
                if (min >= 0) {
                    double cur = max - min + 1;
                    if (cur>maxT) maxT=cur;
                }
            }
        }
        return maxT;
    }
    public static double maxThicknessZ(Region object) {
        double maxT = Double.NEGATIVE_INFINITY;
        for (int y = 0; y < object.getBounds().sizeY(); ++y) {
            for (int x = 0; x < object.getBounds().sizeX(); ++x) {
                int min = -1;
                int max = -1;
                for (int z = 0; z < object.getBounds().sizeZ(); ++z) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = z;
                        break;
                    }
                }
                for (int z = object.getBounds().sizeZ() - 1; z>= 0; --z) {
                    if (object.getMask().insideMask(x, y, z)) {
                        max = y;
                        break;
                    }
                }
                if (min >= 0) {
                    double cur = max - min + 1;
                    if (cur>maxT) maxT=cur;
                }
            }
        }
        return maxT;
    }
    public static double medianThicknessY(Region object) {
        List<Integer> values = new ArrayList<>();
        for (int z = 0; z < object.getBounds().sizeZ(); ++z) {
            for (int x = 0; x < object.getBounds().sizeX(); ++x) {
                int min = -1;
                int max = -1;
                for (int y = 0; y < object.getBounds().sizeY(); ++y) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = y;
                        break;
                    }
                }
                for (int y = object.getBounds().sizeY() - 1; y >= 0; --y) {
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
        for (int z = 0; z < object.getBounds().sizeZ(); ++z) {
            for (int y = 0; y < object.getBounds().sizeY(); ++y) {
                int min = -1;
                int max = -1;
                for (int x = 0; x < object.getBounds().sizeX(); ++x) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = x;
                        break;
                    }
                }
                for (int x = object.getBounds().sizeX() - 1; x >= 0; --x) {
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
    public static double maxThicknessX(Region object) {
        double maxT = Double.NEGATIVE_INFINITY;
        for (int z = 0; z < object.getBounds().sizeZ(); ++z) {
            for (int y = 0; y < object.getBounds().sizeY(); ++y) {
                int min = -1;
                int max = -1;
                for (int x = 0; x < object.getBounds().sizeX(); ++x) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = x;
                        break;
                    }
                }
                for (int x = object.getBounds().sizeX() - 1; x >= 0; --x) {
                    if (object.getMask().insideMask(x, y, z)) {
                        max = x;
                        break;
                    }
                }
                if (min >= 0) {
                    double cur = max - min + 1;
                    if (cur>maxT) maxT = cur;
                }
            }
        }
        return maxT;
    }
    public static double medianThicknessX(Region object) {
        List<Integer> values = new ArrayList<>();
        for (int z = 0; z < object.getBounds().sizeZ(); ++z) {
            for (int y = 0; y < object.getBounds().sizeY(); ++y) {
                int min = -1;
                int max = -1;
                for (int x = 0; x < object.getBounds().sizeX(); ++x) {
                    if (object.getMask().insideMask(x, y, z)) {
                        min = x;
                        break;
                    }
                }
                for (int x = object.getBounds().sizeX() - 1; x >= 0; --x) {
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
