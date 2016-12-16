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
import java.util.List;
import java.util.ListIterator;
import static plugins.Plugin.logger;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debug;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.verboseLevelLimit;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.TrackAssigner.SIErrorValue;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.TrackAssigner.significativeSIErrorThld;

/**
 *
 * @author jollion
 */
public class Assignment {
        List<Object3D> prevObjects;
        List<Object3D> nextObjects;
        int idxPrev, idxNext;
        double sizePrev, sizeNext;
        double previousSizeIncrement = Double.NaN;
        double[] currentScore;
        TrackAssigner ta;
        public Assignment(TrackAssigner ta, int idxPrev, int idxNext) {
            prevObjects = new ArrayList();
            nextObjects = new ArrayList();
            this.idxPrev = idxPrev;
            this.idxNext= idxNext;
            this.ta=ta;
        }
        public Assignment setTrackAssigner(TrackAssigner ta) {
            this.ta =ta;
            return this;
        }
        public Assignment duplicate(TrackAssigner ta) {
            return new Assignment(new ArrayList(prevObjects), new ArrayList(nextObjects), sizePrev, sizeNext, idxPrev, idxNext).setTrackAssigner(ta);
        }
        public void transferData(Assignment other) {
            this.prevObjects=other.prevObjects;
            this.nextObjects=other.nextObjects;
            this.idxNext=other.idxNext;
            this.idxPrev=other.idxPrev;
            this.sizePrev=other.sizePrev;
            this.sizeNext=other.sizeNext;
            this.previousSizeIncrement=other.previousSizeIncrement;
            this.currentScore=other.currentScore;
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
            if (idxPrevEnd()<ta.idxPrevLim) {
                Object3D o = ta.prev.get(idxPrevEnd());
                prevObjects.add(o);
                sizePrev+=ta.sizeFunction.apply(o);
                previousSizeIncrement = Double.NaN;
                currentScore=null;
                return true;
            } else return false;
        }
        public boolean incrementNext() {
            if (idxNextEnd()<ta.idxNextLim) {
                Object3D o = ta.next.get(idxNextEnd());
                nextObjects.add(o);
                sizeNext+=ta.sizeFunction.apply(o);
                currentScore=null;
                return true;
            } else return false;
        }
        public boolean remove(boolean prev, boolean removeFirst) {
            if (prev) {
                if (!prevObjects.isEmpty()) {
                    sizePrev-=ta.sizeFunction.apply(prevObjects.remove(removeFirst ? 0 : prevObjects.size()-1));
                    previousSizeIncrement = Double.NaN;
                    currentScore=null;
                    return true;
                } else return false;
            } else {
                if (!nextObjects.isEmpty()) {
                    sizeNext-=ta.sizeFunction.apply(nextObjects.remove(removeFirst ? 0 : nextObjects.size()-1));
                    return true;
                } else return false;
            }
        }
        public boolean removeUntil(boolean prev, boolean removeFirst, int n) {
            List<Object3D> l = prev ? prevObjects : nextObjects;
            if (l.size()<=n) return false;
            currentScore=null;
            if (prev) previousSizeIncrement = Double.NaN;
            ListIterator<Object3D> it = l.listIterator(removeFirst ? 0 : l.size());
            while(l.size()>n) {
                if (prev) sizePrev -= ta.sizeFunction.apply(removeFirst ? it.next() : it.previous());
                else sizeNext -= ta.sizeFunction.apply(removeFirst ? it.next() : it.previous());
                it.remove();
            }
            return true;
        }

        protected void incrementIfNecessary() {
            if (prevObjects.isEmpty()) incrementPrev();
            if (nextObjects.isEmpty()) incrementNext();
            if (debug && ta.verboseLevel<verboseLevelLimit) logger.debug("L:{} start increment: {}", ta.verboseLevel, this);
            incrementUntilVerifyInequality();
            
            if (!verifyInequality()) return;
            boolean change = true;
            if (ta.currentAssignment!=this) throw new Error("TA's currentAssignment should be calling assignment");
            while (change) change = ta.checkNextIncrement();
        }
                
        protected void incrementUntilVerifyInequality() {
            while(!verifyInequality()) {
                if (sizePrev * ta.baseSizeIncrement[1] < sizeNext) {
                    if (!incrementPrev()) return;
                } else if (sizePrev * ta.baseSizeIncrement[0] > sizeNext) {
                    if (!incrementNext()) return;
                } 
            }
        }
        public double getPreviousSizeIncrement() {
            if (Double.isNaN(previousSizeIncrement) && !ta.prev.isEmpty()) {
                previousSizeIncrement = ta.sizeIncrementFunction.apply(ta.prev.get(0));
                if (ta.prev.size()>1) { // size-weighted barycenter of size increment lineage
                    double totalSize= ta.sizeFunction.apply(ta.prev.get(0));
                    previousSizeIncrement *= totalSize;
                    for (int i = 1; i<ta.prev.size(); ++i) { 
                        double curSI = previousSizeIncrement = ta.sizeIncrementFunction.apply(ta.prev.get(i));
                        if (!Double.isNaN(curSI)) {
                            previousSizeIncrement+= curSI * ta.sizeFunction.apply(ta.prev.get(i));
                            totalSize += ta.sizeFunction.apply(ta.prev.get(i));
                        }
                    }
                    previousSizeIncrement/=totalSize;
                }
            }
            return previousSizeIncrement;
        }
        
        public boolean verifyInequality() {
            return ta.verifyInequality(sizePrev, sizeNext);
        }
        public boolean truncatedEndOfChannel() {
            return (ta.truncatedChannel && idxNextEnd()==ta.idxNextLim   && 
                    (ta.mode==TrackAssigner.AssignerMode.ADAPTATIVE && !Double.isNaN(getPreviousSizeIncrement()) ? getPreviousSizeIncrement()-sizeNext/sizePrev>significativeSIErrorThld : sizePrev * ta.baseSizeIncrement[0] > sizeNext) ); //&& idxEnd-idx==1 // && idxPrevEnd-idxPrev==1
        }
        public boolean needCorrection() {
            return (idxPrevEnd()-idxPrev)>1; //|| (sizePrev * maxGR < size); et supprimer @ increment.. 
        }
        public boolean canBeCorrected() {
            return needCorrection();// && (idxEnd-idx==1) ;
        }
        protected double[] getScore() {
            double prevSizeIncrement = ta.mode==TrackAssigner.AssignerMode.ADAPTATIVE ? getPreviousSizeIncrement() : Double.NaN;
            if (Double.isNaN(prevSizeIncrement)) return new double[]{this.getErrorCount(), Double.NaN};
            if (debug && ta.verboseLevel<verboseLevelLimit) logger.debug("L:{}, assignement score: prevSI: {}, SI: {}", ta.verboseLevel, prevSizeIncrement, sizeNext/sizePrev);
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
            if (ta.mode==TrackAssigner.AssignerMode.ADAPTATIVE) {
            double prevSizeIncrement = getPreviousSizeIncrement();
            if (Double.isNaN(prevSizeIncrement)) {
                return !verifyInequality();
            } else {
                double sizeIncrement = sizeNext/sizePrev;
                if (debug && ta.verboseLevel<verboseLevelLimit) logger.debug("L:{}: {}, sizeIncrementError check: SI:{} lineage SI: {}, error: {}", ta.verboseLevel, this, sizeIncrement, prevSizeIncrement, Math.abs(prevSizeIncrement-sizeIncrement));
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
