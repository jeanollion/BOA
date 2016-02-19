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

/**
 *
 * @author jollion
 */
public class SpotWithinCompartment extends Spot {
    public static ImageObjectInterface bacteria;
    public static Overlay testOverlay;
    
    public static enum Localization {
        UP, MIDDLE, DOWN;
        public Localization getOffsetType(Localization other) {
            if (UP.equals(this)) {
                if (UP.equals(other)) return UP;
                else if (MIDDLE.equals(other)) return UP;
                else if (DOWN.equals(other)) return null;
            } else if (MIDDLE.equals(this)) {
                if (UP.equals(other)) return UP;
                else if (MIDDLE.equals(other)) return MIDDLE;
                else if (DOWN.equals(other)) return DOWN;
            } else if (DOWN.equals(this)) {
                if (UP.equals(other)) return null;
                else if (MIDDLE.equals(other)) return DOWN;
                else if (DOWN.equals(other)) return DOWN;
            } 
            return null;
        }
    }; 
    protected StructureObject object;
    protected SpotCompartiment compartiment;
    protected final Localization localization;
    
    public SpotWithinCompartment(StructureObject object, SpotCompartiment compartiment, double[] scaledCenter) {
        super(scaledCenter[0], scaledCenter[1], scaledCenter[2], 1, 1);
        getFeatures().put(Spot.FRAME, (double)compartiment.object.getTimePoint());
        this.compartiment=compartiment;
        this.object=object;
        if (scaledCenter[1]<(compartiment.offsetDivisionMiddleUp[1])) localization = Localization.UP;
        else if (scaledCenter[1]>(compartiment.offsetDivisionMiddleLow[1])) localization = Localization.DOWN;
        else localization = Localization.MIDDLE;
        /*if (testOverlay!=null && bacteria!=null) {
            BoundingBox off1 = bacteria.getObjectOffset(this.compartiment.object).duplicate().translate(this.compartiment.object.getBounds().duplicate().reverseOffset());
            int[] c1 = this.getCenterInVoxels();
            c1[0]+=off1.getxMin();
            c1[1]+=off1.getyMin();
            TextRoi position  = new TextRoi(c1[0], c1[1], localization.toString());
            
            testOverlay.add(position);
        }*/
            
    }
    
