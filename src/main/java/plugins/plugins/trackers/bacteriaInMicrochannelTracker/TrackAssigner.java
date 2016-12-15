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
package plugins.plugins.trackers.bacteriaInMicrochannelTracker;

import dataStructure.objects.Object3D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import static plugins.Plugin.logger;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.SIIncreaseThld;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debug;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.verboseLevelLimit;

/**
 *
 * @author jollion
 */
public class TrackAssigner {
    final static double significativeSIErrorThld = 0.3; // size increment difference > to this value lead to an error
    final static double SIErrorValue=1; //0.9 -> less weight to sizeIncrement error / 1 -> same weight
    final static double SIIncreaseThld = 0.1;
    public static enum AssignerMode {ADAPTATIVE, RANGE};
    
    double[] currentScore = null;
    private int verboseLevel = 0;
    AssignerMode mode = AssignerMode.ADAPTATIVE;
    final Function<Object3D, Double> sizeFunction;
    final Function<Object3D, Double> sizeIncrementFunction;
    final BiFunction<Object3D, Object3D, Boolean> areFromSameLine;
    final List<Object3D> prev, next;
    final int idxPrevLim, idxNextLim;
    final protected List<Assignment> assignments = new ArrayList();
    Assignment currentAssignment;
    double[] baseSizeIncrement;
    protected TrackAssigner(List<Object3D> prev, List<Object3D> next, double[] baseGrowthRate, Function<Object3D, Double> sizeFunction, Function<Object3D, Double> sizeIncrementFunction, BiFunction<Object3D, Object3D, Boolean> areFromSameLine) {
        idxPrevLim = prev.size();
        idxNextLim = next.size();
        this.prev=prev;
        this.next=next;
        this.sizeFunction = sizeFunction;
        this.sizeIncrementFunction=sizeIncrementFunction;
        if (sizeIncrementFunction==null) mode = AssignerMode.RANGE;
        this.areFromSameLine=areFromSameLine;
        this.baseSizeIncrement=baseGrowthRate;
    }
    private TrackAssigner verboseLevel(int verboseLevel) {
        this.verboseLevel=verboseLevel;
        return this;
    }
    private TrackAssigner setMode(AssignerMode mode) {
        this.mode = mode;
        return this;
    }
    protected TrackAssigner duplicate(boolean duplicateCurrentAssignment) {
        TrackAssigner res = new TrackAssigner(prev, next, baseSizeIncrement, sizeFunction, sizeIncrementFunction, areFromSameLine);
        res.assignments.addAll(assignments);
        if (duplicateCurrentAssignment) {
            res.currentAssignment = currentAssignment.duplicate();
            res.assignments.remove(currentAssignment);
            res.assignments.add(res.currentAssignment);
        }
        res.mode=mode;
        res.verboseLevel=verboseLevel;
        //res.currentScore=currentScore; // do not duplicate scores & previous SI because duplicated objects are subect to modifications..
        //res.previousSizeIncrement=previousSizeIncrement;
        return res;
    }
    
    /**
     * 
     * @return true if there is at least 1 remaining object @ timePoint & timePoint -1
     */
    public boolean nextTrack() {
        //if (debug && idxEnd!=0) logger.debug("t:{}, [{};{}]->[{};{}]", timePoint, idxPrev, idxPrevEnd-1, idx, idxEnd-1);
        if (currentAssignment.idxPrev==idxPrevLim || currentAssignment.idxNext==idxNextLim) return false;
        currentAssignment = new Assignment(currentAssignment.idxPrevEnd(), currentAssignment.idxNextEnd());
        assignments.add(currentAssignment);
        currentAssignment.incrementIfNecessary();
        return true;
    }
    
