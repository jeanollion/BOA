/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.thresholders;

import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObjectProcessing;
import ij.process.AutoThresholder;
import ij.process.AutoThresholder.Method;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Histogram;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.plugins.SimpleThresholder;
import boa.plugins.Thresholder;
import boa.plugins.ThresholderHisto;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class IJAutoThresholder implements SimpleThresholder, ThresholderHisto {
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
    
    @Override
    public double runThresholderHisto(Histogram histogram) {
        return runThresholder(Method.valueOf(method.getSelectedItem()), histogram);
    }
    
    public static double runThresholder(Image input, ImageMask mask, BoundingBox limits, Method method, double percentageSuplementalBackground) {
        if (mask==null) mask=new BlankMask( input);
        Histogram histo = input.getHisto256(mask, limits);
        histo.data[0]+=(int)(percentageSuplementalBackground * input.getSizeXYZ()+0.5);
        histo.removeSaturatingValue(4, true);
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
