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

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.Parameter;
import configuration.parameters.StructureParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.Image;
import image.ImageMask;
import java.util.ArrayList;
import java.util.List;
import measurement.BasicMeasurements;
import measurement.GeometricalMeasurements;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SimpleTrackMeasurements implements Measurement {
    protected StructureParameter structure = new StructureParameter("Structure", -1, false, false);
    protected Parameter[] parameters = new Parameter[]{structure};
    
    
    public SimpleTrackMeasurements(){}
    
    public SimpleTrackMeasurements(int structure){
        this.structure.setSelectedIndex(structure);
    }
    
    @Override public int getCallStructure() {
        return structure.getSelectedStructureIdx();
    }

    @Override public boolean callOnlyOnTrackHeads() {
        return false;
    }

    @Override public List<MeasurementKey> getMeasurementKeys() {
        int structureIdx = structure.getSelectedStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<>();
        res.add(new MeasurementKeyObject("IsTrackHead", structureIdx));
        res.add(new MeasurementKeyObject("TrackHeadIndices", structureIdx));
        
        // only computed for trackHeads
        res.add(new MeasurementKeyObject("TrackLength", structureIdx));
        res.add(new MeasurementKeyObject("TrackObjectCount", structureIdx));

        return res;
    }

    @Override public void performMeasurement(StructureObject object) {
        object.getMeasurements().setValue("IsTrackHead", object.isTrackHead());
        object.getMeasurements().setValue("TrackHeadIndices", StructureObjectUtils.getIndices(object.getTrackHead()));
        if (object.isTrackHead()) {
            List<StructureObject> track = StructureObjectUtils.getTrack(object, false);
            object.getMeasurements().setValue("TrackLength", track.get(track.size()-1).getFrame() - object.getFrame()+1);
            object.getMeasurements().setValue("TrackObjectCount", track.size());
        }
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }
        
}
