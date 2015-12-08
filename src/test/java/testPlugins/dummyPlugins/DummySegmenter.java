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

import configuration.parameters.BooleanParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObjectProcessing;
import image.BlankMask;
import image.Image;
import image.ImageInteger;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Arrays;
import plugins.Segmenter;

/**
 *
 * @author jollion
 */
public class DummySegmenter implements Segmenter {
    BooleanParameter segDir = new BooleanParameter("Segmentation direction", "X", "Y", true);
    NumberParameter objectNb = new NumberParameter("Number of Objects", 0, 2);
    Parameter[] parameters = new Parameter[]{objectNb, segDir};
    public DummySegmenter(){}
    public DummySegmenter(boolean dirX, int objectNb) {
        this.segDir.setSelected(dirX);
        this.objectNb.setValue(objectNb);
    }
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing structureObject) {
        ImageMask mask;
        if (structureObject==null) mask = new BlankMask("", input);
        else mask = structureObject.getMask();
        int nb = objectNb.getValue().intValue();
        //System.out.println("dummy segmenter: nb of objects: "+nb+ " segDir: "+segDir.getSelectedItem());
        BlankMask[] masks = new BlankMask[nb];
        if (segDir.getSelected()) {
            double w = Math.max((mask.getSizeX()+0.0d) / (2*nb+1.0), 1);
            int h = (int)(mask.getSizeY()*0.8d);
            for (int i = 0; i<nb; ++i) masks[i] = new BlankMask("object"+i, (int)w, h, mask.getSizeZ(), (int)((2*i+1)*w) ,(int)(0.1*mask.getSizeY()), 0, mask.getScaleXY(), mask.getScaleZ());
        } else {
            double h = Math.max((mask.getSizeY()+0.0d) / (2*nb+1.0), 1);
            int w = (int)(mask.getSizeX()*0.8d);
            for (int i = 0; i<nb; ++i) masks[i] = new BlankMask("object"+i, w, (int)h, mask.getSizeZ(), (int)(0.1*mask.getSizeX()) ,(int)((2*i+1)*h), 0, mask.getScaleXY(), mask.getScaleZ());
        }
        ArrayList<Object3D> objects = new ArrayList<Object3D>(nb); int idx=1;
        for (BlankMask m :masks) objects.add(new Object3D(m, idx++));
        return new ObjectPopulation(objects, input);
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
    public void setSegmentationDirection(boolean dirX) {
        segDir.setSelected(dirX);
    }
    
    public void setObjectNumber(int objectNb) {
        this.objectNb.setValue(objectNb);
    }
    
}
