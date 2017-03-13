/*
 * Copyright (C) 2017 jollion
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
package plugins.plugins.measurements;

import configuration.parameters.Parameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.StructureParameter;
import configuration.parameters.TextParameter;
import dataStructure.objects.StructureObject;
import java.util.ArrayList;
import java.util.List;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;

/**
 *
 * @author jollion
 */
public class GetAttribute implements Measurement {
    StructureParameter structure = new StructureParameter("Structure", -1, false, false);
    SimpleListParameter<TextParameter> attributes = new SimpleListParameter("Attributes", new TextParameter("Attribute Key", "", false));
    Parameter[] parameters = new Parameter[]{structure, attributes};
    
    public GetAttribute() {}
    
    public GetAttribute(int structureIdx) {
        structure.setSelectedStructureIdx(structureIdx);
    }
    
    public GetAttribute addAttributes(String... attributeNames) {
        for (String s : attributeNames) {
            TextParameter tp = attributes.createChildInstance();
            tp.setValue(s);
            attributes.getChildren().add(tp);
        }
        return this;
    }
    
    @Override
    public int getCallStructure() {
        return structure.getSelectedStructureIdx();
    }

    @Override
    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        List<MeasurementKey> res = new ArrayList<>(attributes.getChildCount());
        for (TextParameter att : attributes.getChildren()) res.add(new MeasurementKeyObject(att.getValue(), structure.getSelectedStructureIdx()));
        return res;
    }
    
    

    @Override
    public void performMeasurement(StructureObject object) {
        for (TextParameter att : attributes.getChildren()) {
            String key = att.getValue();
            Object value = object.getAttribute(key);
            if (value ==null) continue;
            if (value instanceof Number) object.getMeasurements().setValue(key, (Number)value);
            else if (value instanceof double[]) object.getMeasurements().setValue(key, (double[])value);
            else if (value instanceof String) object.getMeasurements().setValue(key, (String)value);
            else if (value instanceof Boolean) object.getMeasurements().setValue(key, (Boolean)value);
        }
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
