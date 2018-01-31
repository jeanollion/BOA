/*
 * Copyright (C) 2018 jollion
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
package boa.plugins.plugins.segmenters;

import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObjectProcessing;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.split_merge.SplitAndMerge;
import boa.image.processing.split_merge.SplitAndMergeEdge;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.plugins.SegmenterSplitAndMerge;
import java.util.List;

/**
 *
 * @author jollion
 */
public class BacteriaShape implements SegmenterSplitAndMerge {

    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        ImageWindowManagerFactory.showImage(input);
        EdgeDetector seg = new EdgeDetector().setSeedRadius(1).setMinSizePropagation(5); // seed radius 1 to have seeds on sides / minsizePropagation: not higer than 10
        seg.setWsMap(Filters.applyFilter(input, new ImageFloat("", input), new Filters.Sigma(), Filters.getNeighborhood(3, input))); // lower than 4
        RegionPopulation pop = seg.partitionImage(input, parent.getMask());
        ImageWindowManagerFactory.showImage(seg.getWsMap(input, null));
        ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input));
        SplitAndMergeEdge sam = new SplitAndMergeEdge(seg.getWsMap(input, null), 0.3, false); // median 0.075
        sam.setTestMode(true);
        sam.merge(pop, 10, 0);
        ImageWindowManagerFactory.showImage(EdgeDetector.generateRegionValueMap(pop, input).setName("after merge"));
        return null;
    }
    
    @Override
    public double split(Image input, Region o, List<Region> result) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double computeMergeCost(Image input, List<Region> objects) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Parameter[] getParameters() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
