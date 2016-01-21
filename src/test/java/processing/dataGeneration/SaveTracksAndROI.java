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
import boa.gui.GUI;
import boa.gui.imageInteraction.IJImageWindowManager;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.MorphiumObjectDAO;
import dataStructure.objects.StructureObject;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Macro;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.macro.Interpreter;
import ij.plugin.frame.RoiManager;
import image.Image;
import image.ImageFormat;
import image.ImageWriter;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 *
 * @author jollion
 */
public class SaveTracksAndROI {
    public static void main(String[] args) {
        String dbName = "fluo151130_OutputNewScaling";
        String baseFileName = "151130_TrackMutation_";
        String directory = "/home/jollion/Documents/LJP/DataLJP/VerifManuelle/";
        int fieldIdx = 8;
        int structureIdx=2;
        new ImageJ(null, ImageJ.NO_SHOW);
        Interpreter.batchMode = true;
        HashMap<Image, Overlay> res = getImagesWithOverlay(dbName, baseFileName, fieldIdx, structureIdx);
        for (Entry<Image, Overlay> e : res.entrySet()) {
            saveImageAndOverlay(directory, e.getKey(), e.getValue());
        }
    }
    
    public static HashMap<Image, Overlay> getImagesWithOverlay(String dbName, String baseFileName, int fieldIdx, int structureIdx) {
        MorphiumMasterDAO m = new MorphiumMasterDAO(dbName);
        MorphiumObjectDAO dao = m.getDao(m.getExperiment().getMicroscopyField(fieldIdx).getName());
        StructureObject root = dao.getRoot(0);
        ArrayList<StructureObject> mcTH = dao.getTrackHeads(root, 0); // MC
        logger.debug("{} MC founds", mcTH.size());
        HashMap<Image, Overlay> res = new HashMap<Image, Overlay>(mcTH.size());
        IJImageWindowManager windowManager = new IJImageWindowManager(null);
        int count = 0;
        for (StructureObject th : mcTH) {
            ArrayList<StructureObject> track = dao.getTrack(th);
            ImageObjectInterface i = windowManager.getImageTrackObjectInterface(track, structureIdx);
            i.setGUIMode(false);
            Image im = i.generateRawImage(structureIdx);
            windowManager.displayObjects(im, i, false, i.getObjects());
            res.put(im, windowManager.getDisplayer().getImage(im).getOverlay());
            im.setName(baseFileName+"F"+String.format("%02d", fieldIdx)+"MC"+String.format("%02d", count++));
        }
        return res;
    }
    
    public static void saveImageAndOverlay(String directory, Image image, Overlay overlay) {
        ImageWriter.writeToFile(image, directory, image.getName(), ImageFormat.TIF);
        if (overlay==null) throw new Error("overlay required");
        RoiManager rm = getRM();
        if (rm==null) throw new Error("RoiManager could not be instanciated");
        if (overlay.size()>=4 && overlay.get(3).getPosition()!=0) Prefs.showAllSliceOnly = true;
        rm.runCommand("reset");
        rm.setEditMode(null, false);
        ImagePlus imp = null;
        for (int i=0; i<overlay.size(); i++) rm.add(imp, overlay.get(i), i+1);
        rm.runCommand("save", directory+image.getName()+".zip");
        ImageWriter.writeToFile(image, directory, image.getName(), ImageFormat.TIF);
    }
    
    private static RoiManager getRM() {
        RoiManager rm = new RoiManager(true);
        //RoiManager rm = RoiManager.getInstance2();
        if (rm==null) {
                if (Macro.getOptions()!=null && Interpreter.isBatchMode())
                        rm = Interpreter.getBatchModeRoiManager();
                if (rm==null) {
                        Frame frame = WindowManager.getFrame("ROI Manager");
                        if (frame==null)
                                IJ.run("ROI Manager...");
                        frame = WindowManager.getFrame("ROI Manager");
                        if (frame==null || !(frame instanceof RoiManager))
                                return null;
                        rm = (RoiManager)frame;
                }
        }
        return rm;
    }
    
    
}
