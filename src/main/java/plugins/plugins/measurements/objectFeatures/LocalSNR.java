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
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.Parameter;
import configuration.parameters.SiblingStructureParameter;
import configuration.parameters.StructureParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import dataStructure.objects.Voxel;
import image.BoundingBox;
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
public class LocalSNR extends SNR {
    
    public LocalSNR() {}
    
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
        
        double radiusTh = (object.is3D() ? Math.pow(object.getVoxels().size() * 3 / (4 * Math.PI), 1/3d) : Math.pow(object.getVoxels().size() / (Math.PI), 0.5));
        double radMin2 = 4 * radiusTh * radiusTh;
        double rad = 3*radiusTh;
        //final ImageByte test = new ImageByte("test object: "+object.getLabel(), parentObject.getImageProperties());
        List<Voxel> localBack = new ArrayList<Voxel>();
        int lastSize = -1;
        while(localBack.size()<2*object.getMask().count() && lastSize<localBack.size()) {
            lastSize = localBack.size();
            final double rad2=rad*rad;
            localBack.clear();
            for (Voxel v : parentObject.getVoxels()) {
                double d2=getDistSq(objectCenter, v.x, v.y, v.z);
                if (d2<=rad2 && d2>radMin2) {
                    localBack.add(v);
                    //test.setPixelWithOffset(v.x, v.y, v.z, 1);
                }
            }
            rad+=1.5;
            
        }
        //logger.debug("radTh: {}, radMin: {}, radMax: {}, count: {}", radiusTh, Math.sqrt(radMin2), rad, localBack.size());
        //for (Voxel v : object.getVoxels()) test.setPixelWithOffset(v.x+offset.getxMin(), v.y+offset.getyMin(), v.z+offset.getzMin(), 2);
        //ImageWindowManagerFactory.showImage(test);
        
        IntensityMeasurements fore = super.core.getIntensityMeasurements(object, offset);
        IntensityMeasurements back = super.core.getIntensityMeasurements(new Object3D(localBack, 1, object.getScaleXY(), object.getScaleZ()), null);
        double d = (fore.mean-back.mean) / back.sd;
        //logger.debug("SNR local object: {}, value: {}, rad: {}, count: {}, objectCount: {}, fore: {}, sd:{}, back: {}, sd: {}", object.getLabel(), d, rad, localBack.size(), object.getMask().count(), fore.mean, fore.sd, back.mean, back.sd);
        return d;
    }
    private static double getDistSq(double[] center, int x, int y, int z) {
        return Math.pow(center[0]-x, 2)+Math.pow(center[1]-y, 2)+Math.pow(center[2]-z, 2);
    }

    public String getDefaultName() {
        return "LocalSNR";
    }
    
}
