/*
 * Copyright (C) 2015 jollion
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
package testPlugins.dummyPlugins;

import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.Voxel;
import image.Image;
import java.util.ArrayList;
import plugins.ObjectSplitter;

/**
 *
 * @author jollion
 */
public class DummySplitter implements ObjectSplitter {

    public ObjectPopulation splitObject(Image input, Object3D object) {
        double yMid  = object.getBounds().getYMean();
        ArrayList<Voxel> v1 = new ArrayList<Voxel>(object.getVoxels().size()/2+1);
        ArrayList<Voxel> v2 = new ArrayList<Voxel>(object.getVoxels().size()/2+1);
        for (Voxel v : object.getVoxels()) {
            if (v.y>=yMid) v1.add(v);
            else v2.add(v);
        }
        Object3D o1  = new Object3D(v1, 1, object.getScaleXY(), object.getScaleZ());
        Object3D o2  = new Object3D(v2, 2, object.getScaleXY(), object.getScaleZ());
        return new ObjectPopulation(null, input).addObjects(o1, o2);
    }

    public Parameter[] getParameters() {
        return new Parameter[0];
    }

    public boolean does3D() {
        return true;
    }
    
}
