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
import image.BoundingBox;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Collections;
import plugins.plugins.trackers.ObjectIdxTracker;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparator;

/**
 *
 * @author jollion
 */
public class SpotCompartiment {
    StructureObject object;
    double[] offsetUp, offsetDown;
    //double[] offsetDivisionUp, offsetDivisionDown;
    //int previousDivisionTime;
    int nextDivisionTimePoint;
    double[] offsetDivisionMiddle;
    static final int poleMargin = 3;
    public SpotCompartiment(StructureObject o) {
        object = o;
        offsetUp = getPole(object.getObject(), poleMargin, true);
        offsetDown = getPole(object.getObject(), poleMargin, false);
        computeDivisionOffset();
        if (object.getTimePoint()==0) {
            LAPTrackerCore.logger.debug("object: {}, bds: {}, pole up: {},  pole donw: {}", object.getIdx(), o.getBounds(),offsetUp, offsetDown );
        }
        //if (object.getNext()!=null && object.getNext().getDivisionSiblings(false)!=null) divisionAtNextTimePoint = true;
        //previousDivisionTime = object.getPreviousDivisionTimePoint();
        nextDivisionTimePoint = object.getNextDivisionTimePoint();
    }
    
    private static double[] getPole(Object3D o, int margin, boolean upper) {
        int y = upper ? o.getBounds().getyMin()+margin : o.getBounds().getyMax() - margin;
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
    private static double[] getMiddle(Object3D o, int limit) {
        int count=0;
        ImageMask mask = o.getMask();
        BoundingBox bds = o.getBounds();
        for (int y = bds.getyMin(); y<=bds.getyMax(); ++y) {
            for (int z = bds.getzMin(); z<=bds.getzMax(); ++z) {
                for (int x = bds.getxMin(); x<=bds.getxMax(); ++x) {
                    if (mask.insideMaskWithOffset(x, y, z)) {
                        count++;
                        if (count == limit) return getPole(o, y, true);
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
    
    private static ArrayList<StructureObject> getDivisionSiblings(StructureObject object) {
        ArrayList<StructureObject> res = null;
        ArrayList<StructureObject> siblings = object.getSiblings();
        for (StructureObject s : siblings) {
            if (s!=object) {
                if (s.getTrackHead().getPrevious()!=null && s.getTrackHead().getPrevious().getTrackHead()==object.getTrackHead()) {
                    if (res==null) res = new ArrayList<StructureObject>(siblings.size());
                    res.add(s);
                }
            }
        }
        if (res!=null) {
            res.add(object);
            Collections.sort(res, getComparator(ObjectIdxTracker.IndexingOrder.YXZ));
        }
        return res;
    }
    private void computeDivisionOffset() {
        /*ArrayList<StructureObject> siblings = getDivisionSiblings(object);
        if (siblings!=null) {
            offsetDivisionUp = getPole(siblings.get(0).getObject(), poleMargin, true);
            offsetDivisionDown = getPole(siblings.get(siblings.size()-1).getObject(), poleMargin, false);
        } else {
            offsetDivisionUp = offsetUp;
            offsetDivisionDown = offsetDown;
        }*/
        if (this.nextDivisionTimePoint>0) {
            StructureObject parent = this.object.getInTrack(nextDivisionTimePoint);
            ArrayList<StructureObject> siblings = getDivisionSiblings(parent);
            
            if (siblings.size()==2) { // cut the object whith the same proportion
                double c1 = (double) siblings.get(0).getMask().count();
                double c2 = (double) siblings.get(1).getMask().count();
                double p = c1 / (c1+c2);
                int limit = (int)(p * this.object.getMask().count()+0.5);
                this.offsetDivisionMiddle = getMiddle(object.getObject(), limit);
                
            } else { // cut in the middle
                offsetDivisionMiddle = new double[]{object.getBounds().getXMean(), object.getBounds().getYMean(), object.getBounds().getZMean()};
            }
        } 
        
    }
    public boolean sameTrackOrDirectChildren(SpotCompartiment nextCompartiment) {
        return (object.getTrackHead()==nextCompartiment.object.getTrackHead() || // same track
                nextCompartiment.object.getTrackHead().getTimePoint()>object.getTimePoint() &&  // from a posterior division
                    nextCompartiment.object.getTrackHead().getPrevious()!=null &&
                    nextCompartiment.object.getTrackHead().getPrevious().getTrackHead()==object.getTrackHead());
        }
}
