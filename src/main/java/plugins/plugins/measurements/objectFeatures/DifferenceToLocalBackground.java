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
package plugins.plugins.measurements.objectFeatures;

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.Parameter;
import configuration.parameters.SiblingStructureParameter;
import configuration.parameters.StructureParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.BoundingBox.LoopFunction;
import image.ImageByte;
import image.ImageMask;
import image.TypeConverter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import plugins.objectFeature.IntensityMeasurement;
import plugins.objectFeature.IntensityMeasurementCore.IntensityMeasurements;
import processing.Filters;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class DifferenceToLocalBackground extends SNR {
    
    public DifferenceToLocalBackground() {}
    
    @Override public double performMeasurement(final Object3D object, BoundingBox offset) {
        if (core==null) synchronized(this) {setUpOrAddCore(null);}
        final Object3D parentObject; 
        if (childrenParentMap==null) parentObject = super.parent.getObject();
        else parentObject=this.childrenParentMap.get(object);
        if (parentObject==null) return 0;
        // iterate into parent to get local background values
        final double[] objectCenter = object.getCenter(intensityMap, false);
        objectCenter[0]+=offset.getxMin();
        objectCenter[1]+=offset.getyMin();
        objectCenter[2]+=offset.getzMin();
        double rad = 1.5*(object.is3D() ? Math.pow(object.getVoxels().size() * 3 / (4 * Math.PI), 1/3d) : Math.pow(object.getVoxels().size() / (2 * Math.PI), 0.5));
        
        final ImageByte test = new ImageByte("test object: "+object.getLabel(), parentObject.getImageProperties());
        List<Voxel> localBack = new ArrayList<Voxel>();
        while(localBack.size()<object.getMask().count()) {
            rad+=1.5;
            final double rad2=rad*rad;
            localBack.clear();
            for (Voxel v : parentObject.getVoxels()) {
                if (getDistSq(objectCenter, v.x, v.y, v.z)<=rad2) {
                    localBack.add(v);
                    test.setPixelWithOffset(v.x, v.y, v.z, 1);
                }
            }
        }
        new IJImageDisplayer().showImage(test);
        
        IntensityMeasurements fore = super.core.getIntensityMeasurements(object, offset);
        IntensityMeasurements back = super.core.getIntensityMeasurements(new Object3D(localBack, 1, object.getScaleXY(), object.getScaleZ()), null);
        //double d = (fore.mean-back.mean) / Math.sqrt(fore.sd*fore.sd / fore.count + back.sd*back.sd / back.count);
        double d = (fore.mean-back.mean) / back.sd;
        logger.debug("object: {}, value: {}, rad: {}, count: {}, objectCount: {}, fore: {}, sd:{}, back: {}, sd: {}", object.getLabel(), d, rad, localBack.size(), object.getMask().count(), fore.mean, fore.sd, back.mean, back.sd);
        return d;
    }
    private static double getDistSq(double[] center, int x, int y, int z) {
        return Math.pow(center[0]-x, 2)+Math.pow(center[1]-y, 2)+Math.pow(center[2]-z, 2);
    }

    public String getDefaultName() {
        return "differenceToLocalBackground";
    }
    
}
