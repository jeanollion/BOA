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
import fiji.plugin.trackmate.Spot;
import image.Image;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Collections;
import plugins.plugins.trackers.ObjectIdxTracker;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparator;

/**
 *
 * @author jollion
 */
public class SpotWithinCompartment extends Spot {
    protected StructureObject object;
    protected SpotCompartiment compartiment;
    
    
    public SpotWithinCompartment(StructureObject object, SpotCompartiment compartiment, double[] scaledCenter) {
        super(scaledCenter[0], scaledCenter[1], scaledCenter[2], 1, 1);
        getFeatures().put(Spot.FRAME, (double)compartiment.object.getTimePoint());
        this.compartiment=compartiment;
        this.object=object;
    }
    
   
    public void setRadius() {
        double radius = object.getObject().is3D() ? Math.pow(3 * object.getObject().getSize() / (4 * Math.PI) , 1d/3d) : Math.sqrt(object.getObject().getSize() / (2 * Math.PI)) ;
        getFeatures().put(Spot.RADIUS, radius);
    }
    
    public void setQuality(Image intensityMap) {
        int[] center = getCenterInVoxels();
        double quality = intensityMap!=null ? object.getObject().isAbsoluteLandMark() ? intensityMap.getPixelWithOffset(center[0], center[1], center[2]) : intensityMap.getPixel(center[0], center[1], center[2]) : 1;
        getFeatures().put(Spot.QUALITY, quality);
    }
    
    private int[] getCenterInVoxels() {
        int[] center =  new int[]{(int)(getFeature(Spot.POSITION_X)/object.getScaleXY()), (int)(getFeature(Spot.POSITION_Y)/object.getScaleXY()), 0};
        if (object.getScaleZ()!=0) center[2] = (int)(getFeature(Spot.POSITION_Z)/object.getScaleZ());
        return center;
    }
    
    @Override
    public double normalizeDiffTo( final Spot s, final String feature ) {
        if (s instanceof SpotWithinCompartment) {
            final double a = getFeature( feature );
            final double b = s.getFeature( feature );
            if ( a == -b ) return 0d;
            else return Math.abs( a - b ) / ( ( a + b ) / 2 );
        } else return super.normalizeDiffTo(s, feature);
    }
    
    @Override
    public double squareDistanceTo( final Spot s ) {
        if (s instanceof SpotWithinCompartment) {
            SpotWithinCompartment ss = (SpotWithinCompartment)s;
            if (this.compartiment.object.getTimePoint()>ss.compartiment.object.getTimePoint()) return ss.squareDistanceTo(this);
            else {
                if (compartiment.sameTrackOrDirectChildren(ss.compartiment)) { // spot in the same track or separated by one division at max
                    //if (ss.compartiment.previousDivisionTime>=compartiment.object.getTimePoint()) return getSquareDistanceDivision(ss);
                    if (this.compartiment.nextDivisionTimePoint<=ss.compartiment.object.getTimePoint()) return getSquareDistanceDivision(ss);
                    else return getSquareDistanceCompartiments(ss);
                } else return Double.POSITIVE_INFINITY; // spots in different tracks -> no link possible
            }
        } else return super.squareDistanceTo(s);
    }
    
    protected double getSquareDistanceCompartiments(SpotWithinCompartment s) {
        double d1 = getSquareDistance(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp);
        double d2 = getSquareDistance(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown);
        if (this.object.getTimePoint()==33 && s.object.getTimePoint()==35) {
                LAPTrackerCore.logger.debug("distance: {} to {}, d1: {}, d2: {}, this offUp: {}, this offDown: {}, offUp: {}, offDown: {}", this.object.getBounds(), s.object.getBounds(), d1, d2, this.compartiment.offsetUp, this.compartiment.offsetDown, s.compartiment.offsetUp, s.compartiment.offsetDown );
        }
        return Math.min(d1, d2);
    }
    protected double getSquareDistanceDivision(SpotWithinCompartment sAfterDivision) {
        //double d1 = getSquareDistance(this, this.compartiment.offsetUp, sAfterDivision, sAfterDivision.compartiment.offsetDivisionUp);
        //double d2 = getSquareDistance(this, this.compartiment.offsetDown, sAfterDivision, sAfterDivision.compartiment.offsetDivisionDown);
        double d1, d2;
        if (sAfterDivision.compartiment.object.getTrackHead()==this.compartiment.object.getTrackHead()) { // this test is only valid for increasing Y-coords when growing
            d1 = getSquareDistance(this, this.compartiment.offsetUp, sAfterDivision, sAfterDivision.compartiment.offsetUp);
            d2 = getSquareDistance(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetDown);
        } else {
            d1 = getSquareDistance(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetUp);
            d2 = getSquareDistance(this, this.compartiment.offsetDown, sAfterDivision, sAfterDivision.compartiment.offsetDown);
        }
        
        if (this.object.getTimePoint()==33 && sAfterDivision.object.getTimePoint()==35) {
                LAPTrackerCore.logger.debug("distance (DIV): {} to {}, d1: {}, d2: {}, this offMiddle: {}, offUp: {}, offDown: {}", this.object.getBounds(), sAfterDivision.object.getBounds(), d1, d2, this.compartiment.offsetDivisionMiddle, sAfterDivision.compartiment.offsetUp, sAfterDivision.compartiment.offsetDown);
        }
        return Math.min(d1, d2);
    }
    protected static double getSquareDistance(Spot s1, double[] offset1, Spot s2, double[] offset2) {
        if (offset1==null || offset2==null) return Double.POSITIVE_INFINITY; //TODO fix bug -> when reach test's limit timePoint, offset are null
        
        return Math.pow((s1.getFeature(POSITION_X)-offset1[0] - s2.getFeature(POSITION_X)+offset2[0]), 2) +
            Math.pow((s1.getFeature(POSITION_Y)-offset1[1] - s2.getFeature(POSITION_Y)+offset2[1]), 2) + 
            Math.pow((s1.getFeature(POSITION_Z)-offset1[2] - s2.getFeature(POSITION_Z)+offset2[2]), 2);
    }
}
