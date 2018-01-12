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
package plugins.plugins.segmenters;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PreFilterSequence;
import dataStructure.objects.RegionPopulation;
import dataStructure.objects.RegionPopulation.MeanIntensity;
import dataStructure.objects.StructureObjectProcessing;
import ij.process.AutoThresholder;
import image.Image;
import image.ImageByte;
import image.ImageOperations;
import plugins.Segmenter;
import plugins.SimpleThresholder;
import plugins.plugins.preFilter.ImageFeature;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.Filters;
import processing.WatershedTransform;

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
        Image watershedMap = watershedMapFilters.filter(input, parent);
        
        ImageByte localExtrema = Filters.localExtrema(watershedMap, null, decreasePropagation.getSelected(), Filters.getNeighborhood(localExtremaRadius.getValue().doubleValue(), localExtremaRadius.getValue().doubleValue() * watershedMap.getScaleXY()/watershedMap.getScaleZ(), watershedMap));
        ImageOperations.and(localExtrema, parent.getMask(), localExtrema);
        RegionPopulation pop = WatershedTransform.watershed(watershedMap, parent.getMask(), localExtrema, decreasePropagation.getSelected(), null, null, false);
        // remove regions with low intensity value
        Image intensityMap = intensityFilter.filter(input, parent);
        if (debug) {
            ImageWindowManagerFactory.showImage(input.duplicate("input map"));
            ImageWindowManagerFactory.showImage(localExtrema.duplicate("local extrema"));
            ImageWindowManagerFactory.showImage(watershedMap.duplicate("watershed map"));
            ImageWindowManagerFactory.showImage(intensityMap.duplicate("intensity map"));
        }
        double thld = threshlod.instanciatePlugin().runSimpleThresholder(intensityMap, parent.getMask());
        
        int tot = pop.getObjects().size();
        pop.filter(new MeanIntensity(thld, foregroundOverThreshold.getSelected(), intensityMap));
        if (debug) logger.debug("WatershedSegmenter: threshold: {}, kept: {}/{}", thld, pop.getObjects().size(), tot);
        return pop;
    }
    
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{watershedMapFilters, decreasePropagation, localExtremaRadius, intensityFilter, threshlod, foregroundOverThreshold};
    }
    
}