    public SpotWithinCompartment duplicate() {
        double[] scaledCenter =  new double[]{getFeature(Spot.POSITION_X), getFeature(Spot.POSITION_Y), getFeature(Spot.POSITION_Z)};
        SpotWithinCompartment res = new SpotWithinCompartment(object, compartiment, scaledCenter);
        res.getFeatures().put(Spot.QUALITY, getFeature(Spot.QUALITY));
        res.getFeatures().put(Spot.RADIUS, getFeature(Spot.RADIUS));
        return res;
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
                    if (this.compartiment.nextDivisionTimePoint>=0 && this.compartiment.nextDivisionTimePoint<=ss.compartiment.object.getTimePoint()) return getSquareDistanceDivision(ss);
                    else return getSquareDistanceCompartiments(ss);
                } else return Double.POSITIVE_INFINITY; // spots in different tracks -> no link possible
            }
        } else return super.squareDistanceTo(s);
    }
    
    protected double getSquareDistanceCompartiments(SpotWithinCompartment s) {
        Localization offsetType = this.localization.getOffsetType(s.localization);
        LAPTrackerCore.logger.debug("s1: {}, comp: {}, tp: {}, s2: {}, comp: {}, tp: {}, offsetType: {}", this.object.getIdx(), this.compartiment.object.getIdx(), this.object.getTimePoint(), s.object.getIdx(), s.compartiment.object.getIdx(), s.object.getTimePoint(), offsetType);
        if (offsetType==null) return Double.POSITIVE_INFINITY;
        else if (Localization.UP.equals(offsetType)) {
            displayOffsets(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp);
            return getSquareDistance(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp);
        } else if (Localization.DOWN.equals(offsetType)) {
            displayOffsets(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown);
            return getSquareDistance(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown);
        } else {
            double d1 = getSquareDistance(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp);
            double d2 = getSquareDistance(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown);
            if (d1>d2) displayOffsets(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown);
            else displayOffsets(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp);
            return Math.min(d1, d2);
        }
    }
    protected double getSquareDistanceDivision(SpotWithinCompartment sAfterDivision) {
        LAPTrackerCore.logger.debug("DIV: s1: {}, comp: {}, tp: {}, loc: {}, s2: {}, comp: {}, tp: {}, localization: {}", this.object.getIdx(), this.compartiment.object.getIdx(), this.object.getTimePoint(), this.localization, sAfterDivision.object.getIdx(), sAfterDivision.compartiment.object.getIdx(), sAfterDivision.object.getTimePoint(), sAfterDivision.localization);
        //double d1 = getSquareDistance(this, this.compartiment.offsetUp, sAfterDivision, sAfterDivision.compartiment.offsetDivisionUp);
        //double d2 = getSquareDistance(this, this.compartiment.offsetDown, sAfterDivision, sAfterDivision.compartiment.offsetDivisionDown);
        double d1, d2;
        if (sAfterDivision.compartiment.object.getTrackHead()==this.compartiment.object.getTrackHead()) { // daughter cell is in the upper part. TODO change test :: this test is only valid for increasing Y-coords when growing
            if (Localization.DOWN.equals(localization)) return Double.POSITIVE_INFINITY;
            else if (Localization.MIDDLE.equals(localization)) {
                if (Localization.DOWN.equals(sAfterDivision.localization)) {
                    displayOffsets(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetDown);
                    return getSquareDistance(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetDown);
                }
                else return Double.POSITIVE_INFINITY;
            } else {
                if (Localization.UP.equals(sAfterDivision.localization)) {
                    displayOffsets(this, this.compartiment.offsetUp, sAfterDivision, sAfterDivision.compartiment.offsetUp);
                    return getSquareDistance(this, this.compartiment.offsetUp, sAfterDivision, sAfterDivision.compartiment.offsetUp);
                }
                else if (Localization.DOWN.equals(sAfterDivision.localization)) {
                    displayOffsets(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetDown);
                    return getSquareDistance(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetDown);
                }
                else {
                    d1 = getSquareDistance(this, this.compartiment.offsetUp, sAfterDivision, sAfterDivision.compartiment.offsetUp);
                    d2 = getSquareDistance(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetDown);
                    if (d1>d2) displayOffsets(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetDown);
                    else  displayOffsets(this, this.compartiment.offsetUp, sAfterDivision, sAfterDivision.compartiment.offsetUp);
                    return Math.min(d1, d2);
                }
            }
        } else {
            if (Localization.UP.equals(localization)) return Double.POSITIVE_INFINITY;
            else if (Localization.MIDDLE.equals(localization)) {
                if (Localization.UP.equals(sAfterDivision.localization)) {
                    displayOffsets(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetUp);
                    return getSquareDistance(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetUp);
                }
                else return Double.POSITIVE_INFINITY;
            } else {
                if (Localization.UP.equals(sAfterDivision.localization)) {
                    displayOffsets(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetUp);
                    return getSquareDistance(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetUp);
                }
                else if (Localization.DOWN.equals(sAfterDivision.localization)) {
                    displayOffsets(this, this.compartiment.offsetDown, sAfterDivision, sAfterDivision.compartiment.offsetDown);
                    return getSquareDistance(this, this.compartiment.offsetDown, sAfterDivision, sAfterDivision.compartiment.offsetDown);
                }
                else {
                    d1 = getSquareDistance(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetUp);
                    d2 = getSquareDistance(this, this.compartiment.offsetDown, sAfterDivision, sAfterDivision.compartiment.offsetDown);
                    if (d1>d2) displayOffsets(this, this.compartiment.offsetDown, sAfterDivision, sAfterDivision.compartiment.offsetDown);
                    else displayOffsets(this, this.compartiment.offsetDivisionMiddle, sAfterDivision, sAfterDivision.compartiment.offsetUp);
                    return Math.min(d1, d2);
                }
            }   
        }
    }
    protected static double getSquareDistance(Spot s1, double[] offset1, Spot s2, double[] offset2) {
        if (offset1==null || offset2==null) return Double.POSITIVE_INFINITY; //TODO fix bug -> when reach test's limit timePoint, offset are null
        return Math.pow((s1.getFeature(POSITION_X)-offset1[0] - s2.getFeature(POSITION_X)+offset2[0]), 2) +
            Math.pow((s1.getFeature(POSITION_Y)-offset1[1] - s2.getFeature(POSITION_Y)+offset2[1]), 2) + 
            Math.pow((s1.getFeature(POSITION_Z)-offset1[2] - s2.getFeature(POSITION_Z)+offset2[2]), 2);
    }
    
    private static void displayOffsets(SpotWithinCompartment s1, double[] offset1, SpotWithinCompartment s2, double[] offset2 ) {
        if (bacteria!=null && testOverlay!=null) {
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
        }
    }
    
}