    private boolean checkNextIncrement() {
        TrackAssigner nextSolution = duplicate(true).verboseLevel(verboseLevel+1);
        // get another solution that verifies inequality
        boolean incrementPrev;
        if (mode==AssignerMode.ADAPTATIVE && !Double.isNaN(currentAssignment.getPreviousSizeIncrement())) incrementPrev = currentAssignment.sizeNext/currentAssignment.sizePrev>currentAssignment.getPreviousSizeIncrement();
        else incrementPrev = currentAssignment.sizePrev<currentAssignment.sizeNext;
        if (incrementPrev) {
            if (!nextSolution.currentAssignment.incrementPrev()) return false;
        } else {
            if (!nextSolution.currentAssignment.incrementNext()) return false;
        }
        nextSolution.currentAssignment.incrementUntilVerifyInequality();
        if (!nextSolution.currentAssignment.verifyInequality()) return false;
        if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, {} next solution: {}, current score: {}, next score: {}", verboseLevel, currentAssignment.toString(false), nextSolution.currentAssignment.toString(false), getCurrentScore(), nextSolution.getCurrentScore());
        //if (debug && verboseLevel<verboseLevelLimit) logger.debug("current: {}, next: {}", this, nextSolution);
        // compare the current & new solution
        double[] newScore = nextSolution.getCurrentScore();
        double curSIIncreaseThld = SIIncreaseThld; // increment only if significative improvement OR objects come from a division at previous timePoint & improvement
        if (incrementPrev && areFromSameLine.apply(prev.get(currentAssignment.idxPrevEnd()-1), prev.get(nextSolution.currentAssignment.idxPrevEnd()-1))) curSIIncreaseThld=0;
        newScore[1]+=curSIIncreaseThld; 
        if (compareScores(getCurrentScore(), newScore, mode!=AssignerMode.RANGE)<=0) return false;
        newScore[1]-=curSIIncreaseThld;
        this.currentScore=nextSolution.currentScore;
        this.assignments.remove(currentAssignment);
        this.currentAssignment = nextSolution.currentAssignment;
        return true;
    }

    public double[] getCurrentScore() {
        if (currentScore==null) {
            currentScore = getScoreForCurrentAndNextAssignments(idxPrevLim, idxNextLim);
        } return currentScore;
    }
    
    private double[] getScoreForCurrentAndNextAssignments(int idxPrevLimit, int idxLimit) { 
        verboseLevel++;
        Assignment current = currentAssignment;
        int assignmentCount = assignments.size();
        if (currentAssignment.truncatedEndOfChannel()) return new double[]{currentAssignment.getErrorCount(), 0}; 
        double[] score = currentAssignment.getScore();
        if (debug && verboseLevel-1<verboseLevelLimit) logger.debug("L:{}, {} score start:{}", verboseLevel-1, currentAssignment, currentScore);
        //if (Double.isNaN(score[1])) return score;
        int count = 1;
        while(nextTrack() && !currentAssignment.truncatedEndOfChannel() && (currentAssignment.idxNextEnd()<=idxLimit || currentAssignment.idxPrevEnd()<=idxPrevLimit)) { // do not take into account score of incomplete division
            double[] newScore=currentAssignment.getScore();
            //score[1] = Math.max(score[1], newScore[1]); // maximum score = worst case scenario
            score[1] += newScore[1];
            ++count;
            score[0]+=newScore[0];
            if (debug && verboseLevel-1<verboseLevelLimit) logger.debug("L:{} score for whole scenario: new score for {}, wcs: {}", verboseLevel-1, currentAssignment, newScore, score);
        }
        score[1] /=count;
        // add unlinked cells
        if (idxPrevLimit==idxPrevLim && currentAssignment.idxPrevEnd()<idxPrevLim-1) score[0]+=idxPrevLim-1 - currentAssignment.idxPrevEnd(); // # of unlinked cells, except the last one
        if (idxLimit==idxNextLim && currentAssignment.idxNextEnd()<idxNextLim-1) score[0]+=idxNextLim-1 - currentAssignment.idxNextEnd();

        // reset to previous state
        verboseLevel--;
        this.currentAssignment=current;
        for (int i = this.assignments.size()-1; i>=assignmentCount; --i) assignments.remove(i);
        return score;
    }
    
