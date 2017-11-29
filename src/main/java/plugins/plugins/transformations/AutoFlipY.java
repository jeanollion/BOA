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
package plugins.plugins.transformations;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import image.BoundingBox;
import image.Image;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageOperations;
import image.ThresholdMask;
import image.TypeConverter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import plugins.SimpleThresholder;
import plugins.Transformation;
import plugins.plugins.thresholders.BackgroundThresholder;
import static plugins.plugins.transformations.AutoFlipY.AutoFlipMethod.FLUO;
import processing.ImageTransformation;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class AutoFlipY implements Transformation {
    public static enum AutoFlipMethod {
        FLUO("Bacteria Fluo");
        //PHASE("Phase Optical Aberration");
        final String name;
        AutoFlipMethod(String name) {
            this.name=name;
        }
    }
    ChoiceParameter method = new ChoiceParameter("Method", Utils.transform(AutoFlipMethod.values(), new String[AutoFlipMethod.values().length], f->f.name), FLUO.name, false);
    PluginParameter<SimpleThresholder> fluoThld = new PluginParameter<>("Threshold for bacteria Segmentation", SimpleThresholder.class, new BackgroundThresholder(2.5, 3, 3), false); 
    ConditionalParameter cond = new ConditionalParameter(method).setActionParameters("Bacteria Fluo", new Parameter[]{fluoThld});
    List<Boolean> config = new ArrayList<>(1);
    
    public AutoFlipY() {}
    
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) throws Exception {
        if (method.getSelectedItem().equals(FLUO.name)) { 
            // rough segmentation and get side where cells are better aligned
            List<Integer> frames = InputImages.chooseNImagesWithSignal(inputImages, channelIdx, 9);
            int countFlip = 0;
            int countNoFlip = 0;
            for (int f: frames) {
                Image image = inputImages.getImage(channelIdx, f);
                if (image.getSizeZ()>1) {
                    int plane = inputImages.getBestFocusPlane(f);
                    if (plane<0) throw new RuntimeException("AutoFlip can only be run on 2D images AND no autofocus algorithm was set");
                    image = image.splitZPlanes().get(plane);
                }
                Boolean flip = isFlipFluo(image);
                if (flip!=null) {
                    if (flip) ++countFlip;
                    else ++countNoFlip;
                }
            }
            config.add(countFlip>countNoFlip);
        } /*else if (method.getSelectedItem().equals(PHASE.name)) { 
            // detection of optical abberation
            // comparison of signal above & under using gradient filer
            
        }*/
    }
    
    private Boolean isFlipFluo(Image image) {
        SimpleThresholder thlder = fluoThld.instanciatePlugin();
        ImageMask mask = new ThresholdMask(image, thlder.runSimpleThresholder(image, null), true, true);
        List<Object3D> objects = ImageLabeller.labelImageList(mask);
        objects.removeIf(o->o.getSize()<10);
        logger.debug("objects: {}", objects.size());
        if (objects.isEmpty() || objects.size()<=2) return null;
        Map<Object3D, BoundingBox> xBounds = objects.stream().collect(Collectors.toMap(o->o, o->new BoundingBox(o.getBounds().getxMin(), o.getBounds().getxMax(), 0, 1, 0, 1)));
        Iterator<Object3D> it = objects.iterator();
        List<Object3D> yMinOs = new ArrayList<>();
        List<Object3D> yMaxOs = new ArrayList<>();
        while(it.hasNext()) {
            Object3D o = it.next();
            List<Object3D> inter = new ArrayList<>(objects);
            inter.removeIf(oo->!xBounds.get(oo).hasIntersection(xBounds.get(o)));
            yMinOs.add(Collections.min(inter, (o1, o2)->Integer.compare(o1.getBounds().getyMin(), o2.getBounds().getyMin())));
            yMaxOs.add(Collections.max(inter, (o1, o2)->Integer.compare(o1.getBounds().getyMax(), o2.getBounds().getyMax())));
            objects.removeAll(inter);
            it = objects.iterator();
        }
        if (testMode) {
            ImageWindowManagerFactory.showImage(TypeConverter.toByteMask(mask, null, 1).setName("Segmentation mask"));
            ImageWindowManagerFactory.showImage(new ObjectPopulation(objects, image).getLabelMap().setName("Segmented Objects"));
        }
        List<Integer> yMins = Utils.transform(yMinOs, o->o.getBounds().getyMin());
        double sigmaMin = getSigma(yMins);
        List<Integer> yMaxs = Utils.transform(yMaxOs, o->o.getBounds().getyMax());
        double sigmaMax = getSigma(yMaxs);
        logger.debug("yMins sigma: {}: {}", sigmaMin, Utils.toStringList(yMins));
        logger.debug("yMaxs sigma {}: {}", sigmaMax, Utils.toStringList(yMaxs));
        logger.debug("flip: {}", sigmaMin>sigmaMax);
        return sigmaMin>sigmaMax;
    }
    
    private static double getSigma(List<Integer> l) {
        double values2 = 0;
        double sum = 0;
        for (int i : l) {
            values2 += i*i;
            sum+=i;
        }
        values2/=(double)l.size();
        sum/=(double)l.size();
        return Math.sqrt(values2 - sum * sum);
    }
    
    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return (config.size()==1);
    }

    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) throws Exception {
        if (config.get(0)) ImageTransformation.flip(image, ImageTransformation.Axis.Y);
        return image;
    }

    @Override
    public List getConfigurationData() {
        return config;
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
