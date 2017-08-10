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
package processing;

import static TestUtils.Utils.logger;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.configuration.Structure;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MasterDAOFactory;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectTrackCorrection;
import dataStructure.objects.StructureObjectUtils;
import de.caluga.morphium.Morphium;
import image.Image;
import image.ImageByte;
import image.ImageFormat;
import image.ImageWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.PluginFactory;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.processingScheme.SegmentAndTrack;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import plugins.plugins.trackers.ClosedMicrochannelTracker;
import testPlugins.dummyPlugins.DummySegmenterSplitAndMerge;

/**
 *
 * @author jollion
 */
public class TestTrackCorrectionNew {
    MasterDAO db;
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        PluginFactory.findPlugins("plugins.plugins");
        PluginFactory.findPlugins("testPlugins.dummyPlugins");
    }
    
    public void setUpDB() {
        if (db==null) {
            String dir = null;
            if (MasterDAOFactory.getCurrentType().equals(MasterDAOFactory.DAOType.DBMap)) dir = testFolder.newFolder("testTrackCorrectionNew").getAbsolutePath();
            db = MasterDAOFactory.createDAO("testTrackCorrection", dir);
        }
        db.reset();
    }

    //@Test 
    public void testOverSegmentation() {
        int[] actual = new int[]{1, 2, 2, 1, 1, 1, 2};
        int[] expected = new int[]{1, 1, 1, 1, 1, 1, 2};
        test(actual, expected);
    }
    
    //@Test
    public void testUnderSegmentation() {
        int[] actual = new int[]{1, 2, 2, 1, 2, 2};
        int[] expected = new int[]{1, 2, 2, 2, 2, 2};
        test(actual, expected);
    }
    
    //@Test 
    public void testOverSegmentationNoDivPrev() {
        int[] actual = new int[]{2, 2, 1, 1, 1};
        int[] expected = new int[]{1, 1, 1, 1, 1};
        test(actual, expected);
    }
    
    //@Test
    public void testUnderSegmentationNoDivNext() {
        int[] actual = new int[]{1, 1, 1, 2, 2, 1};
        int[] expected = new int[]{1, 1, 1, 2, 2, 2};
        test(actual, expected);
    }
    
    //@Test 
    public void testUnderSegmentationAmbiguous() {
        int[] actual = new int[]{1, 2, 2, 1, 1, 2};
        int[] expected = new int[]{1, 1, 1, 1, 1, 2};
        test(actual, expected);
    }
    
    //@Test
    public void testTwoUnderSegmentation() {
        int[] actual = new int[]{1, 2, 2, 2, 1, 2, 1, 2, 2};
        int[] expected = new int[]{1, 2, 2, 2, 2, 2, 2, 2, 2};
        test(actual, expected);
    }
    
    private void test(int[] actual, int[] expected) {
        testWholeProcessDB(actual, expected);
    }
    
    // whole process at once
    private void testWholeProcessDB(int[] actual, int[] expected) {
        setUpDB();
        generateData(actual);
        db.getDao(db.getExperiment().getPosition(0).getName()).clearCache();
        testTrackCorrection(db.getDao(db.getExperiment().getPosition(0).getName()).getRoots(), actual, expected);
    }
    
    private void testTrackCorrection(List<StructureObject> root, int[] actual, int[] expected) {
        StructureObjectUtils.getAllTracks(root, 0); //sets track links
        assertEquals("number of root", actual.length, root.size());
        for (int i = 0; i<expected.length; ++i) {
            assertEquals("correction @t="+i, expected[i], root.get(i).getChildren(0).size()); // object number
            if (expected[i]==1) {
                assertEquals ("object sizeY (single) @t="+i, objectSize+1, root.get(i).getChildren(0).get(0).getObject().getBounds().getSizeY());
                if (actual[i]==2) assertEquals ("object size (merged) @t="+i, objectSize, root.get(i).getChildren(0).get(0).getObject().getVoxels().size());
            }
            else if (expected[i]==2 ) {
                if (actual[i]==2) {
                    assertEquals ("object sizeY (double, 1) @t="+i, objectSize/2, root.get(i).getChildren(0).get(0).getObject().getBounds().getSizeY());
                    assertEquals ("object sizeY (double, 2) @t="+i, objectSize/2, root.get(i).getChildren(0).get(1).getObject().getBounds().getSizeY());
                } else { // split
                    //logger.debug("split: o1: {}, o2: {}", root.get(i).getChildren(0).get(0), root.get(i).getChildren(0).get(1));
                    assertEquals ("object sizeY (split, 1) @t="+i, objectSize/2+1, root.get(i).getChildren(0).get(0).getObject().getBounds().getSizeY());
                    assertEquals ("object sizeY (split, 2) @t="+i, objectSize/2, root.get(i).getChildren(0).get(1).getObject().getBounds().getSizeY());
                }                
            }
        }
        StructureObject parentTrackHead = root.get(0);
        StructureObject trackHead0, trackHead1;
        if (expected[0]==1) {
            trackHead0 = trackHead1 = root.get(0).getChildren(0).get(0);
        } else {
            trackHead0 = root.get(0).getChildren(0).get(0);
            trackHead1 = root.get(0).getChildren(0).get(1);
        }
        for (int i = 0; i<expected.length; ++i) {
            if (i>0) {
                if (expected[i]==expected[i-1]) { // pas de division
                    for (int track = 0; track<expected[i]; ++track) {
                        StructureObject oPrev = root.get(i-1).getChildren(0).get(track);
                        StructureObject oNext = root.get(i).getChildren(0).get(track);
                        //logger.debug("track: {}, next expected: {} actual: {}", track, oNext, oPrev.getNext());
                        assertEquals("time: "+(i-1)+"&"+i+" track: "+track+", next", oNext, oPrev.getNext());
                        assertEquals("time: "+(i-1)+"&"+i+" track: "+track+", prev", oPrev, oNext.getPrevious());
                        StructureObject trackHead = track==0? trackHead0: trackHead1;
                        //logger.debug("{}: trackHead expected: {} actual: {}", track, trackHead, oNext.getTrackHead());
                        assertEquals("time: "+i+" track: "+track+", trackHead", trackHead, oNext.getTrackHead());
                        assertEquals("time: "+i+" track: "+track+", parent trackHead", parentTrackHead, oNext.getParent().getTrackHead());
                    }
                } else { // division
                    StructureObject oPrev = root.get(i-1).getChildren(0).get(0);
                    StructureObject oNext0 = root.get(i).getChildren(0).get(0);
                    StructureObject oNext1 = root.get(i).getChildren(0).get(1);
                    trackHead1 = oNext1;
                    assertEquals("time: "+(i-1)+"&"+i+" track: 0 (div), next", oNext0, oPrev.getNext());
                    assertEquals("time: "+(i-1)+"&"+i+" track: 0 (div), prev", oPrev, oNext0.getPrevious());
                    
                    assertEquals("time: "+(i-1)+"&"+i+" track: 1 (div), prev", oPrev, oNext1.getPrevious());
                    //logger.debug("0: trackHead expected: {} actual: {}", trackHead0, oNext0.getTrackHead());
                    //logger.debug("1: trackHead expected: {} actual: {}", trackHead1, oNext1.getTrackHead());
                    assertEquals("time: "+i+" track: 0, trackHead", trackHead0, oNext0.getTrackHead());
                    assertEquals("time: "+i+" track: 1, trackHead", trackHead1, oNext1.getTrackHead());
                    assertEquals("time: "+i+" track: 0, parent trackHead", parentTrackHead, oNext0.getParent().getTrackHead());
                    assertEquals("time: "+i+" track: 0, parent trackHead", parentTrackHead, oNext1.getParent().getTrackHead());
                }
            }
        }
    
    }
    
    public List<StructureObject> generateData(int... numberOfObjectsT) {
        try {
            return generateData(testFolder.newFolder(), testFolder.newFolder(), db, objectSize, numberOfObjectsT);
        } catch (IOException ex) {
            logger.error("Test Track Correction Error: ", ex);
            return null;
        }
    }
    
    public static List<StructureObject> generateData(File input, File output, MasterDAO db, int objectSize, int... numberOfObjectsT) {
        Image[][] testImage = generateImageTC(objectSize, numberOfObjectsT);
        ImageWriter.writeToFile(input.getAbsolutePath(), "field1", ImageFormat.OMETIF, testImage);
        Experiment xp = generateXP(output.getAbsolutePath());
        db.setExperiment(xp);
        Processor.importFiles(xp, true, input.getAbsolutePath());
        Processor.preProcessImages(db, true);
        MicroscopyField f= xp.getPosition(0);
        List<StructureObject> root = Processor.getOrCreateRootTrack(db.getDao(f.getName()));
        logger.debug("create root objects: {}", root.size());
        Processor.executeProcessingScheme(root, 0, false, false);
        return root;
    }
    
    private static Experiment generateXP(String outputDir) {
        Experiment xp = new Experiment("testTrackCorrection");
        xp.getChannelImages().insert(new ChannelImage("channel"));
        xp.setOutputDirectory(outputDir);
        Structure s = new Structure("Structure", -1, 0);
        xp.getStructures().insert(s);
        s.setProcessingScheme(new SegmentAndTrack(new BacteriaClosedMicrochannelTrackerLocalCorrections().setSegmenter(new DummySegmenterSplitAndMerge()).setCostParameters(10, 100))); //0.9, 1.1 , 1.5 //  double divisionCriterion, double minGrowthRate, double maxGrowthRate, double costLimit, double cumulativeCostLimit
        return xp;
    }
     
    private static Image[][] generateImageTC(int objectSize, int... numberOfObjectsT) {
        Image[][] res = new Image[numberOfObjectsT.length][1];
        for (int t = 0; t<numberOfObjectsT.length; ++t) {
            res[t][0] = new ImageByte("", 3, objectSize+2, 1);
            if (numberOfObjectsT[t]==0) numberOfObjectsT[t]=1;
            int size = res[t][0].getSizeY()/numberOfObjectsT[t];
            for (int y = 1; y<res[t][0].getSizeY(); ++y) if (y%size!=0) res[t][0].setPixel(1, y, 0, 1);
        }
        return res;
    } 
    private final static int objectSize = 10;
}
