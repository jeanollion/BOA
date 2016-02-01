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
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Macro;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.macro.Interpreter;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import image.BoundingBox;
import image.IJImageWrapper;
import image.Image;
import image.ImageFormat;
import image.ImageInteger;
import image.ImageReader;
import image.ImageWriter;
import java.awt.Frame;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 *
 * @author jollion
 */
public class SaveAndRetriveSegmentationData {
    public static void main(String[] args) {
        String dbName = "fluo151130_OutputNewScalingY";
        String baseFileName = "151130_TrackMutation_";
        //String directory = "/home/jollion/Documents/LJP/DataLJP/VerifManuelle/"; // ordi portable
        String directory = "/data/Images/Fluo/films1511/151130/151130_verifManuelleF08_160120/"; // ordi LJP
        int fieldIdx = 8;
        int structureIdx=2;
        double distanceThreshold = 2;
                
        new ImageJ(null, ImageJ.NO_SHOW);
        Interpreter.batchMode = true;
        
        
        // SAVE TO DISK
        //saveToDisk(dbName, directory, baseFileName, fieldIdx, structureIdx);
        
        // RETRIEVE AND COMPARE TO EXPERIMENT
        compareField(dbName, directory, baseFileName, fieldIdx, structureIdx, distanceThreshold);
        
    }
    
    public static void saveToDisk(String dbName, String directory, String baseFileName, int fieldIdx, int structureIdx) {
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
            im.setName(getName(baseFileName, fieldIdx, count++));
        }
        return res;
    }
    
    private static String getName(String baseFileName, int fieldIdx, int mcIdx) {
        return baseFileName+"F"+String.format("%02d", fieldIdx)+"MC"+String.format("%02d", mcIdx);
    }
    
    public static ArrayList<ArrayList<Object3D>> getObjectsMC(String dbName, int fieldIdx, int structureIdx) {
        MorphiumMasterDAO m = new MorphiumMasterDAO(dbName);
        MorphiumObjectDAO dao = m.getDao(m.getExperiment().getMicroscopyField(fieldIdx).getName());
        StructureObject root = dao.getRoot(0);
        ArrayList<StructureObject> mcTH = dao.getTrackHeads(root, 0); // MC
        ArrayList<ArrayList<Object3D>> res = new ArrayList<ArrayList<Object3D>>();
        IJImageWindowManager windowManager = new IJImageWindowManager(null);
        for (StructureObject th : mcTH) {
            ArrayList<StructureObject> track = dao.getTrack(th);
            ImageObjectInterface i = windowManager.getImageTrackObjectInterface(track, structureIdx);
            i.setGUIMode(false);
            ArrayList<StructureObject> so = i.getObjects();
            ArrayList<Object3D> o3DList = new ArrayList<Object3D>(so.size());
            for (StructureObject o : so) {
                BoundingBox b = i.getObjectOffset(o);
                o3DList.add(o.getObject().translate(o.getBounds().reverseOffset()).translate(b));
            }
            res.add(o3DList);
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
    
    public static void compareField(String dbName, String directory, String baseFileName, int fieldIdx, int structureIdx, double distanceThreshold) {
        ArrayList<ArrayList<Object3D>> objectsMC=getObjectsMC(dbName, fieldIdx, structureIdx);
        logger.info("Comparison db: {}, field: {}, structure: {}", dbName, fieldIdx, structureIdx);
        int[] total = new int[4];
        for (int mcIdx = 0; mcIdx<objectsMC.size(); ++mcIdx) {
            String name = directory + getName(baseFileName, fieldIdx, mcIdx);
            Image im = ImageReader.openImage(name+".tif");
            //String corrected = "-corrected";
            String corrected = "";
            double[][] reference = getCenters(getObjects(name+corrected+".zip"), im);
            double[][] observed = getCenters(objectsMC.get(mcIdx), im);
            int[] comparison = compare(observed, reference, distanceThreshold);
            total[0]+=comparison[0];
            total[1]+=comparison[1];
            total[2]+=comparison[2];
            total[3]+=observed.length;
            //logger.info("idx={}: FP: {}, FN: {}, #error: {}, #total: {}", mcIdx, (double)comparison[0]/(double)observed.length, (double)comparison[1]/(double)observed.length, comparison[2], observed.length);
        }
        logger.info("FP: {}, FN: {}, #error: {}, #total: {}", (double)total[0]/(double)total[3], (double)total[1]/(double)total[3], total[2], total[3]);
    }
    
    /**
     * 
     * @param observed
     * @param reference
     * @param threshold
     * @return [false Positive, false negative, stack]
     */
    private static int[] compare(double[][] observed, double[][] reference, double threshold) {
        int falsePositive=0, falseNegative=0, stack=0;
        int[] refMatch = new int[reference.length];
        Arrays.fill(refMatch, -1);
        for (int i = 0; i<observed.length; ++i) {
            int c = getClosestCenter(observed[i], reference, threshold);
            if (c>=0) {
                if (refMatch[c]>=0) stack++;
                else refMatch[c] = i; 
            } else ++falsePositive;
        }
        for (int i = 0; i<reference.length; ++i) {
            if (refMatch[i]<0) ++falseNegative;
        }
        return new int[]{falsePositive, falseNegative, stack};
    }
    
    private static double dist2(double[] c1, double[] c2) {
        return Math.pow(c1[0]-c2[0], 2) + Math.pow(c1[1]-c2[1], 2) ;
    }
    
    private static int getClosestCenter(double[] center, double[][] otherCenters, double threhsold) {
        double minD = threhsold;
        int min = -1;
        for (int i = 0; i<otherCenters.length; ++i) {
            double d = dist2(center, otherCenters[i]);
            if (d<minD) {
                minD = d;
                min = i;
            }
        }
        return min;
    }
    
    public static double[][] getCenters(String name) {
        Image im = ImageReader.openImage(name+".tif");
        ArrayList<Object3D> objects = getObjects(name+".zip");
        return getCenters(objects, im);
    }
    
    public static double[][] getCenters(ArrayList<Object3D> objects, Image image) {
        double[][] res=  new double[objects.size()][];
        for (int i = 0; i<objects.size(); ++i) {
            Object3D o = objects.get(i);
            res[i] = o.getCenter(image);
        }
        return res;
    }
    
    public static ArrayList<Object3D> getObjects(String roiListDir) {
        RoiManager rm = getRM();
        rm.runCommand("open", roiListDir);
        Roi[] rois=  rm.getRoisAsArray();
        ArrayList<Object3D> res = new ArrayList<Object3D>();
        for (int i = 0 ; i<rois.length; ++i) { // only for 2D ROI
            if (rois[i] instanceof PointRoi) {
                //if (!points) continue;
                Polygon p = ((PointRoi)rois[i]).getPolygon();
                logger.debug("ROI: {}, is point and has: {} points", i, p.npoints);
                Voxel v = new Voxel(p.xpoints[0], p.ypoints[0], 0);
                ArrayList<Voxel> vox = new ArrayList<Voxel>(1);
                vox.add(v);
                res.add(new Object3D(vox, i+1, 1, 1));
            } else {
                //if (points) continue;
                ImageProcessor mask = rois[i].getMask();
                Rectangle bds = rois[i].getBounds();
                res.add(new Object3D((ImageInteger)IJImageWrapper.wrap(new ImagePlus("", mask)).addOffset(new BoundingBox(bds.x, bds.y, 0)), i+1));
            }
        } 
        return res;
    }
    
    public static RoiManager getRM() {
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
