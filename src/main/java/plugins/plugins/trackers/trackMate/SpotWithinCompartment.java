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

import boa.gui.imageInteraction.ImageObjectInterface;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import fiji.plugin.trackmate.Spot;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import image.BoundingBox;
import image.Image;
import java.util.Locale;
import static plugins.Plugin.logger;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SpotWithinCompartment extends Spot {
    public static ImageObjectInterface bacteria;
    public static Overlay testOverlay;
    public static boolean displayPoles=false;
    public static double displayDistanceThreshold = 1; 
    public static double poleDistanceFactor = 0; 
    protected Object3D object;
    protected SpotCompartiment compartiment;
    protected final Localization localization;
    final int timePoint;
    boolean isLinkable=true;
    boolean lowQuality=false;
    protected final DistanceComputationParameters distanceParameters;
    
    public SpotWithinCompartment(Object3D object, int timePoint, SpotCompartiment compartiment, double[] scaledCenter, DistanceComputationParameters distanceParameters) {
        super(scaledCenter[0], scaledCenter[1], scaledCenter[2], 1, 1);
        getFeatures().put(Spot.FRAME, (double)compartiment.object.getTimePoint());
        getFeatures().put(Spot.QUALITY, object.getQuality());
        this.compartiment=compartiment;
        this.object=object;
        this.timePoint=timePoint;
        if (scaledCenter[1]<(compartiment.middleYLimits[0])) localization = Localization.UP;
        else if (scaledCenter[1]>=compartiment.middleYLimits[0] && scaledCenter[1]<=compartiment.middleYLimits[1]) localization = Localization.UPPER_MIDDLE;
        else if (scaledCenter[1]>compartiment.middleYLimits[1] && scaledCenter[1]<compartiment.middleYLimits[2]) localization = Localization.MIDDLE;
        else if (scaledCenter[1]>compartiment.middleYLimits[3]) localization = Localization.LOW;
        else localization = Localization.LOWER_MIDDLE;
        if (displayPoles && testOverlay!=null && bacteria!=null) {
            BoundingBox off1 = bacteria.getObjectOffset(this.compartiment.object).duplicate().translate(this.compartiment.object.getBounds().duplicate().reverseOffset());
            int[] c1 = this.getCenterInVoxels();
            c1[0]+=off1.getxMin();
            c1[1]+=off1.getyMin();
            TextRoi position  = new TextRoi(c1[0], c1[1], localization.toString());
            testOverlay.add(position);
        }
        this.distanceParameters=distanceParameters;
        this.isLinkable=object.getQuality()>=distanceParameters.qualityThreshold;
        this.lowQuality=!isLinkable;
    }
    
    
    public SpotWithinCompartment duplicate() {
        double[] scaledCenter =  new double[]{getFeature(Spot.POSITION_X), getFeature(Spot.POSITION_Y), getFeature(Spot.POSITION_Z)};
        SpotWithinCompartment res = new SpotWithinCompartment(object, timePoint, compartiment, scaledCenter, distanceParameters);
        res.getFeatures().put(Spot.QUALITY, getFeature(Spot.QUALITY));
        res.getFeatures().put(Spot.RADIUS, getFeature(Spot.RADIUS));
        return res;
    }
    
   
    public void setRadius() {
        double radius = object.is3D() ? Math.pow(3 * object.getSize() / (4 * Math.PI) , 1d/3d) : Math.sqrt(object.getSize() / (2 * Math.PI)) ;
        getFeatures().put(Spot.RADIUS, radius);
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
            //if (!isLinkable && !ss.isLinkable) return Double.POSITIVE_INFINITY; // no link allowed between to spots that are not linkable
            if (this.compartiment.object.getTimePoint()>ss.compartiment.object.getTimePoint()) return ss.squareDistanceTo(this);
            else {
                if (compartiment.sameTrackOrDirectChildren(ss.compartiment)) { // spot in the same track or separated by one division at max
                    //if (ss.compartiment.previousDivisionTime>=compartiment.object.getTimePoint()) return getSquareDistanceDivision(ss);
                    if (this.compartiment.nextDivisionTimePoint>=0 && this.compartiment.nextDivisionTimePoint<=ss.compartiment.object.getTimePoint()) return getSquareDistanceDivision(ss);
                    else return getSquareDistanceCompartiments(ss);
                } else return Double.POSITIVE_INFINITY; // spots in different tracks -> no link possible
            }
        } else return super.squareDistanceTo(s);
    }
    
    protected double getSquareDistanceCompartiments(SpotWithinCompartment s) {
        Localization offsetType = this.localization.getOffsetType(s.localization);
        if (offsetType==null) return Double.POSITIVE_INFINITY;
        else if (Localization.UP.equals(offsetType)) {
            double d=  getSquareDistance(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp);
            if (displayPoles) displayOffsets(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp, d);
            return d;
        } else if (Localization.LOW.equals(offsetType)) {
            double d =  getSquareDistance(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown);
            if (displayPoles) displayOffsets(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown, d);
            return d;
        } else if (Localization.MIDDLE.equals(offsetType)) {
           double d=  getSquareDistance(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle);
            if (displayPoles) displayOffsets(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle, d);
            return d;
        }  else if (Localization.UPPER_MIDDLE.equals(offsetType)) {
            double d1 = getSquareDistance(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp);
            double d2 = getSquareDistance(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle);
            if (displayPoles) {
                if (d1>d2) displayOffsets(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle, d2);
                else displayOffsets(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp, d1);
            }
            return Math.min(d1, d2);
        } else { // LOWER_MIDDLE
            double d1 = getSquareDistance(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown);
            double d2 = getSquareDistance(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle);
            if (displayPoles) {
                if (d1>d2) displayOffsets(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle, d2);
                else displayOffsets(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown, d1);
            }
            return Math.min(d1, d2);
        }
    }
    protected double getSquareDistanceDivision(SpotWithinCompartment sAfterDivision) {
        boolean upperDaughterCell = sAfterDivision.compartiment.object.getTrackHead()==this.compartiment.object.getTrackHead(); // daughter cell is in the upper part. TODO change test :: this test is only valid for increasing Y-coords when growing
        Localization[] offsetType = localization.getOffsetTypeDivision(sAfterDivision.localization, upperDaughterCell);
        if (offsetType==null) return Double.POSITIVE_INFINITY;
        if (offsetType.length==2) {
            double[] off1  = this.compartiment.getOffset(offsetType[0]);
            double[] off2  = sAfterDivision.compartiment.getOffset(offsetType[1]);
            if (displayPoles) displayOffsets(this, off1, sAfterDivision, off2, getSquareDistance(this, off1, sAfterDivision, off2));
            return getSquareDistance(this, off1, sAfterDivision, off2);
        } else {
            double[] off1  = this.compartiment.getOffset(offsetType[0]);
            double[] off2  = sAfterDivision.compartiment.getOffset(offsetType[1]);
            double d1 = getSquareDistance(this, off1, sAfterDivision, off2);
            double[] off12  = this.compartiment.getOffset(offsetType[2]);
            double[] off22  = sAfterDivision.compartiment.getOffset(offsetType[3]);
            double d2 = getSquareDistance(this, off12, sAfterDivision, off22);
            if (d1>d2) {
                if (displayPoles) displayOffsets(this, off12, sAfterDivision, off22, d2);
                return d2;
            } else {
                if (displayPoles) displayOffsets(this, off1, sAfterDivision, off2, d1);
                return d1;
            }
        }
        
    }
    /*protected static double getSquareDistance(Spot s1, double[] offset1, Spot s2, double[] offset2) {
        if (offset1==null || offset2==null) return Double.POSITIVE_INFINITY; //TODO fix bug -> when reach test's limit timePoint, offset are null
        return Math.pow((s1.getFeature(POSITION_X)-offset1[0] - s2.getFeature(POSITION_X)+offset2[0]), 2) +
            Math.pow((s1.getFeature(POSITION_Y)-offset1[1] - s2.getFeature(POSITION_Y)+offset2[1]), 2) + 
            Math.pow((s1.getFeature(POSITION_Z)-offset1[2] - s2.getFeature(POSITION_Z)+offset2[2]), 2);
    }*/
    
    @Override public String toString() {
        return "spot:"+this.object.getLabel()+"t="+timePoint;
    }
    
    protected static double getSquareDistance(SpotWithinCompartment s1, double[] offset1, SpotWithinCompartment s2, double[] offset2) {
        if (offset1==null || offset2==null) return Double.POSITIVE_INFINITY; //TODO fix bug -> when reach test's limit timePoint, offset are null
        double x1 = s1.getFeature(POSITION_X);
        double x2 = s2.getFeature(POSITION_X);
        double y1 = s1.getFeature(POSITION_Y);
        double y2 = s2.getFeature(POSITION_Y);
        double z1 = s1.getFeature(POSITION_Z);
        double z2 = s2.getFeature(POSITION_Z);
        double d = Math.pow((x1-offset1[0] - x2+offset2[0]), 2) + Math.pow((y1-offset1[1] - y2+offset2[1]), 2) +  Math.pow((z1-offset1[2] - z2+offset2[2]), 2);
        // correction to favorize a specific direction towards a pole
        //double dPole1 = Math.pow((x1-offset1[0]), 2) + Math.pow((y1-offset1[1]), 2) +  Math.pow((z1-offset1[2]), 2);
        //double dPole2 = Math.pow((x2-offset2[0]), 2) + Math.pow((y2-offset2[1]), 2) +  Math.pow((z2-offset2[2]), 2);
        //if (dPole2>dPole1) d+=poleDistanceFactor*(Math.pow(Math.sqrt(dPole2)-Math.sqrt(dPole1), 2));
        /*double dPole1 = Math.abs(y1-offset1[1]);
        double dPole2 = Math.abs(y2-offset2[1]);
        if (dPole2>dPole1) d+=poleDistanceFactor * (dPole2-dPole1);*/
        // additional gap penalty
        d+= s1.distanceParameters.getDistancePenalty(s1.timePoint, s2.timePoint);
        return d;
    }
    
    private static void displayOffsets(SpotWithinCompartment s1, double[] offset1, SpotWithinCompartment s2, double[] offset2, double distance ) {
        if (bacteria!=null && testOverlay!=null) {
            if (distance>displayDistanceThreshold) return;
            BoundingBox off1 = bacteria.getObjectOffset(s1.compartiment.object).duplicate().translate(s1.compartiment.object.getBounds().duplicate().reverseOffset());
            BoundingBox off2 = bacteria.getObjectOffset(s2.compartiment.object).duplicate().translate(s2.compartiment.object.getBounds().duplicate().reverseOffset());
            
            int[] c1 = s1.getCenterInVoxels();
            int[] c2 = s2.getCenterInVoxels();
            int[] cOff1 = new int[]{(int) (offset1[0] / s1.object.getScaleXY()), (int) (offset1[1] / s1.object.getScaleXY())};
            int[] cOff2 = new int[]{(int) (offset2[0] / s1.object.getScaleXY()), (int) (offset2[1] / s1.object.getScaleXY())};
            c1[0]+=off1.getxMin();
            c1[1]+=off1.getyMin();
            c2[0]+=off2.getxMin();
            c2[1]+=off2.getyMin();
            cOff1[0]+=off1.getxMin();
            cOff1[1]+=off1.getyMin();
            cOff2[0]+=off2.getxMin();
            cOff2[1]+=off2.getyMin();
            Line l1 = new Line(c1[0], c1[1], cOff1[0], cOff1[1]);
            Line l2 = new Line(c2[0], c2[1], cOff2[0], cOff2[1]);
            Line l12 = new Line((c1[0]+cOff1[0])/2d, (c1[1]+cOff1[1])/2d, (c2[0]+cOff2[0])/2d, (c2[1]+cOff2[1]) /2d );
            
            testOverlay.add(l1);
            testOverlay.add(l2);
            testOverlay.add(l12);
            TextRoi position  = new TextRoi((c1[0]+cOff1[0]+c2[0]+cOff2[0])/4d, (c1[1]+cOff1[1]+c2[1]+cOff2[1])/4d, Utils.formatDoubleScientific(1, distance));
            testOverlay.add(position);
        }
    }
    public static enum Localization {
        UP, UPPER_MIDDLE, MIDDLE, LOWER_MIDDLE, LOW;
        public Localization getOffsetType(Localization other) {
            if (UP.equals(this)) {
                if (UP.equals(other) || UPPER_MIDDLE.equals(other) || MIDDLE.equals(other)) return UP;
                else return null;
            } else if (MIDDLE.equals(this)) {
                if (MIDDLE.equals(other) || UPPER_MIDDLE.equals(other) || LOWER_MIDDLE.equals(other)) return MIDDLE;
                else if (UP.equals(other)) return UP;
                else if (LOW.equals(other)) return LOW;
                else return null;
            } else if (LOW.equals(this)) {
                if (LOW.equals(other) || LOWER_MIDDLE.equals(other) || MIDDLE.equals(other)) return LOW;
                else return null;
            } else if (UPPER_MIDDLE.equals(this)) {
                if (UP.equals(other)) return UP;
                else if (MIDDLE.equals(other)) return MIDDLE;
                else if (UPPER_MIDDLE.equals(other)) return UPPER_MIDDLE;
                else return null;
            } else if (LOWER_MIDDLE.equals(this)) {
                if (LOW.equals(other)) return LOW;
                else if (MIDDLE.equals(other)) return MIDDLE;
                else if (LOWER_MIDDLE.equals(other)) return LOWER_MIDDLE;
                else return null;
            }
            return null;
        }
        public Localization[] getOffsetTypeDivision(Localization otherAfterDivision, boolean upperDaughterCell) {
            if (upperDaughterCell) {
                if (MIDDLE.equals(this)) {
                    if (LOW.equals(otherAfterDivision) || LOWER_MIDDLE.equals(otherAfterDivision) || MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, LOW};
                    else return null;
                } else if (UPPER_MIDDLE.equals(this)) {
                    if (MIDDLE.equals(otherAfterDivision)) return new Localization[]{UP, UP, MIDDLE, LOW};
                    if (UPPER_MIDDLE.equals(otherAfterDivision)) return new Localization[]{UP, UP};
                    if (LOWER_MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, LOW};
                    else return null;
                } else if (UP.equals(this)) {
                    if (UP.equals(otherAfterDivision) || UPPER_MIDDLE.equals(otherAfterDivision) || MIDDLE.equals(otherAfterDivision)) return new Localization[]{UP, UP};
                    else return null;
                } else return null;
            } else {
                if (MIDDLE.equals(this)) {
                    if (UP.equals(otherAfterDivision) || UPPER_MIDDLE.equals(otherAfterDivision) || MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, UP};
                    else return null;
                } else if (LOWER_MIDDLE.equals(this)) {
                    if (MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, UP, LOW, LOW};
                    if (UPPER_MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, UP};
                    if (LOWER_MIDDLE.equals(otherAfterDivision)) return new Localization[]{LOW, LOW};
                    else return null;
                } else if (LOW.equals(this)) {
                    if (LOW.equals(otherAfterDivision) || LOWER_MIDDLE.equals(otherAfterDivision) || MIDDLE.equals(otherAfterDivision)) return new Localization[]{LOW, LOW};
                    else return null;
                } else return null;
            }
        }
    };
    public static class DistanceComputationParameters {
        public double qualityThreshold = 0;
        public double gapSquareDistancePenalty = 0;
        public double alternativeDistance;
        public DistanceComputationParameters(double alternativeDistance) {
            this.alternativeDistance=alternativeDistance;
        }
        public DistanceComputationParameters setQualityThreshold(double qualityThreshold) {
            this.qualityThreshold=qualityThreshold;
            return this;
        }
        public DistanceComputationParameters setGapSquareDistancePenalty(double gapDistancePenalty) {
            this.gapSquareDistancePenalty=gapDistancePenalty;
            return this;
        }
        public DistanceComputationParameters setAlternativeDistance(double alternativeDistance) {
            this.alternativeDistance=alternativeDistance;
            return this;
        }
        public double getDistancePenalty(int tSource, int tTarget) {
            return (tTarget - tSource-1) * gapSquareDistancePenalty;
        }
    }
}
