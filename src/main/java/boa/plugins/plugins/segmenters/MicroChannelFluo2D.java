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
package boa.plugins.plugins.segmenters;

import boa.plugins.MicrochannelSegmenter;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import ij.process.AutoThresholder;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.image.processing.Filters;
import boa.plugins.Plugin;
import boa.plugins.ToolTip;
import boa.plugins.TrackParametrizable;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.utils.Utils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
public class MicroChannelFluo2D implements MicrochannelSegmenter, TrackParametrizable<MicroChannelFluo2D>, ToolTip {

    NumberParameter channelHeight = new BoundedNumberParameter("Microchannel Height", 0, 430, 5, null).setToolTipText("Height of microchannel in pixels");
    NumberParameter channelWidth = new BoundedNumberParameter("Microchannel Width", 0, 40, 5, null);
    NumberParameter yShift = new BoundedNumberParameter("y-shift (start of microchannel)", 0, 20, 0, null).setToolTipText("Translation of the microchannel in upper direction");
    PluginParameter<boa.plugins.SimpleThresholder> threshold= new PluginParameter<>("Threshold", boa.plugins.SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false); //new BackgroundThresholder(3, 6, 3) when background is removed and images saved in 16b, half of background is trimmed -> higher values
    NumberParameter fillingProportion = new BoundedNumberParameter("Microchannel filling proportion", 2, 0.3, 0.05, 1).setToolTipText("Fill proportion = y-length of bacteria / height of microchannel. If proportion is under this value, the object won't be segmented. Allows to avoid segmenting islated bacteria in central channel");
    NumberParameter minObjectSize = new BoundedNumberParameter("Min. Object Size", 0, 200, 1, null).setToolTipText("To detect microchannel a rough semgentation of bacteria is performed by simple threshold. Object undier this size in pixels are removed, to avoid taking into account objects that are not bacteria");
    Parameter[] parameters = new Parameter[]{channelHeight, channelWidth, yShift, threshold, fillingProportion, minObjectSize};
    public static boolean debug = false;
    public String toolTip = "<html><b>Detection of microchannel using bacteria fluorescence:</b>"
    + "<ol><li>Rough segmentation of cells using \"Threshold\" computed the whole track prior to segmentation step</li>"
    + "<li>Selection of filled channels: lengh in X direction should be over \"Microchannel Height\" x \"Microchannel filling proportion\"</li>"
    + "<li>Computation of Y start: min value of the min y coordinate of the selected objects at step 2</li></ol></html>";
    public MicroChannelFluo2D() {}

