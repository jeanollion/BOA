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
package boa.plugins.plugins.thresholders;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import ij.gui.Plot;
import boa.image.BlankMask;
import boa.image.Histogram;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageMask;
import java.util.HashMap;
import java.util.Map;
import boa.plugins.Thresholder;
import boa.image.processing.Filters;
import boa.image.processing.WatershedTransform;
import boa.image.processing.WatershedTransform.FusionCriterion;
import boa.image.processing.WatershedTransform.PropagationCriterion;
import boa.image.processing.WatershedTransform.Spot;
import static boa.image.processing.WatershedTransform.watershed;
import boa.utils.ArrayUtil;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class ObjectCountThresholder implements Thresholder {
    NumberParameter maxObjectNumber = new BoundedNumberParameter("Max. object number", 0, 10, 1, null);
    BooleanParameter descendingIntensities = new BooleanParameter("Brights spots", true);
    Parameter[] parameters = new Parameter[]{maxObjectNumber, descendingIntensities};
    public static boolean debug = false;
    
    public ObjectCountThresholder() {}
    
    public ObjectCountThresholder(int maxObjectCount) {
        this.maxObjectNumber.setValue(maxObjectCount);
    }
    
    public ObjectCountThresholder setMaxObjectNumber(int max) {
        this.maxObjectNumber.setValue(max);
        return this;
    }
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        ImageMask mask = structureObject!=null ? structureObject.getMask() : new BlankMask("", input);
        return runThresholder(input, mask);
    }
    
    public double runThresholder(Image input, ImageMask mask) {
        
        ImageByte seeds = Filters.localExtrema(input, null, descendingIntensities.getSelected(), descendingIntensities.getSelected() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY, mask, Filters.getNeighborhood(1.5, 1, input));
        boolean bright = descendingIntensities.getSelected();
        if (debug) ImageWindowManagerFactory.showImage(seeds);
        int max = maxObjectNumber.getValue().intValue();
        int[] objectHisto = new int[256];
        boolean byteImage = input instanceof ImageByte;
        double[] mm = input.getMinAndMax(mask);
        
        FusionCriterion f = new FusionCriterion() {
            WatershedTransform instance;
            @Override
            public void setUp(WatershedTransform instance) {
                this.instance=instance;
            }
            @Override
            public boolean checkFusionCriteria(WatershedTransform.Spot s1, WatershedTransform.Spot s2, Voxel currentVoxel) {
                return true;
            }
        };
        PropagationCriterion p = new PropagationCriterion() {
            WatershedTransform instance;
            @Override
            public void setUp(WatershedTransform instance) {
                this.instance=instance;
            }
            @Override
            public boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
                double v = bright ? Math.max(currentVox.value, nextVox.value) : Math.min(currentVox.value, nextVox.value);
                int idx = byteImage ? (int)v : Histogram.convertTo256Threshold(v, mm);
                if (objectHisto[idx]==0) objectHisto[idx] = getSpotNumber(instance, v, bright);
                if (objectHisto[idx]>=max) { // stop propagation
                    instance.getHeap().clear();
                    return false;
                } 
                return true;
            }
        };
        watershed(input, mask, seeds, descendingIntensities.getSelected(), p, f, true);
        if (debug) {
            double[] values = new double[256];
            double[] counts = new double[256];
            for (int i = 0; i<256; ++i) {
                values[i] = byteImage ? i : Histogram.convertHisto256Threshold(i, mm);
                counts[i] = objectHisto[i];
            }
            new Plot("Object Count", "intensity", "count", values, counts).show();
        }
        int i = ArrayUtil.getFirstOccurence(objectHisto, 255, 0, max, false, false);
        if (objectHisto[i]==max && i<255) ++i;
        double value = byteImage ? i : Histogram.convertHisto256Threshold(i, mm);
        if (i<255) value =  (value + (byteImage ? i+1 : Histogram.convertHisto256Threshold(i+1, mm))) / 2;
        if (debug) logger.debug("thld: {} (idx:{})", value, i);
        return value;
    }

    private static int getSpotNumber(WatershedTransform instance, double value, boolean bright) {
        int count = 0;
        for (Spot s : instance.getSpotArray()) {
            if (s!=null) {
                if (s.voxels.size()>1) count++;
                else if (!s.voxels.isEmpty() && s.voxels.iterator().next().value>value==bright) ++count; 
            }
        }
        return count;
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
