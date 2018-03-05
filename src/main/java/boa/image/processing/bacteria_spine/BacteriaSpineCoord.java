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
package boa.image.processing.bacteria_spine;

/**
 *
 * @author jollion
 */
public class BacteriaSpineCoord {
    public final double[] coords = new double[4];
    public double spineCoord(boolean normalized) {
        return normalized?coords[0]/coords[2] : coords[0];
    }
    public double spineLength() {
        return coords[2];
    }
    public double distFromSpine(boolean normalized) {
        return normalized ? coords[1]/coords[3] : coords[1];
    } 
    public double spineRadius() {
        return coords[3];
    }
    public BacteriaSpineCoord setSpineCoord(double spineCoord) {
        coords[0] = spineCoord;
        return this;
    }
    public BacteriaSpineCoord setSpineLength(double spineLengthd) {
        coords[2] = spineLengthd;
        return this;
    }
    public BacteriaSpineCoord setDistFromSpine(double dist) {
        coords[1] = dist;
        return this;
    }
    public BacteriaSpineCoord setSpineRadius(double spineRadius) {
        coords[3] = spineRadius;
        return this;
    }
    @Override
    public String toString() {
        return new StringBuilder().append("along spine: ").append(spineCoord(false)).append("/").append(spineLength()).append(" distance from spine:").append(distFromSpine(false)).append("/").append(spineRadius()).toString();
    }
}
