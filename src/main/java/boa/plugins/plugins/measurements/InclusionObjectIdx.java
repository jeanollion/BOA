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
package boa.plugins.plugins.measurements;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.configuration.parameters.TextParameter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import java.util.ArrayList;
import java.util.List;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;

/**
 *
 * @author jollion
 */
public class InclusionObjectIdx implements Measurement {
    protected StructureParameter objects = new StructureParameter("Structure", -1, false, false);
    protected StructureParameter reference = new StructureParameter("Reference Structure", -1, true, false);
    TextParameter key = new TextParameter("Key Name: ", "RelativeCoord", false);
    protected Parameter[] parameters = new Parameter[]{objects, reference, key};
    
    public InclusionObjectIdx() {}
    
    public InclusionObjectIdx(int structureIdx, int referenceStructureIdx) {
        this.objects.setSelectedStructureIdx(structureIdx);
        this.reference.setSelectedStructureIdx(referenceStructureIdx);
        
    }
    
    public InclusionObjectIdx setMeasurementName(String name) {
        this.key.setValue(name);
        return this;
    }
    
    @Override
    public int getCallStructure() {
        return objects.getSelectedStructureIdx();
    }

    @Override
    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<>();
        res.add(new MeasurementKeyObject(key.getValue(), objects.getSelectedStructureIdx()));
        return res;
    }

    @Override
    public void performMeasurement(StructureObject object) {
        StructureObject refObject;
        if (object.getExperiment().isChildOf(reference.getSelectedStructureIdx(), objects.getSelectedStructureIdx()))  refObject = object.getParent(reference.getSelectedStructureIdx());
        else {
            int refParent = reference.getFirstCommonParentStructureIdx(objects.getSelectedStructureIdx());
            refObject = StructureObjectUtils.getInclusionParent(object.getRegion(), object.getParent(refParent).getChildren(reference.getSelectedStructureIdx()), null);
        }
        if (refObject == null) return;
        object.getMeasurements().setValue(key.getValue(), refObject.getIdx());
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
