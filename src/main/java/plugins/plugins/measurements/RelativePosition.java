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

import configuration.parameters.BooleanParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.Parameter;
import configuration.parameters.StructureParameter;
import configuration.parameters.TextParameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.List;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;

/**
 *
 * @author jollion
 */
public class RelativePosition implements Measurement {
    protected StructureParameter objects = new StructureParameter("Structure", -1, false, false);
    protected StructureParameter reference = new StructureParameter("Reference Structure", -1, true, false);
    BooleanParameter objectMassCenter = new BooleanParameter("Use Mass center for object", true);
    ChoiceParameter refPoint = new ChoiceParameter("Reference Point", new String[]{"Center of Mass", "Geometrical Center", "Corner"}, "Center of Mass", false);
    TextParameter key = new TextParameter("Key Name", "RelativeCoord", false);
    protected Parameter[] parameters = new Parameter[]{objects, reference, objectMassCenter, refPoint, key};
    
    public RelativePosition() {}
    
    public RelativePosition(int objectStructure, int referenceStructure, boolean objectMassCenter, int refPointType) {
        this.objects.setSelectedStructureIdx(objectStructure);
        this.reference.setSelectedStructureIdx(referenceStructure);
        this.objectMassCenter.setSelected(objectMassCenter);
        this.refPoint.setSelectedIndex(refPointType);
    }
    
    public RelativePosition setMeasurementName(String name) {
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
        int structureIdx = objects.getSelectedStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<>();
        res.add(new MeasurementKeyObject(getKey("X"), structureIdx));
        res.add(new MeasurementKeyObject(getKey("Y"), structureIdx));
        res.add(new MeasurementKeyObject(getKey("Z"), structureIdx));
        return res;
    }
    private String getKey(String coord) {
        String ref = key.getValue();
        //if (reference.getSelectedIndex()>=0) ref+=reference.getSelectedStructureIdx();
        return ref+coord;
    }

    @Override
    public void performMeasurement(StructureObject object) {
        StructureObject refObject=null;
        if (reference.getSelectedStructureIdx()>=0) {
            if (object.getExperiment().isChildOf(reference.getSelectedStructureIdx(), objects.getSelectedStructureIdx()))  refObject = object.getParent(reference.getSelectedStructureIdx());
            else {
                int refParent = reference.getFirstCommonParentStructureIdx(objects.getSelectedStructureIdx());
                refObject = StructureObjectUtils.getInclusionParent(object.getObject(), object.getParent(refParent).getChildren(reference.getSelectedStructureIdx()), null);
            }
        }
        if (refObject == null && reference.getSelectedStructureIdx()>=0) return;
        double[] objectCenter = objectMassCenter.getSelected() ? object.getObject().getCenter(object.getParent().getRawImage(object.getStructureIdx()), true) : object.getObject().getCenter(true);
        double[] refPoint;
        if (refObject!=null) {
            if (this.refPoint.getSelectedIndex()==0) refPoint = refObject.getObject().getCenter(refObject.isRoot() ? refObject.getRawImage(refObject.getStructureIdx()) : refObject.getParent().getRawImage(refObject.getStructureIdx()), true);
            else if (this.refPoint.getSelectedIndex()==1) refPoint = refObject.getObject().getCenter(true);
            else { // corner
                refPoint = new double[3];
                refPoint[0] = refObject.getBounds().getxMin() * refObject.getScaleXY();
                refPoint[1] = refObject.getBounds().getyMin() * refObject.getScaleXY();
                refPoint[2] = refObject.getBounds().getzMin() * refObject.getScaleZ();
            }
        } else refPoint = new double[3];
        object.getMeasurements().setValue(getKey("X"), (objectCenter[0]-refPoint[0]));
        object.getMeasurements().setValue(getKey("Y"), (objectCenter[1]-refPoint[1]));
        object.getMeasurements().setValue(getKey("Z"), (objectCenter[2]-refPoint[2]));
        
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
