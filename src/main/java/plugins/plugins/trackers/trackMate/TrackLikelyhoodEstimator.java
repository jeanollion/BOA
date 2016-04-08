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

import dataStructure.objects.StructureObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.math3.distribution.RealDistribution;
import static plugins.Plugin.logger;

/**
 *
 * @author jollion
 */
public class TrackLikelyhoodEstimator {
    RealDistribution lengthDistribution;
    RealDistribution distanceDistribution;
    double minLength; // minimal length for a track
    double roundRange = 0.25d;
    double theoricalLength;
    
    public TrackLikelyhoodEstimator(RealDistribution lengthDistribution, RealDistribution distanceDistribution, int minimalTrackLength) {
        this.lengthDistribution=lengthDistribution;
        this.distanceDistribution=distanceDistribution;
        this.minLength=minimalTrackLength;
        this.theoricalLength = getModalXValue(lengthDistribution, minLength, 0.5d, 0.001);
        logger.debug("theorical length: {}", theoricalLength);
    }
    
    private static double getModalXValue(RealDistribution distribution, double minX, double precision1, double precision2) { 
        double minProbability = distribution.probability(minX);
        double maxX = minX;
        double maxP = minProbability;
        double tempP = maxP;
        for (double x = minX; tempP>minProbability; x+=precision1) {
            tempP = distribution.probability(x);
            if (tempP>maxP) {
                maxP = tempP;
                maxX = x;
            }
        }
        if (precision2 * 10 < precision1) {
            maxX = getModalXValue_(distribution, maxX-precision1, maxX+precision1, precision2*10);
            return getModalXValue_(distribution, maxX-precision2*10, maxX+precision2*10, precision2);
        } else return getModalXValue_(distribution, maxX-precision1, maxX+precision1, precision2);
    }
    
    private static double getModalXValue_(RealDistribution distribution, double lowerXBound, double upperXBound, double precision) { 
        double maxX = upperXBound;
        double maxP = distribution.probability(maxX);
        double tempP;
        for (double x = lowerXBound; x<upperXBound; x+=precision) {
            tempP = distribution.probability(x);
            if (tempP>maxP) {
                maxP = tempP;
                maxX = x;
            }
        }
        logger.debug("modal value search: lb: {}, hb: {}, precision: {}, X: {}, p(X): {}", lowerXBound, upperXBound, precision, maxX, maxP);
        return maxX;
    }
    
    /**
     * 
     * @param track
     * @param divisionIndices array of indices of the {@param frames} array of division, one element = division
     * @param distanceProduct product of probabilities of all displacements
     * @return 
     */
    public double getLikelyhood(Track track, int[] divisionIndices, double distanceProduct) {
        if (divisionIndices.length==0) return lengthDistribution.probability(track.getLength()) * distanceProduct;
        double res = lengthDistribution.probability(track.getLengthFromStart(divisionIndices[0])) * lengthDistribution.probability(track.getLengthToEnd(divisionIndices[divisionIndices.length-1]+1)) * distanceProduct / distanceDistribution.probability(track.squareDistances[divisionIndices[0]]); // divide to remove the displacement
        for (int i = 1; i<divisionIndices.length; ++i) res*=lengthDistribution.probability(track.getLength(divisionIndices[i], divisionIndices[i-1]+1)) / distanceDistribution.probability(track.squareDistances[divisionIndices[i]]); // divide to remove the displacement
        return res;
    }
    
    
    public SplitScenario splitTrack(Track track) {
        // get number of divisions
        double r1 = (double) (track.getLength()+1) / (theoricalLength-1); 
        double r1float = r1 - (int)r1;
        double r2 = (double) (track.getLength()) / (theoricalLength-1); 
        double r2float = r2 - (int)r2;
        if (r1float<0.5-roundRange) return divideTrack(track, (int)r1);
        else if (r2float>0.5+roundRange) return divideTrack(track, (int)r2+1);
        else {
            SplitScenario s1 = divideTrack(track, (int)r1);
            splitTrack(track, (int)r2+1, s1);
            return s1;
        }
    }
    
    private void splitTrack(Track track, int divisionNumber, SplitScenario best) {
        if (divisionNumber==0) {
            best.transferFrom(new SplitScenario(track));
            return;
        }
        SplitScenario current = new SplitScenario(track, divisionNumber);
        if (!current.increment(track, 0)) return;
        current.testLastDivisions(track, best);
        if (divisionNumber == 1) return;
        final int lastSplitIdx = divisionNumber-2;
        int splitIdx = lastSplitIdx; // index of the split site currently incremented
        boolean inc;
        while(splitIdx>=0) {
            inc = current.increment(track, splitIdx);
            if (inc) {
                splitIdx = lastSplitIdx;
                current.testLastDivisions(track, best);
            } else --splitIdx;
        }
    }
    
