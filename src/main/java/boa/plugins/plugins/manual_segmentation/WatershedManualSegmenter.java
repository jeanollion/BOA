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
package boa.plugins.plugins.manual_segmentation;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.Voxel;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageMask;
import java.util.ArrayList;
import java.util.List;
import boa.plugins.ManualSegmenter;
import boa.plugins.Thresholder;
import boa.image.processing.watershed.WatershedTransform;
import boa.plugins.SimpleThresholder;

/**
 *
 * @author jollion
 */
public class WatershedManualSegmenter implements ManualSegmenter {
    BooleanParameter decreasingIntensities = new BooleanParameter("Decreasing intensities", true);
    PreFilterSequence prefilters = new PreFilterSequence("PreFilters");
    PluginParameter<SimpleThresholder> stopThreshold = new PluginParameter<>("Stop threshold", SimpleThresholder.class, false);
    Parameter[] parameters=  new Parameter[]{prefilters, decreasingIntensities, stopThreshold};
    boolean verbose;
    public RegionPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> points) {
        input = prefilters.filter(input, segmentationMask).setName("preFilteredImage");
        SimpleThresholder t = stopThreshold.instanciatePlugin();
        double threshold = t!=null?t.runSimpleThresholder(input, segmentationMask): Double.NaN;
        WatershedTransform.PropagationCriterion prop = Double.isNaN(threshold) ? null : new WatershedTransform.ThresholdPropagationOnWatershedMap(threshold);
        ImageByte mask = new ImageByte("seeds mask", input);
        int label = 1;
        for (int[] p : points) {
            if (segmentationMask.insideMask(p[0], p[1], p[2])) mask.setPixel(p[0], p[1], p[2], label++);
        }
        WatershedTransform.WatershedConfiguration config = new WatershedTransform.WatershedConfiguration().decreasingPropagation(decreasingIntensities.getSelected()).propagationCriterion(prop);
        RegionPopulation pop =  WatershedTransform.watershed(input, segmentationMask, mask, config);
        if (verbose) {
            ImageDisplayer disp = new IJImageDisplayer();
            disp.showImage(input);
            disp.showImage(pop.getLabelMap());
        }
        return pop;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verbose=verbose;
    }
    
}
