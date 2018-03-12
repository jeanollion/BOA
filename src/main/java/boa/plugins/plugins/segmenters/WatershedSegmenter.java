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
package boa.plugins.plugins.segmenters;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.RegionPopulation;
import boa.data_structure.RegionPopulation.MeanIntensity;
import boa.data_structure.StructureObjectProcessing;
import ij.process.AutoThresholder;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.processing.ImageOperations;
import boa.plugins.Segmenter;
import boa.plugins.SimpleThresholder;
import boa.plugins.plugins.pre_filters.ImageFeature;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.image.processing.Filters;
import boa.image.processing.WatershedTransform;

/**
 *
 * @author jollion
 */
public class WatershedSegmenter implements Segmenter {
    PreFilterSequence watershedMapFilters = new PreFilterSequence("Watershed Map").add(new ImageFeature().setFeature(ImageFeature.Feature.GRAD)).setToolTipText("Filter sequence to compute the map on wich the watershed will be performed");
    BooleanParameter decreasePropagation = new BooleanParameter("Decreasing propagation", false).setToolTipText("On watershed map, whether propagation is done from local minima towards increasing insensity or from local maxima towards decreasing intensities");
    NumberParameter localExtremaRadius = new BoundedNumberParameter("Local Extrema Radius", 2, 1.5, 1, null).setToolTipText("Radius for local extrema computation in pixels");
    PreFilterSequence intensityFilter = new PreFilterSequence("Intensity Filter").setToolTipText("Fitler sequence to compute intensity map used to select forground regions");
    PluginParameter<SimpleThresholder> threshlod = new PluginParameter<>("Threshold for foreground selection", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu),false);
    BooleanParameter foregroundOverThreshold = new BooleanParameter("Foreground is over threhsold", true);
    public static boolean debug;
    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        // perform watershed on all local extrema
        Image watershedMap = watershedMapFilters.filter(input, parent.getMask());
        
        ImageByte localExtrema = Filters.localExtrema(watershedMap, null, decreasePropagation.getSelected(), parent.getMask(), Filters.getNeighborhood(localExtremaRadius.getValue().doubleValue(), watershedMap));
        RegionPopulation pop = WatershedTransform.watershed(watershedMap, parent.getMask(), localExtrema, decreasePropagation.getSelected(), null, null, false);
        // remove regions with low intensity value
        Image intensityMap = intensityFilter.filter(input, parent.getMask());
        if (debug) {
            ImageWindowManagerFactory.showImage(input.duplicate("input map"));
            ImageWindowManagerFactory.showImage(localExtrema.duplicate("local extrema"));
            ImageWindowManagerFactory.showImage(watershedMap.duplicate("watershed map"));
            ImageWindowManagerFactory.showImage(intensityMap.duplicate("intensity map"));
        }
        double thld = threshlod.instanciatePlugin().runSimpleThresholder(intensityMap, parent.getMask());
        
        int tot = pop.getRegions().size();
        pop.filter(new MeanIntensity(thld, foregroundOverThreshold.getSelected(), intensityMap));
        if (debug) logger.debug("WatershedSegmenter: threshold: {}, kept: {}/{}", thld, pop.getRegions().size(), tot);
        return pop;
    }
    
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{watershedMapFilters, decreasePropagation, localExtremaRadius, intensityFilter, threshlod, foregroundOverThreshold};
    }
    
}
