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
package plugins.plugins.trackers.trackMate.postProcessing;

import org.apache.commons.math3.distribution.RealDistribution;
import plugins.plugins.trackers.trackMate.postProcessing.TrackLikelyhoodEstimator.AbstractScoreFunction;
import plugins.plugins.trackers.trackMate.postProcessing.TrackLikelyhoodEstimator.DistributionFunction;
import plugins.plugins.trackers.trackMate.postProcessing.TrackLikelyhoodEstimator.Function;
import plugins.plugins.trackers.trackMate.postProcessing.TrackLikelyhoodEstimator.LinearTrimmedFunction;
import plugins.plugins.trackers.trackMate.postProcessing.TrackLikelyhoodEstimator.ScoreFunction;

/**
 *
 * @author jollion
 */
public class DistancePenaltyScoreFunction implements ScoreFunction {
    DistributionFunction lengthFunction;
    Function distanceFunction;
    public DistancePenaltyScoreFunction(RealDistribution lengthDistribution,  RealDistribution distanceDistribution, double distanceThreshold, double maximalPenalty) {
        lengthFunction = new DistributionFunction(lengthDistribution);
        distanceFunction = new DistanceFunction(distanceDistribution, distanceThreshold, maximalPenalty);
    }


    public double getScore(TrackLikelyhoodEstimator.Track track, int[] splitIndices) {
        if (splitIndices.length==0) return lengthFunction.y(track.getLength());
        double lengthProduct = lengthFunction.y(track.getLengthFromStart(splitIndices[0])) * lengthFunction.y(track.getLengthToEnd(splitIndices[splitIndices.length-1]+1));
        double distancePenalty = distanceFunction.y(track.distances[splitIndices[0]]);
        for (int i = 1; i<splitIndices.length; ++i) {
            lengthProduct *= lengthFunction.y(track.getLength(splitIndices[i-1]+1, splitIndices[i]));
            distancePenalty+=distanceFunction.y(track.distances[splitIndices[i]]);
        }
        distancePenalty /= splitIndices.length;//Math.pow(distanceSum, 1.0/(splitIndices.length));
        
        return lengthProduct / distancePenalty; 
    }

    public Function getLengthFunction() {
        return lengthFunction;
    }

    public Function getDistanceFunction() {
        return distanceFunction;
    }
    
    public static class DistanceFunction implements Function {
        final double distanceThreshold, a, m;
        final RealDistribution distanceDistribution;
        public DistanceFunction(RealDistribution distanceDistribution, double distanceThreshold, double maximalPenalty) {
            this.distanceThreshold=distanceThreshold;
            this.distanceDistribution=distanceDistribution;
            double fmin = distanceDistribution.cumulativeProbability(distanceThreshold);
            double fmax = 1;
            m = (1-maximalPenalty) / (fmin - fmax);
            a = maximalPenalty - m * fmax;
        }
        
        public double y(double x) {
            if (x<=distanceThreshold) return 1;
            else return a + distanceDistribution.cumulativeProbability(x) * m;
        }
        
    }
}
