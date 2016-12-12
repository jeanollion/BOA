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
package plugins.plugins.thresholders;

import configuration.parameters.Parameter;
import dataStructure.objects.StructureObjectProcessing;
import image.BoundingBox.LoopFunction;
import image.Image;
import plugins.Thresholder;
import processing.Filters;

/**
 *
 * @author jollion
 */
public class SigmaMuThresholder implements Thresholder {
    public static double getThreshold(Image input) {
        final Image sigmaMu = Filters.sigmaMu(input, null, Filters.getNeighborhood(4, 4, input));
        double sigmaMin = 0.28;
        double sigmaMax = 0.32;
        double[] meanCount = new double[2];
        input.getBoundingBox().translateToOrigin().loop(new LoopFunction() {
            @Override
            public void setUp() { }

            @Override
            public void tearDown() { meanCount[0]/=meanCount[1];}

            @Override
            public void loop(int x, int y, int z) {
                double v = sigmaMu.getPixel(x, y, z);
                if (v>=sigmaMin && v<=sigmaMax) {
                    meanCount[0]+=input.getPixel(x, y, z);
                    ++meanCount[1];
                }
            }
        });
        return meanCount[0];
    }
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        return getThreshold(input);
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[0];
    }
    
}
