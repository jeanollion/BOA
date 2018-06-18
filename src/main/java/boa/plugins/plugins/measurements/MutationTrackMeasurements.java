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

import boa.gui.image_interaction.IJImageDisplayer;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.Region;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageMask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import boa.measurement.BasicMeasurements;
import boa.measurement.GeometricalMeasurements;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;
import boa.utils.Utils;
import boa.utils.geom.Point;

/**
 *
 * @author jollion
 */
public class MutationTrackMeasurements implements Measurement {
    protected StructureParameter bacteria = new StructureParameter("Bacteria Structure", 1, false, false);
    protected StructureParameter mutation = new StructureParameter("Mutation Structure", 2, false, false);
    protected Parameter[] parameters = new Parameter[]{bacteria, mutation};
    
    
    public MutationTrackMeasurements(){}
    
    public MutationTrackMeasurements(int bacteriaStructureIdx, int mutationStructureIdx){
        this.bacteria.setSelectedIndex(bacteriaStructureIdx);
        this.mutation.setSelectedIndex(mutationStructureIdx);
    }
    
    @Override
    public int getCallStructure() {
        return mutation.getParentStructureIdx();
    }

    @Override
    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        int structureIdx = mutation.getSelectedStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>();
        res.add(new MeasurementKeyObject("IsTrackHead", structureIdx));
        res.add(new MeasurementKeyObject("BacteriaIdx", structureIdx));
        res.add(new MeasurementKeyObject("BacteriaIndices", structureIdx));
        res.add(new MeasurementKeyObject("PreviousDivisionFrame", structureIdx));
        res.add(new MeasurementKeyObject("NextDivisionFrame", structureIdx));
        res.add(new MeasurementKeyObject("TrackHeadIndices", structureIdx));
        
        // localization
        res.add(new MeasurementKeyObject("CoordProportionalY", structureIdx));
        res.add(new MeasurementKeyObject("CoordRelToCenterX", structureIdx));
        res.add(new MeasurementKeyObject("CoordRelToCenterY", structureIdx));

        // intensity
        res.add(new MeasurementKeyObject("MeanIntensity", structureIdx));
        res.add(new MeasurementKeyObject("SumIntensity", structureIdx));
        
        // track parameters (only computed for trackHeads)
        res.add(new MeasurementKeyObject("TrackLength", structureIdx));
        res.add(new MeasurementKeyObject("MutationNumber", structureIdx));

        return res;
    }

    @Override
    public void performMeasurement(StructureObject parent) {
        for (StructureObject object : parent.getChildren(mutation.getSelectedStructureIdx())) {
            object.getMeasurements().setValue("IsTrackHead", object.isTrackHead());
            object.getMeasurements().setStringValue("TrackHeadIndices", StructureObjectUtils.getIndices(object.getTrackHead()));
            if (object.isTrackHead()) {
                List<StructureObject> track = StructureObjectUtils.getTrack(object, false);
                int tl = track.get(track.size()-1).getFrame() - object.getFrame()+1;
                int mn = track.size();
                for (StructureObject o : track) {
                    o.getMeasurements().setValue("TrackLength", tl );
                    o.getMeasurements().setValue("MutationNumber", mn);
                }
            }
            Image intensities = parent.getRawImage(mutation.getSelectedStructureIdx());
            Point objectCenter = object.getRegion().getCenter();
            if (objectCenter==null) objectCenter = object.getRegion().getMassCenter(intensities, false);
            object.getMeasurements().setValue("MeanIntensity", BasicMeasurements.getMeanValue(object.getRegion(), intensities));
            object.getMeasurements().setValue("SumIntensity", BasicMeasurements.getSum(object.getRegion(), intensities));
            
            StructureObject parentBacteria = object.getParent(bacteria.getSelectedStructureIdx());
            if (parentBacteria!=null) {
                object.getMeasurements().setValue("BacteriaIdx", parentBacteria.getIdx());
                object.getMeasurements().setStringValue("BacteriaIndices", StructureObjectUtils.getIndices(parentBacteria));
                int prevTP = parentBacteria.getPreviousDivisionTimePoint();
                object.getMeasurements().setValue("PreviousDivisionFrame", prevTP>0 ? prevTP : null);
                int nextTP = parentBacteria.getNextDivisionTimePoint();
                object.getMeasurements().setValue("NextDivisionFrame", nextTP>=0?nextTP:null );

                object.getMeasurements().setValue("CoordProportionalY", getYProportionalPositionWithinContainer(parentBacteria.getRegion(), objectCenter.get(1)));
                Point parentCenter = parentBacteria.getRegion().getGeomCenter(false);
                object.getMeasurements().setValue("CoordRelToCenterY", (objectCenter.get(1)-parentCenter.get(1)));
                object.getMeasurements().setValue("CoordRelToCenterX", (objectCenter.get(0)-parentCenter.get(0)));
            }
        }
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
    private static double getYProportionalPositionWithinContainer(Region container, double yCoordinate) {
        double countNext=-1, countPrev=-1, count = 0;
        ImageMask mask = container.getMask();
        BoundingBox bds = container.getBounds();
        double yLimPrev = (int) yCoordinate;
        double yLimNext = yLimPrev+1;
        for (int y = bds.yMin(); y<=bds.yMax(); ++y) {
            countPrev = countNext;
            for (int z = bds.zMin(); z<=bds.zMax(); ++z) {
                for (int x = bds.xMin(); x<=bds.xMax(); ++x) {
                    if (mask.insideMaskWithOffset(x, y, z)) ++count;
                }
            }
            if (y==yLimPrev) countPrev = count;
            else if (y==yLimNext) countNext = count;
        }
        if (countNext>=0) { // linear approx y-axis within current line
            double curLine = countNext - countPrev;
            double p = yCoordinate - (int)yCoordinate;
            return (countPrev + p * curLine) / count;
        } else return countPrev / count;
    }
    
}
