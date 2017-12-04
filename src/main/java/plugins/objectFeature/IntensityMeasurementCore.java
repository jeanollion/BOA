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

import configuration.parameters.PreFilterSequence;
import static core.Processor.logger;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.Image;
import image.ImageInteger;
import java.util.HashMap;
import measurement.BasicMeasurements;
import plugins.objectFeature.ObjectFeatureCore;
import plugins.plugins.measurements.objectFeatures.LocalSNR;

/**
 *
 * @author jollion
 */
public class IntensityMeasurementCore implements ObjectFeatureCore {
    Image intensityMap, transformedMap;
    HashMap<Object3D, IntensityMeasurements> values = new HashMap<>();
    
    public void setUp(Image intensityMap, StructureObject parent , PreFilterSequence preFilters) {
        this.intensityMap=intensityMap;    
        if (preFilters==null) this.transformedMap=intensityMap;
        else transformedMap = preFilters.filter(intensityMap, parent);
    }
    public Image getIntensityMap(boolean transformed) {
        return transformed ? transformedMap : intensityMap;
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
        public double mean=0, sd=0, min=Double.MAX_VALUE, max=-Double.MAX_VALUE, valueAtCenter=Double.NaN, count=0;
        int[] maxCoords = new int[3];
        BoundingBox offset;
        Object3D o;
        
        public IntensityMeasurements(Object3D o, BoundingBox offset) {
            if (offset==null) offset=new BoundingBox(0, 0, 0);
            this.offset = offset;
            this.o=o;
            if (o.voxelsCreated()) {
                int offX=offset.getxMin()-intensityMap.getOffsetX();
                int offY=offset.getyMin()-intensityMap.getOffsetY();
                int offZ=offset.getzMin()-intensityMap.getOffsetZ();
                //logger.debug("intensity measurements: offX: {}, offY: {}, offZ: {}", offX, offY, offZ);
                for (Voxel v : o.getVoxels()) increment(transformedMap.getPixel(v.x+offX, v.y+offY, v.z+offZ), v.x, v.y, v.z);
                maxCoords[0]+=offset.getxMin();
                maxCoords[1]+=offset.getyMin();
                maxCoords[2]+=offset.getzMin();
                
            } else {
                ImageInteger mask = o.getMask();
                int offX = offset.getxMin()+mask.getOffsetX()-transformedMap.getOffsetX();
                int offY = offset.getyMin()+mask.getOffsetY()-transformedMap.getOffsetY();
                int offZ = offset.getzMin()+mask.getOffsetZ()-transformedMap.getOffsetZ();
                //logger.debug("intensity measurements: offXY: {}, offZ: {}", offXY, offZ);
                for (int z= 0; z<mask.getSizeZ(); ++z) {
                    for (int y= 0; y<mask.getSizeY(); ++y) {
                        for (int x= 0; x<mask.getSizeX(); ++x) {
                            if (mask.insideMask(x, y, z)) increment(transformedMap.getPixel(x+offX, y+offY, z+offZ), x, y, z);
                        }
                    }
                }
                maxCoords[0]+=offset.getxMin()+mask.getOffsetX();
                maxCoords[1]+=offset.getyMin()+mask.getOffsetY();
                maxCoords[2]+=offset.getzMin()+mask.getOffsetZ();
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
        
        public double getValueAtCenter() {
            if (Double.isNaN(valueAtCenter)) {
                double[] center = o.getCenter();
                if (center==null) center = o.getGeomCenter(false);
                int offX=offset.getxMin()-intensityMap.getOffsetX();
                int offY=offset.getyMin()-intensityMap.getOffsetY();
                int offZ=offset.getzMin()-intensityMap.getOffsetZ();
                this.valueAtCenter = intensityMap.getPixel(center[0]+offX, center[1]+offY, center.length>=3 ? center[2]+offZ : 0);
                if (LocalSNR.debug) logger.debug("center: {}, off: {};{};{}, value: {}", center, offX, offY, offZ, this.valueAtCenter);
            }
            return valueAtCenter;
        }
        private void increment(double value, int x, int y , int z) {
            mean+=value;
            sd+=value*value;
            count++;
            if (value>max) {
                max=value;
                maxCoords[0]=x;
                maxCoords[1]=y;
                maxCoords[2]=z;
            }
            if (value<min) min=value; 
        }
    }
}
