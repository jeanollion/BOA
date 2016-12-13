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
import java.util.List;
import plugins.Thresholder;
import processing.Filters;
import utils.Utils;

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
    
    public static double[][] getStatistics(List<Image> images, int binSize, double threshold, boolean overThreshold) { // return mean, sigma, count
        // 1  get max y 
        int yMax = 0;
        for (Image i : images) if (i.getSizeY()>yMax) yMax = i.getSizeY();
        double[][] stats = new double[yMax/binSize+1][3]; // 0 sum, 1 sum2, 3 count
        for (Image i : images) {
            i.getBoundingBox().translateToOrigin().loop(new LoopFunction() {
                @Override
                public void setUp() {}
                @Override
                public void tearDown() {
                    for (int i = 0; i<stats.length; ++i) {
                        stats[i][0]/=stats[i][2];
                        stats[i][1]/=stats[i][2];
                        stats[i][1] = Math.sqrt(stats[i][1] - stats[i][0] * stats[i][0]);
                    }
                }
                @Override
                public void loop(int x, int y, int z) {
                    int idx = y/binSize;
                    double v = i.getPixel(x, y, z);
                    if (v>threshold == overThreshold) {
                        stats[idx][0] +=v;
                        stats[idx][1] +=v*v;
                        ++stats[idx][2];
                    }
                }
            });
        }
        double[] yMean = new double[yMax];
        double[] ySd = new double[yMax];
        double[] yCount  = new double[yMax];
        for (int i = 0; i<yMax; ++i) {
            yMean[i] = stats[i / binSize][0];
            ySd[i] = stats[i / binSize][1];
            yCount[i] = stats[i / binSize][2];
        }
        Utils.plotProfile("Y background profile", yMean);
        Utils.plotProfile("Y sd profile", ySd);
        Utils.plotProfile("Y count profile", yCount);
        return stats;
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
