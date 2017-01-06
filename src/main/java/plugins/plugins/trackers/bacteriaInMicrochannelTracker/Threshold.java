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

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.logger;
import utils.Utils;

/**
 *
 * @author jollion
 */
public abstract class Threshold {
    final List<Image> planes;
    public Threshold(List<Image> planes) {
        this.planes=planes;
    }
    public abstract int[] getFrameRange();
    public abstract void setFrameRange(int[] frameRange);
    public abstract double getThreshold(int frame);
    public abstract double getThreshold(int frame, int y);
    public abstract void freeMemory();
    public static boolean showOne = false;
    public ImageByte getThresholdedPlane(int frame, boolean backgroundUnderThreshold) {
        Image im = planes.get(frame);
        ImageByte res=  new ImageByte("thld", im);
        res.getBoundingBox().translateToOrigin().loop((int x, int y, int z) -> {
            if (im.getPixel(x, y, z) > getThreshold(frame, y) == backgroundUnderThreshold) res.setPixel(x, y, z, 1);
        });
        if (showOne) {
            double[] thld = new double[im.getSizeY()];
            for (int y = 0; y<thld.length; ++y) thld[y] = getThreshold(frame, y);
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
    public static <T, A, R> List<R> slide(List<T> list, int halfWindow, SlidingOperator<T, A, R> operator) {
        if (list.isEmpty()) return Collections.EMPTY_LIST;
        if (list.size()<2*halfWindow+1) halfWindow = (list.size()-1)/2;
        A acc = operator.instanciateAccumulator();
        List<R> res = new ArrayList<>(list.size());
        for (int i = 0; i<=2*halfWindow; ++i) operator.slide(null, list.get(i), acc);
        R start = operator.compute(acc);
        for (int i = 0; i<=halfWindow; ++i) res.add(start);
        for (int i = halfWindow+1; i<list.size()-halfWindow; ++i) {
            operator.slide(list.get(i-halfWindow-1), list.get(i+halfWindow), acc);
            res.add(operator.compute(acc));
        }
        R end = res.get(res.size()-1);
        for (int i = list.size()-halfWindow; i<list.size(); ++i) res.add(end);
        return res;
    }
    /*public static <T, A, R> R[] slideArray(T[] list, int halfWindow, SlidingOperator<T, A, R> operator) {
        R[] res = (R[])new Object[list.length];
        if (list.length==0) return res;
        if (list.length<2*halfWindow+1) halfWindow = (list.length-1)/2;
        A acc = operator.instanciateAccumulator();
        
        for (int i = 0; i<=2*halfWindow; ++i) operator.slide(null, list.get(i), acc);
        R start = operator.compute(acc);
        for (int i = 0; i<=halfWindow; ++i) res.add(start);
        for (int i = halfWindow+1; i<list.size()-halfWindow; ++i) {
            operator.slide(list.get(i-halfWindow-1), list.get(i+halfWindow), acc);
            res.add(operator.compute(acc));
        }
        R end = res.get(res.size()-1);
        for (int i = list.size()-halfWindow; i<list.size(); ++i) res.add(end);
        return res;
    }*/
    public static interface SlidingOperator<T, A, R> {
        public A instanciateAccumulator();
        public void slide(T removeElement, T addElement, A accumulator);
        public R compute(A accumulator);
    }
}
