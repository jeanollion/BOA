/*
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.image;

import boa.image.processing.ImageOperations;
import static boa.utils.Utils.parallele;
import java.util.Collection;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 * @author Jean Ollion
 */
public class HistogramFactory {
    public static Histogram getHistogram(Image image, ImageMask mask, int length, boolean parallele) {
        if (!parallele || image.sizeZ()==0) return getHistogram(image, mask, length);
        List<Image> planes = image.splitZPlanes();
        if (mask==null || mask instanceof BlankMask) return HistogramFactory.getHistogram(planes, null, length, true);
        else {
            Map<Image, ImageMask> map =  IntStream.range(0, planes.size()).mapToObj(i->i).collect(Collectors.toMap(i->planes.get(i), i->new ImageMask2D(mask, i)));
            return HistogramFactory.getHisto(map, null, length , true);
        }
    }
    public static Histogram getHistogram(Image image, ImageMask mask, double binSize, boolean parallele) {
        if (!parallele || image.sizeZ()==0) return getHistogram(image, mask, binSize);
        List<Image> planes = image.splitZPlanes();
        if (mask==null || mask instanceof BlankMask) return HistogramFactory.getHistogram(planes, binSize, null, true);
        else {
            Map<Image, ImageMask> map =  IntStream.range(0, planes.size()).mapToObj(i->i).collect(Collectors.toMap(i->planes.get(i), i->new ImageMask2D(mask, i)));
            return HistogramFactory.getHisto(map, binSize, null , true);
        }
    }
    public static Histogram getHistogram(Image image, ImageMask mask, int length) {
        DoubleSummaryStatistics stats = image.stream(mask, false).summaryStatistics();
        double min = stats.getMin();
        double max = stats.getMax();
        return HistogramFactory.getHistogram(image, mask, length, min, max);
    }
    public static Histogram getHistogram(Image image, ImageMask mask, double binSize) {
        if (image instanceof ImageByte && binSize==1) return HistogramFactory.getHistogram(image, mask, 1d, 256, 0d);
        DoubleSummaryStatistics stats = image.stream(mask, false).summaryStatistics();
        double min = stats.getMin();
        double max = stats.getMax();
        return HistogramFactory.getHistogram(image, mask, binSize, min, max);
    }
    public static Histogram getHistogram(Image image, ImageMask mask, double binSize, double min, double max) {
        return HistogramFactory.getHistogram(image, mask, binSize, (int)((max-min)/binSize)+1, min);
    }
    public static Histogram getHistogram(Image image, ImageMask mask, int length, double min, double max) {
        return HistogramFactory.getHistogram(image, mask, (max-min)/length, length, min);
    }
    
    
    
    
    public static double getBinSize(double min, double max, int length) {
        return (max - min) / length;
    }
    public static double getLength(double min, double max, double binSize) {
        return (int)(max-min)/binSize;
    }
    private static Histogram getHistogram(Image image, ImageMask mask, double binSize, int length, double min) {
        int[] res = new int[length];
        double coeff = 1 / binSize;
        image.stream(mask, false).forEach(v->{
            int idx = (int)((v-min) * coeff);
            if (idx==length) res[length-1]++;
            else if (idx>=0 && idx<length) res[idx]++;
        });
        return new Histogram(res, binSize, min);
    }

    public static Histogram getHistogram(Collection<Image> images, double[] minAndMax, int length, boolean parallele) {
        if (images.isEmpty()) {
            return null;
        }
        if (minAndMax == null) {
            minAndMax = new double[2];
        }
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images, parallele);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        double[] mm = minAndMax;
        return parallele(images.stream(), parallele).map((Image im) -> HistogramFactory.getHistogram(im, null, length, mm[0], mm[1])).reduce((Histogram h1, Histogram h2) -> {
            h1.add(h2);
            return h1;
        }).get();
    }

    /**
     *
     * @param images
     * @param minAndMax the method will output min and max values in this array, except if minAndMax[0]<minAndMax[1] -> in this case will use these values for histogram
     * @return
     */
    public static Histogram getHistogram(Collection<Image> images, double binSize, double[] minAndMax, boolean parallele) {
        if (images.isEmpty()) {
            return null;
        }
        if (minAndMax == null) {
            minAndMax = new double[2];
        }
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images, parallele);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        double[] mm = minAndMax;
        return parallele(images.stream(), parallele).map((Image im) -> HistogramFactory.getHistogram(im, null, binSize, mm[0], mm[1])).reduce((Histogram h1, Histogram h2) -> {
            h1.add(h2);
            return h1;
        }).get();
    }

    public static Histogram getHisto(Map<Image, ImageMask> images, double[] minAndMax, int length, boolean parallele) {
        if (minAndMax == null) {
            minAndMax = new double[2];
        }
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images, parallele);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        double[] mm = minAndMax;
        return parallele(images.entrySet().stream(), parallele).map((Map.Entry<Image, ImageMask> e) -> HistogramFactory.getHistogram(e.getKey(), e.getValue(), length, mm[0], mm[1])).reduce((Histogram h1, Histogram h2) -> {
            h1.add(h2);
            return h1;
        }).get();
    }

    public static Histogram getHisto(Map<Image, ImageMask> images, double binSize, double[] minAndMax, boolean parallele) {
        if (minAndMax == null) {
            minAndMax = new double[2];
        }
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images, parallele);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        double[] mm = minAndMax;
        return parallele(images.entrySet().stream(), parallele).map((Map.Entry<Image, ImageMask> e) -> HistogramFactory.getHistogram(e.getKey(), e.getValue(), binSize, mm[0], mm[1])).reduce((Histogram h1, Histogram h2) -> {
            h1.add(h2);
            return h1;
        }).get();
    }

    public static List<Histogram> getHistoAsList(Collection<Image> images, double binSize, double[] minAndMax, boolean parallele) {
        if (minAndMax == null) {
            minAndMax = new double[2];
        }
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images, parallele);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        double[] mm = minAndMax;
        return parallele(images.stream(), parallele).map((Image im) -> HistogramFactory.getHistogram(im, null, binSize, mm[0], mm[1])).collect(Collectors.toList());
    }

    public static Map<Image, Histogram> getHistoAll(Map<Image, ImageMask> images, double binSize, double[] minAndMax, boolean parallele) {
        if (minAndMax == null) {
            minAndMax = new double[2];
        }
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images, parallele);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        final double[] mm = minAndMax;
        return parallele(images.entrySet().stream(), parallele).collect(Collectors.toMap((Map.Entry<Image, ImageMask> e) -> e.getKey(), (Map.Entry<Image, ImageMask> e) -> HistogramFactory.getHistogram(e.getKey(), e.getValue(), binSize, mm[0], mm[1])));
    }
}
