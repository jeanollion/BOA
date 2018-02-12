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
package boa.dummy_plugins;

import boa.data_structure.Region;
import boa.image.Image;
import java.util.List;
import boa.plugins.ObjectSplitter;
import boa.plugins.SegmenterSplitAndMerge;
import boa.plugins.plugins.segmenters.SimpleThresholder;
import boa.plugins.plugins.thresholders.ConstantValue;

/**
 *
 * @author jollion
 */
public class DummySegmenterSplitAndMerge extends SimpleThresholder implements SegmenterSplitAndMerge {
    ObjectSplitter s = new DummySplitter();
    public DummySegmenterSplitAndMerge() {
        super(new ConstantValue(1));
    }
    public double split(Image image, Region o, List<Region> result) {
        result.addAll(s.splitObject(null, o).getRegions());
        return 1;
    }

    public double computeMergeCost(Image image, List<Region> objects) {
        return 1;
    }
    
}
