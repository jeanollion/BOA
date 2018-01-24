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
package boa.plugins.plugins.measurements.objectFeatures;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.SiblingStructureParameter;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.ImageByte;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import boa.plugins.objectFeature.IntensityMeasurement;
import boa.plugins.objectFeature.IntensityMeasurementCore.IntensityMeasurements;
import boa.image.processing.Filters;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class LocalSNR extends SNR {
    protected BoundedNumberParameter localBackgroundRadius = new BoundedNumberParameter("Local background radius", 1, 8, 0, null);
    public static boolean debug;
    @Override public Parameter[] getParameters() {return new Parameter[]{intensity, backgroundObject, formula, foregroundFormula, dilateExcluded, erodeBorders, localBackgroundRadius};}
    public LocalSNR() {}
    
    public LocalSNR(int backgroundStructureIdx) {
        super(backgroundStructureIdx);
    }
    public LocalSNR setLocalBackgroundRadius(double backgroundRadius) {
        localBackgroundRadius.setValue(backgroundRadius);
        return this;
    }
    @Override public double performMeasurement(final Region object, BoundingBox offset) {
        if (core==null) synchronized(this) {setUpOrAddCore(null, null);}
        if (offset==null) offset=new BoundingBox(0, 0, 0);
        final Region parentObject; 
        if (childrenParentMap==null) parentObject = super.parent.getObject();
        else parentObject=this.childrenParentMap.get(object);
        if (parentObject==null) return 0;
        
        // create mask
        ImageByte bckMask  = TypeConverter.toByteMask(object.getMask(), null, 1).setName("mask:");
        bckMask.addOffset(offset);
        bckMask = Filters.binaryMax(bckMask, null, Filters.getNeighborhood(localBackgroundRadius.getValue().doubleValue(), localBackgroundRadius.getValue().doubleValue(), bckMask), false, true);
        ImageOperations.andWithOffset(bckMask, parentObject.getMask(), bckMask);
        double[] meanSdBck = ImageOperations.getMeanAndSigmaWithOffset(intensityMap, bckMask, null);
        IntensityMeasurements fore = super.core.getIntensityMeasurements(object, offset);
        
        double d = getValue(getForeValue(fore), meanSdBck[0], meanSdBck[1]);
        if (debug) {
            logger.debug("SNR local object: {}, value: {}, rad: {}, count: {}, objectCount: {}, fore: {}, sd:{}, back: {}, sd: {}", object.getLabel(), d, localBackgroundRadius.getValue().doubleValue(), bckMask.count(), object.getMask().count(), fore.mean, fore.sd, meanSdBck[0], meanSdBck[1]);
            ImageWindowManagerFactory.showImage(bckMask);
        }
        return d;
        
    }
    
    @Override 
    public String getDefaultName() {
        return "LocalSNR";
    }
    
}