    public SplitScenario divideTrack(Track track, int divisionNumber) {
        if (divisionNumber==0) return new SplitScenario(track);
        SplitScenario best = new SplitScenario(track, divisionNumber);
        splitTrack(track, divisionNumber, best);
        return best;
    }
    
    private double getDistanceProduct(double[] distances) {
        double res = 1;
        for (double d : distances) res*=d;
        return res;
    }
    
    public class SplitScenario {
        int[] splitIndices;
        double score, distanceProduct;
        private SplitScenario(Track track) { // no division case
            this.distanceProduct = getDistanceProduct(track.squareDistances);
            this.score=lengthDistribution.probability(track.frames[track.frames.length]-track.frames[0]) * distanceProduct ;
            this.splitIndices=new int[0];
        }
        public SplitScenario(Track track, int divisionNumber) {
            this.distanceProduct = getDistanceProduct(track.squareDistances);
            this.score=Double.NEGATIVE_INFINITY;
            this.splitIndices= new int[divisionNumber];
        }
        public SplitScenario(double distanceProduct, int divisionNumber) {
            this.distanceProduct = distanceProduct;
            this.score=Double.NEGATIVE_INFINITY;
            this.splitIndices= new int[divisionNumber];
        }
        public void testLastDivisions(Track track, SplitScenario best) {
            int dIdx = splitIndices.length-1;
            for (int frameIdx = splitIndices[dIdx]; frameIdx<track.frames.length; ++frameIdx) {
                if (track.frames[track.frames.length-1] - track.frames[frameIdx] < minLength) break;
                ++splitIndices[dIdx];
                score=getLikelyhood(track, splitIndices, distanceProduct);
                if (score>best.score) best.transferFrom(this);
            }
        }
        public boolean increment(Track track, int divisionIdx) { // sets the frameDivision index at divisionIdx and forward, return true if there is at leat one acceptable scenario
            ++splitIndices[divisionIdx];
            for (int dIdx = divisionIdx+1; dIdx<splitIndices.length; ++dIdx) {
                while(track.frames[splitIndices[dIdx]]-track.frames[splitIndices[dIdx-1]]<minLength) {
                    ++splitIndices[dIdx];
                    if (splitIndices[dIdx]>=track.frames.length-1) return false;
                }
            }
            return track.frames[track.frames.length-1]-track.frames[splitIndices[splitIndices.length-1]]>=minLength;
        }
        public void transferFrom(SplitScenario other) {
            System.arraycopy(other.splitIndices, 0, this.splitIndices, 0, splitIndices.length);
            this.score=other.score;
        }
        
        public <T> List<List<T>> splitTrack(List<T> track) {
            //if (track.size()!=frames.length) throw new IllegalArgumentException("Invalid track length");
            if (splitIndices.length==0) return null;
            List<List<T>> res = new ArrayList<List<T>>(this.splitIndices.length);
            res.add(track.subList(0, splitIndices[0]));
            for (int i = 1; i<splitIndices.length; ++i) res.add(track.subList(splitIndices[i-1]+1, splitIndices[i]));
            res.add(track.subList(splitIndices[splitIndices.length-1]+1, track.size()));
            return res;
        }
        
        @Override public String toString() {
            return "Split Indices: "+Arrays.toString(splitIndices)+ " score: "+score;
        }
    }
    public static class Track {
        int[] frames;
        double[] squareDistances;
        /**
         * 
         * @param frames array of frames of the track length n
         * @param squareDistances array of square distances between spot of a frame and spot of following frame (length = n-1)
         */
        public Track( int[] frames, double[] squareDistances) {
            this.frames=frames;
            this.squareDistances=squareDistances;
        }
        public Track(List<SpotWithinCompartment> track) {
            frames = new int[track.size()];
            squareDistances = new double[frames.length-1];
            int lim = track.size();
            SpotWithinCompartment prev = track.get(0);
            SpotWithinCompartment cur;
            frames[0] = prev.timePoint;
            for (int i = 1; i<lim; ++i) {
                cur = track.get(i);
                frames[i] = cur.timePoint;
                squareDistances[i-1] = prev.squareDistanceTo(cur);
            }
        }
        public int getLength(int startIdx, int stopIdx) {
            return frames[stopIdx] - frames[startIdx];
        }
        public int getLengthFromStart(int stopIdx) {
            return frames[stopIdx] - frames[0];
        }
        public int getLengthToEnd(int startIdx) {
            return frames[frames.length-1] - frames[startIdx];
        }
        public int getLength() {
            return frames[frames.length-1] - frames[0];
        }
        
    }
}
