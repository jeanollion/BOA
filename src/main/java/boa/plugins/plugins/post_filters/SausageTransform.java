/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.post_filters;

import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.ImageByte;
import boa.image.SimpleBoundingBox;
import boa.image.processing.bacteria_spine.BacteriaSpineFactory;
import boa.image.processing.bacteria_spine.BacteriaSpineFactory.SpineResult;
import boa.image.processing.bacteria_spine.CircularContourFactory;
import boa.image.processing.bacteria_spine.SausageContourFactory;
import boa.plugins.PostFilter;
import boa.plugins.ToolTip;
import boa.utils.geom.Point;
import java.util.Set;
import java.util.stream.Collectors;
import net.imglib2.Localizable;

/**
 *
 * @author Jean Ollion
 */
public class SausageTransform implements PostFilter, ToolTip {

    @Override
    public RegionPopulation runPostFilter(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        childPopulation.getRegions().forEach(r -> transform(r, parent.getBounds()));
        return childPopulation;
    }

    public static void transform(Region r, BoundingBox parentBounds) {
        SpineResult sr = BacteriaSpineFactory.createSpine(r, 1);
        SausageContourFactory.toSausage(sr, 0.5); // resample to be able to fill
        Set<Voxel> sausageContourVox = sr.contour.stream().map(l -> ((Point)l).asVoxel()).collect(Collectors.toSet());
        ImageByte mask = CircularContourFactory.getMaskFromContour(sausageContourVox);
        // mask should not extend outside parent bounds
        if (!r.isAbsoluteLandMark()) parentBounds = new SimpleBoundingBox(parentBounds).resetOffset(); // post-filter -> relative to parent bounds
        if (!BoundingBox.isIncluded(mask, parentBounds)) mask = mask.cropWithOffset(BoundingBox.getIntersection(parentBounds, mask));
        r.setMask(mask);
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[0];
    }

    @Override
    public String getToolTipText() {
        return "Modify the contour of segmented objects so that it has the shape of a sausage (potentially bended rod): constant width in the middle with hemicircular ends. Based on the spine.";
    }
    
}
