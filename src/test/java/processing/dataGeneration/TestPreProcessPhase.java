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
import core.Processor;
import static core.Processor.setTransformations;
import dataStructure.configuration.MicroscopyField;
import dataStructure.containers.InputImagesImpl;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.StructureObject;
import image.Image;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.List;
import plugins.PluginFactory;
import plugins.Transformation;
import plugins.plugins.preFilter.IJSubtractBackground;
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
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        String dbName= "testBF";
        boolean flip = true;
        int field = 0;
        //testTransformation(dbName, 0, 0, 0);
        testPreProcessing(dbName, field, 0, -1, 0, 54);
        //testCrop(dbName, field, 0, flip);
        //testStabilizer(dbName, field, 0, 20, 19, flip);
    }
    
    public static void testTransformation(String dbName, int fieldIdx, int channelIdx, int time) {
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        MicroscopyField f = db.getExperiment().getMicroscopyField(fieldIdx);
        //Processor.setTransformations(f, true);
        InputImagesImpl images = f.getInputImages();
        Image im = images.getImage(channelIdx, time);
        Transformation t = new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT, 0);
        t.computeConfigurationData(channelIdx, images);
        Image res = t.applyTransformation(channelIdx, time, im);
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(im.setName("input"));
        disp.showImage(res.setName("transformed"));
    }
    
    public static void testCrop(String dbName, int fieldIdx, int time, boolean flip) {
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        MicroscopyField f = db.getExperiment().getMicroscopyField(fieldIdx);
        f.getPreProcessingChain().removeAllTransformations();
        f.getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT, 0));
        if (flip) f.getPreProcessingChain().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y));
        f.getPreProcessingChain().addTransformation(0, null, new CropMicroChannelBF2D().setTimePointNumber(1));
        CropMicroChannelBF2D.debug=true;
        //Image[][] imageInputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        //for (int t = 0; t<imageInputTC.length; ++t) imageInputTC[t][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, t);
        
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("input: f="+fieldIdx));
        Processor.setTransformations(f, true);
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("output: f="+fieldIdx));
    }
    
    
    public static void testPreProcessing(String dbName, int fieldIdx, int channelIdx, int time, int tStart, int tEnd) {
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        MicroscopyField f = db.getExperiment().getMicroscopyField(fieldIdx);
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
    
    public static void testStabilizer(String dbName, int fieldIdx, int channelIdx, int tRef, int t, boolean flip) {
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        MicroscopyField f = db.getExperiment().getMicroscopyField(fieldIdx);
        f.getPreProcessingChain().removeAllTransformations();
        f.getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXARTEFACT, 0));
        if (flip) f.getPreProcessingChain().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y));
        f.getPreProcessingChain().addTransformation(0, null, new CropMicroChannelBF2D().setTimePointNumber(5));
        setTransformations(f, true);
        
        InputImagesImpl images = f.getInputImages();
        Image ref = images.getImage(channelIdx, tRef).setName("tRef");
        Image im = images.getImage(channelIdx, t).setName("to translate");
        Image trans = testTranslate(ref, im, 1000, 5e-8, 0).setName("translated");
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(ref);
        disp.showImage(im);
        disp.showImage(trans);
    }
}
