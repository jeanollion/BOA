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
package boa.processing.test;

import static boa.test_utils.TestUtils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.configuration.parameters.PostFilterSequence;
import boa.core.Task;
import boa.configuration.experiment.MicroscopyField;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import ij.ImageJ;
import boa.image.BlankMask;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.Offset;
import boa.image.SimpleOffset;
import java.util.ArrayList;
import java.util.HashSet;
import boa.plugins.ObjectFeature;
import boa.plugins.PluginFactory;
import boa.plugins.Segmenter;
import boa.plugins.plugins.segmenters.MutationSegmenter;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class TestProcessMutations {
    MasterDAO db;
    static int intervalX = 0;
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        //String dbName = "fluo171204_WT_750ms_paramOptimization";
        String dbName = "fluo171219_WT_750ms";
        //String dbName = "fluo170512_WT";
        int fIdx = 314;
        int mcIdx =2;
        //String dbName = "fluo151130_Output";
        TestProcessMutations t = new TestProcessMutations();
        t.init(dbName, null);
        t.testSegMutationsFromXP(fIdx, mcIdx, false, 0,5);
    }
    
    public void testSegMutation(Image input, StructureObject parent, ArrayList<ImageInteger> parentMask_, ArrayList<Image> input_,  ArrayList<ImageInteger> outputLabel, ArrayList<ArrayList<Image>> intermediateImages_) {
        
        ImageInteger parentMask = parent.getObject().getMaskAsImageInteger();
        MutableBoundingBox parentBounds = new MutableBoundingBox(parent.getBounds());
        if (parentBounds.sizeZ()==1 && input.sizeZ()>1) parentBounds.copyZ(input); // case of 2D ref image
        Image localInput = input.sameDimensions(parentBounds.getImageProperties()) ? input : input.cropWithOffset(parentBounds);
        
        ArrayList<Image> intermediateImages = intermediateImages_==null? null:new ArrayList<Image>();
        MutationSegmenter.debug=true;
        //MutationSegmenterScaleSpace seg = new MutationSegmenterScaleSpace().setIntensityThreshold(90);
        //if (parent.getIdx()==2) MutationSegmenter.debug=true;
        //else MutationSegmenter.debug=false;
        //LocalSNR.debug=true;
        Segmenter seg = parent.getExperiment().getStructure(2).getProcessingScheme().getSegmenter();
        
        //TODO apply to segmenter & preFIlteredImages
        ((MutationSegmenter)seg).intermediateImages=intermediateImages;
        
        RegionPopulation pop = seg.runSegmenter(localInput, 2, parent);
        
        
        PostFilterSequence pf = new PostFilterSequence("pf"); 
        //pf.add(new FeatureFilter(new LocalSNR().setLocalBackgroundRadius(6).setBackgroundObjectStructureIdx(1).setRadii(2, 2), 1, true, true));
        //pf.add(new FeatureFilter(new SNR().setBackgroundObjectStructureIdx(1).setRadii(2, 2), 1, true, true));
        //ObjectPopulation popPF = pf.filter(pop.duplicate(), 2, parent);
        
        
        //ObjectPopulation pop = MutationSegmenterScaleSpace.runPlane(input.getZPlane(0), parentMask, 5, 4, 0.75, intermediateImages);
        if (parentMask_!=null) parentMask_.add(parentMask);
        if (input_!=null) input_.add(localInput);
        if (outputLabel!=null) outputLabel.add(pop.getLabelMap().setName("before PF"));
        //if (outputLabel!=null) outputLabel.add(popPF.getLabelMap().setName("after PF"));
        if (intermediateImages_!=null) {
            intermediateImages.add(pop.getLabelMap().setName("before PF"));
            intermediateImages.add(parent.getObjectPopulation(1).getLabelMap().setName("bacteria"));
            intermediateImages_.add(intermediateImages);
        }
    }
    
    
    public void init(String dbName, String dir) {
        db = new Task(dbName, dir).getDB();
        db.setReadOnly(true);
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
        Image input = mc.getRawImage(2);
        input = mc.getExperiment().getStructure(2).getProcessingScheme().getPreFilters().filter(input, mc.getMask());
        logger.debug("prefilters: {}, sizeZ: {}", mc.getExperiment().getStructure(2).getProcessingScheme().getPreFilters().getChildCount(), input.sizeZ());
        if (parentMC) {
            if (mcMask_!=null) mcMask_.add(mc.getObject().getMaskAsImageInteger());
            testSegMutation(input, mc, parentMask_, input_, outputLabel, intermediateImages_);
        } else {
            for (StructureObject bact : mc.getChildren(1)) {
                if (mcMask_!=null) mcMask_.add(mc.getObject().getMaskAsImageInteger());
                testSegMutation(input, bact, parentMask_, input_, outputLabel, intermediateImages_);
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
            if (ySize<(m.sizeY())) ySize = m.sizeY();
            xSize+=intervalX+m.sizeX();
        }
        ySize+=50; // bug -> remove microchannel offsetY ? 
        xSize-=intervalX;
        Image inputPaste = Image.createEmptyImage("input", input.get(0), new BlankMask( xSize, ySize, input.get(0).sizeZ(), 0, 0, 0, input.get(0).getScaleXY(), input.get(0).getScaleZ()));
        Image outputLabelPaste = Image.createEmptyImage("labels", outputLabel.get(0), new BlankMask(xSize, ySize, outputLabel.get(0).sizeZ(), 0, 0, 0, outputLabel.get(0).getScaleXY(), outputLabel.get(0).getScaleZ()));
        Image maskPaste = Image.createEmptyImage("mc mask", mcMask.get(0), new BlankMask(xSize, ySize, mcMask.get(0).sizeZ(), 0, 0, 0, mcMask.get(0).getScaleXY(), mcMask.get(0).getScaleZ()));
        ArrayList<Image> intermediateImagesList = new ArrayList<>();
        for (int i = 0; i<intermediateImages.get(0).size(); ++i) intermediateImagesList.add(Image.createEmptyImage(intermediateImages.get(0).get(i).getName(), intermediateImages.get(0).get(i), new BlankMask(xSize, ySize, intermediateImages.get(0).get(i).sizeZ(), 0, 0, 0, intermediateImages.get(0).get(i).getScaleXY(), intermediateImages.get(0).get(i).getScaleZ())));
        Offset offset = new SimpleOffset(0, 0, 0);
        ImageInteger lastMask = mcMask.get(0);
        Offset mcOffInv = lastMask.getBoundingBox().reverseOffset();
        Image.pasteImage(lastMask, maskPaste, offset);
        for (int i = 0; i<parentMask.size(); ++i) {
            if (!mcMask.get(i).equals(lastMask)) {
                mcOffInv = lastMask.getBoundingBox().reverseOffset();
                offset.translate(new SimpleOffset(mcMask.get(i).sizeX()+intervalX , 0, 0));
                //pasteImage(mcMask.get(i), maskPaste, offset); logger.debug("past mask: sizeX: {}, off: {}", mcMask.get(i).getSizeX(), offset);
            }
            
            Offset localOffset= parentMC? offset : parentMask.get(i).getBoundingBox().translate(offset.xMin(), 0, 0).translate(mcOffInv);
            Image.pasteImage(input.get(i), inputPaste, localOffset);
            Image.pasteImage(outputLabel.get(i), outputLabelPaste, localOffset);
            for (int interIdx = 0; interIdx<intermediateImages.get(i).size(); ++interIdx) Image.pasteImage(intermediateImages.get(i).get(interIdx), intermediateImagesList.get(interIdx), localOffset);
            lastMask = mcMask.get(i);
            
        }
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(inputPaste);
        for (Image i : intermediateImagesList) disp.showImage(i);
        disp.showImage(outputLabelPaste);
        //disp.showImage(maskPaste);
        
    }
    
}
