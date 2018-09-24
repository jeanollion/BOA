/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.processing.test;

import static boa.test_utils.TestUtils.logger;
import boa.gui.image_interaction.IJImageDisplayer;
import boa.configuration.parameters.TransformationPluginParameter;
import boa.core.Processor;
import static boa.core.Processor.setTransformations;
import boa.core.Task;
import boa.configuration.experiment.Position;
import boa.data_structure.input_image.InputImagesImpl;
import boa.data_structure.dao.MasterDAO;
import ij.ImageJ;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.plugins.ConfigurableTransformation;
import boa.plugins.MultichannelTransformation;
import java.util.ArrayList;
import java.util.List;
import boa.plugins.PluginFactory;
import boa.plugins.Transformation;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;
import boa.plugins.plugins.pre_filters.TopHat;
import boa.plugins.plugins.transformations.AutoRotationXY;
import boa.plugins.plugins.transformations.CropMicrochannelsPhase2D;

/**
 *
 * @author Jean Ollion
 */
public class TestPreProcessPhase {
    public static void main(String[] args) throws Exception {
        new ImageJ();
        PluginFactory.findPlugins("boa.plugins.plugins");
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
        Position f = db.getExperiment().getPosition(fieldIdx);
        //Processor.setTransformations(f, true);
        InputImagesImpl images = f.getInputImages();
        Image im = images.getImage(channelIdx, time);
        Transformation t = new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT).setPrefilters(new IJSubtractBackground(0.3, true, false, true, false));
        //Transformation t = new TopHat(12, Double.NaN, true, true);
        //Transformation t = new IJSubtractBackground(0.3, true, false, true, false);
        if (t instanceof ConfigurableTransformation) ((ConfigurableTransformation)t).computeConfigurationData(channelIdx, images);
        Image res = t.applyTransformation(channelIdx, time, im);
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(im.setName("input"));
        disp.showImage(res.setName("transformed"));
    }
    
    public static void testRotation(String dbName, int fieldIdx, int channelIdx, int time) throws Exception {
        MasterDAO db = new Task(dbName).getDB();
        Position f = db.getExperiment().getPosition(fieldIdx);
        InputImagesImpl images = f.getInputImages();
        Image im = images.getImage(channelIdx, time);
        AutoRotationXY t = new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT).setPrefilters(new IJSubtractBackground(0.3, true, false, true, false));
        t.setTestMode(true);
        t.computeConfigurationData(channelIdx, images);
        Image res = t.applyTransformation(channelIdx, time, im);
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(im.setName("input"));
        disp.showImage(res.setName("rotated"));
    }
    
    public static void testCrop(String dbName, int fieldIdx, String positionName, int time, boolean flip) throws Exception {
        MasterDAO db = new Task(dbName).getDB();
        Position f = positionName ==null ? db.getExperiment().getPosition(fieldIdx): db.getExperiment().getPosition(positionName);
        //f.getPreProcessingChain().removeAllTransformations();
        //f.getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT).setPrefilters(new IJSubtractBackground(0.3, true, false, true, false)));
        //if (flip) f.getPreProcessingChain().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y));
        //f.getPreProcessingChain().addTransformation(0, null, new CropMicroChannelBF2D().setTimePointNumber(1));
        List<TransformationPluginParameter<Transformation>> list = new ArrayList<>(f.getPreProcessingChain().getTransformations(false));
        int cropIdx = -1;
        int i = 0;
        for (TransformationPluginParameter<Transformation> tpp : list) {
            if (tpp.instanciatePlugin() instanceof CropMicrochannelsPhase2D) cropIdx = i;
            ++i;
        }
        if (cropIdx<0) {
            logger.error("no crop defined");
            return;
        }
        f.getPreProcessingChain().removeAllTransformations();
        for (i = 0;i<=cropIdx; ++i) f.getPreProcessingChain().addTransformation(list.get(i).getInputChannel(), list.get(i).getOutputChannels(), list.get(i).instanciatePlugin());
        CropMicrochannelsPhase2D.debug=true;
        //Image[][] imageInputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        //for (int t = 0; t<imageInputTC.length; ++t) imageInputTC[t][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, t);
        
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("input: f="+fieldIdx));
        Processor.setTransformations(f);
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("output: f="+fieldIdx));
    }
    
    
    public static void testPreProcessing(String dbName, int fieldIdx, int channelIdx, int time, int tStart, int tEnd) throws Exception {
        MasterDAO db = new Task(dbName).getDB();
        Position f = db.getExperiment().getPosition(fieldIdx);
        InputImagesImpl images = f.getInputImages();
        if (time>=tStart) time -=tStart;
        if (time<0) images.subSetTimePoints(tStart, tEnd);
        IJImageDisplayer disp = new IJImageDisplayer();
        if (time>=0) {
            Image input = images.getImage(channelIdx, time).duplicate("input");
            Processor.setTransformations(f);
            Image output = images.getImage(channelIdx, time).setName("output");
            disp.showImage(input);
            disp.showImage(output);
        } else { // display all
            List<Image> input = new ArrayList<Image>(tEnd-tStart+1);
            for (int t = 0; t<=(tEnd-tStart); ++t) input.add(images.getImage(channelIdx, t).duplicate("input"+t));
            Processor.setTransformations(f);
            List<Image> output = new ArrayList<Image>(tEnd-tStart+1);
            for (int t = 0; t<=(tEnd-tStart); ++t) output.add(images.getImage(channelIdx, t).duplicate("output"+t));
            disp.showImage(Image.mergeZPlanes(input).setName("input"));
            disp.showImage(Image.mergeZPlanes(output).setName("output"));
        }
        
        
    }
    
}
