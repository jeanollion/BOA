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

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.GroupParameter;
import boa.configuration.parameters.NumberParameter;
import boa.data_structure.input_image.InputImages;
import boa.image.BoundingBox;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageProperties;
import boa.plugins.ConfigurableTransformation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import boa.plugins.Cropper;
import boa.plugins.MultichannelTransformation;
import static boa.plugins.Plugin.logger;
import boa.plugins.Transformation;
import boa.utils.ArrayUtil;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 * @author jollion
 */
public abstract class CropMicroChannels implements ConfigurableTransformation, MultichannelTransformation {
    public static boolean debug = false;
    
    protected NumberParameter xStart = new BoundedNumberParameter("X start", 0, 0, 0, null);
    protected NumberParameter xStop = new BoundedNumberParameter("X stop (0 for image width)", 0, 0, 0, null);
    protected NumberParameter yStart = new BoundedNumberParameter("Y start", 0, 0, 0, null);
    protected NumberParameter yStop = new BoundedNumberParameter("Y stop (0 for image heigth)", 0, 0, 0, null);
    protected GroupParameter boundGroup = new GroupParameter("Bound constraint", xStart, xStop, yStart, yStop);
    //protected NumberParameter margin = new BoundedNumberParameter("X-Margin", 0, 0, 0, null).setToolTipText("Microchannels closer to X-border (left or right) than this value will be removed");
    protected NumberParameter channelHeight = new BoundedNumberParameter("Channel Height", 0, 410, 0, null);
    protected NumberParameter cropMarginY = new BoundedNumberParameter("Crop Margin", 0, 45, 0, null).setToolTipText("The y-start point will be shifted of this value towards upper direction");
    protected NumberParameter frameNumber = new BoundedNumberParameter("Frame Number", 0, 0, 0, null);
    
    ChoiceParameter referencePoint = new ChoiceParameter("Reference point", new String[]{"Top", "Bottom"}, "Top", false);
    Map<Integer, ? extends BoundingBox> cropBounds;
    BoundingBox bounds;
    public CropMicroChannels setReferencePoint(boolean top) {
        this.referencePoint.setSelectedIndex(top ? 0 : 1);
        return this;
    }
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        if (channelIdx<0) throw new IllegalArgumentException("Channel no configured");
        Image<? extends Image> image = inputImages.getImage(channelIdx, inputImages.getDefaultTimePoint());
        // check configuration validity
        if (xStop.getValue().intValue()==0 || xStop.getValue().intValue()>=image.sizeX()) xStop.setValue(image.sizeX()-1);
        if (xStart.getValue().intValue()>=xStop.getValue().intValue()) {
            logger.warn("CropMicroChannels2D: illegal configuration: xStart>=xStop, set to default values");
            xStart.setValue(0);
            xStop.setValue(image.sizeX()-1);
        }
        if (yStop.getValue().intValue()==0 || yStop.getValue().intValue()>=image.sizeY()) yStop.setValue(image.sizeY()-1);
        if (yStart.getValue().intValue()>=yStop.getValue().intValue()) {
            logger.warn("CropMicroChannels2D: illegal configuration: yStart>=yStop, set to default values");
            yStart.setValue(0);
            yStop.setValue(image.sizeY()-1);
        }
        
        if (channelHeight.getValue().intValue()>image.sizeY()) throw new IllegalArgumentException("channel height > image height");
        
