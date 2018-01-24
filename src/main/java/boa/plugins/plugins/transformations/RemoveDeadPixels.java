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
package boa.plugins.plugins.transformations;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.Voxel;
import boa.image.BlankMask;
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
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.SlidingOperator;

/**
 *
 * @author jollion
 */
public class RemoveDeadPixels implements Transformation {
    NumberParameter threshold = new BoundedNumberParameter("Local Threshold", 5, 30, 0, null).setToolTipText("Difference between pixels and median transform (radius 1 pix.) is computed. If difference is higer than this threshold pixel is considered as dead and will be replaced by the median value");
    NumberParameter frameRadius = new BoundedNumberParameter("Frame Radius", 0, 4, 1, null).setToolTipText("Number of frame to average");
    List<List<Double>> configDataFXYZV=new ArrayList<>();
    HashMapGetCreate<Integer, Set<Voxel>> configMapF;
    public RemoveDeadPixels(){}
    public RemoveDeadPixels(double threshold, int frameRadius) {
        this.threshold.setValue(threshold);
        this.frameRadius.setValue(frameRadius);
    }
    public Map<Integer, Set<Voxel>> getDeadVoxels() {
        if (configMapF==null) {
            synchronized(this) {
                if (configMapF==null) {
                    configMapF=new HashMapGetCreate<>(new HashMapGetCreate.SetFactory<>());
                    for (List<Double> v : configDataFXYZV) configMapF.getAndCreateIfNecessary(v.get(0).intValue()).add(new Voxel(v.get(1).intValue(), v.get(2).intValue(), v.get(3).intValue(), v.get(4).floatValue()));
                }
            }
        }
        return configMapF;
    }
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) throws Exception { 
        configDataFXYZV.clear();
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
                if (frameRadius<=1) {
                    accumulator.value=addElement;
                } else {
                    if (removeElement!=null && addElement!=null) {
                        accumulator.value.getBoundingBox().translateToOrigin().loop((x, y, z)->{
                            accumulator.value.setPixel(x, y, z, accumulator.value.getPixel(x, y, z)+(addElement.getPixel(x, y, z)-removeElement.getPixel(x, y, z))/fRd);
                        });
                    }else if (addElement!=null) {
                        accumulator.value.getBoundingBox().translateToOrigin().loop((x, y, z)->{
                            accumulator.value.setPixel(x, y, z, accumulator.value.getPixel(x, y, z)+addElement.getPixel(x, y, z)/fRd);
                        });
                    } else if (removeElement!=null) {
                        accumulator.value.getBoundingBox().translateToOrigin().loop((x, y, z)->{
                            accumulator.value.setPixel(x, y, z, accumulator.value.getPixel(x, y, z)-removeElement.getPixel(x, y, z)/fRd);
                        });
                    }
                }
                accumulator.key = accumulator.key+1; /// keep track of current frame
            }
            @Override public Void compute(Pair<Integer, Image> accumulator) {   
                Filters.median(accumulator.value, median, n);
                //Filters.median(inputImages.getImage(channelIdx, accumulator.key), median, n);
                if (testMode) {
                    testMeanTC[accumulator.key][0] = accumulator.value.duplicate();
                    testMedianTC[accumulator.key][0] = median.duplicate();
                }
                median.getBoundingBox().translateToOrigin().loop((x, y, z)->{
                    float med = median.getPixel(x, y, z);
                    if (accumulator.value.getPixel(x, y, z)-med>= thld) {
                        Voxel v =new Voxel(x, y, z, med);
                        for (int f = Math.max(0, accumulator.key-frameRadius); f<=accumulator.key; ++f) configMapF.getAndCreateIfNecessary(f).add(v);
                    }
                });
                return null;
            }
        };
        List<Image> imList = new ArrayList<>(inputImages.getFrameNumber());
        for (int f = 0; f<inputImages.getFrameNumber(); ++f) imList.add(inputImages.getImage(channelIdx, f));
        SlidingOperator.performSlideLeft(imList, frameRadius, operator);
        
        if (testMode) {
            // first frames are not computed
            for (int f = 0; f<frameRadius-1; ++f) testMeanTC[f][0] = testMeanTC[frameRadius-1][0];
            for (int f = 0; f<frameRadius-1; ++f) testMedianTC[f][0] = testMedianTC[frameRadius-1][0];
            ImageWindowManagerFactory.instanciateDisplayer().showImage5D("Sliding median", testMedianTC);
            ImageWindowManagerFactory.instanciateDisplayer().showImage5D("Sliding mean", testMeanTC);
            logger.debug("number of dead voxels detected: {}", configMapF.size());
        }
    }

    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return (configMapF!=null) || !configDataFXYZV.isEmpty(); // when config is computed put 1 vox and frame -1 to set config. 
    }

    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) throws Exception {
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
                m.setUp(image, image.getSizeZ()>1 ? new EllipsoidalNeighborhood(1.5, 1, true) : new EllipsoidalNeighborhood(1.5, true)); // excludes center pixel);
                for (Voxel v : dv) image.setPixel(v.x, v.y, v.z, m.applyFilter(v.x, v.y, v.z)); //
            }
        }
        return image;
    }
    /*
    @Override
    public List getConfigurationData() {
        if (configMapF!=null && !configMapF.isEmpty() && configMapF.size()!=configDataFXYZV.size()) {
            configDataFXYZV.clear();
            for (Entry<Integer, Set<Voxel>> e : configMapF.entrySet()) {
                for (Voxel v : e.getValue()) {
                    List<Double> l = new ArrayList<>(5);
                    l.add(e.getKey().doubleValue());
                    l.add((double)v.x);
                    l.add((double)v.y);
                    l.add((double)v.z);
                    l.add((double)v.value);
                    configDataFXYZV.add(l);
                }
            }
        }
        if (configDataFXYZV.isEmpty()) configDataFXYZV.add(Arrays.asList(new Double[]{-1d, 0d, 0d, 0d, 0d})); // to say its been configured
        return configDataFXYZV;
    }*/

    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
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
    
}
