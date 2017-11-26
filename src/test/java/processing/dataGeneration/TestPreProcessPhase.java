/*
 * Copyright (C) 2016 jollion
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
package processing.dataGeneration;

import static TestUtils.Utils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.TransformationPluginParameter;
import core.Processor;
import static core.Processor.setTransformations;
import core.Task;
import dataStructure.configuration.MicroscopyField;
import dataStructure.containers.InputImagesImpl;
import dataStructure.objects.MasterDAO;
import ij.ImageJ;
import image.Image;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.List;
import plugins.PluginFactory;
import plugins.Transformation;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.preFilter.TopHat;
import plugins.plugins.transformations.AutoRotationXY;
import plugins.plugins.transformations.CropMicroChannelBF2D;
import plugins.plugins.transformations.CropMicroChannelFluo2D;
import plugins.plugins.transformations.CropMicroChannels2D;
import plugins.plugins.transformations.Flip;
import plugins.plugins.transformations.ImageStabilizerCore;
import static plugins.plugins.transformations.ImageStabilizerXY.testTranslate;
import plugins.plugins.transformations.SaturateHistogram;
import plugins.plugins.transformations.SimpleRotationXY;
import processing.ImageTransformation;

/**
 *
 * @author jollion
 */
public class TestPreProcessPhase {
    public static void main(String[] args) throws Exception {
        new ImageJ();
        PluginFactory.findPlugins("plugins.plugins");
        //String dbName= "boa_phase140115mutH";
        //String dbName = "boa_phase150616wt";
        String dbName = "fluo171113_WT_15s";
        
        boolean flip = true;
        int field = 0;
        String fieldName = null;
        int time = 0;
        //testRotation(dbName, 0, 0, time);
        //CropMicroChannelBF2D.debug=true;
        //testPreProcessing(dbName, field, 0, time, 0, 2);
        testCrop(dbName, field, fieldName, time, flip);
        //testStabilizer(dbName, field, 0, 20, 19, flip);
    }
    
    public static void testTransformation(String dbName, int fieldIdx, int channelIdx, int time) throws Exception {
        MasterDAO db = new Task(dbName).getDB();
        MicroscopyField f = db.getExperiment().getPosition(fieldIdx);
        //Processor.setTransformations(f, true);
        InputImagesImpl images = f.getInputImages();
        Image im = images.getImage(channelIdx, time);
        Transformation t = new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT).setPrefilters(new IJSubtractBackground(0.3, true, false, true, false));
        //Transformation t = new TopHat(12, Double.NaN, true, true);
        //Transformation t = new IJSubtractBackground(0.3, true, false, true, false);
        t.computeConfigurationData(channelIdx, images);
        Image res = t.applyTransformation(channelIdx, time, im);
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(im.setName("input"));
        disp.showImage(res.setName("transformed"));
    }
    
    public static void testRotation(String dbName, int fieldIdx, int channelIdx, int time) throws Exception {
        MasterDAO db = new Task(dbName).getDB();
        MicroscopyField f = db.getExperiment().getPosition(fieldIdx);
        InputImagesImpl images = f.getInputImages();
        Image im = images.getImage(channelIdx, time);
        AutoRotationXY t = new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT).setPrefilters(new IJSubtractBackground(0.3, true, false, true, false));
        
        AutoRotationXY.testMode=true;
        
        t.computeConfigurationData(channelIdx, images);
        Image res = t.applyTransformation(channelIdx, time, im);
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(im.setName("input"));
        disp.showImage(res.setName("rotated"));
    }
    
    public static void testCrop(String dbName, int fieldIdx, String positionName, int time, boolean flip) throws Exception {
        MasterDAO db = new Task(dbName).getDB();
        MicroscopyField f = positionName ==null ? db.getExperiment().getPosition(fieldIdx): db.getExperiment().getPosition(positionName);
        //f.getPreProcessingChain().removeAllTransformations();
        //f.getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT).setPrefilters(new IJSubtractBackground(0.3, true, false, true, false)));
        //if (flip) f.getPreProcessingChain().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y));
        //f.getPreProcessingChain().addTransformation(0, null, new CropMicroChannelBF2D().setTimePointNumber(1));
        List<TransformationPluginParameter<Transformation>> list = new ArrayList<>(f.getPreProcessingChain().getTransformations(false));
        int cropIdx = -1;
        int i = 0;
        for (TransformationPluginParameter<Transformation> tpp : list) {
            if (tpp.instanciatePlugin() instanceof CropMicroChannelBF2D) cropIdx = i;
            ++i;
        }
        if (cropIdx<0) {
            logger.error("no crop defined");
            return;
        }
        f.getPreProcessingChain().removeAllTransformations();
        for (i = 0;i<=cropIdx; ++i) f.getPreProcessingChain().addTransformation(list.get(i).getInputChannel(), list.get(i).getOutputChannels(), list.get(i).instanciatePlugin());
        CropMicroChannelBF2D.debug=true;
        //Image[][] imageInputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        //for (int t = 0; t<imageInputTC.length; ++t) imageInputTC[t][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, t);
        
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("input: f="+fieldIdx));
        Processor.setTransformations(f, true);
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("output: f="+fieldIdx));
    }
    
    
    public static void testPreProcessing(String dbName, int fieldIdx, int channelIdx, int time, int tStart, int tEnd) throws Exception {
        MasterDAO db = new Task(dbName).getDB();
        MicroscopyField f = db.getExperiment().getPosition(fieldIdx);
        InputImagesImpl images = f.getInputImages();
        if (time>=tStart) time -=tStart;
        if (time<0) images.subSetTimePoints(tStart, tEnd);
        IJImageDisplayer disp = new IJImageDisplayer();
        if (time>=0) {
            Image input = images.getImage(channelIdx, time).duplicate("input");
            Processor.setTransformations(f, true);
            Image output = images.getImage(channelIdx, time).setName("output");
            disp.showImage(input);
            disp.showImage(output);
        } else { // display all
            List<Image> input = new ArrayList<Image>(tEnd-tStart+1);
            for (int t = 0; t<=(tEnd-tStart); ++t) input.add(images.getImage(channelIdx, t).duplicate("input"+t));
            Processor.setTransformations(f, true);
            List<Image> output = new ArrayList<Image>(tEnd-tStart+1);
            for (int t = 0; t<=(tEnd-tStart); ++t) output.add(images.getImage(channelIdx, t).duplicate("output"+t));
            disp.showImage(Image.mergeZPlanes(input).setName("input"));
            disp.showImage(Image.mergeZPlanes(output).setName("output"));
        }
        
        
    }
    
}
