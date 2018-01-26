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
package boa.plugins.objectFeature;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.BoundingBox;
import boa.image.Image;
import java.util.List;
import boa.plugins.ObjectFeature;
import boa.plugins.objectFeature.ObjectFeatureCore;
import boa.plugins.objectFeature.ObjectFeatureWithCore;
import boa.plugins.plugins.measurements.SimpleObjectFeature;

/**
 *
 * @author jollion
 */
public abstract class IntensityMeasurement extends SimpleObjectFeature implements ObjectFeatureWithCore {
    protected IntensityMeasurementCore core;
    protected StructureParameter intensity = new StructureParameter("Intensity").setAutoConfiguration(true);
    protected Image intensityMap;
    protected StructureObject parent;
    public IntensityMeasurement setIntensityStructure(int structureIdx) {
        this.intensity.setSelectedStructureIdx(structureIdx);
        return this;
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
        IntensityMeasurementCore newCore = null;
        if (availableCores!=null) {
            for (ObjectFeatureCore c : availableCores) {
                if (c instanceof IntensityMeasurementCore && ((IntensityMeasurementCore)c).getIntensityMap(false)==intensityMap) {
                    newCore=(IntensityMeasurementCore)c;
                    break;
                }
            }
        }
        if (newCore==null) {
            if (core==null) {
                core = new IntensityMeasurementCore();
                core.setUp(intensityMap, parent.getMask(), preFilters);
            }
            if (availableCores!=null) availableCores.add(core);
        } else core=newCore;
    }    
}
