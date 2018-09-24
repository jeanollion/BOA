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
import boa.data_structure.StructureObject;
import boa.image.Image;
import boa.image.ImageInteger;
import java.util.ArrayList;
import java.util.List;
import boa.plugins.PluginFactory;
import boa.plugins.Transformation;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;
import boa.plugins.plugins.transformations.AutoRotationXY;
import boa.plugins.plugins.transformations.CropMicrochannelsPhase2D;
import boa.plugins.plugins.transformations.CropMicrochannelsFluo2D;
import boa.plugins.plugins.transformations.Flip;
import boa.plugins.plugins.transformations.ImageStabilizerCore;
import boa.plugins.plugins.transformations.ImageStabilizerXY;
import boa.plugins.plugins.transformations.SaturateHistogram;
import boa.plugins.plugins.transformations.SaturateHistogramHyperfluoBacteria;
import boa.plugins.plugins.transformations.SimpleRotationXY;
import boa.image.processing.ImageTransformation;
import boa.plugins.ConfigurableTransformation;

/**
 *
 * @author Jean Ollion
 */
public class TestPreProcess {
    public static void main(String[] args) throws Exception {
        PluginFactory.findPlugins("boa.plugins.plugins");
        ImageStabilizerXY.debug=true;
        //String dbName= "boa_fluo160428";
        //String dbName= "fluo151127";
        //String dbName = "boa_fluo170117_GammeMutTrackStab";
        String dbName = "fluo171113_WT_15s";
        // 12 -> flip = true
        boolean flip = false;
        int field = 17;
        //testTransformation(dbName, 0, 0, 0);
        //testPreProcessing(dbName, field, 0, -1, 0, 150);
        //testCrop(dbName, field, 0, flip);
        //displayPreProcessed(dbName, field, 2, 0, 680);
        //testStabilizer(dbName, field, 0, 19, 0, flip);
        //testStabFromXP(dbName, field, 1, 500, 1000);
        test(dbName, 0, null, 0);
    }
    public static void test(String dbName, int posIdx, String positionName, int time) throws Exception {
        MasterDAO db = new Task(dbName).getDB();
        Position f = positionName ==null ? db.getExperiment().getPosition(posIdx): db.getExperiment().getPosition(positionName);
        List<TransformationPluginParameter<Transformation>> list = new ArrayList<>(f.getPreProcessingChain().getTransformations(false));
        int cropIdx = -1;
        int i = 0;
        for (TransformationPluginParameter<Transformation> tpp : list) {
            if (tpp.instanciatePlugin() instanceof CropMicrochannelsFluo2D) cropIdx = i;
            ++i;
        }
        if (cropIdx<0) {
            logger.error("no crop defined");
            return;
        }
        f.getPreProcessingChain().removeAllTransformations();
        for (i = 0;i<=cropIdx; ++i) f.getPreProcessingChain().addTransformation(list.get(i).getInputChannel(), list.get(i).getOutputChannels(), list.get(i).instanciatePlugin());
        
        CropMicrochannelsFluo2D.debug=true;
        
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("input: f="+posIdx));
        Processor.setTransformations(f);
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("output: f="+posIdx));
    
    }
    public static void testTransformation(String dbName, int fieldIdx, int channelIdx, int time) throws Exception {
       MasterDAO db = new Task(dbName).getDB();
        Position f = db.getExperiment().getPosition(fieldIdx);
        //Processor.setTransformations(f, true);
        InputImagesImpl images = f.getInputImages();
        Image im = images.getImage(channelIdx, time);
        Transformation t = new IJSubtractBackground(0.5, true, false, true, false);
        //AutoRotationXY stabilizer = new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR, 0);
        if (t instanceof ConfigurableTransformation) ((ConfigurableTransformation)t).computeConfigurationData(channelIdx, images);
        Image res = t.applyTransformation(channelIdx, time, im);
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(im.setName("input"));
        disp.showImage(res.setName("transformed"));
    }
    
    public static void testCrop(String dbName, int fieldIdx, int time, boolean flip) throws Exception {
        MasterDAO db = new Task(dbName).getDB();
        Position f = db.getExperiment().getPosition(fieldIdx);
        f.getPreProcessingChain().removeAllTransformations();
        f.getPreProcessingChain().addTransformation(0, null, new SaturateHistogram(350, 450));
        f.getPreProcessingChain().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
        f.getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR));
        if (flip) f.getPreProcessingChain().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y));
        /*f.getPreProcessingChain().addTransformation(0, null, new CropMicroChannels2D());
        CropMicroChannels2D.debug=true;*/
        f.getPreProcessingChain().addTransformation(0, null, new CropMicrochannelsFluo2D());
        CropMicrochannelsFluo2D.debug=true;
        //Image[][] imageInputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        //for (int stabilizer = 0; stabilizer<imageInputTC.length; ++stabilizer) imageInputTC[stabilizer][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, stabilizer);
        
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
        images.subSetTimePoints(tStart, tEnd);
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
    
    public static void testStabFromXP(String dbName, int fieldIdx, int channelIdx,int tStart, int tEnd) throws Exception {
        MasterDAO db = new Task(dbName).getDB();
        Position f = db.getExperiment().getPosition(fieldIdx);
        List<TransformationPluginParameter<Transformation>> tr = new ArrayList<>(f.getPreProcessingChain().getTransformations(false));
        TransformationPluginParameter<Transformation> stab=null;
        for (int i = 0; i<tr.size(); ++i) {
            if (tr.get(i).instanciatePlugin() instanceof ImageStabilizerXY) {
                stab = tr.get(i);
                tr = tr.subList(0, i);
                break;
            }
        }
        if (stab==null) throw new IllegalArgumentException("No stabilizer in XP");
        f.getPreProcessingChain().removeAllTransformations();
        for (TransformationPluginParameter<Transformation> t : tr) f.getPreProcessingChain().addTransformation(t.getInputChannel(), t.getOutputChannels(), t.instanciatePlugin());
        InputImagesImpl images = f.getInputImages();
        images.subSetTimePoints(tStart, tEnd);
        IJImageDisplayer disp = new IJImageDisplayer();
        Processor.setTransformations(f);
        List<Image> input = new ArrayList<Image>(tEnd-tStart+1);
        for (int t = 0; t<=(tEnd-tStart); ++t) input.add(images.getImage(channelIdx, t).duplicate("input"+t));
        Transformation stabilizer = stab.instanciatePlugin();
        if (stabilizer instanceof ConfigurableTransformation) ((ConfigurableTransformation)stabilizer).computeConfigurationData(stab.getInputChannel(), images);
        images.addTransformation(stab.getInputChannel(), stab.getOutputChannels(), stabilizer);
        List<Image> output = new ArrayList<Image>(tEnd-tStart+1);
        for (int t = 0; t<=(tEnd-tStart); ++t) output.add(images.getImage(channelIdx, t).duplicate("output"+t));
        disp.showImage(Image.mergeZPlanes(input).setName("input"));
        disp.showImage(Image.mergeZPlanes(output).setName("output"));
        
    }
    
    public static void displayPreProcessed(String dbName, int fieldIdx, int structureIdx, int tStart, int tEnd) {
        MasterDAO db = new Task(dbName).getDB();
        Position f = db.getExperiment().getPosition(fieldIdx);
        InputImagesImpl images = f.getInputImages();
        IJImageDisplayer disp = new IJImageDisplayer();
        int channelIdx = db.getExperiment().getStructure(structureIdx).getChannelImage();
        List<Image> input = new ArrayList<Image>(tEnd-tStart+1);
        for (int t = 0; t<images.getFrameNumber(); ++t) input.add(images.getImage(channelIdx, t).duplicate("input"+t));
        
        List<StructureObject> roots = db.getDao(f.getName()).getRoots();
        List<Image> output = new ArrayList<Image>(tEnd-tStart+1);
        for (int t = tStart; t<=tEnd; ++t) {
            output.add(roots.get(t).getRawImage(structureIdx));
        }
        disp.showImage(Image.mergeZPlanes(input).setName("input"));
        disp.showImage(Image.mergeZPlanes(output).setName("output"));
    }
    
    public static void testStabilizer(String dbName, int fieldIdx, int channelIdx, int tRef, int t, boolean flip) throws Exception {
        MasterDAO db = new Task(dbName).getDB();
        Position f = db.getExperiment().getPosition(fieldIdx);
        f.getPreProcessingChain().removeAllTransformations();
        int bactChann = 1;
        f.getPreProcessingChain().addTransformation(bactChann, null, new SaturateHistogramHyperfluoBacteria());
        f.getPreProcessingChain().addTransformation(bactChann, null, new IJSubtractBackground(20, true, false, true, false));
        f.getPreProcessingChain().addTransformation(bactChann, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR));
        if (flip) f.getPreProcessingChain().addTransformation(bactChann, null, new Flip(ImageTransformation.Axis.Y));
        //f.getPreProcessingChain().addTransformation(bactChann, null, new CropMicroChannels2D());
        setTransformations(f);
        
        InputImagesImpl images = f.getInputImages();
        Image ref = images.getImage(channelIdx, tRef).setName("tRef");
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(ref);
        
    }
}
