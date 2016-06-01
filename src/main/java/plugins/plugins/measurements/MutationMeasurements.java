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
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.Image;
import java.util.ArrayList;
import java.util.List;
import measurement.BasicMeasurements;
import measurement.GeometricalMeasurements;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;

/**
 *
 * @author jollion
 */
public class MutationMeasurements implements Measurement {
    protected StructureParameter bacteria = new StructureParameter("Bacteria Structure", 1, false, false);
    protected StructureParameter mutation = new StructureParameter("Mutation Structure", 2, false, false);
    protected Parameter[] parameters = new Parameter[]{bacteria, mutation};
    
    
    public MutationMeasurements(){}
    
    public MutationMeasurements(int bacteriaStructureIdx, int mutationStructureIdx){
        this.bacteria.setSelectedIndex(bacteriaStructureIdx);
        this.mutation.setSelectedIndex(mutationStructureIdx);
    }
    
    public int getCallStructure() {
        return mutation.getSelectedStructureIdx();
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        int structureIdx = mutation.getSelectedStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>();
        res.add(new MeasurementKeyObject("IsTrackHead", structureIdx));
        res.add(new MeasurementKeyObject("ParentBacteriaIdx", structureIdx));
        
        return res;
    }

    public void performMeasurement(StructureObject object, List<StructureObject> modifiedObjects) {
        object.getMeasurements().setValue("IsTrackHead", object.isTrackHead());
        /*Object3D mutObject = object.getObject();
        Image mutImage = object.getRawImage(mutation.getSelectedIndex());
        BoundingBox parentOffset = object.getParent().getBounds();
        double[] center = mutObject.getCenter(mutImage, true);
        center[0]-=parentOffset.getxMin()*object.getScaleXY();
        center[1]-=parentOffset.getyMin()*object.getScaleXY();
        object.getMeasurements().setValue("MutationCenterX", center[0]);
        object.getMeasurements().setValue("MutationCenterY", center[1]);
        object.getMeasurements().setValue("MeanYFPInMutation", BasicMeasurements.getMeanValue(mutObject, mutImage, true));
        object.getMeasurements().setValue("MutationArea", GeometricalMeasurements.getVolume(mutObject));
        */
        StructureObject parentBacteria;
        if (bacteria.getSelectedStructureIdx()==object.getParent().getStructureIdx()) parentBacteria = object.getParent();
        else parentBacteria = StructureObjectUtils.getInclusionParent(object.getObject(), object.getParent().getChildren(bacteria.getSelectedStructureIdx()), null);
        if (parentBacteria==null) {
            //logger.warn("No bacteria parent found for object: {}", object);
        } else object.getMeasurements().setValue("ParentBacteriaIdx", parentBacteria.getIdx());
        modifiedObjects.add(object);
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
