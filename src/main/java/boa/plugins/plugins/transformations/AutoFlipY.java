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

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.image.BlankMask;
import static boa.image.BoundingBox.intersect2D;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.SimpleBoundingBox;
import boa.image.processing.ImageOperations;
import boa.image.ThresholdMask;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import boa.plugins.SimpleThresholder;
import boa.plugins.Transformation;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import static boa.plugins.plugins.transformations.AutoFlipY.AutoFlipMethod.FLUO;
import static boa.plugins.plugins.transformations.AutoFlipY.AutoFlipMethod.FLUO_HALF_IMAGE;
import boa.image.processing.ImageTransformation;
import boa.plugins.ToolTip;
import static boa.plugins.plugins.transformations.AutoFlipY.AutoFlipMethod.PHASE;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.Arrays;

/**
 *
 * @author jollion
 */
public class AutoFlipY implements Transformation, ToolTip {

    
    public static enum AutoFlipMethod {
        FLUO("Bacteria Fluo", "Detects side where bacteria are more aligned -> should be the upper side"),
        FLUO_HALF_IMAGE("Bacteria Fluo: Upper Half of Image", "Bacteria should be present in upper half of the image"),
        PHASE("Phase Contrast Optical Aberration", "Optical Aberration is detected and side where variance along X axis is maximal is selected OR if the optical aberration is closer to one side on the image than microchannel height the other side is selected");
        final String name;
        final String toolTip;
        AutoFlipMethod(String name, String toolTip) {
            this.name=name;
            this.toolTip=toolTip;
        }
        public static AutoFlipMethod getMethod(String name) {
            for (AutoFlipMethod m : AutoFlipMethod.values()) if (m.name.equals(name)) return m;
            return null;
        }
    }
    String toolTip = "Methods for flipping image along Y-axis in order to set the close-end of channel at the top of the image. Should be set after the rotation";
    ChoiceParameter method = new ChoiceParameter("Method", Utils.transform(AutoFlipMethod.values(), new String[AutoFlipMethod.values().length], f->f.name), FLUO_HALF_IMAGE.name, false);
    PluginParameter<SimpleThresholder> fluoThld = new PluginParameter<>("Threshold for bacteria Segmentation", SimpleThresholder.class, new BackgroundThresholder(4, 5, 3), false); 
    NumberParameter minObjectSize = new BoundedNumberParameter("Minimal Object Size", 1, 100, 10, null).setToolTipText("Object under this size (in pixels) will be removed");
    NumberParameter microchannelLength = new BoundedNumberParameter("Microchannel Length", 0, 400, 100, null).setToolTipText("Typical Microchannel Length");
    ConditionalParameter cond = new ConditionalParameter(method).setActionParameters("Bacteria Fluo", new Parameter[]{fluoThld, minObjectSize}).setActionParameters("Phase Contrast Optical Aberration", new Parameter[]{microchannelLength});
    Boolean flip = null;
    public AutoFlipY() {
        cond.addListener(p->{ 
            AutoFlipMethod m = AutoFlipMethod.getMethod(method.getSelectedItem());
            if (m!=null) cond.setToolTipText(m.toolTip);
            else cond.setToolTipText("Choose autoflip algorithm");
        });
    }
    @Override
    public String getToolTipText() {
        return toolTip;
    }
    public AutoFlipY setMethod(AutoFlipMethod method) {
        this.method.setValue(method.name);
        return this;
    }
    List<Image> upperObjectsTest, lowerObjectsTest;
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        flip=null;
        if (method.getSelectedItem().equals(FLUO.name)) { 
            if (testMode) {
                upperObjectsTest=new ArrayList<>();
                lowerObjectsTest=new ArrayList<>();
            }
            // rough segmentation and get side where cells are better aligned
            List<Integer> frames = InputImages.chooseNImagesWithSignal(inputImages, channelIdx, 5);
            List<Boolean> flips = frames.stream().parallel().map(f->{
                Image<? extends Image> image = inputImages.getImage(channelIdx, f);
                if (image.sizeZ()>1) {
                    int plane = inputImages.getBestFocusPlane(f);
                    if (plane<0) throw new RuntimeException("AutoFlip can only be run on 2D images AND no autofocus algorithm was set");
                    image = image.splitZPlanes().get(plane);
                }
                return isFlipFluo(image);
            }).collect(Collectors.toList());
            long countFlip = flips.stream().filter(b->b!=null && b).count();
            long countNoFlip = flips.stream().filter(b->b!=null && !b).count();
            if (testMode) {
                ImageWindowManagerFactory.showImage(Image.mergeZPlanes(upperObjectsTest).setName("Upper Objects"));
                ImageWindowManagerFactory.showImage(Image.mergeZPlanes(lowerObjectsTest).setName("Lower Objects"));
                upperObjectsTest.clear();
                lowerObjectsTest.clear();
            }
            flip = countFlip>countNoFlip;
            logger.debug("AutoFlipY: {} (flip:{} vs:{})", flip, countFlip, countNoFlip);
        } else if (method.getSelectedItem().equals(FLUO_HALF_IMAGE.name)) { 
            // compares signal in upper half & lower half -> signal should be in upper half
            List<Integer> frames = InputImages.chooseNImagesWithSignal(inputImages, channelIdx, 1);
            List<Boolean> flips = frames.stream().parallel().map(f->{
                Image<? extends Image> image = inputImages.getImage(channelIdx, f);
                if (image.sizeZ()>1) {
                    int plane = inputImages.getBestFocusPlane(f);
                    if (plane<0) throw new RuntimeException("AutoFlip can only be run on 2D images AND no autofocus algorithm was set");
                    image = image.splitZPlanes().get(plane);
                }
                return isFlipFluoUpperHalf(image);
            }).collect(Collectors.toList());
            long countFlip = flips.stream().filter(b->b!=null && b).count();
            long countNoFlip = flips.stream().filter(b->b!=null && !b).count();
            
            flip = countFlip>countNoFlip;
            logger.debug("AutoFlipY: {} (flip:{} vs:{})", flip, countFlip, countNoFlip);
        } else if (method.getSelectedItem().equals(PHASE.name)) {
            int length = microchannelLength.getValue().intValue();
            Image image = inputImages.getImage(channelIdx, inputImages.getDefaultTimePoint());
            // if rotation before -> top & bottom of image can contain zeros -> mean proj would return NaN
            float[] yProj = ImageOperations.meanProjection(image, ImageOperations.Axis.Y, null, v->v>0);
            int start = 0;
            while (start<yProj.length && Float.isNaN(yProj[start])) ++start;
            int end = yProj.length;
            while (end>0 && Float.isNaN(yProj[end-1])) --end;
            if (start>=end)  throw new RuntimeException("Autoflip error: no values>0 @ Frame: "+inputImages.getDefaultTimePoint()+", channel "+channelIdx);
            int peakIdx = ArrayUtil.max(yProj, start, end);           
            double median = ArrayUtil.median(Arrays.copyOfRange(yProj, start, end-start));
            double peakHeight = yProj[peakIdx] - median;
            float thld = (float)(peakHeight * 0.25 + median  ); //
            int startOfPeakIdx = ArrayUtil.getFirstOccurence(yProj, peakIdx, start, thld, true, true)-length/6; // is there enough space above the aberration ? 
            if (startOfPeakIdx-start<length*0.75) {
                flip = true;
                return;
            }
            int endOfPeakIdx = ArrayUtil.getFirstOccurence(yProj, peakIdx, end, thld, true, true)+length/6; // is there enough space under the aberration ? 
            if (end - endOfPeakIdx<=length*0.75) {
                flip = false;
                return;
            }
            //logger.debug("would flip: {} values: [{};{}], peak: [{}-{}-{}] height: {} [{}-{}]", flip, start, end, startOfPeakIdx, peakIdx, endOfPeakIdx,yProj[peakIdx]-median, yProj[peakIdx], median );
                
            // compare upper and lower side X-variances withing frame of microchannel length
            float[] xProjUpper = ImageOperations.meanProjection(image, ImageOperations.Axis.X, new SimpleBoundingBox(0, image.sizeX()-1, Math.max(start, startOfPeakIdx-length), startOfPeakIdx, 0, image.sizeZ()-1), v->v>0); 
            float[] xProjLower = ImageOperations.meanProjection(image, ImageOperations.Axis.X, new SimpleBoundingBox(0, image.sizeX()-1, endOfPeakIdx, Math.min(end-1, endOfPeakIdx+length), 0, image.sizeZ()-1), v->v>0);
            double varUpper = ArrayUtil.meanSigma(xProjUpper, 0, xProjUpper.length, null)[1];
            double varLower = ArrayUtil.meanSigma(xProjLower, 0, xProjLower.length, null)[1];
            flip = varLower>varUpper;
            logger.debug("AutoFlipY: {} (var upper: {}, var lower: {} aberration: [{};{};{}]", flip, varLower, varUpper,startOfPeakIdx, peakIdx, endOfPeakIdx );
            
        }
    }
    private Boolean isFlipPhaseOpticalAberration(Image image) {
        /* 
        1) search for optical aberration
        2) get x variance for each line above and under aberration -> microchannels are where variance is maximal
        */
        ImageMask upper = new BlankMask( image.sizeX(), image.sizeY()/2, image.sizeZ(), image.xMin(), image.yMin(), image.zMin(), image.getScaleXY(), image.getScaleZ());
        ImageMask lower = new BlankMask( image.sizeX(), image.sizeY()/2, image.sizeZ(), image.xMin(), image.yMin()+image.sizeY()/2, image.zMin(), image.getScaleXY(), image.getScaleZ());
        double upperMean = ImageOperations.getMeanAndSigmaWithOffset(image, upper, null)[0];
        double lowerMean = ImageOperations.getMeanAndSigmaWithOffset(image, lower, null)[0];
        if (testMode) logger.debug("AutoFlipY: upper half mean {} lower: {}", upperMean, lowerMean);
        if (upperMean>lowerMean) return false;
        else if (lowerMean>upperMean) return true;
        else return null;
    }
    private Boolean isFlipFluoUpperHalf(Image image) {
        ImageMask upper = new BlankMask( image.sizeX(), image.sizeY()/2, image.sizeZ(), image.xMin(), image.yMin(), image.zMin(), image.getScaleXY(), image.getScaleZ());
        ImageMask lower = new BlankMask( image.sizeX(), image.sizeY()/2, image.sizeZ(), image.xMin(), image.yMin()+image.sizeY()/2, image.zMin(), image.getScaleXY(), image.getScaleZ());
        double upperMean = ImageOperations.getMeanAndSigmaWithOffset(image, upper, null)[0];
        double lowerMean = ImageOperations.getMeanAndSigmaWithOffset(image, lower, null)[0];
        if (testMode) logger.debug("AutoFlipY: upper half mean {} lower: {}", upperMean, lowerMean);
        if (upperMean>lowerMean) return false;
        else if (lowerMean>upperMean) return true;
        else return null;
    }
    private Boolean isFlipFluo(Image image) {
        int minSize = minObjectSize.getValue().intValue();
        SimpleThresholder thlder = fluoThld.instanciatePlugin();
        ImageMask mask = new ThresholdMask(image, thlder.runSimpleThresholder(image, null), true, true);
        List<Region> objects = ImageLabeller.labelImageList(mask);
        objects.removeIf(o->o.size()<minSize);
        // filter by median sizeY
        Map<Region, Integer> sizeY = objects.stream().collect(Collectors.toMap(o->o, o->o.getBounds().sizeY()));
        double medianSizeY = ArrayUtil.medianInt(sizeY.values());
        objects.removeIf(o->sizeY.get(o)<medianSizeY/2);
        if (testMode) logger.debug("objects: {}, minSize: {}, minSizeY: {} (median sizeY: {})", objects.size(), minSize, medianSizeY/2, medianSizeY);
        if (objects.isEmpty() || objects.size()<=2) return null;
        Map<Region, MutableBoundingBox> xBounds = objects.stream().collect(Collectors.toMap(o->o, o->new MutableBoundingBox(o.getBounds().xMin(), o.getBounds().xMax(), 0, 1, 0, 1)));
        Iterator<Region> it = objects.iterator();
        List<Region> yMinOs = new ArrayList<>();
        List<Region> yMaxOs = new ArrayList<>();
        while(it.hasNext()) {
            Region o = it.next();
            List<Region> inter = new ArrayList<>(objects);
            inter.removeIf(oo->!intersect2D(xBounds.get(oo), xBounds.get(o)));
            yMinOs.add(Collections.min(inter, (o1, o2)->Integer.compare(o1.getBounds().yMin(), o2.getBounds().yMin())));
            yMaxOs.add(Collections.max(inter, (o1, o2)->Integer.compare(o1.getBounds().yMax(), o2.getBounds().yMax())));
            objects.removeAll(inter);
            it = objects.iterator();
        }
        // filter outliers with distance to median value
        double yMinMed = ArrayUtil.medianInt(Utils.transform(yMinOs, o->o.getBounds().yMin()));
        yMinOs.removeIf(o->Math.abs(o.getBounds().yMin()-yMinMed)>o.getBounds().sizeY()/4);
        double yMaxMed = ArrayUtil.medianInt(Utils.transform(yMaxOs, o->o.getBounds().yMax()));
        yMaxOs.removeIf(o->Math.abs(o.getBounds().yMax()-yMaxMed)>o.getBounds().sizeY()/4);
        
        if (testMode) {
            //ImageWindowManagerFactory.showImage(TypeConverter.toByteMask(mask, null, 1).setName("Segmentation mask"));
            this.upperObjectsTest.add(new RegionPopulation(yMinOs, image).getLabelMap().setName("Upper Objects"));
            this.lowerObjectsTest.add(new RegionPopulation(yMaxOs, image).getLabelMap().setName("Lower Objects"));
        }
        List<Pair<Integer, Integer>> yMins = Utils.transform(yMinOs, o->new Pair<>(o.getBounds().yMin(), o.getBounds().sizeY()));
        double sigmaMin = getSigma(yMins);
        List<Pair<Integer, Integer>> yMaxs = Utils.transform(yMaxOs, o->new Pair<>(o.getBounds().yMax(), o.getBounds().sizeY()));
        double sigmaMax = getSigma(yMaxs);
        if (testMode) {
            logger.debug("yMins sigma: {}: {}", sigmaMin, Utils.toStringList(yMins));
            logger.debug("yMaxs sigma {}: {}", sigmaMax, Utils.toStringList(yMaxs));
            logger.debug("flip: {}", sigmaMin>sigmaMax);
        }
        return sigmaMin>sigmaMax;
    }
    
    private static double getSigma(List<Pair<Integer, Integer>> l) {
        double mean = 0;
        for (Pair<Integer, Integer> p : l) mean +=p.key;
        mean/=(double)l.size();
        double mean2 = 0;
        double count = 0;
        for (Pair<Integer, Integer> p : l) {
            mean2 += Math.pow(p.key-mean, 2) * p.value;
            count+=p.value;
        }
        return mean2/count;
    }
    
    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return flip!=null;
    }
    
    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (flip) {
            ///logger.debug("AutoFlipY: flipping (flip config: {} ({}))", flip, flip.getClass().getSimpleName());
            return ImageTransformation.flip(image, ImageTransformation.Axis.Y);
        } //else logger.debug("AutoFlipY: no flip");
        return image;
    }

    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }
    boolean testMode;
    @Override
    public void setTestMode(boolean testMode) {
        this.testMode=testMode;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{cond};
    }
    
}
