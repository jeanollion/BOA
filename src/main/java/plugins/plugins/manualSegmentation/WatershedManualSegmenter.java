/*
 * Copyright (C) 2016 jollion
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
package plugins.plugins.manualSegmentation;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import configuration.parameters.BooleanParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PreFilterSequence;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
import image.Image;
import image.ImageByte;
import image.ImageMask;
import java.util.ArrayList;
import java.util.List;
import plugins.ManualSegmenter;
import plugins.Thresholder;
import processing.WatershedTransform;

/**
 *
 * @author jollion
 */
public class WatershedManualSegmenter implements ManualSegmenter {
    BooleanParameter decreasingIntensities = new BooleanParameter("Decreasing intensities", true);
    PreFilterSequence prefilters = new PreFilterSequence("PreFilters");
    PluginParameter<Thresholder> stopThreshold = new PluginParameter<Thresholder>("Stop threshold", Thresholder.class, false);
    Parameter[] parameters=  new Parameter[]{prefilters, decreasingIntensities, stopThreshold};
    boolean verbose;
    public ObjectPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> points) {
        input = prefilters.filter(input, parent).setName("preFilteredImage");
        Thresholder t = stopThreshold.instanciatePlugin();
        double threshold = t.runThresholder(input, parent);
        ImageByte mask = new ImageByte("seeds mask", input);
        int label = 1;
        for (int[] p : points) {
            if (segmentationMask.insideMask(p[0], p[1], p[2])) mask.setPixel(p[0], p[1], p[2], label++);
        }
        ObjectPopulation pop =  WatershedTransform.watershed(input, segmentationMask, mask, decreasingIntensities.getSelected(), new WatershedTransform.ThresholdPropagationOnWatershedMap(threshold), null);
        if (verbose) {
            ImageDisplayer disp = new IJImageDisplayer();
            disp.showImage(input);
            disp.showImage(pop.getLabelImage());
        }
        return pop;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public void setVerboseMode(boolean verbose) {
        this.verbose=verbose;
    }
    
}
