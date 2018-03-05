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
package boa.image.processing.bacteria_skeleton;

import boa.image.processing.bacteria_skeleton.BacteriaSpineLocalizer.Compartment;
import boa.image.processing.bacteria_skeleton.BacteriaSpineLocalizer.ReferencePole;

/**
 *
 * @author jollion
 */
public class BacteriaSpineCoord {
    public ReferencePole pole;
    public Compartment comp;
    public boolean left;
    public double absoluteDistanceFromSpine;
    public double normalizedSpineCoordinate;
    @Override
    public String toString() {
        return new StringBuilder().append("along spine: ").append(normalizedSpineCoordinate).append(" distance from spine:").append(absoluteDistanceFromSpine).append(" left:").append(left).append("compartment: ").append(comp).append("pole: ").append(pole).toString();
    }
}
