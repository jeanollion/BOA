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
import utils.ArrayUtil;

/**
 *
 * @author jollion
 */
public class RelativePosition implements Measurement {
    protected StructureParameter objects = new StructureParameter("Structure", -1, false, false);
    protected StructureParameter reference = new StructureParameter("Reference Structure", -1, true, false);
    ChoiceParameter objectCenter= new ChoiceParameter("Object center", new String[]{"Mass", "Geometrical", "From segmentation"}, "Mass", false);
    ChoiceParameter refPoint = new ChoiceParameter("Reference Point", new String[]{"Center of Mass", "Geometrical Center", "Corner"}, "Center of Mass", false);
    TextParameter key = new TextParameter("Key Name", "RelativeCoord", false);
    protected Parameter[] parameters = new Parameter[]{objects, reference, objectCenter, refPoint, key};
    
    public RelativePosition() {}
    
    public RelativePosition(int objectStructure, int referenceStructure, int objectCenterType, int refPointType) {
        this.objects.setSelectedStructureIdx(objectStructure);
        this.reference.setSelectedStructureIdx(referenceStructure);
        this.objectCenter.setSelectedIndex(objectCenterType);
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
        double[] objectCenter=null;
        int ctype= this.objectCenter.getSelectedIndex();
        if (ctype==0) objectCenter = object.getObject().getMassCenter(object.getParent().getRawImage(object.getStructureIdx()), true);
        else if (ctype==1) objectCenter = object.getObject().getGeomCenter(true);
        else if (ctype==2) {
            objectCenter = ArrayUtil.duplicate(object.getObject().getCenter());
            objectCenter[0]*=object.getObject().getScaleXY();
            objectCenter[1]*=object.getObject().getScaleXY();
            if (objectCenter.length>2) objectCenter[2]*=object.getObject().getScaleZ();
        }
        if (objectCenter==null) return;
        double[] refPoint;
        if (refObject!=null) {
            if (this.refPoint.getSelectedIndex()==0) refPoint = refObject.getObject().getMassCenter(refObject.isRoot() ? refObject.getRawImage(refObject.getStructureIdx()) : refObject.getParent().getRawImage(refObject.getStructureIdx()), true);
            else if (this.refPoint.getSelectedIndex()==1) refPoint = refObject.getObject().getGeomCenter(true);
            else { // corner
                refPoint = new double[objectCenter.length];
                refPoint[0] = refObject.getBounds().getxMin() * refObject.getScaleXY();
                refPoint[1] = refObject.getBounds().getyMin() * refObject.getScaleXY();
                if (objectCenter.length>2) refPoint[2] = refObject.getBounds().getzMin() * refObject.getScaleZ();
            }
        } else refPoint = new double[3];
        object.getMeasurements().setValue(getKey("X"), (objectCenter[0]-refPoint[0]));
        object.getMeasurements().setValue(getKey("Y"), (objectCenter[1]-refPoint[1]));
        if (objectCenter.length>2) object.getMeasurements().setValue(getKey("Z"), (objectCenter[2]-refPoint[2]));
        
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
