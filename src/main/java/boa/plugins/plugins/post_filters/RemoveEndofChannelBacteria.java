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
import boa.data_structure.RegionPopulation.ContactBorderMask;
import boa.data_structure.RegionPopulation.Filter;
import boa.data_structure.RegionPopulation.Size;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.measurement.GeometricalMeasurements;
import boa.plugins.PostFilter;
import boa.plugins.ToolTip;

/**
 *
 * @author jollion
 */
public class RemoveEndofChannelBacteria implements PostFilter, ToolTip {
    BoundedNumberParameter contactProportion = new BoundedNumberParameter("Contact Proportion", 3, 0.25, 0, 1).setToolTipText("contact = number of pixels in contact with open end of channel / width of cell. If contact > this value, cell will be erased. If value = 0 -> this condition won't be tested");
    BoundedNumberParameter sizeLimit = new BoundedNumberParameter("Minimum Size", 0, 300, 0, null).setToolTipText("If cell is in contact with open end of channel and size (in pixels) is inferior to this value, it will be erased even if the proportion of contact is lower than the threshold. O = no size limit");
    BooleanParameter doNotRemoveIfOnlyOne = new BooleanParameter("Do not remove if only one object", true).setToolTipText("If only one object is present in microchannel, it won't be removed even if it is in contact with microchanel opened-end");
    BoundedNumberParameter contactSidesProportion = new BoundedNumberParameter("Contact with Sides Proportion", 3, 0, 0, 1).setToolTipText("contact with sides = number of pixels in contact with left or right sides of microchannel / width of cell. If contact > this value, cell will be erased. If value = 0 -> this condition won't be tested");
    Parameter[] parameters = new Parameter[]{doNotRemoveIfOnlyOne, contactProportion, sizeLimit, contactSidesProportion};
    
    @Override
    public String getToolTipText() {
        return "Removes trimmed bacteria (in contact with open-end of microchannels)";
    }
    
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
        if (doNotRemoveIfOnlyOne.getSelected() && childPopulation.getRegions().size()==1) return childPopulation;
        final ContactBorder yDown = new ContactBorder(0, parent.getMask(), Border.YDown);
        final ContactBorder xlr = new ContactBorder(0, parent.getMask(), Border.Xlr); 
        final double contactThld=contactProportion.getValue().doubleValue();
        final double contactSideThld = contactSidesProportion.getValue().doubleValue();
        final double sizeLimit  = this.sizeLimit.getValue().doubleValue();
        childPopulation.filter(o->{
            double thick = o.size() / GeometricalMeasurements.getFeretMax(o) ; // estimation of width for rod-shapes objects
            if (contactThld>0) { // contact end
                double contactDown = yDown.getContact(o);
                if (contactDown>0 && sizeLimit>0 && o.size()<sizeLimit) return false;
                double contactYDownNorm = contactDown / thick;
                if (contactYDownNorm>=contactThld) return false;
            }
            if (contactSideThld>0) { // contact on sides
                double contactSides = xlr.getContact(o);
                if (contactSides>0 && sizeLimit>0 && o.size()<sizeLimit) return false;
                double contactXNorm = contactSides / thick;
                if (contactXNorm>=contactSideThld) return false;
            }
            //logger.debug("remove contactDown end of channel o: {}, value: {}({}/{}), remove?{}",getSO(parent, childStructureIdx, o), contactYDownNorm, contactDown, GeometricalMeasurements.medianThicknessX(o), contactYDownNorm >= contactThld);
            return true;
        });
        
        return childPopulation;
    }
    private static StructureObject getSO(StructureObject parent, int childStructureIdx, Region ob ) {
        for (StructureObject o : parent.getChildren(childStructureIdx)) if (o.getRegion()==ob) return o;
        return null;
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }

    
    
}
