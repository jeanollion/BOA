/*
 * Copyright (C) 2015 jollion
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
package utils;

import static core.Processor.logger;
import ij.gui.Plot;
import ij.measure.CurveFitter;
import image.ImageFloat;
import java.util.ArrayList;
import processing.ImageFeatures;

/**
 *
 * @author jollion
 */
public class ArrayUtil {
    
    public static float selectKth(float[] arr, int k) { // from : http://blog.teamleadnet.com/2012/07/quick-select-algorithm-find-kth-element.html
        if (arr == null) throw new IllegalArgumentException("Select K: array null");
        if ( arr.length <= k) throw new IllegalArgumentException("Select K: k>=length of array");
        int from = 0, to = arr.length - 1;
        // if from == to we reached the kth element
        while (from < to) {
            int r = from, w = to;
            float mid = arr[(r + w) / 2];
            // stop if the reader and writer meets
            while (r < w) {
                if (arr[r] >= mid) { // put the large values at the end
                    float tmp = arr[w];
                    arr[w] = arr[r];
                    arr[r] = tmp;
                    w--;
                } else { // the value is smaller than the pivot, skip
                    r++;
                }
            }
            // if we stepped up (r++) we need to step one down
            if (arr[r] > mid) {
                r--;
            }
            // the r pointer is on the end of the first k elements
            if (k <= r) {
                to = r;
            } else {
                from = r + 1;
            }
        }
        return arr[k];
    }

    
    public static int max(float[] array) {
        return max(array, 0, array.length);
    }
    
    /**
     * 
     * @param array 
     * @param start start of search index, inclusive
     * @param stop end of search index, exclusive
     * @return index of maximum value
     */
    public static int max(float[] array, int start, int stop) {
        if (start<0) start=0;
        if (stop>array.length) stop=array.length;
        if (stop<start) {int temp = start; start=stop; stop=temp;}
        int idxMax = start;
        for (int i = start+1; i<stop; ++i) if (array[i]>array[idxMax]) idxMax=i;
        return idxMax;
    }
    public static int max(double[] array) {
        return max(array, 0, array.length);
    }
    public static int max(double[] array, int start, int stop) {
        if (start<0) start=0;
        if (stop>array.length) stop=array.length;
        if (stop<start) {int temp = start; start=stop; stop=temp;}
        int idxMax = start;
        for (int i = start+1; i<stop; ++i) if (array[i]>array[idxMax]) idxMax=i;
        return idxMax;
    }
    public static int max(int[] array) {
        return max(array, 0, array.length);
    }
    public static int max(int[] array, int start, int stop) {
        if (start<0) start=0;
        if (stop>array.length) stop=array.length;
        if (stop<start) {int temp = start; start=stop; stop=temp;}
        int idxMax = start;
        for (int i = start+1; i<stop; ++i) if (array[i]>array[idxMax]) idxMax=i;
        return idxMax;
    }
    public static int min(float[] array) {
        return min(array, 0, array.length);
    }
    public static int min(float[] array, int start, int stop) {
        if (start<0) start=0;
        if (stop>array.length) stop=array.length;
        if (stop<start) {int temp = start; start=stop; stop=temp;}
        int idxMin = start;
        for (int i = start+1; i<stop; ++i) if (array[i]<array[idxMin]) idxMin=i;
        return idxMin;
    }
    public static int min(double[] array) {
        return min(array, 0, array.length);
    }
    public static int min(double[] array, int start, int stop) {
        if (start<0) start=0;
        if (stop>array.length) stop=array.length;
        if (stop<start) {int temp = start; start=stop; stop=temp;}
        int idxMin = start;
        for (int i = start+1; i<stop; ++i) if (array[i]<array[idxMin]) idxMin=i;
        return idxMin;
    }
    
    public static int getFirstOccurence(float[] array, int start, int stop, float value, boolean inferior, boolean strict) {
        if (start<0) start=0;
        if (stop>array.length) stop=array.length;
        int i = start;
        if (array[start]<value) { // increasing values
            if (start<=stop) { // increasing indicies
                while(i<stop-1 && array[i]<value) i++;
                if (inferior && (strict?array[i]>=value:array[i]>value) && i>start) return i-1;
                else return i;
            } else { // decreasing indicies
                while(i>stop && array[i]<value) i--;
                if (inferior && (strict?array[i]>=value:array[i]>value) && i<start) return i+1;
                else return i;
            }
        } else { // decreasing values
            if (start<=stop) { // increasing indicies
                while(i<stop-1 && array[i]>value) i++;
                if (!inferior && (strict?array[i]<=value:array[i]<value) && i>start) return i-1;
                else return i;
            } else { // decreasing indicies
                while(i>stop && array[i]>value) i--;
                if (!inferior && (strict?array[i]<=value:array[i]<value) && i<start) return i+1;
                else return i;
            }
        }
    }
    
