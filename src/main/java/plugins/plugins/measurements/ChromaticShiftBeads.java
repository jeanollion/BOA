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
package plugins.plugins.measurements;

import configuration.parameters.Parameter;
import configuration.parameters.StructureParameter;
import configuration.parameters.TextParameter;
import dataStructure.objects.StructureObject;
import java.util.ArrayList;
import java.util.List;
import measurement.GeometricalMeasurements;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;

/**
 *
 * @author jollion
 */
public class ChromaticShiftBeads implements Measurement{
    protected StructureParameter structure = new StructureParameter("Structure 1", 0, false, false);
    protected StructureParameter structure2 = new StructureParameter("Structure 2", 1, false, false);
    protected Parameter[] parameters = new Parameter[]{structure, structure2};

    public ChromaticShiftBeads() {}
    
    public ChromaticShiftBeads(int structureIdx1, int structureIdx2) {
       structure.setSelectedIndex(structureIdx1);
       structure2.setSelectedIndex(structureIdx2);
    }
    
    public int getCallStructure() {
        return structure.getFirstCommonParentStructureIdx(structure2.getSelectedIndex());
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>(1);
        res.add(new MeasurementKeyObject("dXPix", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("dYPix", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("dZSlice", structure.getSelectedIndex()));
        return res;
    }

    public void performMeasurement(StructureObject object, List<StructureObject> modifiedObjects) {
        
        List<StructureObject> objects1 = object.getChildren(structure.getSelectedIndex());
        List<StructureObject> objects2 = object.getChildren(structure2.getSelectedIndex());
        logger.debug("chromatic shift: s1: {}, count: {}, s2: {}, count: {}", structure.getSelectedIndex(), objects1.size(), structure2.getSelectedIndex(), objects2.size());
        // for each object from s1, get closest object from s2
        for (StructureObject o1 : objects1) {
            StructureObject closest = null;
            double dist = Double.MAX_VALUE;
            for (StructureObject o2 : objects2) {
                double d = GeometricalMeasurements.getDistance(o1.getObject(), o2.getObject());
                if (d<dist) {
                    dist = d;
                    closest = o2;
                }
            }
            if (closest !=null) {
                double[] c1 = o1.getObject().getCenter(object.getRawImage(structure.getSelectedIndex()), false);
                double[] c2 = closest.getObject().getCenter(object.getRawImage(structure2.getSelectedIndex()), false);
                o1.getMeasurements().setValue("dXPix", c2[0]-c1[0]);
                o1.getMeasurements().setValue("dYPix", c2[1]-c1[1]);
                o1.getMeasurements().setValue("dZSlice", c2[2]-c1[2]);
                logger.debug("Chromatic Shift: o1: {}, closest: {} (dist: {}), dX: {}, dY: {}, dZ: {}", o1, closest, dist, c2[0]-c1[0], c2[1]-c1[1], c2[2]-c1[2]);
                modifiedObjects.add(o1);
            }
        }
        
    }

    public Parameter[] getParameters() {
        return parameters;
    }
}
