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
package boa.plugins.plugins.trackers.nested_spot_tracker;

import boa.data_structure.StructureObject;
import boa.utils.geom.Point;
import fiji.plugin.trackmate.Spot;
import net.imglib2.RealLocalizable;

/**
 *
 * @author jollion
 */
public abstract class SpotWithQuality extends Spot {

    public SpotWithQuality(Point location, double radius, double quality) {
        super(location.numDimensions()<3 ? location.duplicate(3) : location, radius, quality);
    }
    
    public abstract boolean isLowQuality();
    public abstract int frame(); 
    public abstract StructureObject parent();
}
