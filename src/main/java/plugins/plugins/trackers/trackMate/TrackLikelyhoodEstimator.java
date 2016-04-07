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
package plugins.plugins.trackers.trackMate;

import org.apache.commons.math3.distribution.RealDistribution;

/**
 *
 * @author jollion
 */
public class TrackLikelyhoodEstimator {
    RealDistribution lengthDistribution;
    RealDistribution distanceDistribution;
    double minLength;
    public double getLikelyhood(double length) {
        return lengthDistribution.probability(length);
    }
    /**
     * 
     * @param frames array of frames of the track length n
     * @param distances array of distances between spot of a frame and spot of following frame (length = n-1)
     * @param divisionIndices array of indices of the {@param frames} array of division, one element = division
     * @return 
     */
    public double getLikelyhood(int[] frames, double[] distances, int[] divisionIndices) {
        if (divisionIndices.length==0) return lengthDistribution.probability(frames[frames.length]-frames[0]);
        double res = lengthDistribution.probability(frames[divisionIndices[0]]-frames[0]) * distanceDistribution.probability(distances[divisionIndices[0]]);
        for (int i = 1; i<divisionIndices.length; ++i) {
            res*=lengthDistribution.probability(frames[divisionIndices[i]]-frames[divisionIndices[i-1]]) * distanceDistribution.probability(distances[divisionIndices[i]]);
        }
        return res;
    }
    
    public SplitScenario divideTrack(int[] frames, double[] distances, int divisionNumber) {
        if (divisionNumber==0) return new SplitScenario(frames);
        SplitScenario best = new SplitScenario(divisionNumber);
        SplitScenario current = new SplitScenario(divisionNumber);
        if (!current.increment(frames, 0)) return null;
        if (divisionNumber == 1) {
            current.testLastDivisions(frames, distances, best);
            return best;
        }
        int currentDivisionIdx = divisionNumber-2;
        boolean inc;
        while(true) {
            current.testLastDivisions(frames, distances, best);
            inc = current.increment(frames, currentDivisionIdx);
            while(!inc) {
                --currentDivisionIdx;
                if (currentDivisionIdx<0) return best;
                inc = current.increment(frames, currentDivisionIdx);
            }
            
            
        }
    }
    
    private void divideTrack(int[] frames, double[] distances, SplitScenario best, SplitScenario temp, int divisionIdx) {
        if (divisionIdx==best.divisionIndices.length-1) {
            for (int frameIdx = temp.divisionIndices[divisionIdx]; frameIdx<frames.length; ++frameIdx) {
                if (frames[frames.length-1] - frames[frameIdx] < minLength) break;
                temp.incrementLast(frames, distances);
                if (temp.score<best.score) best.transfer(temp);
            }
        } else {
            if (temp.increment(frames, divisionIdx)) divideTrack(frames, distances, best, temp, divisionIdx+1);
        }
    }
    public class SplitScenario {
        int[] divisionIndices;
        double score;
        private SplitScenario(int[] frames) { // no division case
            this.score=getLikelyhood(frames[frames.length]-frames[0]);
            this.divisionIndices=new int[0];
        }
        public SplitScenario(int divisionNumber) {
            this.score=Double.POSITIVE_INFINITY;
            this.divisionIndices= new int[divisionNumber];
        }
        public void testLastDivisions(int[] frames, double[] distances, SplitScenario best) {
            int dIdx = divisionIndices.length-1;
            for (int frameIdx = divisionIndices[dIdx]; frameIdx<frames.length; ++frameIdx) {
                if (frames[frames.length-1] - frames[frameIdx] < minLength) break;
                ++divisionIndices[dIdx];
                score=getLikelyhood(frames, distances, divisionIndices);
                if (score<best.score) best.transfer(this);
            }
        }
        public boolean increment(int[] frames, int divisionIdx) { // sets the frameDivision index at divisionIdx and forward, return true if there is at leat one acceptable scenario
            ++divisionIndices[divisionIdx];
            for (int dIdx = divisionIdx+1; dIdx<divisionIndices.length; ++dIdx) {
                while(frames[divisionIndices[dIdx]]-frames[divisionIndices[dIdx-1]]<minLength) {
                    ++divisionIndices[dIdx];
                    if (divisionIndices[dIdx]>=frames.length-1) return false;
                }
            }
            return frames[frames.length-1]-frames[divisionIndices[divisionIndices.length-1]]>=minLength;
        }
        public void transfer(SplitScenario other) {
            System.arraycopy(other.divisionIndices, 0, this.divisionIndices, 0, divisionIndices.length);
            this.score=other.score;
        }
    }
}
