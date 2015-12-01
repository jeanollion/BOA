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
public class MeasurementBacteria implements Measurement {
    protected StructureParameter structure = new StructureParameter("Bacteria Structure", -1, false, false);
    protected StructureParameter mutation = new StructureParameter("Mutation Structure", -1, false, false);
    protected Parameter[] parameters = new Parameter[]{structure, mutation};
    public int getCallStructure() {
        return structure.getSelectedIndex();
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>();
        res.add(new MeasurementKeyObject("Area(units)", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("FeretMax(units)", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("FeretMin(units)", structure.getSelectedIndex()));
        //res.add(new MeasurementKeyObject("Squeleton", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("MutationCount", structure.getSelectedIndex()));
        return res;
    }

    public void performMeasurement(StructureObject object, List<StructureObject> modifiedObjects) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
