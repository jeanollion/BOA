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
    double minLength, maxLength; // minimal length for a track
    double theoricalLength;
    //double distanceDXHalf;
    public TrackLikelyhoodEstimator(RealDistribution lengthDistribution, RealDistribution distanceDistribution, int minimalTrackLength) {
        this.lengthDistribution=lengthDistribution;
        this.distanceDistribution=distanceDistribution;
        this.minLength=minimalTrackLength;
        //this.distanceDXHalf=0.1/2d;
        this.theoricalLength = getModalXValue(lengthDistribution, minLength, 0.5d, 0.01);
        maxLength = getMaxXValue(lengthDistribution, theoricalLength, lengthDistribution.density(minLength), 0.5d, 0.01);
        //logger.debug("minLength: {}/D:{}, theorical length: {}/D:{}, maximalLength: {}/D:{}", minLength, lengthDistribution.density(minLength), theoricalLength, lengthDistribution.density(theoricalLength), maxLength, lengthDistribution.density(maxLength));
    }
    
    private static double getMaxXValue(RealDistribution distribution, double minX, double y, double precision1, double precision2) {
        double tempY = distribution.density(minX);
        while(tempY>y) {
            minX+=precision1;
            tempY=distribution.density(minX);
        }
        minX-=precision1;
        tempY=distribution.density(minX);
        while(tempY>y) {
            minX+=precision2;
            tempY=distribution.density(minX);
        }
        return minX;
    }
    
    private static double getModalXValue(RealDistribution distribution, double minX, double precision1, double precision2) { 
        double minProbability = distribution.density(minX);
        double maxX = minX;
        double maxP = minProbability;
        double tempP = maxP;
        double tempX = minX;
        while(tempP>=minProbability) {
            tempX+=precision1;
            tempP = distribution.density(tempX);
            if (tempP>maxP) {
                maxP = tempP;
                maxX = tempX;
            }
        }
        if (precision2 * 10 < precision1) {
            maxX = getModalXValue_(distribution, maxX-precision1, maxX+precision1, precision2*10);
            return getModalXValue_(distribution, maxX-precision2*10, maxX+precision2*10, precision2);
        } else return getModalXValue_(distribution, maxX-precision1, maxX+precision1, precision2);
    }
    
    private static double getModalXValue_(RealDistribution distribution, double lowerXBound, double upperXBound, double precision) { 
        double maxX = upperXBound;
        double maxP = distribution.density(maxX);
        double tempP;
        for (double x = lowerXBound; x<upperXBound; x+=precision) {
            tempP = distribution.density(x);
            if (tempP>maxP) {
                maxP = tempP;
                maxX = x;
            }
        }
        //logger.debug("modal value search: lb: {}, hb: {}, precision: {}, X: {}, p(X): {}", lowerXBound, upperXBound, precision, maxX, maxP);
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
        if (divisionIndices.length==0) return lengthDistribution.density(track.getLength()) * distanceProduct;
        double res = lengthDistribution.density(track.getLengthFromStart(divisionIndices[0])) * lengthDistribution.density(track.getLengthToEnd(divisionIndices[divisionIndices.length-1]+1)) * distanceProduct / distanceDistribution.probability(track.squareDistances[divisionIndices[0]]); // divide to remove the displacement contribution from the product
        for (int i = 1; i<divisionIndices.length; ++i) res*=lengthDistribution.density(track.getLength(divisionIndices[i-1]+1, divisionIndices[i])) / distanceDistribution.density(track.squareDistances[divisionIndices[i]]); 
        return res;

    }
    
    public RealDistribution getLengthDistribution() {
        return lengthDistribution;
    }
    
    public RealDistribution getDistanceDistribution() {
        return distanceDistribution;
    }
    
    public SplitScenario splitTrack(Track track) {
        // get number of divisions
        int[] splitNumbers = getSplitNumber(track);
        logger.debug("split track: divisions Indices: {}", splitNumbers);
        SplitScenario s1 = splitTrack(track, splitNumbers[0]);
        for (int i =1; i<splitNumbers.length; ++i) splitTrack(track, splitNumbers[i], s1);
        return s1;
    }
    
    public int[] getSplitNumber(Track track) {
        if (track.getLength()<=maxLength) return new int[]{0};
        double meanGap = track.getMeanGapLength();
        double rTh = (double) (track.getLength()-theoricalLength) / (theoricalLength+meanGap);
        double rMax = (double) (track.getLength()-minLength) / (minLength+meanGap);
        double rMin = (double) (track.getLength()-maxLength) / (maxLength+meanGap);
        logger.debug("split track: length: {}, rMin {}, rTh: {}, rMax: {}, meanGap: {}",track.getLength(), rMin, rTh, rMax, meanGap);
        int nMin = (int)Math.floor(rMin);
        int nMax = (int)rMax;
        if (nMin==nMax) return new int[]{nMin};
        else if (nMax-nMin==1) return new int[]{nMin, nMax};
        else return new int[]{(int)rTh, 1+(int)rTh};
    }
    
    private void splitTrack(Track track, int divisionNumber, SplitScenario best) {
        if (divisionNumber==0) {
            SplitScenario cur = new SplitScenario(track);
            if (cur.compareTo(best)>0) best.transferFrom(cur);
            return;
        }
        SplitScenario current = new SplitScenario(track, divisionNumber);
        logger.debug("split in: {}, best: {}, current: {}", divisionNumber, best, current);
        if (!current.increment(track, 0)) return;
        current.testLastDivisions(track, best);
        if (divisionNumber == 1) {
            logger.debug("split in: {}, new best: {}", divisionNumber, best);
            return;
        }
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
        logger.debug("split in: {}, new best: {}", divisionNumber, best);
    }
    
    public SplitScenario splitTrack(Track track, int divisionNumber) {
        if (divisionNumber==0) return new SplitScenario(track);
        SplitScenario best = new SplitScenario(track, divisionNumber);
        TrackLikelyhoodEstimator.this.splitTrack(track, divisionNumber, best);
        return best;
    }
    
    private double getDistanceProduct(double[] distances) {
        double res = 1;
        for (double d : distances) res*=distanceDistribution.density(d);
        return res;
    }
    
    public class SplitScenario implements Comparable<SplitScenario> {
        int[] splitIndices;
        double score, distanceProduct;
        private SplitScenario(Track track) { // no division case
            this.distanceProduct = getDistanceProduct(track.squareDistances);
            this.splitIndices=new int[0];
            this.score=getLikelyhood(track, splitIndices, distanceProduct);
        }
        public SplitScenario(Track track, int divisionNumber) {
            this.distanceProduct = getDistanceProduct(track.squareDistances);
            this.score=Double.NEGATIVE_INFINITY;
            this.splitIndices= new int[divisionNumber];
        }
        
        public void testLastDivisions(Track track, SplitScenario best) {
            int dIdx = splitIndices.length-1;
            for (int frameIdx = splitIndices[dIdx]; frameIdx<track.frames.length; ++frameIdx) {
                if (track.getLengthToEnd(frameIdx+1) < minLength) break;
                ++splitIndices[dIdx];
                score=getLikelyhood(track, splitIndices, distanceProduct);
                if (this.compareTo(best)>0) best.transferFrom(this);
                logger.debug("new best: {} (last test: {})", best, this);
            }
            
        }
        public boolean increment(Track track, int divisionIdx) { // sets the frameDivision index at divisionIdx and forward, return true if there is at leat one acceptable scenario
            ++splitIndices[divisionIdx];
            if (divisionIdx==0) {
                while(track.getLengthFromStart(splitIndices[0])<minLength) ++splitIndices[0];
                if (splitIndices[0]>=track.frames.length) {
                    logger.debug("increment: no solution @ idx: {}, scenario: {}", 0, this);
                    return false;
                }
            }
            for (int dIdx = divisionIdx+1; dIdx<splitIndices.length; ++dIdx) {
                splitIndices[dIdx] = splitIndices[dIdx-1]+1;
                while(track.getLength(splitIndices[dIdx-1]+1, splitIndices[dIdx])<minLength) {
                    ++splitIndices[dIdx];
                    if (splitIndices[dIdx]>=track.frames.length-1) {
                        logger.debug("increment: no solution @ idx: {}, scenario: {}", dIdx, this);
                        return false;
                    }
                }
            }
            if (track.getLengthToEnd(splitIndices[splitIndices.length-1])>=minLength) return true;
            else {
                logger.debug("increment: no solution @ idx: {}, scenario: {}", splitIndices.length-1, this);
                return false;
            }
        }
        public void transferFrom(SplitScenario other) {
            if (other.splitIndices.length!=splitIndices.length) splitIndices = new int[other.splitIndices.length];
            System.arraycopy(other.splitIndices, 0, this.splitIndices, 0, splitIndices.length);
            this.score=other.score;
            this.distanceProduct = other.distanceProduct;
        }
        
        public <T> List<List<T>> splitTrack(List<T> track) {
            //if (track.size()!=frames.length) throw new IllegalArgumentException("Invalid track length");
            List<List<T>> res = new ArrayList<List<T>>(this.splitIndices.length+1);
            if (splitIndices.length==0) {
                res.add(track);
                return res;
            }
            res.add(track.subList(0, splitIndices[0]+1));
            for (int i = 1; i<splitIndices.length; ++i) res.add(track.subList(splitIndices[i-1]+1, splitIndices[i]+1));
            res.add(track.subList(splitIndices[splitIndices.length-1]+1, track.size()));
            return res;
        }
        @Override public int compareTo(SplitScenario other) {
            return Double.compare(score, other.score);
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
        public double getMeanGapLength() {
            double mean = 0;
            for (int i = 1; i<frames.length; ++i) mean+= (frames[i]-frames[i-1]);
            return mean/(frames.length-1);
        }
        @Override public String toString() {
            return "Track: "+Arrays.toString(frames);
        }
    }
}