    public MicroChannelFluo2D(int channelHeight, int channelWidth, int yMargin, double fillingProportion, int minObjectSize) {
        this.channelHeight.setValue(channelHeight);
        this.channelWidth.setValue(channelWidth);
        this.yShift.setValue(yMargin);
        this.fillingProportion.setValue(fillingProportion);
        this.minObjectSize.setValue(minObjectSize);
    }

    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        double thld = Double.isNaN(thresholdValue) ? this.threshold.instanciatePlugin().runThresholder(input, parent) : thresholdValue;
        logger.debug("thresholder: {} : {}", threshold.getPluginName(), threshold.getParameters());
        Result r = segmentMicroChannels(input, null, yShift.getValue().intValue(), channelWidth.getValue().intValue(), channelHeight.getValue().intValue(), fillingProportion.getValue().doubleValue(), thld, minObjectSize.getValue().intValue(), debug);
        if (r==null) return null;
        else return r.getObjectPopulation(input, true);
    }
    
    @Override
    public Result segment(Image input) {
        double thld = Double.isNaN(thresholdValue) ? this.threshold.instanciatePlugin().runSimpleThresholder(input, null) : thresholdValue;
        Result r = segmentMicroChannels(input, null, yShift.getValue().intValue(), channelWidth.getValue().intValue(), channelHeight.getValue().intValue(), fillingProportion.getValue().doubleValue(), thld, minObjectSize.getValue().intValue(), debug);
        return r;
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    // use threshold implementation
    protected double thresholdValue = Double.NaN;


    @Override
    public ApplyToSegmenter<MicroChannelFluo2D> run(int structureIdx, List<StructureObject> parentTrack) {
        double thld = TrackParametrizable.getGlobalThreshold(structureIdx, parentTrack, this.threshold.instanciatePlugin());
        return (p, s)->s.thresholdValue=thld;
    }
    /**
     *  Detection of microchannel using bacteria fluorescence
     *  1) Rough segmentation of cells on {@param image} using {@param thld} if {@param thld} is NaN threshold is computed using {@link boa.plugins.plugins.thresholders.BackgroundThresholder#runThresholder(boa.image.Image, boa.image.ImageMask, double, double, int, double, double[]) background thresholder} {@param thresholdedImage} is used instead if not null
        2) Selection of filled channels lengh in X direction should be over {@param channelHeight} x {@param fillingProportion} 
        3) Computation of Y start: min value of the min y coordinate of the selected objects at step 2
     * @param image
     * @param thresholdedImage optional (if null {@param image} will be semgmented using {@param thld}) binary image with bacteria roughly segmented
     * @param yShift y start of microchannels will be shifted of this value towards the top of the image
     * @param channelWidth microchannel width. if value <0, the average value of segmented object width is used
     * @param channelHeight used to determined if channels are filled enough
     * @param fillingProportion used to determined if channels are filled enough
     * @param minObjectSize used to filter small objects
     * @param thld optiona lused for image binarization, can be NaN
     * @param testMode
     * @return 
     */
    public static Result segmentMicroChannels(Image image, ImageInteger thresholdedImage, int yShift, int channelWidth, int channelHeight, double fillingProportion, double thld, int minObjectSize, boolean testMode) {
        
        // get thresholded image
        if (Double.isNaN(thld) && thresholdedImage == null) {
            thld = BackgroundThresholder.runThresholder(image, null, 3, 6, 3, Double.MAX_VALUE, null); //IJAutoThresholder.runThresholder(image, null, AutoThresholder.Method.Triangle); // OTSU / TRIANGLE / YEN
        }
        if (testMode)  Plugin.logger.debug("crop micochannels threshold : {}", thld);
        ImageInteger mask = thresholdedImage == null ? ImageOperations.threshold(image, thld, true, true) : thresholdedImage;
        // remove small objects
        Filters.binaryClose(mask, mask, Filters.getNeighborhood(1, 0, image)); // case of low intensity signal -> noisy. // remove small objects?
        List<Region> bacteria = ImageOperations.filterObjects(mask, mask, (Region o) -> o.getSize() < minObjectSize);
        // selected filled microchannels
        float[] xProj = ImageOperations.meanProjection(mask, ImageOperations.Axis.X, null);
        ImageFloat imProjX = new ImageFloat("proj(X)", mask.getSizeX(), new float[][]{xProj});
        double thldX = channelHeight * fillingProportion; // only take into account roughly filled channels
        thldX /= (double) (image.getSizeY() * image.getSizeZ()); // mean X projection
        ImageByte projXThlded = ImageOperations.threshold(imProjX, thldX, true, false).setName("proj(X) thlded: " + thldX);
        if (testMode) {
            ImageWindowManagerFactory.showImage(mask);
            Utils.plotProfile(imProjX, 0, 0, true);
            Utils.plotProfile(projXThlded, 0, 0, true);
        }
        List<Region> xObjectList = ImageLabeller.labelImageList(projXThlded);
        if (xObjectList.isEmpty()) {
            return null;
        }
        if (channelWidth <= 1) {
            channelWidth = (int) xObjectList.stream().mapToInt((Region o) -> o.getBounds().getSizeX()).average().getAsDouble();
        }
        int leftLimit = channelWidth / 2 + 1;
        int rightLimit = image.getSizeX() - leftLimit;
        Iterator<Region> it = xObjectList.iterator();
        while (it.hasNext()) {
            BoundingBox b = it.next().getBounds();
            if (b.getXMean() < leftLimit || b.getXMean() > rightLimit) {
                it.remove(); //if (b.getxMin()<Xmargin || b.getxMax()>rightLimit) it.remove(); //
            }
        }
        if (xObjectList.isEmpty()) {
            return null;
        }
        // fusion of overlapping objects
        it = xObjectList.iterator();
        Region prev = it.next();
        while (it.hasNext()) {
            Region next = it.next();
            if (prev.getBounds().getxMax() + 1 > next.getBounds().getxMin()) {
                prev.addVoxels(next.getVoxels());
                it.remove();
            } else {
                prev = next;
            }
        }
        Region[] xObjects = xObjectList.toArray(new Region[xObjectList.size()]);
        if (xObjects.length == 0) {
            return null;
        }
        if (testMode) {
            ImageWindowManagerFactory.showImage(new RegionPopulation(bacteria, mask).getLabelMap().setName("segmented bacteria"));
        }
        if (testMode) {
            Plugin.logger.debug("mc: {}, objects: {}", Utils.toStringArray(xObjects, (Region o) -> o.getBounds()), bacteria.size());
        }
        if (bacteria.isEmpty()) {
            return null;
        }
        int[] yMins = new int[xObjects.length];
        Arrays.fill(yMins, Integer.MAX_VALUE);
        for (Region o : bacteria) {
            BoundingBox b = o.getBounds();
            //if (debug) logger.debug("object: {}");
            X_SEARCH:
            for (int i = 0; i < xObjects.length; ++i) {
                BoundingBox inter = b.getIntersection(xObjects[i].getBounds());
                if (inter.getSizeX() >= 2) {
                    if (b.getyMin() < yMins[i]) {
                        yMins[i] = b.getyMin();
                    }
                    break X_SEARCH;
                }
            }
        }
        // get median yMin
        List<Integer> yMinsList = new ArrayList<>(yMins.length);
        for (int yMin : yMins) {
            if (yMin != Integer.MAX_VALUE) {
                yMinsList.add(yMin);
            }
        }
        if (yMinsList.isEmpty()) {
            return null;
        }
        //int yMin = (int)Math.round(ArrayUtil.medianInt(yMinsList));
        //if (debug) logger.debug("Ymin: {}, among: {} values : {}, shift: {}", yMin, yMinsList.size(), yMins, yShift);
        int yMin = Collections.min(yMinsList);
        List<int[]> sortedMinMaxYShiftList = new ArrayList<>(xObjects.length);
        for (int i = 0; i < xObjects.length; ++i) {
            if (yMins[i] == Integer.MAX_VALUE) {
                continue;
            }
            int xMin = (int) (xObjects[i].getBounds().getXMean() - channelWidth / 2.0);
            int xMax = (int) (xObjects[i].getBounds().getXMean() + channelWidth / 2.0); // mc remains centered
            if (xMin < 0 || xMax >= image.getSizeX()) {
                continue; // exclude outofbounds objects
            }
            int[] minMaxYShift = new int[]{xMin, xMax, yMins[i] - yMin < yShift ? 0 : yMins[i] - yMin};
            sortedMinMaxYShiftList.add(minMaxYShift);
        }
        Collections.sort(sortedMinMaxYShiftList, (int[] i1, int[] i2) -> Integer.compare(i1[0], i2[0]));
        return new Result(sortedMinMaxYShiftList, Math.max(0, yMin - yShift), yMin + channelHeight - yShift);
    }

    @Override
    public String getToolTipText() {
        return toolTip;
    }

}
