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
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageMask;
import plugins.Thresholder;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class IJAutoThresholder implements Thresholder {
    ChoiceParameter method = new ChoiceParameter("Method", AutoThresholder.getMethods(), AutoThresholder.Method.Otsu.toString(), false);
    
    
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        return runThresholder(input, structureObject.getMask(), Method.valueOf(method.getSelectedItem()));
    }
    
    public static double runThresholder(Image input, ImageMask mask, Method method) {
        if (mask==null) mask=new BlankMask("", input);
        float[] mm = input.getMinAndMax(mask);
        int[] histo = input.getHisto256(mask);
        double binSize=(input instanceof ImageByte)?1:(mm[1]-mm[0])/256d;
        double min = (input instanceof ImageByte)?0:mm[0];
        AutoThresholder at = new AutoThresholder();
        double thld = at.getThreshold(method, histo);
        return thld*binSize+min;
    }

    public Parameter[] getParameters() {
        return new Parameter[]{method};
    }

    public boolean does3D() {
        return true;
    }
    
}
