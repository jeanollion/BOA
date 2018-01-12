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
import dataStructure.objects.Region;
import dataStructure.objects.StructureObject;
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
public class BacteriaFluoMeasurements implements Measurement {
    protected StructureParameter bacteria = new StructureParameter("Bacteria Structure", 1, false, false);
    protected StructureParameter mutation = new StructureParameter("Mutation Structure", 2, false, false);
    protected Parameter[] parameters = new Parameter[]{bacteria, mutation};
    
    
    public BacteriaFluoMeasurements(){}
    
    public BacteriaFluoMeasurements(int bacteriaStructureIdx, int mutationStructureIdx){
        this.bacteria.setSelectedIndex(bacteriaStructureIdx);
        this.mutation.setSelectedIndex(mutationStructureIdx);
    }
    
    public int getCallStructure() {
        return bacteria.getSelectedStructureIdx();
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        int structureIdx = bacteria.getSelectedStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>();
        res.add(new MeasurementKeyObject("BacteriaCenterX", structureIdx));
        res.add(new MeasurementKeyObject("BacteriaCenterY", structureIdx));
        res.add(new MeasurementKeyObject("MeanRFPInBacteria", structureIdx));
        res.add(new MeasurementKeyObject("MeanYFPInBacteria", structureIdx));
        res.add(new MeasurementKeyObject("BacteriaLength", structureIdx));
        res.add(new MeasurementKeyObject("BacteriaArea", structureIdx));
        res.add(new MeasurementKeyObject("MutationCountInBacteria", structureIdx));
        // from tracking
        res.add(new MeasurementKeyObject("TrackErrorPrev", structureIdx));
        res.add(new MeasurementKeyObject("TrackErrorNext", structureIdx));
        res.add(new MeasurementKeyObject("SizeIncrement", structureIdx));
        res.add(new MeasurementKeyObject("TrackErrorSizeIncrement", structureIdx));
        return res;
    }

    public void performMeasurement(StructureObject object) {
        Region bactObject = object.getObject();
        Image bactImage = object.getRawImage(bacteria.getSelectedIndex());
        Image mutImage = object.getRawImage(mutation.getSelectedIndex());
        BoundingBox parentOffset = object.getParent().getBounds();
        double[] center=bactObject.getMassCenter(bactImage, true);
        center[0]-=parentOffset.getxMin()*object.getScaleXY();
        center[1]-=parentOffset.getyMin()*object.getScaleXY();
        //if (object.getTimePoint()==0) logger.debug("object: {} center: {}, parentOffset: {}, objectoffset: {} bactImageOffset: {}, mutImageOffset: {}", object, center, parentOffset, object.getBounds(), bactImage.getBoundingBox(), mutImage.getBoundingBox());
        object.getMeasurements().setValue("BacteriaCenterX", center[0]);
        object.getMeasurements().setValue("BacteriaCenterY", center[1]);
        object.getMeasurements().setValue("MeanRFPInBacteria", BasicMeasurements.getMeanValue(bactObject, bactImage, true));
        object.getMeasurements().setValue("MeanYFPInBacteria", BasicMeasurements.getMeanValue(bactObject, mutImage, true));
        object.getMeasurements().setValue("BacteriaLength", GeometricalMeasurements.getFeretMax(bactObject));
        object.getMeasurements().setValue("BacteriaArea", GeometricalMeasurements.getVolume(bactObject));
        int includedMutations = ObjectInclusionCount.count(object, mutation.getSelectedIndex(), 0, false);
        object.getMeasurements().setValue("MutationCountInBacteria", includedMutations);
        
        List<StructureObject> mutList = object.getChildren(mutation.getSelectedIndex()); // return included mutations
        if (includedMutations!=mutList.size()) logger.warn("Error Mutation count in: {}: {} vs: {}", object, includedMutations, mutList.size());
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
