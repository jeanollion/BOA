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
package processing.dataGeneration;

import static TestUtils.Utils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import dataStructure.objects.MorphiumMasterDAO;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.Experiment.ImportImageMethod;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.configuration.Structure;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.Object3D;
import dataStructure.objects.MorphiumObjectDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import ij.process.AutoThresholder;
import image.Image;
import image.ImageFormat;
import image.ImageInteger;
import image.ImageMask;
import image.ImageWriter;
import java.io.File;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import plugins.PluginFactory;
import plugins.Segmenter;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.measurements.BacteriaLineageIndex;
import plugins.plugins.measurements.BacteriaMeasurementsWoleMC;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.preFilter.Median;
import plugins.plugins.processingScheme.SegmentAndTrack;
import plugins.plugins.processingScheme.SegmentOnly;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.MicroChannelFluo2D;
import plugins.plugins.segmenters.MutationSegmenter;
import plugins.plugins.segmenters.SpotFluo2D5;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackers.BacteriaClosedMicrochannelTrackerLocalCorrections;
import plugins.plugins.trackers.ClosedMicrochannelTracker;
import plugins.plugins.trackers.MicrochannelProcessor;
import plugins.plugins.trackers.ObjectIdxTracker;
import plugins.plugins.transformations.AutoRotationXY;
import plugins.plugins.transformations.CropMicroChannels2D;
import plugins.plugins.transformations.Flip;
import plugins.plugins.transformations.ImageStabilizerXY;
import plugins.plugins.transformations.ScaleHistogram;
import plugins.plugins.transformations.ScaleHistogramSignalExclusionY;
import plugins.plugins.transformations.SelectBestFocusPlane;
import plugins.plugins.transformations.SuppressCentralHorizontalLine;
import processing.ImageTransformation;
import processing.ImageTransformation.InterpolationScheme;
import testPlugins.dummyPlugins.DummySegmenter;
import utils.MorphiumUtils;
import utils.Utils;
import static utils.Utils.deleteDirectory;

/**
 *
 * @author jollion
 */
