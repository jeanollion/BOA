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
package boa.plugins.legacy.trackHisto;

import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageMask;
import boa.plugins.TrackPreFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import boa.plugins.plugins.processing_scheme.SegmentOnly.ApplyToSegmenter;
import static boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.logger;
import boa.utils.Utils;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
public abstract class Threshold implements ApplyToSegmenter {
    final TreeMap<StructureObject, Image> planes;
    final Map<Integer, StructureObject> parentsByF;
    final Map<Image, ImageMask> maskMap;
    final int offsetFrame;
    int[] frameRange;
    public Threshold(TreeMap<StructureObject, Image> planes, int offsetFrame) {
        this.planes=planes;
        parentsByF = StructureObjectUtils.splitByFrame(planes.keySet());
        this.offsetFrame=offsetFrame;
        this.maskMap = TrackPreFilter.getMaskMap(planes);
    }
    public abstract boolean hasAdaptativeByY();
    /**
     * 
     * @return frame range containing cell, bounds included
     */
    public int[] getFrameRange() {return frameRange!=null ? new int[]{frameRange[0]+offsetFrame, frameRange[1]+offsetFrame} : null;}
    public abstract void setFrameRange(int[] frameRange);
    public abstract double getThreshold(int frame);
    public abstract double getThreshold(int frame, int y);
    public abstract void freeMemory();
    public static boolean showOne = false;
    public ImageByte getThresholdedPlane(int f, boolean backgroundUnderThreshold) {
        StructureObject p = parentsByF.get(f);
        Image im = planes.get(p);
        ImageByte res=  new ImageByte("thld", im);
        res.getBoundingBox().translateToOrigin().loop((int x, int y, int z) -> {
            if (im.getPixel(x, y, z) > getThreshold(f, y) == backgroundUnderThreshold) res.setPixel(x, y, z, 1);
        });
        if (showOne) {
            double[] thld = new double[im.getSizeY()];
            for (int y = 0; y<thld.length; ++y) thld[y] = getThreshold(f, y);
            Utils.plotProfile("thld Y", thld);
            showOne = false;
            ImageWindowManagerFactory.showImage(im.duplicate("image to be thlded"));
            ImageWindowManagerFactory.showImage(res.duplicate("thlded mask"));
        }
        return res;
    }
    public abstract void setAdaptativeByY(int yHalfWindow);
    public abstract void setAdaptativeThreshold(double adaptativeCoefficient, int adaptativeThresholdHalfWindow);
    public abstract double getThreshold();
    public static double[] interpolate(double[] values, int interval, int length) {
        double[] res = new double[length];
        Arrays.fill(res, (values.length-1)*interval, length, values[values.length-1]);
        for (int i = 0; i<(values.length-1)*interval; ++i) { // smooth: linear approx between points
            int idx = i/interval;
            double d = (double)i%interval / (double)interval;
            res[i] = (values[idx] * (1-d) + values[idx+1] * d);
        }
        return res;
    }
    
}