    private static int compareScores(double[] s1, double[] s2, boolean useScore) { // 0 = nb errors / 1 = score value. return -1 if first is better
        if (!useScore) return Double.compare(s1[0], s2[0]);
        else {
            if (s1[0]<s2[0]) return -1;
            else if (s1[0]>s2[0]) return 1;
            else if (s1[1]<s2[1]) return -1;
            else if (s1[1]>s2[1]) return 1;
            else return 0;
        }
    }
    
    protected boolean verifyInequality(double sizePrev, double sizeNext) {
        return sizePrev * baseSizeIncrement[0] <= sizeNext && sizeNext <= sizePrev * baseSizeIncrement[1];
    }
    

    @Override public String toString() {
        return "L:"+verboseLevel+ currentAssignment.toString(true);
    }

    public class Assignment {
        List<Object3D> prevObjects;
        List<Object3D> nextObjects;
        int idxPrev, idxNext;
        double sizePrev, sizeNext;
        double previousSizeIncrement = Double.NaN;
        double[] currentScore;
        public Assignment(int idxPrev, int idxNext) {
            prevObjects = new ArrayList();
            nextObjects = new ArrayList();
            this.idxPrev = idxPrev;
            this.idxNext= idxNext;
        }
        public Assignment duplicate() {
            return new Assignment(new ArrayList(prevObjects), new ArrayList(nextObjects), sizePrev, sizeNext, idxPrev, idxNext);
            
        }
        public Assignment(List<Object3D> prev, List<Object3D> next, double sizePrev, double sizeNext, int idxPrev, int idxNext) {
            this.sizeNext=sizeNext;
            this.sizePrev=sizePrev;
            this.prevObjects = prev;
            this.nextObjects = next;
            this.idxPrev= idxPrev;
            this.idxNext = idxNext;
        }
        public int sizePrev() {
            return prevObjects.size();
        }
        public int sizeNext() {
            return nextObjects.size();
        }
        public int idxPrevEnd() {
            return idxPrev + prevObjects.size();
        }
        public int idxNextEnd() {
            return idxNext + nextObjects.size();
        }
        public boolean incrementPrev() {
            if (idxPrevEnd()<idxPrevLim) {
                Object3D o = prev.get(idxPrevEnd());
                prevObjects.add(o);
                sizePrev+=sizeFunction.apply(o);
                previousSizeIncrement = Double.NaN;
                currentScore=null;
                return true;
            } else return false;
        }
        public boolean decrementPrev() {
            if (!prevObjects.isEmpty()) {
                sizePrev-=sizeFunction.apply(prevObjects.remove(prevObjects.size()-1));
                previousSizeIncrement = Double.NaN;
                currentScore=null;
                return true;
            } else return false;
        }
        public boolean incrementNext() {
            if (idxNextEnd()<idxNextLim) {
                Object3D o = next.get(idxNextEnd());
                sizeNext+=sizeFunction.apply(o);
                currentScore=null;
                return true;
            } else return false;
        }
        public boolean decrementNext() {
            if (!nextObjects.isEmpty()) {
                sizeNext-=sizeFunction.apply(nextObjects.remove(nextObjects.size()-1));
                return true;
            } else return false;
        }
        protected void incrementIfNecessary() {
            if (prevObjects.isEmpty()) incrementPrev();
            if (nextObjects.isEmpty()) incrementNext();
            incrementUntilVerifyInequality();
            if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{} start increment: {}", verboseLevel, this);
            if (!verifyInequality()) return;
            boolean change = true;
            while (change) change = checkNextIncrement();
        }
                
