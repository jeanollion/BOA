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
public class MutationTrackMeasurements implements Measurement {
    protected StructureParameter bacteria = new StructureParameter("Bacteria Structure", 1, false, false);
    protected StructureParameter mutation = new StructureParameter("Mutation Structure", 2, false, false);
    protected Parameter[] parameters = new Parameter[]{bacteria, mutation};
    
    
    public MutationTrackMeasurements(){}
    
    public MutationTrackMeasurements(int bacteriaStructureIdx, int mutationStructureIdx){
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
        res.add(new MeasurementKeyObject("BacteriaIdx", structureIdx));
        res.add(new MeasurementKeyObject("BacteriaIndices", structureIdx));
        res.add(new MeasurementKeyObject("PreviousDivisionFrame", structureIdx));
        res.add(new MeasurementKeyObject("NextDivisionFrame", structureIdx));
        res.add(new MeasurementKeyObject("TrackHeadIndices", structureIdx));
        
        // localization
        res.add(new MeasurementKeyObject("YCoordProportional", structureIdx));
        res.add(new MeasurementKeyObject("XCoordRelToCenter", structureIdx));
        res.add(new MeasurementKeyObject("YCoordRelToCenter", structureIdx));

        // track parameters (only computed for trackHeads
        res.add(new MeasurementKeyObject("TrackLength", structureIdx));
        res.add(new MeasurementKeyObject("MutationNumber", structureIdx));
        
        
        return res;
    }

    public void performMeasurement(StructureObject object, List<StructureObject> modifiedObjects) {
        // limit to trackHead of mother bacteria
        StructureObject parentBacteria;
        if (bacteria.getSelectedStructureIdx()==object.getParent().getStructureIdx()) parentBacteria = object.getParent();
        else parentBacteria = StructureObjectUtils.getInclusionParent(object.getObject(), object.getParent().getChildren(bacteria.getSelectedStructureIdx()), null);
        if (parentBacteria == null) return;
        object.getMeasurements().setValue("IsTrackHead", object.isTrackHead());
        object.getMeasurements().setValue("BacteriaIdx", parentBacteria.getIdx());
        object.getMeasurements().setValue("BacteriaIndices", StructureObjectUtils.getIndices(parentBacteria));
        int prevTP = parentBacteria.getPreviousDivisionTimePoint();
        object.getMeasurements().setValue("PreviousDivisionFrame", prevTP>0 ? prevTP : null);
        int nextTP = parentBacteria.getNextDivisionTimePoint();
        object.getMeasurements().setValue("NextDivisionFrame", nextTP>=0?nextTP:null );
        object.getMeasurements().setValue("TrackHeadIndices", StructureObjectUtils.getIndices(object.getTrackHead()));
        
        double[] objectCenter = object.getObject().getCenter(object.getRawImage(mutation.getSelectedStructureIdx()), false);
        object.getMeasurements().setValue("YCoordProportional", getYProportionalPositionWithinContainer(parentBacteria.getObject(), objectCenter[1]));
        double[] parentCenter = parentBacteria.getObject().getCenter(false);
        object.getMeasurements().setValue("YCoordRelToCenter", (objectCenter[1]-parentCenter[1]));
        object.getMeasurements().setValue("XCoordRelToCenter", (objectCenter[0]-parentCenter[0]));
        
        if (object.isTrackHead()) {
            List<StructureObject> track = StructureObjectUtils.getTrack(object, false);
            object.getMeasurements().setValue("TrackLength", track.get(track.size()-1).getTimePoint() - object.getTimePoint()+1);
            object.getMeasurements().setValue("MutationNumber", track.size());
        }
        modifiedObjects.add(object);
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
    private static double getYProportionalPositionWithinContainer(Object3D container, double yCoordinate) {
        double countNext=-1, countPrev=-1, count = 0;
        ImageMask mask = container.getMask();
        BoundingBox bds = container.getBounds();
        double yLimPrev = (int) yCoordinate;
        double yLimNext = yLimPrev+1;
        for (int y = bds.getyMin(); y<=bds.getyMax(); ++y) {
            countPrev = countNext;
            for (int z = bds.getzMin(); z<=bds.getzMax(); ++z) {
                for (int x = bds.getxMin(); x<=bds.getxMax(); ++x) {
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
