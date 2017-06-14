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

import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.BoundingBox;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static plugins.Plugin.logger;
import plugins.plugins.trackers.ObjectIdxTracker;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparator;
import plugins.plugins.trackers.trackMate.SpotWithinCompartment.Localization;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SpotCompartiment {
    public final StructureObject object;
    double[] offsetUp, offsetDown;
    //double[] offsetDivisionUp, offsetDivisionDown;
    //int previousDivisionTime;
    int nextDivisionTimePoint;
    double[] offsetDivisionMiddle;
    double[] middleYLimits;
    boolean upperDaughterCell=true;
    public static double middleAreaProportion = 0.5;
    boolean truncated = false;
    public SpotCompartiment(StructureObject o) {
        long t0 = System.currentTimeMillis();
        object = o;
        double[][] poles = getPoles(object.getObject(), 0.5);
        offsetUp = poles[0];
        offsetDown = poles[1];
        //nextDivisionTimePoint = object.getNextDivisionTimePoint();
        nextDivisionTimePoint = getNextDivisionFrame(object, 0.8);
        computeDivisionOffset();
        computeIsUpperDaughterCell();
        truncated = (Double)object.getAttribute("EndOfChannelContact", 0d)>0.45; // estimate if attribute not present
        //if (object.getNext()!=null && object.getNext().getDivisionSiblings(false)!=null) divisionAtNextTimePoint = true;
        //previousDivisionTime = object.getPreviousDivisionTimePoint();
        
        long t1 = System.currentTimeMillis();
        //logger.debug("spotCompartiment: {}, creation time: {}", this, t0-t1);
    }
    
    public static int getNextDivisionFrame(StructureObject o, double sizeProportion) {
        while (o!=null) {
            if (divisionAtNextFrame(o, sizeProportion)) return o.getFrame()+1;
            o=o.getNext();
        }
        return -1;
    }
    public static int getPrevDivisionFrame(StructureObject o, double sizeProportion) {
        while (o.getPrevious()!=null) {
            if (divisionAtNextFrame(o.getPrevious(), sizeProportion)) return o.getFrame();
            o=o.getPrevious();
        }
        return -1;
    }
    public static boolean divisionAtNextFrame(StructureObject prev, double sizeProportion) {
        if (prev.getParent().getNext()==null) return false;
        List<StructureObject> next = new ArrayList<>(prev.getParent().getNext().getChildren(prev.getStructureIdx()));
        next.removeIf(o->!prev.equals(o.getPrevious()));
        if (next.size()>1) return true;
        if (next.size()==1) {
            //logger.debug("div next frame for {}->{}: attibute: {}, size {}, sizePrev*0.8:{} eocp: {}, eoc:{}", prev, next.get(0), (Boolean)next.get(0).getAttribute("TruncatedDivision"), (double)next.get(0).getObject().getSize() , prev.getObject().getSize() * sizeProportion, isEndOfChannel(prev) , isEndOfChannel(next.get(0)));
            Object o = next.get(0).getAttribute("TruncatedDivision", false);
            if (o!=null) return (Boolean)o;
            if (!isEndOfChannel(prev) || !isEndOfChannel(next.get(0))) return false; // only end of channel
            return (double)next.get(0).getObject().getSize() < prev.getObject().getSize() * sizeProportion;
        } else return false;
    }
    public static boolean isEndOfChannel(StructureObject o) {
        return (o==Collections.max(o.getParent().getChildren(o.getStructureIdx()), (o1, o2)->Double.compare(o1.getBounds().getYMean(), o2.getBounds().getYMean())));
    }
    @Override public String toString() {
        return "{"+object.toString()+"offX:"+object.getBounds().getxMin()*object.getScaleXY()+";Y="+object.getBounds().getyMin()*object.getScaleZ()+"|isUpperDaugther:"+upperDaughterCell+"|Ylim:"+Utils.toStringArray(middleYLimits)+"|up:"+Utils.toStringArray(offsetUp)+"|down:"+Utils.toStringArray(offsetDown)+"|middle:"+Utils.toStringArray(offsetDivisionMiddle);
    }
    
    public double[] getOffset(Localization localization) {
        if (Localization.UP.equals(localization)) return offsetUp;
        else if (Localization.LOW.equals(localization)) return this.offsetDown;
        else if (Localization.MIDDLE.equals(localization)) return this.offsetDivisionMiddle;
        else return null;
    }
    
    private static double[] getPole(Object3D o, double margin) {
        int y =  o.getBounds().getyMin()+(int)margin;
        double xMean = 0, zMean = 0, count=0;
        ImageMask mask = o.getMask();
        BoundingBox bds = o.getBounds();
        for (int z = bds.getzMin(); z<=bds.getzMax(); ++z) {
            for (int x = bds.getxMin(); x<=bds.getxMax(); ++x) {
                if (mask.insideMaskWithOffset(x, y, z)) {
                    xMean+=x;
                    zMean+=z;
                    count++;
                }
            }
        }
        if (count==0) {
            xMean = o.getBounds().getYMean();
            zMean = o.getBounds().getZMean();
        } else {
            xMean/=count;
            zMean/=count;
        }
        return new double[]{xMean * o.getScaleXY(), y * o.getScaleXY(), zMean * o.getScaleZ()};
    }
    
    private static double[][] getPoles(Object3D o, double proportionOfWidth) {
        int[] ySize = new int[o.getBounds().getSizeY()];
        double meanYSize = 0;
        ImageMask mask = o.getMask();
        BoundingBox bds = o.getBounds();
        int yMin = bds.getyMin();
        for (int y = yMin; y<=bds.getyMax(); ++y) {
            for (int z = bds.getzMin(); z<=bds.getzMax(); ++z) {
                for (int x = bds.getxMin(); x<=bds.getxMax(); ++x) {
                    if (mask.insideMaskWithOffset(x, y, z)) {
                        ySize[y-yMin]++;
                    }
                }
            }
            meanYSize +=ySize[y-yMin];
        }
        meanYSize /= (double)ySize.length;
        double minYSize = proportionOfWidth * meanYSize;
        int yUp = 0;
        while(ySize[yUp]<minYSize) yUp++;
        double[] poleUp = getPole(o, yUp);
        int yDown = ySize.length-1;
        while(ySize[yDown]<minYSize) yDown--;
        double[] poleDown = getPole(o, yDown);
        return new double[][]{poleUp, poleDown};
    }
    
    private static double[] getYPositionWithinCompartimentByCount(Object3D o, int limit) {
        int count=0, countPrev, countCur=-1;
        ImageMask mask = o.getMask();
        BoundingBox bds = o.getBounds();
        for (int y = bds.getyMin(); y<=bds.getyMax(); ++y) {
            countPrev = count;
            for (int z = bds.getzMin(); z<=bds.getzMax(); ++z) {
                for (int x = bds.getxMin(); x<=bds.getxMax(); ++x) {
                    if (mask.insideMaskWithOffset(x, y, z)) {
                        count++;
                        if (count == limit) countCur = count;
                    }
                }
            }
            if (countCur>0) {
                double p = (countCur-countPrev) / (count - countPrev);
                double yApprox = y * p + (y+1) * (1-p);
                return getPole(o, yApprox-bds.getyMin());
            }
        }
        throw new Error("get Division middle : limit unreached");
    }
    
    private static double[] getYPositionWithinCompartimentByCount(Object3D o, int... limit) {
        int count=0;
        ImageMask mask = o.getMask();
        BoundingBox bds = o.getBounds();
        int currentLimit = 0;
        double[] res = new double[limit.length];
        for (int y = bds.getyMin(); y<=bds.getyMax(); ++y) {
            for (int z = bds.getzMin(); z<=bds.getzMax(); ++z) {
                for (int x = bds.getxMin(); x<=bds.getxMax(); ++x) {
                    if (mask.insideMaskWithOffset(x, y, z)) {
                        count++;
                        if (count == limit[currentLimit]) {
                            res[currentLimit++] = y * o.getScaleXY();
                            if (currentLimit==limit.length) return res;
                        }
                    }
                }
            }
        }
        throw new Error("get Division middle : limit unreached");
    }
    
    /*protected double[] getPoleDivisionOffsetUp(SpotCompartiment previousCompartiment) {
        if (compartimentOffsetDivisionUp==null) {
            // get previous division sibling @ same timePoint
            StructureObject sibling = compartiment.object.getTrackHead().getPrevious();
            while(sibling!=null && sibling.getTimePoint()!=compartiment.object.getTimePoint()) sibling = sibling.getNext();
            if (sibling!=null) {
                compartimentOffsetDivisionUp = getPole(sibling.getObject(), poleMargin, true);
            } else {
                throw new Error("SpotWithinCompartment :: no offset Found: "+compartiment.toString());
                //compartimentOffsetDivision = new double[]{}; // to signal no sibling was found
            }
        }
        return compartimentOffsetDivisionUp;
    }*/
    
    private void computeIsUpperDaughterCell() {
        // suppose div @ trackHead
        List<StructureObject> siblings = object.getTrackHead().getDivisionSiblings(true);
        Collections.sort(siblings, (o1, o2)->Integer.compare(o1.getBounds().getyMin(), o2.getBounds().getyMin()));
        this.upperDaughterCell = siblings.get(0).getTrackHead().equals(object.getTrackHead());
        //logger.debug("object: {}, prev siblings: {}, upper?: {}", object, siblings, upperDaughterCell);
        //object.setAttribute("upper daughter cell", upperDaughterCell);
    }
    
    private static List<StructureObject> getDivisionSiblings(StructureObject previous) {
        if (previous==null || previous.getParent().getNext()==null) return Collections.EMPTY_LIST;
        List<StructureObject> candidates = previous.getParent().getNext().getChildren(previous.getStructureIdx());
        List<StructureObject> res= new ArrayList<>(2);
        for (StructureObject o : candidates) if (previous.equals(o.getPrevious())) res.add(o);
        Collections.sort(res, (o1, o2) -> Integer.compare(o1.getBounds().getyMin(), o2.getBounds().getyMin()));
        return res;
    }
    
    private void computeDivisionOffset() {
        int count = this.object.getMask().count();
        int upperCompartimentCount = count/2;
        if (this.nextDivisionTimePoint>0) {
            StructureObject beforeDiv = this.object.getInTrack(nextDivisionTimePoint-1);
            if (beforeDiv==null) logger.debug("no object found before div at frame: {}, for track: {}, object: {}", nextDivisionTimePoint-1, object.getTrackHead(), object);
            List<StructureObject> siblings = getDivisionSiblings(beforeDiv);
            if (siblings.size()==2) { // cut the object whith the same proportion
                double c1 = (double) siblings.get(0).getMask().count();
                double c2 = (double) siblings.get(1).getMask().count();
                double p = c1 / (c1+c2);
                upperCompartimentCount = (int)(p * count +0.5);
                this.offsetDivisionMiddle = SpotCompartiment.getYPositionWithinCompartimentByCount(object.getObject(), upperCompartimentCount); 
            }
        } 
        if (offsetDivisionMiddle==null) offsetDivisionMiddle = object.getObject().getGeomCenter(true);
        
        // upper and lower bounds for the middle area
        int upperMiddleUpLimit = (int) (upperCompartimentCount/2 - middleAreaProportion/2d * upperCompartimentCount+0.5);
        int upperMiddleLowLimit = (int) (upperCompartimentCount/2 + middleAreaProportion/2d * upperCompartimentCount+0.5);
        int lowerCompartimentCount = count - upperCompartimentCount;
        int lowerMiddleUpLimit = (int) (upperCompartimentCount + lowerCompartimentCount/2 - middleAreaProportion/2d * lowerCompartimentCount+0.5);
        int lowerMiddleLowLimit = (int) (upperCompartimentCount + lowerCompartimentCount/2 + middleAreaProportion/2d * lowerCompartimentCount+0.5);
        middleYLimits = getYPositionWithinCompartimentByCount(object.getObject(), upperMiddleUpLimit, upperMiddleLowLimit, lowerMiddleUpLimit, lowerMiddleLowLimit);
    }
    
    public boolean sameTrackOrDirectChildren(SpotCompartiment nextCompartiment) {
        return (object.getTrackHead()==nextCompartiment.object.getTrackHead() || // same track
                nextCompartiment.object.getTrackHead().getFrame()>object.getFrame() &&  // from a posterior division
                    nextCompartiment.object.getTrackHead().getPrevious()!=null &&
                    nextCompartiment.object.getTrackHead().getPrevious().getTrackHead()==object.getTrackHead());
        }
}
