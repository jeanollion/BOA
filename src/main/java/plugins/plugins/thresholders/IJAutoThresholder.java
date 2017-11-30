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
package plugins.plugins.thresholders;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObjectProcessing;
import ij.process.AutoThresholder;
import ij.process.AutoThresholder.Method;
import image.BlankMask;
import image.BoundingBox;
import image.Histogram;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageMask;
import plugins.SimpleThresholder;
import plugins.Thresholder;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class IJAutoThresholder implements SimpleThresholder {
    ChoiceParameter method = new ChoiceParameter("Method", AutoThresholder.getMethods(), AutoThresholder.Method.Otsu.toString(), false);
    
    public IJAutoThresholder setMethod(AutoThresholder.Method method) {
        this.method.setValue(method.toString());
        return this;
    }
    
    @Override 
    public double runSimpleThresholder(Image input, ImageMask mask) {
        return runThresholder(input, mask, Method.valueOf(method.getSelectedItem()));
    }
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        ImageMask mask = structureObject!=null?structureObject.getMask():new BlankMask(input);
        return runSimpleThresholder(input, mask);
    }
    
    public static double runThresholder(Image input, ImageMask mask, Method method) {
        return runThresholder(input, mask, null, method, 0);
    }
    
    public static double runThresholder(Image input, ImageMask mask, BoundingBox limits, Method method, double percentageSuplementalBackground) {
        if (mask==null) mask=new BlankMask("", input);
        Histogram histo = input.getHisto256(mask, limits);
        histo.data[0]+=(int)(percentageSuplementalBackground * input.getSizeXYZ()+0.5);
        histo.removeSaturatingValue(4);
        AutoThresholder at = new AutoThresholder();
        double thld = at.getThreshold(method, histo.data);
        return Histogram.convertHisto256Threshold(thld, input, mask, limits);
    }
    
    public static double runThresholder(Method method, Histogram histo) {
        if (method==null) return Double.NaN;
        AutoThresholder at = new AutoThresholder();
        double thld = at.getThreshold(method, histo.data);
        return histo.getValueFromIdx(thld);
    }
    
    @Override    
    public Parameter[] getParameters() {
        return new Parameter[]{method};
    }

    public boolean does3D() {
        return true;
    }
    
}