        protected void incrementUntilVerifyInequality() {
            previousSizeIncrement=Double.NaN;
            currentScore=null;
            boolean change = false;
            while(!verifyInequality()) {
                change = false;
                if (sizePrev * baseSizeIncrement[1] < sizeNext) {
                    if (!incrementPrev()) return;
                    else change = true;
                } else if (sizePrev * baseSizeIncrement[0] > sizeNext) {
                    if (!incrementNext()) return;
                    else change = true;
                } else if (!change) return;
            }
        }
        public double getPreviousSizeIncrement() {
            if (Double.isNaN(previousSizeIncrement) && !prev.isEmpty()) {
                previousSizeIncrement = sizeIncrementFunction.apply(prev.get(0));
                if (prev.size()>1) { // size-weighted barycenter of size increment lineage
                    double totalSize= sizeFunction.apply(prev.get(0));
                    previousSizeIncrement *= totalSize;
                    for (int i = 1; i<prev.size(); ++i) { 
                        double curSI = previousSizeIncrement = sizeIncrementFunction.apply(prev.get(i));
                        if (!Double.isNaN(curSI)) {
                            previousSizeIncrement+= curSI * sizeFunction.apply(prev.get(i));
                            totalSize += sizeFunction.apply(prev.get(i));
                        }
                    }
                    previousSizeIncrement/=totalSize;
                }
            }
            return previousSizeIncrement;
        }
        
        public boolean verifyInequality() {
            return TrackAssigner.this.verifyInequality(sizePrev, sizePrev);
        }
        public boolean truncatedEndOfChannel() {
            return (idxNextEnd()==idxNextLim   && 
                    (mode==AssignerMode.ADAPTATIVE && !Double.isNaN(getPreviousSizeIncrement()) ? getPreviousSizeIncrement()-sizeNext/sizePrev>significativeSIErrorThld : sizePrev * baseSizeIncrement[0] > sizeNext) ); //&& idxEnd-idx==1 // && idxPrevEnd-idxPrev==1
        }
        public boolean needCorrection() {
            return (idxPrevEnd()-idxPrev)>1; //|| (sizePrev * maxGR < size); et supprimer @ increment.. 
        }
        public boolean canBeCorrected() {
            return needCorrection();// && (idxEnd-idx==1) ;
        }
        protected double[] getScore() {
            double prevSizeIncrement = mode==AssignerMode.ADAPTATIVE ? getPreviousSizeIncrement() : Double.NaN;
            if (Double.isNaN(prevSizeIncrement)) return new double[]{this.getErrorCount(), Double.NaN};
            if (debug && verboseLevel<verboseLevelLimit) logger.debug("L:{}, assignement score: prevSI: {}, SI: {}", verboseLevel, prevSizeIncrement, sizeNext/sizePrev);
            return new double[]{getErrorCount(), Math.abs(prevSizeIncrement - sizeNext/sizePrev)};
        }
        
        public int getErrorCount() {
            int res =  Math.max(0, nextObjects.size()-2) + // division in more than 2
                    prevObjects.size()-1; // merging
            //if ((!verifyInequality() || significantSizeIncrementError()) && !truncatedEndOfChannel()) ++res; // bad size increment
            if (!truncatedEndOfChannel()) {
                if (!verifyInequality()) res+=1;
                else if (significantSizeIncrementError()) res+=SIErrorValue;//res+=0.9;
            }
            return res;        
        }
        
        public boolean significantSizeIncrementError() {
            if (mode==AssignerMode.ADAPTATIVE) {
            double prevSizeIncrement = getPreviousSizeIncrement();
            if (Double.isNaN(prevSizeIncrement)) {
                return !verifyInequality();
            } else {
                double sizeIncrement = sizeNext/sizePrev;
                if (debug && verboseLevel<verboseLevelLimit) logger.debug("{}, sizeIncrementError check: SI:{} lineage SI: {}, error: {}", this, sizeIncrement, prevSizeIncrement, Math.abs(prevSizeIncrement-sizeIncrement));
                return Math.abs(prevSizeIncrement-sizeIncrement)>significativeSIErrorThld;
            }
            } else return !verifyInequality();
        }
        public String toString(boolean size) {
            String res = "["+idxPrev+";"+(idxPrevEnd()-1)+"]->[" + idxNext+";"+(idxNextEnd()-1)+"]";
            if (size) res +="/Sizes:"+String.format("%.2f", sizePrev)+ "->"+String.format("%.2f", sizeNext)+ "/Ineq:"+verifyInequality()+"/Errors:"+getErrorCount()+"/SI:"+sizeNext/sizePrev+"/SIPrev:"+getPreviousSizeIncrement();
            return res;
        }
        @Override public String toString() {
            return toString(true);
        }
    }

}

