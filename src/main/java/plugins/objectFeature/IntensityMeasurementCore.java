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
package plugins.objectFeature;

import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.Image;
import image.ImageInteger;
import java.util.HashMap;
import measurement.BasicMeasurements;
import plugins.objectFeature.ObjectFeatureCore;

/**
 *
 * @author jollion
 */
public class IntensityMeasurementCore implements ObjectFeatureCore {
    Image intensityMap;
    HashMap<Object3D, IntensityMeasurements> values = new HashMap<Object3D, IntensityMeasurements>();
    
    public void setUp(Image intensityMap) {
        this.intensityMap=intensityMap;    
    }
    public Image getIntensityMap() {
        return intensityMap;
    }
    public IntensityMeasurements getIntensityMeasurements(Object3D o, BoundingBox offset) {
        IntensityMeasurements i = values.get(o);
        if (i==null) {
            i = new IntensityMeasurements(o, offset);
            values.put(o, i);
        }
        return i;
    }
    
    public class IntensityMeasurements {
        public double mean=0, sd=0, min=Double.MAX_VALUE, max=-Double.MAX_VALUE, count=0;
        BoundingBox offset;
        Object3D o;
        public IntensityMeasurements(Object3D o, BoundingBox offset) {
            this.offset = offset;
            this.o=o;
            
            if (o.voxelsCreated()) {
                if (offset==null) {
                    for (Voxel v : o.getVoxels()) increment(intensityMap.getPixel(v.x, v.y, v.z));
                } else {
                    for (Voxel v : o.getVoxels()) increment(intensityMap.getPixel(v.x+offset.getxMin(), v.y+offset.getyMin(), v.z+offset.getzMin()));
                }
            } else {
                ImageInteger mask = o.getMask();
                if (offset==null) offset=new BoundingBox(0, 0, 0);
                
                int offXY = (offset.getyMin()+mask.getOffsetY()) * intensityMap.getSizeX() + offset.getxMin()+mask.getOffsetX();
                int offZ = offset.getzMin()+mask.getOffsetZ();
                for (int z= 0; z<mask.getSizeZ(); ++z) {
                    for (int xy= 0; z<mask.getSizeXY(); ++xy) {
                        if (mask.insideMask(xy, z)) increment(intensityMap.getPixel(xy+offXY, z+offZ));
                    }
                }
            }
            
            if (count==0) {
                max=Double.NaN;
                min = Double.NaN;
                sd=Double.NaN;
                mean=Double.NaN;
            } else {
                mean/=count;
                sd = Math.sqrt(sd/count-mean*mean);
            }
        }
        private void increment(double value) {
            mean+=value;
            sd+=value*value;
            count++;
            if (value>max) max=value;
            if (value<min) min=value; 
        }
    }
}
