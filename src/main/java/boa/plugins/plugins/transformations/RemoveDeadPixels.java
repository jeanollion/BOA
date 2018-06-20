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
package boa.plugins.plugins.transformations;

import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.Voxel;
import static boa.image.BoundingBox.loop;
import boa.image.Image;
import boa.image.ImageFloat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import boa.plugins.Transformation;
import boa.image.processing.Filters;
import boa.image.processing.Filters.Median;
import boa.image.processing.ImageFeatures;
import boa.image.processing.ImageOperations.Axis;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.plugins.ConfigurableTransformation;
import boa.plugins.ToolTip;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.SlidingOperator;
import java.util.stream.IntStream;

/**
 *
 * @author jollion
 */
public class RemoveDeadPixels implements ConfigurableTransformation, ToolTip {
    NumberParameter threshold = new BoundedNumberParameter("Local Threshold", 5, 30, 0, null).setToolTipText("Difference between pixels and median of the direct neighbors is computed. If difference is higer than this threshold pixel is considered as dead and will be replaced by the median value");
    NumberParameter frameRadius = new BoundedNumberParameter("Frame Radius", 0, 4, 1, null).setToolTipText("Number of frame to average. Set 1 to perform transformation Frame by Frame. A higher value will average previous frames");
    HashMapGetCreate<Integer, Set<Voxel>> configMapF;
    public RemoveDeadPixels(){}
    public RemoveDeadPixels(double threshold, int frameRadius) {
        this.threshold.setValue(threshold);
        this.frameRadius.setValue(frameRadius);
    }
    public Map<Integer, Set<Voxel>> getDeadVoxels() {
        return configMapF;
    }
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages)   { 
        configMapF = new HashMapGetCreate<>(new HashMapGetCreate.SetFactory<>());
        Image median = new ImageFloat("", inputImages.getImage(channelIdx, 0));
        Neighborhood n =  new EllipsoidalNeighborhood(1.5, true); // excludes center pixel // only on same plane
        double thld= threshold.getValue().doubleValue();
        int frameRadius = this.frameRadius.getValue().intValue();
        double fRd= (double)frameRadius;
        final Image[][] testMeanTC= testMode ? new Image[inputImages.getFrameNumber()][1] : null;
        final Image[][] testMedianTC= testMode ? new Image[inputImages.getFrameNumber()][1] : null;
        // perform sliding mean of image
        SlidingOperator<Image, Pair<Integer, Image>, Void> operator = new SlidingOperator<Image, Pair<Integer, Image>, Void>() {
            @Override public Pair<Integer, Image> instanciateAccumulator() {
                return new Pair(-1, new ImageFloat("", median));
            }
            @Override public void slide(Image removeElement, Image addElement, Pair<Integer, Image> accumulator) {
                if (frameRadius<=1) { // no averaging in time
                    accumulator.value=addElement;
                } else {
                    if (removeElement!=null && addElement!=null) {
                        loop(accumulator.value.getBoundingBox().resetOffset(), (x, y, z)->{
                            accumulator.value.setPixel(x, y, z, accumulator.value.getPixel(x, y, z)+(addElement.getPixel(x, y, z)-removeElement.getPixel(x, y, z))/fRd);
                        }, true);
                    }else if (addElement!=null) {
                        loop(accumulator.value.getBoundingBox().resetOffset(), (x, y, z)->{
                            accumulator.value.setPixel(x, y, z, accumulator.value.getPixel(x, y, z)+addElement.getPixel(x, y, z)/fRd);
                        }, true);
                    } else if (removeElement!=null) {
                        loop(accumulator.value.getBoundingBox().resetOffset(), (x, y, z)->{
                            accumulator.value.setPixel(x, y, z, accumulator.value.getPixel(x, y, z)-removeElement.getPixel(x, y, z)/fRd);
                        }, true);
                    }
                }
                accumulator.key = accumulator.key+1; /// keep track of current frame
            }
            @Override public Void compute(Pair<Integer, Image> accumulator) {   
                Filters.median(accumulator.value, median, n, true);
                //Filters.median(inputImages.getImage(channelIdx, accumulator.key), median, n);
                if (testMode) {
                    testMeanTC[accumulator.key][0] = accumulator.value.duplicate();
                    testMedianTC[accumulator.key][0] = median.duplicate();
                }
                loop(median.getBoundingBox().resetOffset(), (x, y, z)->{
                    float med = median.getPixel(x, y, z);
                    if (accumulator.value.getPixel(x, y, z)-med>= thld) {
                        Voxel v =new Voxel(x, y, z, med);
                        for (int f = Math.max(0, accumulator.key-frameRadius); f<=accumulator.key; ++f) {
                            configMapF.getAndCreateIfNecessary(f).add(v);
                            //Set<Voxel> set = configMapF.getAndCreateIfNecessarySync(f);
                            //synchronized (set) {set.add(v);}
                        }
                    }
                });  // not parallele
                return null;
            }
        };
        List<Image> imList = new ArrayList<>(inputImages.getFrameNumber());
        for (int f = 0; f<inputImages.getFrameNumber(); ++f) imList.add(inputImages.getImage(channelIdx, f));
        if (frameRadius>=1) SlidingOperator.performSlideLeft(imList, frameRadius, operator);
        else IntStream.range(0, imList.size()).parallel().forEach(i-> operator.compute(new Pair<>(i, imList.get(i))));
        if (testMode) {
            // first frames are not computed
            for (int f = 0; f<frameRadius-1; ++f) testMeanTC[f][0] = testMeanTC[frameRadius-1][0];
            for (int f = 0; f<frameRadius-1; ++f) testMedianTC[f][0] = testMedianTC[frameRadius-1][0];
            ImageWindowManagerFactory.showImage5D("Sliding median", testMedianTC);
            ImageWindowManagerFactory.showImage5D("Sliding mean", testMeanTC);
            logger.debug("number of dead voxels detected: {}", configMapF.size());
        }
    }

    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return (configMapF!=null); // when config is computed put 1 vox and frame -1 to set config. 
    }

    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        /*double thld= threshold.getValue().doubleValue();
        Image median = Filters.median(image, null, Filters.getNeighborhood(1.5, 1, image));
        //Image blurred = ImageFeatures.gaussianSmooth(image, 5, 5, false);
        median.getBoundingBox().translateToOrigin().loop((x, y, z)->{
            float med = median.getPixel(x, y, z);
            if (image.getPixel(x, y, z)-med>= thld) {
                image.setPixel(x, y, z, med);
                //if (testMode) logger.debug("pixel @ [{};{};{}] f={}, value: {}, median: {}, diff: {}", timePoint, x, y, z, image.getPixel(x, y, z), med, image.getPixel(x, y, z)-med );
            }
        });
        return image;
        */
        Map<Integer, Set<Voxel>> map = this.getDeadVoxels();
        if (map.containsKey(timePoint)) {
            Set<Voxel> dv= map.get(timePoint);
            if (!dv.isEmpty()) {
                Median m = new Median();
                m.setUp(image, image.sizeZ()>1 ? new EllipsoidalNeighborhood(1.5, 1, true) : new EllipsoidalNeighborhood(1.5, true)); // excludes center pixel);
                for (Voxel v : dv) image.setPixel(v.x, v.y, v.z, m.applyFilter(v.x, v.y, v.z)); //
            }
        }
        return image;
    }
    
    boolean testMode;
    @Override
    public void setTestMode(boolean testMode) {
        this.testMode=testMode;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{threshold, frameRadius};
    }

    @Override
    public String getToolTipText() {
        return "Removes pixels that have much higer values than their surroundings (in space & time)";
    }
    
}
