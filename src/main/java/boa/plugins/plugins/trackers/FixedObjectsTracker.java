/*
 * Copyright (C) 2017 jollion
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
package boa.plugins.plugins.trackers;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PostFilterSequence;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.Image;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.List;
import boa.plugins.Segmenter;
import boa.plugins.TrackerSegmenter;

/**
 *
 * @author jollion
 */
public class FixedObjectsTracker implements TrackerSegmenter {
    protected NumberParameter refAverage = new BoundedNumberParameter("Number of frame to average around reference frame", 0, 0, 0, null);
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<>("Segmentation algorithm", Segmenter.class, false);
    protected Parameter[] parameters = new Parameter[]{segmenter, refAverage};
    
    public FixedObjectsTracker() {}
    
    public FixedObjectsTracker(Segmenter segmenter) {
        this.segmenter.setPlugin(segmenter);
    }
    
    @Override
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, TrackPreFilterSequence trackPreFilters, PostFilterSequence postFilters) {
        //get input image
        int frame = parentTrack.get(0).getMicroscopyField().getDefaultTimePoint();
        int fInf = parentTrack.get(0).getFrame();
        int fSup = parentTrack.get(parentTrack.size()-1).getFrame();
        if (frame<fInf) frame = parentTrack.get(0).getFrame();
        if (frame>fSup) frame = fSup;
        int av = refAverage.getValue().intValue();
        StructureObject parent=null;
        for (StructureObject o : parentTrack) {
            if (o.getFrame()==frame) {
                parent = o;
                break;
            }
        }
        // TODO take into acount trackPreFilters & track parametrizer
        Image input;
        if (av>1) {
            List<Image> toAv = new ArrayList<>(av);
            int fMin = Math.max(fInf, frame - av/2);
            int fMax = Math.min(fSup+1, fMin+av);
            if (fMax-fMin<av) fMin = Math.max(0, fMax-av);
            for (StructureObject o : parentTrack) {
                if (o.getFrame()>=fMin && o.getFrame()<fMax) toAv.add(o.getRawImage(structureIdx));
            }
            input = ImageOperations.meanZProjection(Image.mergeZPlanes(toAv));
        } else input = parent.getRawImage(structureIdx);
        
        // segment objects on input image
        Segmenter s = segmenter.instanciatePlugin();
        RegionPopulation pop = s.runSegmenter(input, structureIdx, parent);
        for (StructureObject p : parentTrack) p.setChildrenObjects(pop, structureIdx);
        track(structureIdx, parentTrack);
    }

    @Override
    public Segmenter getSegmenter() {
        return segmenter.instanciatePlugin();
    }

    @Override
    public void track(int structureIdx, List<StructureObject> parentTrack) {
        new ObjectIdxTracker().track(structureIdx, parentTrack);
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[0];
    }
    
}
