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
package plugins.plugins.postFilters;

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.ObjectPopulation.ContactBorder;
import dataStructure.objects.ObjectPopulation.ContactBorder.Border;
import dataStructure.objects.ObjectPopulation.Filter;
import dataStructure.objects.ObjectPopulation.Size;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import measurement.GeometricalMeasurements;
import plugins.PostFilter;

/**
 *
 * @author jollion
 */
public class MicrochannelPhaseArtifacts implements PostFilter {
    BoundedNumberParameter XThickness = new BoundedNumberParameter("X-thickness", 1, 6, 0, null).setToolTipText("Thickness along X-axis should be under this threshold to erase object");
    BoundedNumberParameter XContactFraction = new BoundedNumberParameter("Minimum X contact", 2, 0.75, 0, 1).setToolTipText("Contact with X-border of channel divided by Y-thickness should be higher than this value to erase object");
    Parameter[] parameters = new Parameter[]{XThickness, XContactFraction};
    
    public MicrochannelPhaseArtifacts(){}

    
    @Override public ObjectPopulation runPostFilter(StructureObject parent, int childStructureIdx, ObjectPopulation childPopulation) {
        childPopulation.filter(getFilter(XThickness.getValue().doubleValue(), XContactFraction.getValue().doubleValue()));
        return childPopulation;
    }
    public static ObjectPopulation.Filter getFilter(double maxThickness, double minContactFraction) {
        return new ObjectPopulation.Filter() {
            ContactBorder borderX, borderY;
            @Override
            public void init(ObjectPopulation population) {
                this.borderX = new ContactBorder(0, population.getImageProperties(), Border.X).setTolerance(1);
                this.borderY = new ContactBorder(0, population.getImageProperties(), Border.YUp).setTolerance(1);
            }

            @Override
            public boolean keepObject(Object3D object) {
                double xThickness = GeometricalMeasurements.medianThicknessX(object);
                if (xThickness>maxThickness) return true;
                double length = GeometricalMeasurements.getFeretMax(object)/object.getScaleXY();
                borderX.setLimit((int)(length * minContactFraction+0.5));
                if (!borderX.keepObject(object)) return false;
                borderY.setLimit((int)(xThickness * minContactFraction+0.5) );
                return borderY.keepObject(object);
            }
        };
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
