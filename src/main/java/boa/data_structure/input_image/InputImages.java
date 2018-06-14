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
package boa.data_structure.input_image;

import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import static boa.image.Image.logger;
import boa.image.ImageFloat;
import boa.image.processing.ImageOperations;
import boa.plugins.plugins.thresholders.BackgroundFit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 * @author nasique
 */
public interface InputImages {
    public Image getImage(int channelIdx, int timePoint);
    public int getFrameNumber();
    public int getChannelNumber();
    public int getDefaultTimePoint();
    public int getSizeZ(int channelIdx);
    public int getBestFocusPlane(int timePoint);
    public void flush();
    public double getCalibratedTimePoint(int c, int t, int z);
    public boolean singleFrameChannel(int channelIdx);
    
    
    public static Image getAverageFrame(InputImages images, int channelIdx, int frame,  int numberOfFramesToAverage) {
        if (numberOfFramesToAverage<=1) return images.getImage(channelIdx, frame);
        List<Image> imagesToAv = new ArrayList<>(numberOfFramesToAverage);
        int fMin = Math.max(0, frame-numberOfFramesToAverage/2);
        int fMax = Math.min(images.getFrameNumber(), fMin+numberOfFramesToAverage);
        if (fMax-fMin<numberOfFramesToAverage) fMin = Math.max(0, fMax-numberOfFramesToAverage);
        for (int f = fMin; f<fMax; ++f) imagesToAv.add(images.getImage(channelIdx, f));
        return ImageOperations.meanZProjection(Image.mergeZPlanes(imagesToAv));
    }
    /**
     * See {@link #chooseNImagesWithSignal(java.util.List, int) }
     * @param images
     * @param channelidx
     * @param n
     * @return 
     */
    public static List<Integer> chooseNImagesWithSignal(InputImages images, int channelidx, int n) {
        List<Image> imagesByFrame = new ArrayList<>(images.getFrameNumber());
        for (int i = 0; i<images.getFrameNumber(); ++i) imagesByFrame.add(images.getImage(channelidx, i));
        return chooseNImagesWithSignal(imagesByFrame, n);

    }
    /**
     * Measure amount of signal in each image by counting number of pixel above  a threshold computed by {@link boa#plugins#plugins#thresholder#BackgroundFit} method with parameter = 3.
     * @param images
     * @param n number of indices to return
     * @return list of image indexed from {@param images} list paired with signal measurement
     */
    public static List<Integer> chooseNImagesWithSignal(List<Image> images, int n) {
        if (n>=images.size()) return Utils.toList(ArrayUtil.generateIntegerArray(images.size()));
        // signal is measured as number of 
        long t0 = System.currentTimeMillis();
        double sTot = images.get(0).sizeXYZ();
        Histogram histo = HistogramFactory.getHistogram(()->Image.stream(images).parallel(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
        double thld = BackgroundFit.backgroundFit(histo, 3);
        List<Pair<Integer, Double>> signal = IntStream.range(0, images.size()).parallel()
                .mapToObj((int i) ->  new Pair<>(i, images.get(i).stream().filter(v->v>thld).count() /  sTot))
                .sorted((p1, p2)->-Double.compare(p1.value, p2.value)).collect(Collectors.toList());
        if (n==1) return Arrays.asList(new Integer[]{signal.get(0).key});
        // choose n frames among the X frames with most signal
        int candidateNumber = Math.max(images.size()/4, n);
        double delta = Math.max((double)candidateNumber / (double)(n+1), 1);
        signal = signal.subList(0, candidateNumber);
        List<Pair<Integer, Double>> res = new ArrayList<>(n);
        for (int i =0; i<n; ++i) {
            int idx = (int)(delta*i);
            res.add(signal.get(idx));
        }
        long t1 = System.currentTimeMillis();
        logger.debug("choose {} images: {} t={} (among: {})", n, res, t1-t0, signal);
        return Pair.unpairKeys(res);
    }
}