public class TestProcessBacteria {
    /*MasterDAO mDAO;
    Experiment xp;
    
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        TestProcessBacteria t = new TestProcessBacteria();
        //t.testCrop("/data/Images/Fluo/films1511/151127/champ1", true);
        //t.correctTracks("testFluo60", 0, 1);
        //t.process("testFluo60", 0, false);
        //t.testSegBactTrackErrors();
        //t.testRotation();
        //t.testSegBacteries();
        //t.testSegBacteriesFromXP();
        //t.subsetTimePoints(88, 119, "/data/Images/Fluo/films1511/151130/champ01", "/data/Images/Fluo/films1511/151130/champ01_sub88-118", "champ1sub88-118");
        //t.testSegMicroChannels();
        //t.testSegBactAllTimes();
        //t.runSegmentationBacteriaOnSubsetofDBXP(569, 630);
        //t.process(0, false);
        //t.testStabilizer();
        //t.testPreProcessing("fluo151127", 0);
        //t.testSegMicrochannelsFromXP();
    }
    
    
    
    //@Test
    public void testImport(String inputDir) {
        Processor.importFiles(xp, inputDir); //       /data/Images/Fluo/me121r-1-9-15-lbiptg100x /data/Images/Fluo/test
        //assertEquals("number of fields detected", 1, xp.getMicroscopyFields().getChildCount());
        logger.info("total microscopy field count: {}", xp.getMicrocopyFieldCount());
        //logger.info("imported field: name: {} image: timepoint: {} scale xy: {} scale z: {}", xp.getMicroscopyField(0).getName(), xp.getMicroscopyField(0).getTimePointNumber(), xp.getMicroscopyField(0).getScaleXY(), xp.getMicroscopyField(0).getScaleZ());
        //ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(xp.getMicroscopyField(0).getImages().getImage(0, 0));
        //ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(xp.getMicroscopyField(0).getImages().getImage(0, 1));
    }
    
    private void subsetTimePoints(int tStart, int tEnd, String inputDir, String outputDir, String name) {
        if (xp==null) {
            setUpXp(false, outputDir);
            testImport(inputDir);
        }
        if (xp.getImportImageMethod().equals(ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD)) {
            Image[][] imageTC = new Image[tEnd-tStart][1];
            for (int i = tStart; i<tEnd; ++i) imageTC[i-tStart][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, i);
            ImageWriter.writeToFile(outputDir, name+"_REF", ImageFormat.OMETIF, imageTC);
            for (int i = tStart; i<tEnd; ++i) imageTC[i-tStart][0] = xp.getMicroscopyField(0).getInputImages().getImage(1, i);
            ImageWriter.writeToFile(outputDir, name, ImageFormat.OMETIF, imageTC);
        } else { // single file
            Image[][] imageTC = new Image[tEnd-tStart][2];
            for (int t = tStart; t<tEnd; ++t) {
                for (int c = 0; c<2; ++c) imageTC[t-tStart][c] = xp.getMicroscopyField(0).getInputImages().getImage(c, t);
            }
            ImageWriter.writeToFile(outputDir, name, ImageFormat.OMETIF, imageTC);
        }
    }
    
    public void testRotation() {
        setUpXp(false, "/data/Images/Fluo/OutputTest");
        testImport("/data/Images/Fluo/testsub");
        ImageDisplayer disp = new IJImageDisplayer();
        for (int i =0; i<xp.getMicrocopyFieldCount(); ++i) {
            xp.getMicroscopyField(i).getPreProcessingChain().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
            xp.getMicroscopyField(i).getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR, 0));

            disp.showImage(xp.getMicroscopyField(i).getInputImages().getImage(0, 0).duplicate("input:"+xp.getMicroscopyField(i).getName()));
            Processor.setTransformations(xp.getMicroscopyField(i), true);
            disp.showImage(xp.getMicroscopyField(i).getInputImages().getImage(0, 0).duplicate("output:"+xp.getMicroscopyField(i).getName()));
        }
    }
    
    //@Test 
    public void testStabilizer() {
        setUpXp(false, "/data/Images/Fluo/OutputTest");
        xp.setImportImageMethod(Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD);
        testImport("/data/Images/Fluo/testsub60");
        
        xp.getMicroscopyField(0).getPreProcessingChain().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
        xp.getMicroscopyField(0).getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR, 0));
        xp.getMicroscopyField(0).getPreProcessingChain().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y));
        xp.getMicroscopyField(0).getPreProcessingChain().addTransformation(0, null, new CropMicroChannels2D());
        xp.getMicroscopyField(0).getPreProcessingChain().addTransformation(0, null, new ImageStabilizerXY());
        //xp.getMicroscopyField(0).getPreProcessingChain().addTransformation(0, null, new ImageStabilizer().setReferenceTimePoint(0));
        //Image[][] imageInputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        //for (int t = 0; t<imageInputTC.length; ++t) imageInputTC[t][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, t);
        Processor.preProcessImages(mDAO, true);
        ImageDisplayer disp = new IJImageDisplayer();
        Image[][] imageOutputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        for (int t = 0; t<imageOutputTC.length; ++t) imageOutputTC[t][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, t);
        //disp.showImage5D("intensityMap", imageInputTC);
        disp.showImage5D("output", imageOutputTC);
    }
    
    public void testCrop(String inputDir, boolean flip) {
        setUpXp(false, "/data/Images/Fluo/OutputTest");
        testImport(inputDir);
        //List<Integer> flip = Arrays.asList(new Integer[]{0});
        for (int i =0; i<xp.getMicrocopyFieldCount(); ++i) testCrop(i, flip); //flip.contains(new Integer(i))
    }
    
    public void testCrop(int fieldIdx, boolean flip) {
        xp.getMicroscopyField(fieldIdx).getPreProcessingChain().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
        xp.getMicroscopyField(fieldIdx).getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR, 0));
        if (flip) xp.getMicroscopyField(fieldIdx).getPreProcessingChain().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y));
        xp.getMicroscopyField(fieldIdx).getPreProcessingChain().addTransformation(0, null, new CropMicroChannels2D());
              
        //Image[][] imageInputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        //for (int t = 0; t<imageInputTC.length; ++t) imageInputTC[t][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, t);
        
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(xp.getMicroscopyField(fieldIdx).getInputImages().getImage(0, 0).duplicate("input:"+fieldIdx));
        Processor.setTransformations(xp.getMicroscopyField(fieldIdx), true);
        disp.showImage(xp.getMicroscopyField(fieldIdx).getInputImages().getImage(0, 0).duplicate("output:"+fieldIdx));
    }
    
    public void testSegMicroChannels() {
        testCrop("/data/Images/Fluo/films1510/63me120r-14102015-LR62R1-lbiptg100x_1", false);
        Image image = xp.getMicroscopyField(0).getInputImages().getImage(0, 0);
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(image);
        ObjectPopulation pop = MicroChannelFluo2D.run(image, 300, 22, 2);
        Image labels = pop.getLabelImage();
        
        
        disp.showImage(labels);

    }
    
    public void testSegBacteries() {
        testCrop("/data/Images/Fluo/testsub", false);
        
        Image image = xp.getMicroscopyField(0).getInputImages().getImage(0, 0);
        ObjectPopulation objects = MicroChannelFluo2D.run(image, 350, 30, 5);
        Object3D o = objects.getObjects().get(1);
        ImageMask parentMask = o.getMask();
        Image input = image.crop(o.getBounds());
        testSegBacteria(input, parentMask);
    }
    
    public void testSegBacteriesFromXP() {
        int time = 78;
        int microChannel =0;
        int field = 0;
        //String dbName = "fluo1510";
        String dbName = "testFluo60";
        Morphium m=MorphiumUtils.createMorphium(dbName);
        ExperimentDAO xpDAO = new ExperimentDAO(m);
        xp=xpDAO.getExperiment();
        logger.info("Experiment: {} retrieved from db: {}", xp.getName(), dbName);

        MorphiumObjectDAO dao = new MorphiumObjectDAO(m, xpDAO);
        MicroscopyField f = xp.getMicroscopyField(field);
        StructureObject root = dao.getRoot(f.getName(), time);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildren(0).get(microChannel);
        Image input = mc.getRawImage(1);
        ImageMask parentMask = mc.getMask();
        BacteriaFluo.debug=true;
        ObjectPopulation pop = BacteriaFluo.run(input, parentMask, 0.03, 100, 10, 3, 40, 2, 1, 2, null);
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(input);
        disp.showImage(pop.getLabelImage());
        
        // test split
        //ObjectPopulation popSplit = testObjectSplitter(intensityMap, pop.getChildren().get(0));
        //disp.showImage(popSplit.getLabelImage());
    }
    
     public void testSegMicrochannelsFromXP() {
        int time = 0;
        int field = 0;
        String dbName = "fluo151127";
        //String dbName = "testFluo60";
        Morphium m=MorphiumUtils.createMorphium(dbName);
        ExperimentDAO xpDAO = new ExperimentDAO(m);
        xp=xpDAO.getExperiment();
        logger.info("Experiment: {} retrieved from db: {}", xp.getName(), dbName);

        MorphiumObjectDAO dao = new MorphiumObjectDAO(m, xpDAO);
        MicroscopyField f = xp.getMicroscopyField(field);
        StructureObject root = dao.getRoot(f.getName(), time);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        Image image = root.getRawImage(0).setName("input");
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(image);
        MicroChannelFluo2D.debug=true;
        ObjectPopulation pop = MicroChannelFluo2D.run(image, 300, 22, 2);
        Image labels = pop.getLabelImage();
        
        
        disp.showImage(labels);
        
        
        
    } 
    
    public static ObjectPopulation testObjectSplitter(Image input, Object3D objectToSplit) {
        Image splitImage = input.crop(objectToSplit.getBounds());
        ImageInteger splitMask = objectToSplit.getMask();
        return WatershedObjectSplitter.split(splitImage, splitMask, false);
    }
    
    public static void testSegBacteria(Image input, ImageMask parentMask) {
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(input);
        BacteriesFluo2D.debug=true;
        ObjectPopulation pop = BacteriesFluo2D.run(input, parentMask, 0.3, 2, 15,3);
        disp.showImage(pop.getLabelImage());
    }
    
    public int getTrackErrorNumber(MicroscopyField f, MorphiumObjectDAO dao) {
        ArrayList<StructureObject> segO = new ArrayList<StructureObject> ();
        Processor.executeProcessingScheme(f.createRootObjects(dao), 1, false, true);
        dao.storeLater(segO, true, false);
        return dao.getTrackErrors(f.getName(), 1).size();
    }
    
    public void process(String dbName, boolean preProcess, int... fields) {
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        xp=db.getExperiment();
        logger.info("Experiment: {} retrieved from db: {}", xp.getName(), dbName);
        MorphiumObjectDAO dao = db.getDao();
        if (fields.length==0) {
            if (preProcess) Processor.preProcessImages(xp, db, true);
            //Processor.processAndTrackStructures(xp, dao);
            Processor.processAndTrackStructures(db, preProcess);
        } else {
            for (int field : fields) {
                MicroscopyField f = xp.getMicroscopyField(field);
                if (preProcess) Processor.preProcessImages(f, dao, true, true);
                //Processor.processAndTrackStructures(xp, f, dao, true, true);
                Processor.processAndTrackStructures(f.getName(), dao, preProcess);
                dao.clearCache();
            }
        }
    }
    
    public void testPreProcessing(String dbName, int field) {
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        xp=db.getExperiment();
        logger.info("Experiment: {} retrieved from db: {}", xp.getName(), dbName);
        MicroscopyField f = xp.getMicroscopyField(field);
        Processor.setTransformations(f, false);
        Image im =f.getInputImages().getImage(0, 0).setName("preProcessed");
        new IJImageDisplayer().showImage(im);
    }
    
    public void testSegBactAllTimes() {
        String dbName = "testFluo";
        try {
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setGlobalLogLevel(3);
            cfg.setDatabase(dbName);
            cfg.addHost("localhost", 27017);
            Morphium m=new Morphium(cfg);
            ExperimentDAO xpDAO = new ExperimentDAO(m);
            xp=xpDAO.getExperiment();
            logger.info("Experiment: {} retrieved from db: {}", xp.getName(), dbName);
            
            
            MorphiumObjectDAO dao = new MorphiumObjectDAO(m, xpDAO);
            
            MicroscopyField f = xp.getMicroscopyField(0);
            int nbVariables = 11;
            int nbChannels = 8;
            double[][][] optimizationParameter = new double[f.getTimePointNumber()][nbChannels][nbVariables+2];
            
            for (int t = 0; t<f.getTimePointNumber(); ++t) { //
                
                if (false) {
                Image image = f.getInputImages().getImage(0, t);
                ObjectPopulation popMC = MicroChannelFluo2D.run(image, 350, 30, 5);
                
                int mcIdx = 0;
                for (Object3D o : popMC.getObjects()) {
                    //logger.debug("timePoint: {}, microchannel: {}", t, mcIdx);
                    ImageMask parentMask = o.getMask();
                    Image input = image.crop(o.getBounds());
                    BacteriesFluo2D.optimizationParameters=new double[nbVariables+2];
                    ObjectPopulation pop = BacteriesFluo2D.run(input, parentMask, 0.1, 2, 15, 1);
                    optimizationParameter[t][mcIdx++]=BacteriesFluo2D.optimizationParameters;
                }
                } else {
                    StructureObject root = dao.getRoot(f.getName(), t);
                    //logger.debug("field name: {}, root==null? {}", xp.getMicroscopyField(1).getName(), root==null);
                    ArrayList<StructureObject> mc = root.getChildren(0);
                    int mcIdx = 0;
                    for (StructureObject o : mc) {
                        //logger.debug("timePoint: {}, channel: {}", t, o.getIdx());
                        Image input = o.getRawImage(1);
                        ImageMask parentMask = o.getMask();
                        BacteriesFluo2D.optimizationParameters=new double[nbVariables+2];
                        ObjectPopulation pop = BacteriesFluo2D.run(input, parentMask, 0.1, 2, 15, 1);
                        optimizationParameter[t][mcIdx++]=BacteriesFluo2D.optimizationParameters;
                    }
                }
            }
            
            // analyse values 
            
            double[] values = new double[nbVariables];
            double[] values2=new double[nbVariables];
            double tmp;
            for (int t = 0; t<optimizationParameter.length; ++t) {
                for (int c = 0; c<nbChannels; ++c) {
                    for (int v = 0; v<nbVariables; ++v) {
                        tmp = optimizationParameter[t][c][0]/optimizationParameter[t][c][v+2];
                        values[v]+= tmp;
                        values2[v]+=tmp*tmp;
                    }
                }
            }
            double[] mean = new double[nbVariables];
            double[] sd = new double[nbVariables];
            for (int v = 0; v<nbVariables; ++v) {
                    //values[c][v] = Math.sqrt(values2[c][v]/(double)optimizationParameter.length - Math.pow(values[c][v]/(double)optimizationParameter.length, 2))/(values2[c][v]/(double)optimizationParameter.length);
                    //logger.debug("variable: {}, channel: {} sd: {}", v+1, c, values[c][v]);
                    mean[v] =values[v]/(double)(optimizationParameter.length * nbChannels);
                    sd[v] = Math.sqrt(values2[v]/(double)(optimizationParameter.length * nbChannels) - mean[v]*mean[v]);
                    logger.debug("variable: {}, mean: {} sd: {}, sd/mu:{}", v, mean[v], sd[v], sd[v]/mean[v]);
            }
            logger.debug("before compute displayed values");
            // display values
            float[][] dispValues = new float[nbVariables][optimizationParameter.length*nbChannels];
            for (int t = 0; t<optimizationParameter.length; ++t) {
                for (int c = 0; c<nbChannels; ++c) {
                    for (int v= 0;v<nbVariables; ++v) {
                        dispValues[v][c*optimizationParameter.length+t] = (float)optimizationParameter[t][c][0]/(float)optimizationParameter[t][c][v+2];
                    }
                }
            }
            logger.debug("before displayed values");
            for (int v = 0; v<nbVariables; ++v) Utils.plotProfile("variable: "+v, dispValues[v]);

            logger.debug("displayed values");
            
        } catch (UnknownHostException ex) {
            logger.error("store xp error: ", ex);
        }
    }
    */
}
