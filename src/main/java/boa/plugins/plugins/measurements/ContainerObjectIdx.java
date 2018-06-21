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
package boa.plugins.plugins.measurements;

import boa.configuration.experiment.Experiment;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ParameterUtils;
import boa.configuration.parameters.ParentStructureParameter;
import boa.configuration.parameters.StructureParameter;
import boa.configuration.parameters.TextParameter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import java.util.ArrayList;
import java.util.List;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;
import boa.plugins.ToolTip;

/**
 *
 * @author jollion
 */
public class ContainerObjectIdx implements Measurement, ToolTip {
    protected StructureParameter objects = new StructureParameter("Objects", -1, false, false).setToolTipText("Objects to perform measurement on");
    protected StructureParameter reference = new StructureParameter("Container Object", -1, false, false).setToolTipText("Objects type that contain <em>Objects</em>");
    TextParameter key = new TextParameter("Key Name: ", "ContainerObjectIdx", false);
    protected Parameter[] parameters = new Parameter[]{objects, reference, key};
    @Override
    public String getToolTipText() {
        return "For each object A of type defined in <em>Objects</em>, computes the index of the object B of type defined in <em>Container Object</em> that contains A, if B exists";
    }
    
    public ContainerObjectIdx() {
        reference.addListener(p->{
            Experiment xp = ParameterUtils.getExperiment(p);
            if (xp==null) return;
            int sIdx = ((StructureParameter)p).getSelectedStructureIdx();
            if (sIdx>=0) key.setValue(xp.getStructure(sIdx).getName()+"Idx");
        });
    }
    
    public ContainerObjectIdx(int structureIdx, int referenceStructureIdx) {
        this();
        this.objects.setSelectedStructureIdx(structureIdx);
        this.reference.setSelectedStructureIdx(referenceStructureIdx);
        
    }
    
    public ContainerObjectIdx setMeasurementName(String name) {
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
