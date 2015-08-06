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
package plugin.dummyPlugins;

import configuration.parameters.BooleanParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObjectProcessing;
import image.BlankMask;
import image.Image;
import image.ImageInteger;
import image.ImageMask;
import plugins.Segmenter;

/**
 *
 * @author jollion
 */
public class DummySegmenter implements Segmenter {
    BooleanParameter segDir = new BooleanParameter("Segmentation direction", new String[]{"X", "Y"}, true);
    NumberParameter objectNb = new NumberParameter("Number of Objects", 0, 2);
    Parameter[] parameters = new Parameter[]{objectNb, segDir};
    
    public DummySegmenter(boolean dirX, int objectNb) {
        this.segDir.setSelected(dirX);
        this.objectNb.setValue(objectNb);
    }
    
    public ImageInteger runSegmenter(Image input, StructureObjectProcessing structureObject) {
        ImageMask mask = structureObject.getMask();
        int nb = objectNb.getValue().intValue();
        BlankMask[] masks = new BlankMask[nb];
        if (segDir.getSelected()) {
            double w = Math.max((mask.getSizeX()+0.0d) / (nb+1.0), 1);
            int h = (int)(mask.getSizeY()*0.8d);
            for (int i = 0; i<nb; ++i) masks[i] = new BlankMask("object"+i, (int)w, h, mask.getSizeZ(), (int)(2*w+1) ,(int)(0.1*mask.getSizeY()), 0, mask.getScaleXY(), mask.getScaleZ());
        } else {
            double h = Math.max((mask.getSizeY()+0.0d) / (nb+1.0), 1);
            int w = (int)(mask.getSizeX()*0.8d);
            for (int i = 0; i<nb; ++i) masks[i] = new BlankMask("object"+i, w, (int)h, mask.getSizeZ(), (int)(0.1*mask.getSizeX()) ,(int)(2*h+1), 0, mask.getScaleXY(), mask.getScaleZ());
        }
        return ImageInteger.mergeBinary(mask, masks);
    }

    public boolean isTimeDependent() {
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
