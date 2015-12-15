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
package testPlugins.dummyPlugins;

import dataStructure.objects.Object3D;
import java.util.List;
import plugins.ObjectSplitter;
import plugins.SegmenterSplitAndMerge;
import plugins.plugins.segmenters.SimpleThresholder;
import plugins.plugins.thresholders.ConstantValue;

/**
 *
 * @author jollion
 */
public class DummySegmenterSplitAndMerge extends SimpleThresholder implements SegmenterSplitAndMerge {
    ObjectSplitter s = new DummySplitter();
    public DummySegmenterSplitAndMerge() {
        super(new ConstantValue(1));
    }
    public double split(Object3D o, List<Object3D> result) {
        result.addAll(s.splitObject(null, o, true).getObjects());
        return 1;
    }

    public double computeMergeCost(List<Object3D> objects) {
        return 1;
    }
    
}
