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
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.MorphiumObjectDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.Morphium;
import ij.ImageJ;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageMask;
import image.ImageOperations;
import static image.ImageOperations.pasteImage;
import java.util.ArrayList;
import java.util.HashSet;
import plugins.PluginFactory;
import plugins.plugins.measurements.objectFeatures.LocalSNR;
import plugins.plugins.measurements.objectFeatures.SNR;
import plugins.plugins.postFilters.FeatureFilter;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.MutationSegmenter;
import plugins.plugins.segmenters.MutationSegmenterScaleSpace;
import plugins.plugins.segmenters.MutationSegmenterScaleSpace2;
import plugins.plugins.segmenters.SpotFluo2D5;
import processing.neighborhood.CylindricalNeighborhood;
import processing.neighborhood.EllipsoidalNeighborhood;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class TestProcessMutations {
    MorphiumMasterDAO db;
    static int intervalX = 0;
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        new ImageJ();
        String dbName = "boa_fluo170117_GammeMutTrack";
        //final String dbName = "boa_fluo151127_test";
        int fIdx = 0;
        int mcIdx =0;
        //String dbName = "fluo151130_Output";
        TestProcessMutations t = new TestProcessMutations();
        t.init(dbName);
        t.testSegMutationsFromXP(fIdx, mcIdx, true, 0,100);
    }
    
    public void testSegMutation(StructureObject parent, ArrayList<ImageInteger> parentMask_, ArrayList<Image> input_,  ArrayList<ImageInteger> outputLabel, ArrayList<ArrayList<Image>> intermediateImages_) {
        Image input = parent.getRawImage(2);
        ImageInteger parentMask = parent.getMask();
        ArrayList<Image> intermediateImages = intermediateImages_==null? null:new ArrayList<Image>();
        //MutationSegmenterScaleSpace seg = new MutationSegmenterScaleSpace().setIntensityThreshold(90);
        MutationSegmenter.debug=true;
        MutationSegmenter seg = new MutationSegmenter().setIntensityThreshold(1.5).setThresholdSeeds(1.5).setThresholdPropagation(1).setSubtractBackgroundScale(6).setScale(2.5);
        //seg.getPostFilters().removeAllElements();
        //seg.getPostFilters().add(new FeatureFilter(new LocalSNR().setBackgroundObjectStructureIdx(1), 0.75, true, true));
        seg.intermediateImages=intermediateImages;
        ObjectPopulation popPF = seg.runSegmenter(input, 2, parent);
        
        //ObjectPopulation pop = MutationSegmenterScaleSpace.runPlane(input.getZPlane(0), parentMask, 5, 4, 0.75, intermediateImages);
        if (parentMask_!=null) parentMask_.add(parentMask);
        if (input_!=null) input_.add(input);
        if (outputLabel!=null) outputLabel.add(popPF.getLabelMap());
        //if (outputLabel!=null) outputLabel.add(beforePF);
        if (intermediateImages_!=null) {
            //intermediateImages.add(beforePF);
            intermediateImages.add(parent.getObjectPopulation(1).getLabelMap().setName("bacteria"));
            intermediateImages_.add(intermediateImages);
        }
    }
    
    
    public void init(String dbName) {
        db = new MorphiumMasterDAO(dbName);
        logger.info("Experiment: {} retrieved from db: {}", db.getExperiment().getName(), dbName);
    }
    public void testSegMutationsFromXP(int fieldIdx, int mcIdx, int time) {
        testSegMutationsFromXP(fieldIdx, mcIdx, true, time, null, null, null, null, null);
    }
    public void testSegMutationsFromXP(int fieldIdx, int mcIdx, boolean parentMC, int time, ArrayList<ImageInteger> mcMask_, ArrayList<ImageInteger> parentMask_, ArrayList<Image> input_,  ArrayList<ImageInteger> outputLabel, ArrayList<ArrayList<Image>> intermediateImages_) {
        MicroscopyField f = db.getExperiment().getPosition(fieldIdx);
        StructureObject root = db.getDao(f.getName()).getRoot(time);
        if (root==null) return;
        //logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildren(0).get(mcIdx);
        if (mcMask_!=null) mcMask_.add(mc.getMask());
        if (parentMC) {
            testSegMutation(mc, parentMask_, input_, outputLabel, intermediateImages_);
        } else {
            for (StructureObject bact : mc.getChildren(1)) {
                testSegMutation(bact, parentMask_, input_, outputLabel, intermediateImages_);
            }
        }
    }
    
    
    public void testSegMutationsFromXP(int fieldIdx, int mcIdx, boolean parentMC, int tStart, int tEnd) {
        ArrayList<ImageInteger> mcMask = new ArrayList<ImageInteger>();
        ArrayList<ImageInteger> parentMask = new ArrayList<ImageInteger>();
        ArrayList<Image> input = new ArrayList<Image>();
        ArrayList<ImageInteger> outputLabel = new ArrayList<ImageInteger>(); 
        ArrayList<ArrayList<Image>> intermediateImages = new ArrayList<ArrayList<Image>>();
        
        for (int t = tStart; t<=tEnd; ++t) {
            logger.debug("SEG MUT: time: {}", t);
            testSegMutationsFromXP(fieldIdx, mcIdx, parentMC, t, mcMask, parentMask, input, outputLabel, intermediateImages);
        }
        
        int xSize = 0;
        int ySize = 0;
        HashSet<ImageMask> mcMaskUnique = new HashSet<ImageMask>();
        mcMaskUnique.addAll(mcMask);
        logger.debug("number of bacteries: {}, number of distinct microchannels: {}", parentMask.size(), mcMaskUnique.size());
        for (ImageMask m : mcMaskUnique) {
            if (ySize<m.getSizeY()) ySize = m.getSizeY();
            xSize+=intervalX+m.getSizeX();
        }
        xSize-=intervalX;
        Image inputPaste = Image.createEmptyImage("input", input.get(0), new BlankMask("", xSize, ySize, input.get(0).getSizeZ(), 0, 0, 0, 1, 1));
        Image outputLabelPaste = Image.createEmptyImage("labels", outputLabel.get(0), new BlankMask("", xSize, ySize, outputLabel.get(0).getSizeZ(), 0, 0, 0, 1, 1));
        Image maskPaste = Image.createEmptyImage("mc mask", mcMask.get(0), new BlankMask("", xSize, ySize, mcMask.get(0).getSizeZ(), 0, 0, 0, 1, 1));
        ArrayList<Image> intermediateImagesList = new ArrayList<Image>();
        for (int i = 0; i<intermediateImages.get(0).size(); ++i) intermediateImagesList.add(Image.createEmptyImage(intermediateImages.get(0).get(i).getName(), intermediateImages.get(0).get(i), new BlankMask("", xSize, ySize, intermediateImages.get(0).get(i).getSizeZ(), 0, 0, 0, 1, 1)));
        BoundingBox offset = new BoundingBox(0, 0, 0);
        ImageInteger lastMask = mcMask.get(0);
        pasteImage(lastMask, maskPaste, offset);
        for (int i = 0; i<parentMask.size(); ++i) {
            if (!mcMask.get(i).equals(lastMask)) {
                offset.translate(mcMask.get(i).getSizeX()+intervalX , 0, 0);
                //pasteImage(mcMask.get(i), maskPaste, offset); logger.debug("past mask: sizeX: {}, off: {}", mcMask.get(i).getSizeX(), offset);
            }
            
            BoundingBox localOffset= parentMC? offset : parentMask.get(i).getBoundingBox().translate(offset.getxMin(), 0, 0);
            pasteImage(input.get(i), inputPaste, localOffset);
            pasteImage(outputLabel.get(i), outputLabelPaste, localOffset);
            for (int interIdx = 0; interIdx<intermediateImages.get(i).size(); ++interIdx) pasteImage(intermediateImages.get(i).get(interIdx), intermediateImagesList.get(interIdx), localOffset);
            lastMask = mcMask.get(i);
            
        }
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(inputPaste);
        for (Image i : intermediateImagesList) disp.showImage(i);
        disp.showImage(outputLabelPaste);
        //disp.showImage(maskPaste);
        
    }
    
}
