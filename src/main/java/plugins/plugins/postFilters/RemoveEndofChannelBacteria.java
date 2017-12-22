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

import configuration.parameters.BooleanParameter;
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
public class RemoveEndofChannelBacteria implements PostFilter {
    BoundedNumberParameter contactProportion = new BoundedNumberParameter("Contact Proportion", 3, 0.9, 0, 1).setToolTipText("contact = number of pixels in contact with end of channel / median width of cell. If contact > this value, cell will be erased");
    BoundedNumberParameter sizeLimit = new BoundedNumberParameter("Minimum Size", 0, 300, 0, null).setToolTipText("If cell is in contact with border and size (in pixels) is inferior to this value, it will be erased. O = no size limit");
    BooleanParameter doNotRemoveIfOnlyOne = new BooleanParameter("Do not remove if only one object", true);
    Parameter[] parameters = new Parameter[]{doNotRemoveIfOnlyOne, contactProportion, sizeLimit};
    
    public RemoveEndofChannelBacteria(){}
    public RemoveEndofChannelBacteria setContactProportion(double prop) {
        this.contactProportion.setValue(prop);
        return this;
    }
    public RemoveEndofChannelBacteria setSizeLimit(double size) {
        this.sizeLimit.setValue(size);
        return this;
    }
    
    @Override public ObjectPopulation runPostFilter(StructureObject parent, int childStructureIdx, ObjectPopulation childPopulation) {
        final ContactBorder c = new ContactBorder(0, childPopulation.getImageProperties(), Border.YDown);
        final double contactThld=contactProportion.getValue().doubleValue();
        final double sizeLimit  = this.sizeLimit.getValue().doubleValue();
        childPopulation.filter(o->{
            if (doNotRemoveIfOnlyOne.getSelected() && childPopulation.getObjects().size()==1) return true;
            double contact = c.getContact(o);
            if (contact==0) return true;
            if (contact>0 && sizeLimit>0 && o.getSize()<sizeLimit) {
                //logger.debug("remove small object in contact with end of channel :{}", getSO(parent, childStructureIdx, o));
                return false;
            }
            double contactNorm = contact / GeometricalMeasurements.medianThicknessX(o);
            //logger.debug("remove contact end of channel o: {}, value: {}({}/{}), remove?{}",getSO(parent, childStructureIdx, o), contactNorm, contact, GeometricalMeasurements.medianThicknessX(o), contactNorm >= contactThld);
            return (contactNorm < contactThld); // keep object
        });
        
        return childPopulation;
    }
    private static StructureObject getSO(StructureObject parent, int childStructureIdx, Object3D ob ) {
        for (StructureObject o : parent.getChildren(childStructureIdx)) if (o.getObject()==ob) return o;
        return null;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
