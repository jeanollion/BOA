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
import boa.data_structure.Measurements;
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
import org.apache.commons.math3.linear.RealMatrix;
import boa.plugins.Measurement;
import boa.utils.geom.Point;

/**
 *
 * @author jollion
 */
public class BacteriaTransMeasurements implements Measurement {
    protected StructureParameter bacteria = new StructureParameter("Bacteria Structure", 1, false, false);
    protected Parameter[] parameters = new Parameter[]{bacteria};
    
    public BacteriaTransMeasurements(){}
    
    public BacteriaTransMeasurements(int bacteriaStructureIdx){
        this.bacteria.setSelectedIndex(bacteriaStructureIdx);
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
        res.add(new MeasurementKeyObject("BacteriaLength", structureIdx));
        res.add(new MeasurementKeyObject("BacteriaArea", structureIdx));
        
        // from tracking
        res.add(new MeasurementKeyObject(StructureObject.trackErrorPrev, structureIdx));
        res.add(new MeasurementKeyObject(StructureObject.trackErrorNext, structureIdx));
        res.add(new MeasurementKeyObject("SizeIncrement", structureIdx));
        res.add(new MeasurementKeyObject("TrackErrorSizeIncrement", structureIdx));
        
        return res;
    }

    @Override public void performMeasurement(StructureObject object) {
        Region bactObject = object.getRegion();
        BoundingBox parentOffset = object.getParent().getBounds();
        Point center=bactObject.getGeomCenter(false);
        center.translateRev(parentOffset);
        center.multiply(object.getScaleXY(), 0);
        center.multiply(object.getScaleXY(), 1);
        //if (object.getTimePoint()==0) logger.debug("object: {} center: {}, parentOffset: {}, objectoffset: {} bactImageOffset: {}, mutImageOffset: {}", object, center, parentOffset, object.getBounds(), bactImage.getBoundingBox(), mutImage.getBoundingBox());
        Measurements m = object.getMeasurements();
        m.setValue("BacteriaCenterX", center.get(0));
        m.setValue("BacteriaCenterY", center.get(1));
        m.setValue("BacteriaLength", GeometricalMeasurements.getFeretMax(bactObject));
        m.setValue("BacteriaArea", GeometricalMeasurements.getVolume(bactObject));
        
        m.setValue(StructureObject.trackErrorNext, object.hasTrackLinkError(false, true));
        m.setValue(StructureObject.trackErrorPrev, object.hasTrackLinkError(true, false));
        Object si = object.getAttribute("SizeIncrement");
        if (si instanceof Number) m.setValue("SizeIncrement", (Number)si);
        Object tesi = object.getAttribute("TrackErrorSizeIncrement");
        m.setValue("TrackErrorSizeIncrement", Boolean.TRUE.equals(tesi));
        
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
