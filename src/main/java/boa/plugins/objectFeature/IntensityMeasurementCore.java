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
package boa.plugins.objectFeature;

import boa.configuration.parameters.PreFilterSequence;
import static boa.core.Processor.logger;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import java.util.HashMap;
import boa.measurement.BasicMeasurements;
import boa.plugins.objectFeature.ObjectFeatureCore;
import boa.plugins.plugins.measurements.objectFeatures.LocalSNR;
import boa.utils.DoubleStatistics;
import java.util.stream.DoubleStream;

/**
 *
 * @author jollion
 */
public class IntensityMeasurementCore implements ObjectFeatureCore {
    Image intensityMap, transformedMap;
    HashMap<Region, IntensityMeasurements> values = new HashMap<>();
    
    public void setUp(Image intensityMap, ImageMask mask , PreFilterSequence preFilters) {
        this.intensityMap=intensityMap;    
        if (preFilters==null) this.transformedMap=intensityMap;
        else transformedMap = preFilters.filter(intensityMap, mask);
    }
    public Image getIntensityMap(boolean transformed) {
        return transformed ? transformedMap : intensityMap;
    }
    public IntensityMeasurements getIntensityMeasurements(Region o) {
        IntensityMeasurements i = values.get(o);
        if (i==null) {
            i = new IntensityMeasurements(o);
            values.put(o, i);
        }
        return i;
    }
    
    public class IntensityMeasurements {
        public double mean=Double.NaN, sd=Double.NaN, min=Double.NaN, max=Double.NaN, valueAtCenter=Double.NaN, count=Double.NaN;
        Region o;
        
        public IntensityMeasurements(Region o) {
            this.o=o;
            DoubleStatistics stats = DoubleStatistics.getStats(intensityMap.stream(o.getMask(), o.isAbsoluteLandMark()));
            mean = stats.getAverage();
            sd = stats.getStandardDeviation();
            min = stats.getMin();
            max = stats.getMax();
            count = stats.getCount();
        }
        
        public double getValueAtCenter() {
            if (Double.isNaN(valueAtCenter)) {
                double[] center = o.getCenter();
                if (center==null) center = o.getGeomCenter(false);
                this.valueAtCenter = o.isAbsoluteLandMark() ? intensityMap.getPixelWithOffset(center[0], center[1], center.length>=3 ? center[2] : 0) : intensityMap.getPixel(center[0], center[1], center.length>=3 ? center[2] : 0);
            }
            return valueAtCenter;
        }
    }
}
