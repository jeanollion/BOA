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
package boa.plugins.plugins.measurements;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
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
public class BacteriaMeasurementsWoleMC implements Measurement {
    protected StructureParameter bacteria = new StructureParameter("Bacteria Structure", -1, false, false);
    protected StructureParameter mutation = new StructureParameter("Mutation Structure", -1, false, false);
    protected Parameter[] parameters = new Parameter[]{bacteria, mutation};
    
    
    public BacteriaMeasurementsWoleMC(){}
    
    public BacteriaMeasurementsWoleMC(int bacteriaStructureIdx, int mutationStructureIdx){
        this.bacteria.setSelectedIndex(bacteriaStructureIdx);
        this.mutation.setSelectedIndex(mutationStructureIdx);
    }
    
    public int getCallStructure() {
        return bacteria.getParentStructureIdx();
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        int parentIdx = bacteria.getParentStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>();
        res.add(new MeasurementKeyObject("BacteriaMeanIntensity", parentIdx));
        res.add(new MeasurementKeyObject("MutationMeanIntensity", parentIdx));
        res.add(new MeasurementKeyObject("MutationSNR", parentIdx));
        res.add(new MeasurementKeyObject("MutationForeground", parentIdx));
        //res.add(new MeasurementKeyObject("FeretMin(units)", structure.getSelectedIndex()));
        //res.add(new MeasurementKeyObject("Squeleton", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("MutationCount", parentIdx));
        res.add(new MeasurementKeyObject("BacteriaCount", parentIdx));
        res.add(new MeasurementKeyObject("M_MutationCount", parentIdx));
        res.add(new MeasurementKeyObject("M_FeretMax", parentIdx));
        res.add(new MeasurementKeyObject("M_MeanValue", parentIdx));
        res.add(new MeasurementKeyObject("M_Volume", parentIdx));
        //res.add(new MeasurementKeyObject("MutationForegroundSingle", mutation.getSelectedIndex()));
        return res;
    }

    public void performMeasurement(StructureObject object) {
        // measurements on microchannels
        Region o = object.getObject();
        long t0 = System.currentTimeMillis();
        object.getMeasurements().setValue("MutationCount", ObjectInclusionCount.count(object, mutation.getSelectedIndex(), 0, true));
        object.getMeasurements().setValue("BacteriaCount", ObjectInclusionCount.count(object, bacteria.getSelectedIndex(), 0, false));
        long t1 = System.currentTimeMillis();
        Image bactImage = object.getRawImage(bacteria.getSelectedIndex());
        Image mutImage = object.getRawImage(mutation.getSelectedIndex());
        long t2 = System.currentTimeMillis();
        //BoundingBox bounds = o.getBounds().duplicate();
        //o.translate(bounds, true);
        /*logger.debug("mc bb:{}, mask bb: {}, image bb: {}", o.getBounds(), o.getMask().getBoundingBox(), bactImage.getBoundingBox());
        int xMax=0, yMax=0;
        for (Voxel v : o.getVoxels()) {
            if (v.x>xMax) xMax=v.x;
            if (v.y>yMax) yMax=v.y;
        }
        logger.debug("vox xMax: {}, yMax: {}", xMax, yMax);*/
        object.getMeasurements().setValue("BacteriaMeanIntensity", BasicMeasurements.getMeanValue(o, bactImage, true));
        List<StructureObject> mutList = object.getChildren(mutation.getSelectedIndex());
        if (mutList.isEmpty()) object.getMeasurements().setValue("MutationMeanIntensity", BasicMeasurements.getMeanValue(o, mutImage, true));
        if (!mutList.isEmpty()) {
            List<Voxel> mutVox = new ArrayList<Voxel>();
            for (StructureObject m : mutList) mutVox.addAll(m.getObject().getVoxels());
            double[] snr = BasicMeasurements.getSNR(mutVox, o.getVoxels(), mutImage, true);
            object.getMeasurements().setValue("MutationSNR", snr[0]);
            object.getMeasurements().setValue("MutationForeground", snr[1]);    
            object.getMeasurements().setValue("MutationMeanIntensity", snr[2]);    
        }
        long t3 = System.currentTimeMillis();
        /*for (StructureObject m : mutList) {
              m.getMeasurements().setValue("MutationForegroundSingle", BasicMeasurements.getMeanValue(m.getObject().getVoxels(), mutImage));
              modifiedObjects.add(m);
        }*/
        long t4=t3, t5=t3;
        List<StructureObject> bactList = object.getChildren(bacteria.getSelectedIndex());
        if (!bactList.isEmpty()) {
            StructureObject mm = bactList.get(0);
            object.getMeasurements().setValue("M_MutationCount", ObjectInclusionCount.count(mm, mutList, 0, true));
            t4 = System.currentTimeMillis();
            object.getMeasurements().setValue("M_FeretMax", GeometricalMeasurements.getFeretMax(mm.getObject()));
            object.getMeasurements().setValue("M_MeanValue", BasicMeasurements.getMeanValue(mm.getObject(), bactImage, true));
            object.getMeasurements().setValue("M_Volume", GeometricalMeasurements.getVolume(mm.getObject()));
            t5 = System.currentTimeMillis();
            
        }
        
        //o.resetOffset();
        //logger.debug("BMWMC on {}. inclusion: {}ms, get images: {}ms, intensity meas: {}ms, M inclusion: {}ms, M intensity {}ms", object, t1-t0, t2-t1, t3-t2, t4-t3, t5-t4);
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
