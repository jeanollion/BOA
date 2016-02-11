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
import java.util.ArrayList;

/**
 *
 * @author jollion
 */
public class SpotWithinCompartment extends Spot {
    protected StructureObject object;
    protected StructureObject compartiment;
    double[] compartimentOffset;
    double[] compartimentOffsetDivision;
    
    public SpotWithinCompartment(StructureObject object, StructureObject compartiment, double[] scaledCenter) {
        super(scaledCenter[0], scaledCenter[1], scaledCenter[2], 1, 1);
        getFeatures().put(Spot.FRAME, (double)compartiment.getTimePoint());
        this.compartiment=compartiment;
        this.object=object;
        compartimentOffset = new double[]{compartiment.getBounds().getxMin() * object.getScaleXY(), compartiment.getBounds().getyMin() * object.getScaleXY(), compartiment.getBounds().getzMin() * object.getScaleZ()};
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
            if (ss.compartiment.getTrackHead()==compartiment.getTrackHead()) { //distance is relative to the compartment for spots within the same compartiment track
                return getSquareDistanceCompartiments(ss);
            } else if (ss.compartiment.getTrackHead().getPrevious()==compartiment) { //distance is relative to the compartment for spots within the same compartiment track
                return getSquareDistanceNextCompartiments(ss);
            } else if (compartiment.getTrackHead().getPrevious()==ss.compartiment) {
                return ss.getSquareDistanceNextCompartiments(this);
            } else return Double.POSITIVE_INFINITY; // spots in different tracks -> no link possible -> mettre max value? ou la valeur de distance maxi? 
        } else return super.squareDistanceTo(s);
    }
    protected double[] getOffsetDivision() {
        if (compartimentOffsetDivision==null) {
            // get previous division sibling @ same timePoint
            StructureObject sibling = compartiment.getTrackHead().getPrevious();
            while(sibling!=null && sibling.getTimePoint()!=compartiment.getTimePoint()) sibling = sibling.getNext();
            if (sibling!=null) {
                compartimentOffsetDivision = new double[]{sibling.getBounds().getxMin() * object.getScaleXY(), sibling.getBounds().getyMin() * object.getScaleXY(), sibling.getBounds().getzMin() * object.getScaleZ()};
            } else {
                throw new Error("SpotWithinCompartment :: no offset Found: "+compartiment.toString());
                //compartimentOffsetDivision = new double[]{}; // to signal no sibling was found
            }
        }
        return compartimentOffsetDivision;
    }
    protected double getSquareDistanceCompartiments(SpotWithinCompartment s) {
        return Math.pow((getFeature(POSITION_X)-compartimentOffset[0] - s.getFeature(POSITION_X)+s.compartimentOffset[0]), 2) +
            Math.pow((getFeature(POSITION_Y)-compartimentOffset[1] - s.getFeature(POSITION_Y)+s.compartimentOffset[1]), 2) + 
            Math.pow((getFeature(POSITION_Z)-compartimentOffset[2] - s.getFeature(POSITION_Z)+s.compartimentOffset[2]), 2);
    }
    protected double getSquareDistanceNextCompartiments(SpotWithinCompartment sFromNextCompartiment) {
        double[] nextOffset = sFromNextCompartiment.getOffsetDivision();
        return Math.pow((getFeature(POSITION_X)-compartimentOffset[0] - sFromNextCompartiment.getFeature(POSITION_X)+nextOffset[0]), 2) +
            Math.pow((getFeature(POSITION_Y)-compartimentOffset[1] - sFromNextCompartiment.getFeature(POSITION_Y)+nextOffset[1]), 2) + 
            Math.pow((getFeature(POSITION_Z)-compartimentOffset[2] - sFromNextCompartiment.getFeature(POSITION_Z)+nextOffset[2]), 2);
    }
}
