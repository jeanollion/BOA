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
package boa.plugins.plugins.trackers.nested_spot_tracker;

import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.image.processing.bacteria_spine.BacteriaSpineCoord;
import boa.image.processing.bacteria_spine.BacteriaSpineLocalizer;
import boa.utils.geom.Point;
import fiji.plugin.trackmate.Spot;
import java.util.Map;

/**
 *
 * @author jollion
 */
public class NestedSpot extends SpotWithQuality {
    protected boolean lowQuality;
    final protected StructureObject parent;
    final protected Region region;
    final protected DistanceComputationParameters distanceParameters;
    final protected Map<StructureObject, BacteriaSpineLocalizer> localizerMap;
    final BacteriaSpineCoord spineCoord;
    public NestedSpot(Region region, StructureObject parent, Map<StructureObject, BacteriaSpineLocalizer> localizerMap, DistanceComputationParameters distanceParameters) {
        this(region, parent, localizerMap, distanceParameters, localizerMap.get(parent).getSpineCoord(region.getCenter()));
    }
    private NestedSpot(Region region, StructureObject parent, Map<StructureObject, BacteriaSpineLocalizer> localizerMap, DistanceComputationParameters distanceParameters,  BacteriaSpineCoord spineCoord) {
        super(region.getCenter().duplicate().multiplyDim(region.getScaleXY(), 0).multiplyDim(region.getScaleXY(), 1).multiplyDim(region.getScaleZ(), 2), 1, 1);
        this.localizerMap = localizerMap;
        this.region = region;
        this.parent = parent;
        this.spineCoord = spineCoord;
        getFeatures().put(Spot.FRAME, (double)parent.getFrame());
        getFeatures().put(Spot.QUALITY, region.getQuality());
        this.distanceParameters=distanceParameters;
    }
    public NestedSpot duplicate() {
        return new NestedSpot(region, parent, localizerMap, distanceParameters, spineCoord);
    }
    @Override
    public boolean isLowQuality() {
        return lowQuality;
    }
    @Override
    public int frame() {
        return parent.getFrame();
    }
    @Override 
    public StructureObject parent() {
        return this.parent;
    }
    public Region getRegion() {
        return region;
    }
    @Override
    public double squareDistanceTo( final Spot s ) {
        if (s instanceof NestedSpot) {
            NestedSpot ss= (NestedSpot)s;
            if (!distanceParameters.includeLQ && (lowQuality || ss.lowQuality)) return Double.POSITIVE_INFINITY;
            if (this.frame()>ss.frame()) return ss.squareDistanceTo(this);
            if (ss.frame()-frame()>distanceParameters.maxFrameDiff) return Double.POSITIVE_INFINITY;
            return BacteriaSpineLocalizer.distanceSq(spineCoord, ss.region.getCenter(), parent, ss.parent, distanceParameters.projectionType, localizerMap, false);
            
        } else return super.squareDistanceTo(s);
    }
}
