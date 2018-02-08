/*
 * Copyright (C) 2017 jollion
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
package boa.image;

import boa.image.processing.ImageOperations;
import static boa.image.Image.logger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public class Histogram {

    public final int[] data;
    public final boolean byteHisto;
    public final double[] minAndMax;

    public Histogram(int[] data, boolean byteHisto, double[] minAndMax) {
        this.data = data;
        this.byteHisto = byteHisto;
        this.minAndMax = minAndMax;
    }
    public Histogram duplicate() {
        int[] dataC = new int[256];
        System.arraycopy(data, 0, dataC, 0, 256);
        return new Histogram(dataC, byteHisto, new double[]{minAndMax[0], minAndMax[1]});
    }
    public double getHistoMinBreak() {
        if (byteHisto) return 0;
        else return minAndMax[0];
    }
    public double getBinSize() {
        return byteHisto ? 1 : (minAndMax[1] - minAndMax[0]) / 256d;
    }
    public void add(Histogram other) {
        for (int i = 0; i < 256; ++i) data[i] += other.data[i];
    }
    public void remove(Histogram other) {
        for (int i = 0; i < 256; ++i) data[i] -= other.data[i];
    }
    
    public double getValueFromIdx(double thld256) {
        if (byteHisto) return thld256;
        return convertHisto256Threshold(thld256, minAndMax);
    }
    public double getIdxFromValue(double thld) {
        if (byteHisto) {
            int idx = (int)Math.round(thld);
            if (idx<0) idx =0;
            if (idx>255) idx = 255;
            return idx;
        }
        return convertTo256Threshold(thld, minAndMax);
    }
    public static double convertHisto256Threshold(double threshold256, double[] minAndMax) {
        return threshold256 * (minAndMax[1] - minAndMax[0]) / 256.0 + minAndMax[0];
    }

    public static int convertTo256Threshold(double threshold, double[] minAndMax) {
        int res = (int) Math.round((threshold - minAndMax[0]) * 256 / ((minAndMax[1] - minAndMax[0])));
        if (res >= 256) {
            res = 255;
        }
        return res;
    }

    public static double convertHisto256Threshold(double threshold256, Image input, ImageMask mask, BoundingBox limits) {
        if (mask == null) {
            mask = new BlankMask("", input);
        }
        double[] mm = input.getMinAndMax(mask, limits);
        if (input instanceof ImageByte) {
            return threshold256;
        } else {
            return Histogram.convertHisto256Threshold(threshold256, mm);
        }
    }
    /**
     *
     * @param images
     * @param minAndMax the method will output min and max values in this array, except if minAndMax[0]<minAndMax[1] -> in this case will use these values for histogram
     * @return
     */
    public static Histogram getHisto256(Collection<Image> images, double[] minAndMax) {
        if (minAndMax==null) minAndMax=new double[2];
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        Histogram histo = null;
        for (Image im : images) {
            Histogram h = im.getHisto256(minAndMax[0], minAndMax[1], null, null);
            if (histo == null) {
                histo = h;
            } else {
                histo.add(h);
            }
        }
        return histo;
    }
    public static Histogram getHisto256(Map<Image, ImageMask> images, double[] minAndMax) {
        if (minAndMax==null) minAndMax=new double[2];
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        Histogram histo = null;
        for (Entry<Image, ImageMask> e : images.entrySet()) {
            Histogram h = e.getKey().getHisto256(minAndMax[0], minAndMax[1], e.getValue(), null);
            if (histo == null) {
                histo = h;
            } else {
                histo.add(h);
            }
        }
        return histo;
    }
    public static List<Histogram> getHisto256AsList(Collection<Image> images, double[] minAndMax) {
        if (minAndMax==null) minAndMax=new double[2];
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        List<Histogram> res = new ArrayList<>(images.size());
        for (Image im : images) res.add(im.getHisto256(minAndMax[0], minAndMax[1], null, null));
        return res;
    }
    public static Map<Image, Histogram> getHistoAll256(Map<Image, ImageMask> images, double[] minAndMax) {
        if (minAndMax==null) minAndMax=new double[2];
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        final double[] mm = minAndMax;
        return images.entrySet().stream().collect(Collectors.toMap(e->e.getKey(), e->e.getKey().getHisto256(mm[0], mm[1], e.getValue(), null)));
    }
    public int count() {
        int sum = 0;
        for (int i : data) sum+=i;
        return sum;
    }
    public void removeSaturatingValue(double countThlFactor, boolean highValues) {
        if (highValues) {
            if (this.byteHisto) {
                int i = 255;
                if (data[i]==0) while(i>0 && data[i-1]==0) --i;
                if (i>0) {
                    //logger.debug("remove saturating value: {} (prev: {}, i: {})", data[i], data[i-1], i);
                    if (data[i]>data[i-1]*countThlFactor) data[i]=0;
                }
            } else {
                //logger.debug("remove saturating value: {} (prev: {})", data[255], data[254]);
                if (data[255]>data[254]*countThlFactor) {
                    data[255]=0;
                }
            }
        } else {
            if (this.byteHisto) {
                int i = 0;
                if (data[i]==0) while(i>0 && data[i+1]==0) ++i;
                if (i<255) {
                    //logger.debug("remove saturating value: {} (prev: {}, i: {})", data[i], data[i-1], i);
                    if (data[i]>data[i+1]*countThlFactor) data[i]=0;
                }
            } else {
                //logger.debug("remove saturating value: {} (prev: {})", data[255], data[254]);
                if (data[0]>data[1]*countThlFactor) {
                    data[0]=0;
                }
            }
        }
    }
    public double[] getQuantiles(double... percent) {
        double binSize = getBinSize();
        int gcount = 0;
        for (int i : data) gcount += i;
        double[] res = new double[percent.length];
        for (int i = 0; i<res.length; ++i) {
            int count = gcount;
            double limit = count * (1-percent[i]); // 1- ?
            if (limit >= count) {
                res[i] = minAndMax[0];
                continue;
            }
            count = data[255];
            int idx = 255;
            while (count < limit && idx > 0) {
                idx--;
                count += data[idx];
            }
            double idxInc = (data[idx] != 0) ? (count - limit) / (data[idx]) : 0; //lin approx
            res[i] = (double) (idx + idxInc) * binSize + getHistoMinBreak();
        }
        return res;
    }
}
