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
import configuration.parameters.StructureParameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;

/**
 *
 * @author jollion
 */
public class TrackLength implements Measurement {
    protected StructureParameter structure = new StructureParameter("Structure", 0, false, false);
    protected Parameter[] parameters = new Parameter[]{structure};
    
    public TrackLength() {}
    
    public TrackLength(int structureIdx) {
        structure.setSelectedStructureIdx(structureIdx);
    }
    
    @Override
    public int getCallStructure() {
        return structure.getSelectedStructureIdx();
    }

    @Override
    public boolean callOnlyOnTrackHeads() {
        return true;
    }

    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<>(4);
        res.add(new MeasurementKeyObject("TrackFrameLength", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("TrackObjectCount", structure.getSelectedIndex()));
        return res;
    }

    @Override
    public void performMeasurement(StructureObject object) {
        List<StructureObject> track = StructureObjectUtils.getTrack(object, false);
        object.getMeasurements().setValue("TrackFrameLength", track.get(track.size()-1).getFrame() - track.get(0).getFrame() +1 );
        object.getMeasurements().setValue("TrackObjectCount", track.size() );
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
