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
package boa.plugins.plugins.post_filters;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.RegionPopulation.ContactBorder;
import boa.data_structure.RegionPopulation.Border;
import boa.data_structure.RegionPopulation.Filter;
import boa.data_structure.RegionPopulation.Size;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import boa.measurement.GeometricalMeasurements;
import boa.plugins.PostFilter;

/**
 *
 * @author jollion
 */
public class MicrochannelPhaseArtifacts implements PostFilter {
    BoundedNumberParameter XThickness = new BoundedNumberParameter("X-thickness", 1, 7, 0, null).setToolTipText("Thickness along X-axis should be under this threshold to erase object");
    BoundedNumberParameter XContactFraction = new BoundedNumberParameter("Minimum X contact", 2, 0.75, 0, 1).setToolTipText("Contact with X-border of channel divided by length should be higher than this value to erase object");
    BoundedNumberParameter YContactFraction = new BoundedNumberParameter("Minimum Y contact", 2, 0.90, 0, 1).setToolTipText("Contact with upper Y-border of channel divided by X-thickness should be higher than this value to erase object");
    BoundedNumberParameter cornerContactFraction = new BoundedNumberParameter("corner contact fraction", 2, 0.5, 0, 1).setToolTipText("Contact with X-border (left XOR right) & upper Y-border of channel divided by perimeter should be higher than this value to erase object");
    Parameter[] parameters = new Parameter[]{XThickness, XContactFraction, YContactFraction, cornerContactFraction};
    
    public MicrochannelPhaseArtifacts(){}

    public MicrochannelPhaseArtifacts setThickness(int thickness) {
        this.XThickness.setValue(thickness);
        return this;
    }
    
    @Override public RegionPopulation runPostFilter(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        childPopulation.filter(getFilter(XThickness.getValue().doubleValue(), XContactFraction.getValue().doubleValue(), 0, 0));
        childPopulation.filter(getFilter(XThickness.getValue().doubleValue(), 0, YContactFraction.getValue().doubleValue(), 0));
        childPopulation.filter(getFilter(XThickness.getValue().doubleValue(), 0, 0, cornerContactFraction.getValue().doubleValue())); // for corner removal contact condition
        return childPopulation;
    }
    public static RegionPopulation.Filter getFilter(double maxThickness, double minXContactFraction, double minYContactFraction, double minContactFractionCorner) {
        return new RegionPopulation.Filter() {
            ContactBorder borderY, borderXl, borderXr;
            RegionPopulation population;
            @Override
            public void init(RegionPopulation population) {
                this.population=population;
                this.borderY = new ContactBorder(0, population.getImageProperties(), Border.YUp);//.setTolerance(1);
                this.borderXl = new ContactBorder(0, population.getImageProperties(), Border.Xl).setTolerance(1);
                this.borderXr = new ContactBorder(0, population.getImageProperties(), Border.Xr).setTolerance(1);
            }

            @Override
            public boolean keepObject(Region object) {
                double xThickness = GeometricalMeasurements.medianThicknessX(object);
                if (xThickness>maxThickness) { 
                    if (xThickness<1.5*maxThickness) { // allow corner check for objects a little bit thicker
                        if (population.isInContactWithOtherObject(object)) return true;
                        if (isCorner(object, minContactFractionCorner)) return false;
                    }
                    return true;
                }
                if (minXContactFraction>0) {
                    double length = GeometricalMeasurements.getFeretMax(object);
                    int lim = (int)(length * minXContactFraction+0.5);
                    int xl = borderXl.getContact(object);
                    int xr = borderXr.getContact(object);
                    if (xl>0 && xr>0) return true; // artifacts are not in contact with both borders
                    int x = Math.max(xl, xr);
                    if (x>lim) {
                        //logger.debug("X contact: Contact value: {} length: {}, limit: {}", x, length, lim);
                        return false;
                    }
                }
                if (minContactFractionCorner<=0 && minYContactFraction<=0) return true;
                if (population.isInContactWithOtherObject(object)) return true;
                if (minYContactFraction>0) {
                    int lim = (int)(xThickness * minYContactFraction+0.5);
                    borderY.setLimit(lim);
                    //logger.debug("Y contact: Contact value: {} thickness: {}, limit: {}", borderY.getContact(object), xThickness, lim);
                    if (!borderY.keepObject(object)) {
                        return false;
                    }
                }
                return !isCorner(object, minContactFractionCorner);
            }

            public boolean isCorner(Region object, double minContactFraction) {
                if (minContactFraction<=0) return false;
                int xl = borderXl.getContact(object);
                int xr = borderXr.getContact(object);
                if (xl>0 && xr>0) return false; // not a corner
                int x = Math.max(xl, xr);
                if (x==0) return false;
                int y = borderY.getContact(object);
                if (y==0) return false;
                double limCorner = object.getContour().size() * minContactFraction;
                //logger.debug("corner detection: x: {}, xr: {}, y: {}, sum: {} lim: {}", xl, xr, y, x+y, limCorner);
                return x+y>limCorner;
            }
        };
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
