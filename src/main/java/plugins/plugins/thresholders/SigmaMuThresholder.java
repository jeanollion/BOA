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
import image.ImageOperations;
import java.util.List;
import plugins.Thresholder;
import processing.Filters;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SigmaMuThresholder implements Thresholder {
    
    public static double getThreshold(Image input, double sigmaMin, double sigmaMax, double thld, boolean backgroundUnderThld) {
        final Image sigmaMu = Filters.sigmaMu(input, null, Filters.getNeighborhood(4, 4, input));
        double[] meanCount = new double[2];
        input.getBoundingBox().translateToOrigin().loop((int x, int y, int z) -> {
            double v = sigmaMu.getPixel(x, y, z);
            if (v>=sigmaMin && v<=sigmaMax) {
                v = input.getPixel(x, y, z);
                if (v > thld == backgroundUnderThld) {
                    meanCount[0]+=v;
                    ++meanCount[1];
                }
            }
        });
        meanCount[0]/=meanCount[1];
        return meanCount[0];
    }
    
    /**
     *
     * @param images
     * @param binSize
     * @param threshold
     * @param backgroundUnderThreshold
     * @param raw 
     * @return matrix dim 1 = alongY : y sinon plane, dim 2 = stats value: raw sum, sum2 & count, sinon mu sigma & count 
     */
    public static double[][] getStatistics(List<Image> images, boolean alongY, double threshold, boolean backgroundUnderThreshold, boolean raw) { // return mean, sigma, count
        // 1  get max y 
        int dimLength = 0;
        if (alongY) for (Image i : images) if (i.getSizeY()>dimLength) dimLength = i.getSizeY();
        else dimLength = images.size();
        double[][] stats = new double[dimLength][3]; // 0 sum, 1 sum2, 3 count
        for (int imIdx = 0; imIdx<images.size(); ++imIdx) {
            Image i = images.get(imIdx);
            final int ii = imIdx;
            double currentThreshold = ImageOperations.getMeanAndSigma(i, null, v->v>threshold==backgroundUnderThreshold)[0]; // mean within cells : avoid area within cell but no at borders of cell
            i.getBoundingBox().translateToOrigin().loop((int x, int y, int z) -> {
                int idx = alongY ? y : ii;
                double v = i.getPixel(x, y, z);
                if (v>currentThreshold == backgroundUnderThreshold) {
                    stats[idx][0] +=v;
                    stats[idx][1] +=v*v;
                    ++stats[idx][2];
                }
            });
        }
        if (!raw) {
            for (int i = 0; i<stats.length; ++i) {
                stats[i][0]/=stats[i][2];
                stats[i][1]/=stats[i][2];
                stats[i][1] = Math.sqrt(stats[i][1] - stats[i][0] * stats[i][0]);
            }
            double[] yMean = new double[dimLength];
            double[] ySd = new double[dimLength];
            double[] yCount  = new double[dimLength];
            for (int i = 0; i<dimLength; ++i) {
                yMean[i] = stats[i][0];
                ySd[i] = stats[i][1];
                yCount[i] = stats[i][2];
            }
            Utils.plotProfile("Y background profile", yMean);
            Utils.plotProfile("Y sd profile", ySd);
            Utils.plotProfile("Y count profile", yCount);
        }
        return stats;
    }
    
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        return getThreshold(input, 0.28, 0.32, Double.POSITIVE_INFINITY, true);
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[0];
    }
    
}