        int framesN = frameNumber.getValue().intValue();
        List<Integer> frames; 
        switch(framesN) {
            case 0:
                frames = IntStream.range(0, inputImages.getFrameNumber()).mapToObj(i->(Integer)i).collect(Collectors.toList());
                break;
            case 1:
                frames = new ArrayList<Integer>(){{add(inputImages.getDefaultTimePoint());}};
                break;
            default :
                frames =  InputImages.chooseNImagesWithSignal(inputImages, channelIdx, framesN);
        }
        boolean test = testMode;
        if (framesN!=1) this.setTestMode(false);
        Function<Integer, MutableBoundingBox> getBds = i -> {
            Image<? extends Image> im = inputImages.getImage(channelIdx, i);
            if (im.sizeZ()>1) {
                int plane = inputImages.getBestFocusPlane(i);
                if (plane<0) throw new RuntimeException("CropMicrochannel can only be run on 2D images AND no autofocus algorithm was set");
                im = im.splitZPlanes().get(plane);
            }
            return getBoundingBox(im);
        };
        Map<Integer, MutableBoundingBox> bounds = frames.stream().parallel().collect(Collectors.toMap(i->i, i->getBds.apply(i)));
        if (framesN!=1 && test) { // only test for one frame
            this.setTestMode(true);
            getBds.apply(inputImages.getDefaultTimePoint());
        }
        if (bounds.isEmpty()) throw new RuntimeException("Bounds could not be computed");
        // uniformize y + reduce sizeY if necessary
        int sizeY = bounds.values().stream().mapToInt(b->b.sizeY()).max().getAsInt();
        if (referencePoint.getSelectedIndex()==0) { // ref = top
            int yMax = bounds.values().stream().mapToInt(b->b.yMin()+sizeY-1).max().getAsInt();
            int sY = yMax>=image.sizeY() ? sizeY -(yMax-image.sizeY()+1) : sizeY;
            for (MutableBoundingBox bb : bounds.values()) bb.setyMax(bb.yMin()+sY-1);
        } else { //ref = bottom
            int yMin = bounds.values().stream().mapToInt(b->b.yMax()-(sizeY-1)).max().getAsInt();
            int sY = yMin<0 ? sizeY-1 + yMin : sizeY;
            for (MutableBoundingBox bb : bounds.values()) bb.setyMin(bb.yMax()-(sY-1));
        }
        
        if (framesN !=0) { // merge all bounds by expanding
            Iterator<MutableBoundingBox> it = bounds.values().iterator();
            MutableBoundingBox bds = it.next();
            while (it.hasNext()) bds.union(it.next());
            this.bounds = bds;
        } else {
            // uniformize X if necessary -> min X size + ref point = middle point
            int sizeX = bounds.values().stream().mapToInt(b->b.sizeX()).min().getAsInt();
            bounds.values().stream().filter(bb->bb.sizeX()>sizeX).forEach(bb-> {
                int diff = bb.sizeX() - sizeX;
                int addLeft=diff/2;
                int remRight = diff - addLeft;
                bb.setxMin(bb.xMin()+addLeft);
                bb.setxMax(bb.xMax()-remRight);
            });
            this.cropBounds = bounds;
        }
    }
    /**
     * 
     * @param image
     * @param y
     * @return array containing xMin, xMax such that {@param image} has non null values @ y={@param y} in range [xMin, xMax] and that is range is maxmimal
     */
    protected static int[] getXMinAndMax(Image image, int y) {
        int start = 0;
        while (start<image.sizeX() && image.getPixel(start, y, 0)==0) ++start;
        int end = image.sizeX()-1;
        while (end>=0 && image.getPixel(end, y, 0)==0) --end;
        return new int[]{start, end};
    }
    /**
     *  
     
     * @param image
     * @param x
     * @return array containing yMin and yMax such that the whole {@param x} line strictly before yMin and aftery yMax of {@param image} have null values
     */
    protected static int[] getYMinAndMax(Image image, int x) {
        int start = 0;
        while (start<image.sizeY() && image.getPixel(x, start, 0)==0) ++start;
        int end = image.sizeY()-1;
        while (end>=0 && image.getPixel(x, end, 0)==0) --end;
        return new int[]{start, end};
    }
    
    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return bounds!=null || cropBounds!=null && this.cropBounds.size()==totalTimePointNumber;
    }
    
    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }
    protected abstract MutableBoundingBox getBoundingBox(Image image);
    
    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return bounds!=null ? image.crop(bounds) : image.crop(cropBounds.get(timePoint));
    }
    
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
