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

import boa.configuration.parameters.BooleanParameter;
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
import boa.measurement.GeometricalMeasurements;
import boa.plugins.PostFilter;

/**
 *
 * @author jollion
 */
public class RemoveEndofChannelBacteria implements PostFilter {
    BoundedNumberParameter contactProportion = new BoundedNumberParameter("Contact Proportion", 3, 0.9, 0, 1).setToolTipText("contact = number of pixels in contact with open end of channel / median width of cell. If contact > this value, cell will be erased. If value = 0 -> this condition won't be tested");
    BoundedNumberParameter sizeLimit = new BoundedNumberParameter("Minimum Size", 0, 300, 0, null).setToolTipText("If cell is in contact with open end of channel and size (in pixels) is inferior to this value, it will be erased. O = no size limit");
    BooleanParameter doNotRemoveIfOnlyOne = new BooleanParameter("Do not remove if only one object", true).setToolTipText("If only one object is present in microchannel, it won't be removed");
    BoundedNumberParameter contactSidesProportion = new BoundedNumberParameter("Contact with Sides Proportion", 3, 0.6, 0, 1).setToolTipText("contact with sides = number of pixels in contact with left or right sides of microchannel / min(median size of cell along Y, median size of cell along X) . If contact > this value, cell will be erased. If value = 0 -> this condition won't be tested");
    Parameter[] parameters = new Parameter[]{doNotRemoveIfOnlyOne, contactProportion, sizeLimit, contactSidesProportion};
    
    public RemoveEndofChannelBacteria(){}
    public RemoveEndofChannelBacteria setContactProportion(double prop) {
        this.contactProportion.setValue(prop);
        return this;
    }
    public RemoveEndofChannelBacteria setContactSidesProportion(double prop) {
        this.contactSidesProportion.setValue(prop);
        return this;
    }
    public RemoveEndofChannelBacteria setSizeLimit(double size) {
        this.sizeLimit.setValue(size);
        return this;
    }
    
    @Override public RegionPopulation runPostFilter(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        final ContactBorder yDown = new ContactBorder(0, childPopulation.getImageProperties(), Border.YDown);
        final ContactBorder xlr = new ContactBorder(0, childPopulation.getImageProperties(), Border.Xlr); 
        final double contactThld=contactProportion.getValue().doubleValue();
        final double contactSideThld = contactSidesProportion.getValue().doubleValue();
        final double sizeLimit  = this.sizeLimit.getValue().doubleValue();
        childPopulation.filter(o->{
            if (doNotRemoveIfOnlyOne.getSelected() && childPopulation.getRegions().size()==1) return true;
            double thickX = GeometricalMeasurements.medianThicknessX(o);
            if (contactThld>0) {
                double contactDown = yDown.getContact(o);
                if (contactDown>0 && sizeLimit>0 && o.getSize()<sizeLimit) return false;
                double contactYDownNorm = contactDown / thickX;
                if (contactYDownNorm>=contactThld) return false;
            }
            if (contactSideThld>0) { // end of channel : bacteria going out of channel -> contact with
                double contactSides = xlr.getContact(o);
                if (contactSides>0 && sizeLimit>0 && o.getSize()<sizeLimit) return false;
                double thickY = GeometricalMeasurements.medianThicknessY(o);
                double contactXNorm = contactSides / Math.min(thickX, thickY);
                if (contactXNorm>=contactSideThld) return false;
            }
            //logger.debug("remove contactDown end of channel o: {}, value: {}({}/{}), remove?{}",getSO(parent, childStructureIdx, o), contactYDownNorm, contactDown, GeometricalMeasurements.medianThicknessX(o), contactYDownNorm >= contactThld);
            return true;
        });
        
        return childPopulation;
    }
    private static StructureObject getSO(StructureObject parent, int childStructureIdx, Region ob ) {
        for (StructureObject o : parent.getChildren(childStructureIdx)) if (o.getObject()==ob) return o;
        return null;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