    public static int[] getRegionalExtrema(float[] array, int scale, boolean max) {
        ArrayList<Integer> localExtrema = new ArrayList<Integer>();
        // get local extrema
        if (max) for (int i = 0; i<array.length; ++i) {if (isLocalMax(array, scale, i)) localExtrema.add(i);}
        else for (int i = 0; i<array.length; ++i) {if (isLocalMin(array, scale, i)) localExtrema.add(i);}
        if (localExtrema.size()<=1) return Utils.toArray(localExtrema, false);
        
        // suppress plateau
        ArrayList<Integer> regionalExtrema = new ArrayList<Integer>(localExtrema.size());
        for (int i = 1; i<localExtrema.size(); ++i) {
            if (localExtrema.get(i)==localExtrema.get(i-1)+1) {
                int j = i+1;
                while (j<localExtrema.size() && localExtrema.get(j)==localExtrema.get(j-1)+1){j++;}
                logger.debug("i: {}, j:{}, loc i-1: {}, loc j-1: {}",i, j, localExtrema.get(i-1), localExtrema.get(j-1));
                regionalExtrema.add((localExtrema.get(i-1)+localExtrema.get(j-1))/2); //mid-value of plateau (i-1 = borne inf, j-1 = borne sup)
                i=j;
            } else regionalExtrema.add(localExtrema.get(i-1));
        }
        // add last element if not plateau:
        if (localExtrema.get(localExtrema.size()-1)!=localExtrema.get(localExtrema.size()-2)+1) regionalExtrema.add(localExtrema.get(localExtrema.size()-1));
        return Utils.toArray(regionalExtrema, false);
    }
    
    protected static boolean isLocalMax(float[] array, int scale, int idx) {
        for (int i = 1; i<=scale; ++i) {
            if (idx-i>=0 && array[idx-i]>array[idx]) return false;
            if (idx+i<array.length && array[idx+i]>array[idx]) return false; 
        }
        return true;
    }
    protected static boolean isLocalMin(float[] array, int scale, int idx) {
        for (int i = 1; i<=scale; ++i) {
            if (idx-i>=0 && array[idx-i]<array[idx]) return false;
            if (idx+i<array.length && array[idx+i]<array[idx]) return false; 
        }
        return true;
    }
    
    public static float[] getDerivative(float[] array, double scale, int order, boolean override) {
        ImageFloat in = new ImageFloat("", array.length, new float[][]{array});
        ImageFloat out = ImageFeatures.getDerivative(in, scale, order, 0, 0, override);
        return out.getPixelArray()[0];
    }
    
    public static double[] subset(double[] data, int idxStart, int idxStop) {
        double[] res = new double[idxStop-idxStart];
        System.arraycopy(data, idxStart, res, 0, res.length);
        return res;
    }
    
    public static double[] getMeanAndSigma(double[] data) {
        double mean = 0;
        double values2 = 0;
        for (int i = 0; i < data.length; ++i) {
            mean += data[i];
            values2 += data[i] * data[i]; 
        }
        mean /= (double)data.length;
        values2 /= (double)data.length;
        return new double[]{mean, Math.sqrt(values2 - mean * mean)};
    }
    public static double[] gaussianFit(int[] data) {
        double[] data2 = new double[data.length];
        for (int i = 0; i<data.length; ++i) data2[i] = data[i];
        return gaussianFit(data2);
    }
    public static double[] gaussianFit(float[] data) {
        double[] data2 = new double[data.length];
        for (int i = 0; i<data.length; ++i) data2[i] = data[i];
        return gaussianFit(data2);
    }
    /**
     * Gaussian Fit using ImageJ's curveFitter
     * @param data
     * @param halfData
     * @return fit parameters: 0=MEAN / 1=sigma
     */
    public static double[] gaussianFit(double[] data) {
        //int maxIdx = max(data);
        //double maxValue = data[maxIdx];
        double[] xData;
        /*if (halfData) { // replicate data
            double[] data2 = new double[data.length * 2 -1];
            xData = new double[data2.length];
            if (maxIdx<data.length/2) { //replicate left
                for (int i = 0; i<data.length; ++i) {
                    data2[i] = data[data.length-i-1];
                    data2[i+data.length-1]=data[i];
                    //xData[i]=i-data.length+1;
                }
            } else { // replicate right
                for (int i = 0; i<data.length; ++i) {
                    data2[i] = data[i];
                    data2[i+data.length-1]=data[data.length-i-1];
                    //xData[i]=i-data.length+1;
                }
            }
            data=data2;
        }*/ //else {
            xData = new double[data.length];
            for (int i = 0; i<data.length; ++i) xData[i] = i;
        //}
        
        CurveFitter fit = new CurveFitter(xData, data);
        fit.setMaxIterations(10000);
        fit.setRestarts(1000);
        fit.doFit(CurveFitter.GAUSSIAN);
        double[] params = fit.getParams();
        //Utils.plotProfile("gaussian fit: X-center: "+params[2]+ " sigma: "+params[3], data);
        //Utils.plotProfile("residuals", fit.getResiduals());
        return new double[]{params[2], params[3]};
        
        /*WeightedObservedPoints obs = new WeightedObservedPoints();
        for (int i = 0; i<data.length; ++i) obs.add(xData[i], data[i]);
        GaussianCurveFitter fitter = GaussianCurveFitter.create().withStartPoint(new double[]{maxValue, halfData?0:maxIdx, getMeanAndSigma(data)[1]});
        double[] params = fitter.fit(obs.toList());
        
        Utils.plotProfile("gaussian fit: X-center: "+(params[1]+" XOffset: "+(data.length-1)/2)+ " sigma: "+params[2]+" initialGuess: "+getMeanAndSigma(data)[1], data);
        return new double[]{params[2], params[1]};
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-math3</artifactId>
            <version>3.5</version>
        </dependency>
        */
    }
    
}
