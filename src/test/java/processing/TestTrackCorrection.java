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

import boa.gui.imageInteraction.IJImageDisplayer;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.MicroscopyField;
import dataStructure.configuration.Structure;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectTrackCorrection;
import image.Image;
import static image.Image.logger;
import image.ImageByte;
import image.ImageFormat;
import image.ImageWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.PluginFactory;
import plugins.plugins.ObjectSplitter.WatershedObjectSplitter;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.SimpleThresholder;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.trackCorrector.MicroChannelBacteriaTrackCorrector;
import plugins.plugins.trackers.ClosedMicrochannelTracker;
import testPlugins.dummyPlugins.DummySplitter;

/**
 *
 * @author jollion
 */
public class TestTrackCorrection {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    
    @Test
    public void testOverSegmentation() {
        ArrayList<StructureObject> root = generateData(1, 2, 2, 1, 1, 1, 2);
        int[] expected = new int[]{1, 1, 1, 1, 1, 1, 2};
        ArrayList<StructureObjectTrackCorrection> correctedObjects = new ArrayList<StructureObjectTrackCorrection>();
        Processor.correctTrack(new MicroChannelBacteriaTrackCorrector(), new DummySplitter(), root.get(0), 0, null, false, correctedObjects);
        for (int i = 0; i<expected.length; ++i) assertEquals("OverSegmentation correction @t="+i, expected[i], root.get(i).getChildren(0).size());
    }
    
    @Test
    public void testUnderSegmentation() {
        ArrayList<StructureObject> root = generateData(1, 2, 2, 1, 2, 2);
        int[] expected = new int[]{1, 2, 2, 2, 2, 2};
        ArrayList<StructureObjectTrackCorrection> correctedObjects = new ArrayList<StructureObjectTrackCorrection>();
        Processor.correctTrack(new MicroChannelBacteriaTrackCorrector(), new DummySplitter(), root.get(0), 0, null, false, correctedObjects);
        for (int i = 0; i<expected.length; ++i) assertEquals("UnderSegmentation correction @t="+i, expected[i], root.get(i).getChildren(0).size());
    }
    
    @Test
    public void testOverSegmentationNoDivPrev() {
        ArrayList<StructureObject> root = generateData(2, 2, 1, 1, 1);
        int[] expected = new int[]{1, 1, 1, 1, 1};
        ArrayList<StructureObjectTrackCorrection> correctedObjects = new ArrayList<StructureObjectTrackCorrection>();
        Processor.correctTrack(new MicroChannelBacteriaTrackCorrector(), new DummySplitter(), root.get(0), 0, null, false, correctedObjects);
        for (int i = 0; i<expected.length; ++i) assertEquals("OverSegmentation No Div Prev Correction @t="+i, expected[i], root.get(i).getChildren(0).size());
    }
    
    @Test
    public void testUnderSegmentationNoDivNext() {
        ArrayList<StructureObject> root = generateData(1, 1, 1, 2, 2, 1);
        int[] expected = new int[]{1, 1, 1, 2, 2, 2};
        ArrayList<StructureObjectTrackCorrection> correctedObjects = new ArrayList<StructureObjectTrackCorrection>();
        Processor.correctTrack(new MicroChannelBacteriaTrackCorrector(), new DummySplitter(), root.get(0), 0, null, false, correctedObjects);
        for (int i = 0; i<expected.length; ++i) assertEquals("UnderSegmentation No Div Next Correction @t="+i, expected[i], root.get(i).getChildren(0).size());
    }
    
    @Test
    public void testUnderSegmentationAmbiguous() {
        ArrayList<StructureObject> root = generateData(1, 2, 2, 1, 1, 2);
        int[] expected = new int[]{1, 1, 1, 1, 1, 2};
        ArrayList<StructureObjectTrackCorrection> correctedObjects = new ArrayList<StructureObjectTrackCorrection>();
        Processor.correctTrack(new MicroChannelBacteriaTrackCorrector().setDefaultCorrection(true), new DummySplitter(), root.get(0), 0, null, false, correctedObjects);
        for (int i = 0; i<expected.length; ++i) assertEquals("UnderSegmentation No Div Next Correction @t="+i, expected[i], root.get(i).getChildren(0).size());
    }
    
    
    
    private ArrayList<StructureObject> generateData(int... numberOfObjectsT) {
        try {
            Image[][] testImage = generateImageTC(numberOfObjectsT);
            File input = testFolder.newFolder();
            ImageWriter.writeToFile(input.getAbsolutePath(), "field1", ImageFormat.OMETIF, testImage);
            Experiment xp = generateXP(testFolder.newFolder().getAbsolutePath());
            Processor.importFiles(xp, input.getAbsolutePath());
            Processor.preProcessImages(xp, null, true);
            MicroscopyField f= xp.getMicroscopyField(0);
            return Processor.processAndTrackStructures(xp, f, null, false, 0);
        } catch (IOException ex) {
            logger.error("Test Track Correction Error: ", ex);
            return null;
        }
    }
    
    private static Experiment generateXP(String outputDir) throws IOException {
        PluginFactory.findPlugins("plugins.plugins");
        PluginFactory.findPlugins("testPlugins.dummyPlugins");
        Experiment xp = new Experiment();
        xp.getChannelImages().insert(new ChannelImage("channel"));
        xp.setOutputImageDirectory(outputDir);
        Structure s = new Structure("Structure", -1, 0);
        xp.getStructures().insert(s);
        s.getProcessingChain().setSegmenter(new SimpleThresholder(new ConstantValue(1)));
        s.setTracker(new ClosedMicrochannelTracker());
        return xp;
    }
    
    private static Image[][] generateImageTC(int... numberOfObjectsT) {
        Image[][] res = new Image[numberOfObjectsT.length][1];
        for (int t = 0; t<numberOfObjectsT.length; ++t) {
            res[t][0] = new ImageByte("", 3, 12, 1);
            if (numberOfObjectsT[t]==0) numberOfObjectsT[t]=1;
            int size = res[t][0].getSizeY()/numberOfObjectsT[t];
            for (int y = 1; y<res[t][0].getSizeY(); ++y) if (y%size!=0) res[t][0].setPixel(1, y, 0, 1);
        }
        return res;
    } 
}
