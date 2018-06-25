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

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.processing.bacteria_spine.BacteriaSpineCoord;
import boa.image.processing.bacteria_spine.BacteriaSpineLocalizer;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;
import boa.plugins.MultiThreaded;
import boa.plugins.ToolTip;
import boa.utils.geom.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 *
 * @author Jean Ollion
 */
public class SpineCoordinates implements Measurement, MultiThreaded, ToolTip {
    protected StructureParameter bacteria = new StructureParameter("Bacteria", -1, false, false);
    protected StructureParameter spot = new StructureParameter("Spot", -1, false, false);
    protected Parameter[] parameters = new Parameter[]{bacteria, spot};
    
    public SpineCoordinates() {}
    public SpineCoordinates(int spotIdx, int bacteriaIdx) {
        this.spot.setSelectedIndex(spotIdx);
        this.bacteria.setSelectedIndex(bacteriaIdx);
    }
    @Override
    public String getToolTipText() {
        return "Project the spot center in the spine (skeleton) coordinate system of the bacteria that contains the spot (if exists) and return the spine coordinates<br />To compute the spine, <em>Bacteria</em> must correspond to objects with rod shapes<br />Spot center is by default the center defined by the segmenter, if no center is defined, the mass center is used<br /><ol><li><em>SpineCoord</em> is the coordinate along the bacteria axis</li><li><em>SpineRadialCoord is the coordinate perpendicular to the radial axis (negative on the left side)</em></li><li><em>SpineLength is the total spine length</em></li><li><em>SpineRadiius is the width of the bacteria at the position of the spot</em></li></ol><>";
    }
    
    @Override
    public int getCallStructure() {
        return spot.getParentStructureIdx();
    }

    @Override
    public boolean callOnlyOnTrackHeads() {
        return true;
    }

    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<>();
        res.add(new MeasurementKeyObject("SpineCoord", spot.getSelectedStructureIdx()));
        res.add(new MeasurementKeyObject("SpineRadialCoord", spot.getSelectedStructureIdx()));
        res.add(new MeasurementKeyObject("SpineLength", spot.getSelectedStructureIdx()));
        res.add(new MeasurementKeyObject("SpineLength", bacteria.getSelectedStructureIdx()));
        res.add(new MeasurementKeyObject("SpineRadius", spot.getSelectedStructureIdx()));
        return res;
    }

    @Override
    public void performMeasurement(StructureObject parentTrackHead) {
        List<StructureObject> parentTrack = StructureObjectUtils.getTrack(parentTrackHead, false);
        Map<StructureObject, StructureObject> spotMapBacteria = new ConcurrentHashMap<>();
        parentTrack.parallelStream().forEach(parent -> {
            List<StructureObject> allBacteria = parent.getChildren(bacteria.getSelectedStructureIdx());
            List<StructureObject> allSpots = parent.getChildren(spot.getSelectedStructureIdx());
            Map<StructureObject, StructureObject> sMb = StructureObjectUtils.getInclusionParentMap(allSpots, allBacteria);
            spotMapBacteria.putAll(sMb);
        });
        Map<StructureObject, BacteriaSpineLocalizer> bacteriaMapLocalizer = new HashSet<>(spotMapBacteria.values()).parallelStream().collect(Collectors.toMap(b->b, b->new BacteriaSpineLocalizer(b.getRegion()) ));
        spotMapBacteria.entrySet().parallelStream().forEach(e-> {
            Point center = e.getKey().getRegion().getCenter();
            if (center==null) center = e.getKey().getRegion().getGeomCenter(false);
            BacteriaSpineCoord coord = bacteriaMapLocalizer.get(e.getValue()).getSpineCoord(center);
            e.getKey().getMeasurements().setValue("SpineCoord", coord.spineCoord(false));
            e.getKey().getMeasurements().setValue("SpineRadialCoord", coord.radialCoord(false));
            e.getKey().getMeasurements().setValue("SpineLength", coord.spineLength());
            e.getValue().getMeasurements().setValue("SpineLength", coord.spineLength());
            e.getKey().getMeasurements().setValue("SpineRadius", coord.spineLength()); // radius at spot position
        });
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    @Override
    public void setMultithread(boolean parallele) {
        
    }

    
    
}
