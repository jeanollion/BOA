/*
 * Copyright (C) 2017 jollion
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

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObjectProcessing;
import ij.process.AutoThresholder;
import image.Image;
import image.ImageInteger;
import image.ImageMask;
import image.ImageOperations;
import java.util.List;
import plugins.SimpleThresholder;

/**
 *
 * @author jollion
 */
public class BacteriaFluoThresholder implements SimpleThresholder {
    BoundedNumberParameter factor = new BoundedNumberParameter("Maximum Foreground proportion", 2, 0.2, 0, 1);
    Parameter[] parameters = new Parameter[]{factor};
    @Override
    public double runThresholder(Image image) {
        return getThreshold(image, factor.getValue().doubleValue(), null, null, null, null);
    }
    
    public static double getThreshold(Image image, double factor, ImageMask mask, ImageInteger tempMask, List<ImageInteger> masks, List<ImageInteger> masks2) {
        double thld = IJAutoThresholder.runThresholder(image, mask, AutoThresholder.Method.Otsu);
        double totalSize= mask!=null ? mask.count() : image.getSizeXYZ();
        double[] msc = ImageOperations.getMeanAndSigma(image, mask, v->v<thld);
        double foreProp = 1 - msc[2] / totalSize ;
        logger.debug("thld1: {}, fore prop: {}", thld, foreProp);
        //if (msc[2]/totalSize < factor) return getRescueThreshold(image, mask);
        tempMask = ImageOperations.threshold(image, thld, false, true, true, tempMask);
        if (mask!=null) ImageOperations.and(tempMask, mask, tempMask);
        if (masks!=null) masks.add(tempMask.duplicate("thld1"));
        double thld2 = IJAutoThresholder.runThresholder(image, tempMask, AutoThresholder.Method.Otsu);
        double[] msc2 = ImageOperations.getMeanAndSigma(image, null, v->v<thld2);
        double foreProp2 = 1 - msc2[2] / totalSize ;
        if (masks2!=null) {
            tempMask = ImageOperations.threshold(image, thld2, false, true, true, tempMask);
            masks2.add(tempMask.duplicate("thld2"));
        }
        logger.debug("thld2: {}, fore prop: {}, augmentation: {}", thld2, foreProp2, foreProp2/foreProp);
        //if (foreProp2 > factor) return thld;
        //else return thld2;
        return thld2;
    }
    
    public static double getRescueThreshold(Image image, ImageMask mask) {
        double res = BackgroundThresholder.run(image, mask, 3, 3, 2, null);
        logger.debug("kappa sigma: {}", res);
        return res;
    }

    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        return getThreshold(input, factor.getValue().doubleValue(), structureObject.getMask(), null, null, null);
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
