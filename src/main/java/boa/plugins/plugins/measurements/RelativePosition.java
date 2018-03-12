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

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
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
import boa.utils.ArrayUtil;
import boa.utils.geom.Point;

/**
 *
 * @author jollion
 */
public class RelativePosition implements Measurement {
    protected StructureParameter objects = new StructureParameter("Structure", -1, false, false);
    protected StructureParameter reference = new StructureParameter("Reference Structure", -1, true, false);
    ChoiceParameter objectCenter= new ChoiceParameter("Object Point", new String[]{"Mass", "Geometrical", "From segmentation", "Upper Left Corner"}, "Mass", false);
    ChoiceParameter refPoint = new ChoiceParameter("Reference Point", new String[]{"Center of Mass", "Geometrical Center", "Upper Left Corner"}, "Center of Mass", false).setToolTipText("If no reference structure is selected the reference point will automatically be the upper left corner of the image");;
    TextParameter key = new TextParameter("Key Name", "RelativeCoord", false);
    //ConditionalParameter refCond = new ConditionalParameter(reference); structure param not actionable...
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
                refObject = StructureObjectUtils.getInclusionParent(object.getRegion(), object.getParent(refParent).getChildren(reference.getSelectedStructureIdx()), null);
            }
        }
        if (refObject == null && reference.getSelectedStructureIdx()>=0) return;
        Point objectCenter=null;
        int ctype= this.objectCenter.getSelectedIndex();
        switch (ctype) {
            case 0:
                objectCenter = object.getRegion().getMassCenter(object.getParent().getRawImage(object.getStructureIdx()), false); // mass center
                break;
            case 1:
                objectCenter = object.getRegion().getGeomCenter(false); // geom center
                break;
            case 2: // from segmentation
                objectCenter = object.getRegion().getCenter().duplicate();
                break;
            case 3: // corner
                objectCenter = Point.asPoint(object.getRegion().getBounds());
                break;
            default:
                break;
        }
        if (objectCenter==null) return;
        objectCenter.multiply(object.getRegion().getScaleXY(), 0);
        objectCenter.multiply(object.getRegion().getScaleXY(), 1);
        objectCenter.multiply(object.getRegion().getScaleZ(), 2);
        Point refPoint=null;
        if (refObject!=null) {
            switch (this.refPoint.getSelectedIndex()) {
                case 0:
                    refPoint = refObject.getRegion().getMassCenter(refObject.isRoot() ? refObject.getRawImage(refObject.getStructureIdx()) : refObject.getParent().getRawImage(refObject.getStructureIdx()), false);
                    break;
                case 1:
                    refPoint = refObject.getRegion().getGeomCenter(false);
                    break;
                default:
                    // corner
                    objectCenter = Point.asPoint(refObject.getBounds());
                    break;
            }
        } else refPoint = new Point(0, 0, 0); // absolute
        refPoint.multiply(object.getRegion().getScaleXY(), 0);
        refPoint.multiply(object.getRegion().getScaleXY(), 1);
        refPoint.multiply(object.getRegion().getScaleZ(), 2);
        object.getMeasurements().setValue(getKey("X"), (objectCenter.get(0)-refPoint.get(0)));
        object.getMeasurements().setValue(getKey("Y"), (objectCenter.get(1)-refPoint.get(1)));
        if (objectCenter.numDimensions()>2) object.getMeasurements().setValue(getKey("Z"), (objectCenter.get(2)-refPoint.getWithDimCheck(2)));
        
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
