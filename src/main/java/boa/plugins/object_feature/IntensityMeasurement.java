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
package boa.plugins.object_feature;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.parameters.ObjectClassParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import java.util.List;
import boa.plugins.ObjectFeature;
import boa.plugins.object_feature.ObjectFeatureCore;
import boa.plugins.object_feature.ObjectFeatureWithCore;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.SimpleObjectFeature;

/**
 *
 * @author jollion
 */
public abstract class IntensityMeasurement extends SimpleObjectFeature implements ObjectFeatureWithCore {
    protected IntensityMeasurementCore core;
    protected ObjectClassParameter intensity = new ObjectClassParameter("Intensity").setAutoConfiguration(ObjectClassParameter.defaultAutoConfiguration());
    protected Image intensityMap;
    public IntensityMeasurement setIntensityStructure(int structureIdx) {
        this.intensity.setSelectedClassIdx(structureIdx);
        return this;
    }
    public int getIntensityStructure() {
        return this.intensity.getSelectedClassIdx();
    }
    @Override public IntensityMeasurement setUp(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        super.setUp(parent, childStructureIdx, childPopulation);
        this.parent=parent;
        if (intensity.getSelectedIndex()==-1) intensity.setSelectedIndex(childStructureIdx);
        this.intensityMap=parent.getRawImage(intensity.getSelectedIndex());
        return this;
    }
    
    @Override public Parameter[] getParameters() {return new Parameter[]{intensity};}

    @Override
    public void setUpOrAddCore(List<ObjectFeatureCore> availableCores, PreFilterSequence preFilters) {
        IntensityMeasurementCore existingCore = null;
        if (availableCores!=null) {
            for (ObjectFeatureCore c : availableCores) {
                if (c instanceof IntensityMeasurementCore && ((IntensityMeasurementCore)c).getIntensityMap(false)==intensityMap) {
                    existingCore=(IntensityMeasurementCore)c;
                    break;
                }
            }
        }
        if (existingCore==null) {
            if (core==null) {
                core = new IntensityMeasurementCore();
                core.setUp(intensityMap, parent.getMask(), preFilters);
            }
            if (availableCores!=null) availableCores.add(core);
        } else core=existingCore;
    }    
}
