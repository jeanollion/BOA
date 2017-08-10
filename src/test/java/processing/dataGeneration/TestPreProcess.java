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

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.TransformationPluginParameter;
import core.Processor;
import static core.Processor.setTransformations;
import core.Task;
import dataStructure.configuration.MicroscopyField;
import dataStructure.containers.InputImagesImpl;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.StructureObject;
import image.Image;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.List;
import plugins.PluginFactory;
import plugins.Transformation;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.transformations.AutoRotationXY;
import plugins.plugins.transformations.CropMicroChannelFluo2D;
import plugins.plugins.transformations.CropMicroChannels2D;
import plugins.plugins.transformations.Flip;
import plugins.plugins.transformations.ImageStabilizerCore;
import plugins.plugins.transformations.ImageStabilizerXY;
import static plugins.plugins.transformations.ImageStabilizerXY.testTranslate;
import plugins.plugins.transformations.SaturateHistogram;
import plugins.plugins.transformations.SaturateHistogramHyperfluoBacteria;
import plugins.plugins.transformations.SimpleRotationXY;
import processing.ImageTransformation;

/**
 *
 * @author jollion
 */
public class TestPreProcess {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        ImageStabilizerXY.debug=true;
        //String dbName= "boa_fluo160428";
        //String dbName= "fluo151127";
        String dbName = "boa_fluo170117_GammeMutTrackStab";
        // 12 -> flip = true
        boolean flip = false;
        int field = 9;
        //testTransformation(dbName, 0, 0, 0);
        //testPreProcessing(dbName, field, 0, -1, 0, 150);
        //testCrop(dbName, field, 0, flip);
        //displayPreProcessed(dbName, field, 2, 0, 680);
        //testStabilizer(dbName, field, 0, 19, 0, flip);
        testStabFromXP(dbName, field, 1, 0, 40);
    }
    
    public static void testTransformation(String dbName, int fieldIdx, int channelIdx, int time) {
       MasterDAO db = new Task(dbName).getDB();
        MicroscopyField f = db.getExperiment().getPosition(fieldIdx);
        //Processor.setTransformations(f, true);
        InputImagesImpl images = f.getInputImages();
        Image im = images.getImage(channelIdx, time);
        Transformation t = new IJSubtractBackground(0.5, true, false, true, false);
        //AutoRotationXY t = new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR, 0);
        t.computeConfigurationData(channelIdx, images);
        Image res = t.applyTransformation(channelIdx, time, im);
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(im.setName("input"));
        disp.showImage(res.setName("transformed"));
    }
    
    public static void testCrop(String dbName, int fieldIdx, int time, boolean flip) {
        MasterDAO db = new Task(dbName).getDB();
        MicroscopyField f = db.getExperiment().getPosition(fieldIdx);
        f.getPreProcessingChain().removeAllTransformations();
        f.getPreProcessingChain().addTransformation(0, null, new SaturateHistogram(350, 450));
        f.getPreProcessingChain().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
        f.getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR));
        if (flip) f.getPreProcessingChain().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y));
        /*f.getPreProcessingChain().addTransformation(0, null, new CropMicroChannels2D());
        CropMicroChannels2D.debug=true;*/
        f.getPreProcessingChain().addTransformation(0, null, new CropMicroChannelFluo2D().setTimePointNumber(5));
        CropMicroChannelFluo2D.debug=true;
        //Image[][] imageInputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        //for (int t = 0; t<imageInputTC.length; ++t) imageInputTC[t][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, t);
        
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("input: f="+fieldIdx));
        Processor.setTransformations(f, true);
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("output: f="+fieldIdx));
    }
    
    
    public static void testPreProcessing(String dbName, int fieldIdx, int channelIdx, int time, int tStart, int tEnd) {
        MasterDAO db = new Task(dbName).getDB();
        MicroscopyField f = db.getExperiment().getPosition(fieldIdx);
        InputImagesImpl images = f.getInputImages();
        if (time>=tStart) time -=tStart;
        images.subSetTimePoints(tStart, tEnd);
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
    
    public static void testStabFromXP(String dbName, int fieldIdx, int channelIdx,int tStart, int tEnd) {
        MasterDAO db = new Task(dbName).getDB();
        MicroscopyField f = db.getExperiment().getPosition(fieldIdx);
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
        Processor.setTransformations(f, true);
        List<Image> input = new ArrayList<Image>(tEnd-tStart+1);
        for (int t = 0; t<=(tEnd-tStart); ++t) input.add(images.getImage(channelIdx, t).duplicate("input"+t));
        Transformation stabilizer = stab.instanciatePlugin();
        stabilizer.computeConfigurationData(stab.getInputChannel(), images);
        stab.setConfigurationData(stabilizer.getConfigurationData());
        images.addTransformation(stab.getInputChannel(), stab.getOutputChannels(), stabilizer);
        List<Image> output = new ArrayList<Image>(tEnd-tStart+1);
        for (int t = 0; t<=(tEnd-tStart); ++t) output.add(images.getImage(channelIdx, t).duplicate("output"+t));
        disp.showImage(Image.mergeZPlanes(input).setName("input"));
        disp.showImage(Image.mergeZPlanes(output).setName("output"));
        
    }
    
    public static void displayPreProcessed(String dbName, int fieldIdx, int structureIdx, int tStart, int tEnd) {
        MasterDAO db = new Task(dbName).getDB();
        MicroscopyField f = db.getExperiment().getPosition(fieldIdx);
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
    
    public static void testStabilizer(String dbName, int fieldIdx, int channelIdx, int tRef, int t, boolean flip) {
        MasterDAO db = new Task(dbName).getDB();
        MicroscopyField f = db.getExperiment().getPosition(fieldIdx);
        f.getPreProcessingChain().removeAllTransformations();
        int bactChann = 1;
        f.getPreProcessingChain().addTransformation(bactChann, null, new SaturateHistogramHyperfluoBacteria());
        f.getPreProcessingChain().addTransformation(bactChann, null, new IJSubtractBackground(20, true, false, true, false));
        f.getPreProcessingChain().addTransformation(bactChann, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR));
        if (flip) f.getPreProcessingChain().addTransformation(bactChann, null, new Flip(ImageTransformation.Axis.Y));
        f.getPreProcessingChain().addTransformation(bactChann, null, new CropMicroChannels2D());
        setTransformations(f, true);
        
        InputImagesImpl images = f.getInputImages();
        Image ref = images.getImage(channelIdx, tRef).setName("tRef");
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(ref);
        
    }
}
