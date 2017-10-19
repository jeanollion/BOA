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
    public double runThresholder(Image input) {
        return runThresholder(input, null);
    }
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        ImageMask mask = structureObject!=null?structureObject.getMask():new BlankMask(input);
        return runThresholder(input, mask, Method.valueOf(method.getSelectedItem()));
    }
    
    public static double runThresholder(Image input, ImageMask mask, Method method) {
        return runThresholder(input, mask, null, method, 0);
    }
    
    public static double runThresholder(Image input, ImageMask mask, BoundingBox limits, Method method, double percentageSuplementalBackground) {
        if (mask==null) mask=new BlankMask("", input);
        int[] histo = input.getHisto256(mask, limits);
        histo[0]+=(int)(percentageSuplementalBackground * input.getSizeXYZ()+0.5);
        AutoThresholder at = new AutoThresholder();
        double thld = at.getThreshold(method, histo);
        return convertHisto256Threshold(thld, input, mask, limits);
    }
    
    public static double runThresholder(Method method, int[] histogram, double[] minAndMax, boolean byteImage) {
        if (method==null) return Double.NaN;
        AutoThresholder at = new AutoThresholder();
        double thld = at.getThreshold(method, histogram);
        if (byteImage) return thld;
        else return convertHisto256Threshold(thld, minAndMax);
    }
    
    public static double convertHisto256Threshold(double threshold256, double[] minAndMax) {
        return threshold256 * (minAndMax[1] - minAndMax[0]) / 256.0 + minAndMax[0];
    }
    
    public static int convertTo256Threshold(double threshold, double[] minAndMax) {
        int res = (int)Math.round((threshold - minAndMax[0])* 256 / ((minAndMax[1] - minAndMax[0])));
        if (res>=256) res = 255;
        return res;
    }
    
    public static double convertHisto256Threshold(double threshold256, Image input, ImageMask mask, BoundingBox limits) {
        if (mask == null) mask = new BlankMask("", input);
        double[] mm = input.getMinAndMax(mask, limits);
        if (input instanceof ImageByte) return threshold256;
        else  return convertHisto256Threshold(threshold256, mm);
    }
    
    public Parameter[] getParameters() {
        return new Parameter[]{method};
    }

    public boolean does3D() {
        return true;
    }
    
}
