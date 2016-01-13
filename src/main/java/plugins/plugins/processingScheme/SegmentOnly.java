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
package plugins.plugins.processingScheme;

import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import java.util.List;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;

/**
 *
 * @author jollion
 */
public class SegmentOnly implements ProcessingScheme {
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<Segmenter>("Segmentation algorithm", Segmenter.class, false);
    Parameter[] parameters= new Parameter[]{segmenter};
    
    public SegmentOnly() {}
    
    public SegmentOnly(Segmenter segmenter) {
        this.segmenter.setPlugin(segmenter);
    }
    
    @Override public void segmentAndTrack(final int structureIdx, final List<StructureObject> parentTrack) {
        ThreadAction<StructureObject> ta = new ThreadAction<StructureObject>() {
            @Override public void run(StructureObject parent, int idx) {
                Segmenter s = segmenter.instanciatePlugin();
                ObjectPopulation pop = s.runSegmenter(parent.getRawImage(structureIdx), structureIdx, parent);
                parent.setChildrenObjects(pop, structureIdx);
            }
        };
        ThreadRunner.execute(parentTrack, ta);
    }

    @Override public void trackOnly(int structureIdx, List<StructureObject> parentTrack) {}

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
