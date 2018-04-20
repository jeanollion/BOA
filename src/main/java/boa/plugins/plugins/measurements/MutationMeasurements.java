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

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import java.util.ArrayList;
import java.util.List;
import boa.measurement.BasicMeasurements;
import boa.measurement.GeometricalMeasurements;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;

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

    public void performMeasurement(StructureObject object) {
        object.getMeasurements().setValue("IsTrackHead", object.isTrackHead());
        /*Region mutObject = object.getRegion();
        Image mutImage = object.getRawImage(mutation.getSelectedIndex());
        BoundingBox parentOffset = object.getParent().getBounds();
        double[] center = mutObject.getCenter(mutImage, true);
        center[0]-=parentOffset.getxMin()*object.getScaleXY();
        center[1]-=parentOffset.getyMin()*object.getScaleXY();
        object.getMeasurements().setStringValue("MutationCenterX", center[0]);
        object.getMeasurements().setStringValue("MutationCenterY", center[1]);
        object.getMeasurements().setStringValue("MeanYFPInMutation", BasicMeasurements.getMeanValue(mutObject, mutImage, true));
        object.getMeasurements().setStringValue("MutationArea", GeometricalMeasurements.getVolume(mutObject));
        */
        StructureObject parentBacteria;
        if (bacteria.getSelectedStructureIdx()==object.getParent().getStructureIdx()) parentBacteria = object.getParent();
        else parentBacteria = StructureObjectUtils.getInclusionParent(object.getRegion(), object.getParent().getChildren(bacteria.getSelectedStructureIdx()), null);
        if (parentBacteria==null) {
            //logger.warn("No bacteria parent found for object: {}", object);
        } else object.getMeasurements().setValue("ParentBacteriaIdx", parentBacteria.getIdx());
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
