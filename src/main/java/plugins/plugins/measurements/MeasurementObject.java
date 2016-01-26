/*
 * Copyright (C) 2016 jollion
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
import configuration.parameters.PluginParameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.StructureParameter;
import dataStructure.objects.StructureObject;
import java.util.ArrayList;
import java.util.List;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;
import plugins.ObjectFeature;
import plugins.objectFeature.ObjectFeatureCore;
import plugins.objectFeature.ObjectFeatureParameter;
import plugins.objectFeature.ObjectFeatureWithCore;

/**
 *
 * @author jollion
 */
public class MeasurementObject implements Measurement {
    StructureParameter structure = new StructureParameter("Structure", -1, false, false);
    SimpleListParameter<ObjectFeatureParameter> features = new SimpleListParameter<ObjectFeatureParameter>("Features", -1, ObjectFeatureParameter.class);
    Parameter[] parameters = new Parameter[]{structure, features};
    public int getCallStructure() {
        return structure.getParentStructureIdx();
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res=  new ArrayList<MeasurementKey>(features.getChildCount());
        for (ObjectFeatureParameter ofp : features.getActivatedChildren()) res.add(new MeasurementKeyObject(ofp.getKeyName(), structure.getSelectedIndex()));
        return res;
    }

    public void performMeasurement(StructureObject object, List<StructureObject> modifiedObjects) {
        int structureIdx = structure.getSelectedIndex();
        ArrayList<ObjectFeatureCore> cores = new ArrayList<ObjectFeatureCore>();
        for (ObjectFeatureParameter ofp : features.getActivatedChildren()) {
            ObjectFeature f = ofp.getFeature();
            if (f!=null) {
                f.setUp(object, structureIdx);
                if (f instanceof ObjectFeatureWithCore) ((ObjectFeatureWithCore)f).setUpOrAddCore(cores);
                for (StructureObject o : object.getChildObjects(structureIdx)) {
                    double m = f.performMeasurement(o.getObject(), null); // no additional offset from object to direct parent
                    o.getMeasurements().setValue(ofp.getKeyName(), m);
                    modifiedObjects.add(o);
                }
            }
        }
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
