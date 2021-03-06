/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.utils.geom;

import boa.data_structure.Voxel;
import boa.image.Offset;
import boa.utils.Utils;
import java.util.Arrays;
import java.util.Collection;
import net.imglib2.RealLocalizable;

/**
 *
 * @author Jean Ollion
 */
public class Vector extends Point<Vector>  {
    public Vector(float... coords) {
        super(coords);
    }
    
    public Vector(Voxel start, Voxel end) {
        this(end.x-start.x, end.y-start.y, end.z-start.z);
    }
    public static Vector vector2DFromOffset(Offset start, Offset end) {
        return new Vector(end.xMin()-start.xMin(), end.yMin()-start.yMin());
    }
    public static Vector vector2D(RealLocalizable start, RealLocalizable end) {
        return new Vector((float)(end.getDoublePosition(0)-start.getDoublePosition(0)), (float)(end.getDoublePosition(1)-start.getDoublePosition(1)));
    }
    public static Vector vector(Point start, Point end) {
        float[] coords = new float[start.numDimensions()];
        for (int i = 0; i<coords.length; ++i) coords[i] = end.coords[i] - start.coords[i];
        return new Vector(coords);
    }
    public static Vector vector(RealLocalizable start, RealLocalizable end) {
        float[] coords = new float[start.numDimensions()];
        for (int i = 0; i<coords.length; ++i) coords[i] =(float)(end.getDoublePosition(i) - start.getDoublePosition(i));
        return new Vector(coords);
    }
    public boolean isNull() {
        for (int i = 0; i<coords.length; ++i) if (coords[i]!=0) return false;
        return true;
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
    public static double crossProduct2D(Vector u, Vector v) {
        return u.coords[0]*v.coords[1]-u.coords[1]*v.coords[0];
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
