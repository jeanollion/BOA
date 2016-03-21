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

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.Parameter;
import configuration.parameters.StructureParameter;
import configuration.parameters.TextParameter;
import dataStructure.objects.StructureObject;
import image.BoundingBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;

/**
 *
 * @author jollion
 */
public class BacteriaLineageIndex implements Measurement {
    protected StructureParameter structure = new StructureParameter("Bacteria Structure", 1, false, false);
    protected TextParameter keyName = new TextParameter("Lineage Index Name", "LineageIndex", false);
    protected Parameter[] parameters = new Parameter[]{structure, keyName};
    public static char[] lineageName = new char[]{'H', 'T'};
    
    public BacteriaLineageIndex() {}
    
    public BacteriaLineageIndex(int structureIdx) {
        structure.setSelectedIndex(structureIdx);
    }
    
    public BacteriaLineageIndex(int structureIdx, String keyName) {
        structure.setSelectedIndex(structureIdx);
        this.keyName.setValue(keyName);
    }
    
    @Override
    public int getCallStructure() {
        return structure.getParentStructureIdx();
    }
    
    @Override
    public boolean callOnlyOnTrackHeads() {
        return true;
    }
    
    @Override
    public void performMeasurement(StructureObject parentTrackHead, List<StructureObject> modifiedObjects) {
        int bIdx = structure.getSelectedIndex();
        String key = this.keyName.getValue();
        ArrayList<StructureObject> bacteria = parentTrackHead.getChildren(bIdx);
        int trackHeadIdx = 0;
        for (StructureObject o : bacteria) {
            o.getMeasurements().setValue(key, getTrackHeadName(trackHeadIdx++));
            modifiedObjects.add(o);
        }
        while(parentTrackHead.getNext()!=null) {
            parentTrackHead = parentTrackHead.getNext();
            bacteria = parentTrackHead.getChildren(bIdx);
            for (StructureObject o : bacteria) {
                if (o.getPrevious()==null) o.getMeasurements().setValue(key, getTrackHeadName(trackHeadIdx++));
                else if (o.isTrackHead()) o.getMeasurements().setValue(key, o.getPrevious().getMeasurements().getValueAsString(key)+lineageName[0]);
                else o.getMeasurements().setValue(key, o.getPrevious().getMeasurements().getValueAsString(key)+lineageName[1]);
                modifiedObjects.add(o);
            }
        }
    }
    
    @Override 
    public ArrayList<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>(1);
        res.add(new MeasurementKeyObject(keyName.getValue(), structure.getSelectedIndex()));
        return res;
    }
    
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    private static String getTrackHeadName(int trackHeadIdx) {
        char c = (char)(trackHeadIdx%26 + 65); //ASCII UPPER CASE +65
        int mod = trackHeadIdx/26;
        if (mod>0) return String.valueOf(c)+mod;
        else return String.valueOf(c);
    }
}
