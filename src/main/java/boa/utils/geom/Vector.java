/*
 * Copyright (C) 2018 jollion
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
package boa.utils.geom;

import boa.data_structure.Voxel;
import boa.image.Offset;
import boa.utils.Utils;
import java.util.Arrays;
import java.util.Collection;

/**
 *
 * @author jollion
 */
public class Vector extends Point<Vector>  {
    public Vector(float... coords) {
        super(coords);
    }
    
    public Vector(Voxel start, Voxel end) {
        this(end.x-start.x, end.y-start.y, end.z-start.z);
    }
    public static Vector vector2D(Offset start, Offset end) {
        return new Vector(end.xMin()-start.xMin(), end.yMin()-start.yMin());
    }
    public static Vector vector(Point start, Point end) {
        return weightedSum(start, end, -1, 1);
    }
    public static Vector weightedSum(Point v1, Point v2, double weight1, double weight2) {
        float[] coords = new float[v1.coords.length];
        for (int i = 0; i<coords.length; ++i) coords[i] = (float)(v1.coords[i] * weight1 + v2.coords[i]*weight2);
        return new Vector(coords);
    }
    public Vector add(Vector other, double w) {
        for (int i = 0; i<coords.length; ++i) coords[i]+=other.coords[i]*w;
        return this;
    }
    public double norm() {
        double norm = 0;
        for (float c : coords) norm+=c*c;
        return Math.sqrt(norm);
    }
    public Vector normalize() {
        double norm = norm();
        for (int i = 0; i<coords.length; ++i) coords[i]/=norm;
        return this;
    }
    public Vector multiply(double factor) {
        for (int i = 0; i<coords.length; ++i) coords[i]*=factor;
        return this;
    }
    public double dotProduct(Vector v) {
        double sum = 0;
        for (int i = 0; i<coords.length; ++i) sum+=coords[i]*v.coords[i];
        return sum;
    }
    /**
     * Un-oriented angle
     * @param v
     * @return angle in radian in [0; pi]
     */
    public double angle(Vector v) {
        double n = norm() * v.norm();
        if (n > 0) return Math.acos(dotProduct(v)/n);
        return Double.NaN;
    }
    /**
     * Un-oriented angle
     * @param v
     * @return angle in radian in [0; pi/2]
     */
    public double angle90(Vector v) {
        double n = norm() * v.norm();
        if (n > 0) return Math.acos(Math.abs(dotProduct(v)/n));
        return Double.NaN;
    }
    /**
     * Oriented angle in XY plane
     * @return oriented angle of {@param v} relative to this vector 
     */
    public double angleXY(Vector v) {
        return Math.atan2(v.coords[1], v.coords[0]) - Math.atan2(coords[1], coords[0]);
    }
    public static Vector crossProduct3D(Vector u, Vector v) {
        return new Vector(
                u.getWithDimCheck(2)*v.getWithDimCheck(3) - u.getWithDimCheck(3)*v.getWithDimCheck(2),
                u.getWithDimCheck(3)*v.getWithDimCheck(1) - u.getWithDimCheck(1)*v.getWithDimCheck(3),
                u.getWithDimCheck(1)*v.getWithDimCheck(2) - u.getWithDimCheck(2)*v.getWithDimCheck(1)
        );
    }
    
    public Vector rotateXY90() {
        float temp = coords[0];
        coords[0] = coords[1];
        coords[1] = -temp;
        return this;
    }
    @Override public Vector duplicate() {
        return new Vector(Arrays.copyOf(coords, coords.length));
    }
}