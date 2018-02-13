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
package boa.misc;

import static boa.test_utils.TestUtils.logger;
import boa.gui.GUI;
import boa.gui.imageInteraction.IJImageWindowManager;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.core.Task;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.Region;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
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
import boa.image.BoundingBox;
import boa.image.IJImageWrapper;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.io.ImageFormat;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.image.ImageProperties;
import boa.image.io.ImageReader;
import boa.image.io.ImageWriter;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import boa.utils.Pair;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author jollion
 */
public class SaveAndRetriveSegmentationData {
    public static void main(String[] args) {
        String dbName = "fluo151127";
        String baseFileName = "151127_TrackMutation_";
        String directory = "/data/Images/Fluo/films1511/151127/151127_verifManuelleF00_160317/"; // ordi LJP
        int fieldIdx = 0;
        int structureIdx=2;
        int bacteriaStructureIdx=1; // for insideness
        double distanceThreshold =2;
                
        new ImageJ(null, ImageJ.NO_SHOW);
        Interpreter.batchMode = true;
        
        // SAVE TO DISK
        //saveToDisk(dbName, directory, baseFileName, fieldIdx, structureIdx);
        saveToDisk(dbName, directory, baseFileName, fieldIdx, 2, new int[]{1, 2}, new int[]{2});
        // RETRIEVE AND COMPARE TO EXPERIMENT
        //compareField(dbName, directory, baseFileName, fieldIdx, structureIdx, bacteriaStructureIdx, distanceThreshold);
        
    }
    
    public static void saveToDisk(String dbName, String directory, String baseFileName, int fieldIdx, int structureIdx, int[] roiStructureIdx, int[] trackStructureIdx) {
        if (directory.endsWith(File.separator)) directory+=File.separator;
        HashMap<Image, Overlay> res = getImagesWithOverlay(dbName, baseFileName, fieldIdx, structureIdx, roiStructureIdx, trackStructureIdx);
        for (Entry<Image, Overlay> e : res.entrySet()) {
            saveImageAndOverlay(directory, e.getKey(), e.getValue());
        }
    }
    
    public static void saveToDisk(String dbName, String directory, String baseFileName, int fieldIdx, int structureIdx) {
        saveToDisk(dbName, directory, baseFileName, fieldIdx, structureIdx, new int[]{structureIdx}, null);
    }
    
    public static HashMap<Image, Overlay> getImagesWithOverlay(String dbName, String baseFileName, int fieldIdx, int structureIdx, int[] roiStructureIdx, int[] trackStructureIdx) {
        MasterDAO m = new Task(dbName).getDB();
        ObjectDAO dao = m.getDao(m.getExperiment().getPosition(fieldIdx).getName());
        StructureObject root = dao.getRoot(0);
        List<StructureObject> mcTH = dao.getTrackHeads(root, 0); // MC
        logger.debug("{} MC founds", mcTH.size());
        HashMap<Image, Overlay> res = new HashMap<Image, Overlay>(mcTH.size());
        IJImageWindowManager windowManager = new IJImageWindowManager(null);
        int count = 0;
        for (StructureObject th : mcTH) {
            List<StructureObject> track = dao.getTrack(th);
            logger.debug("processing: {}", th);
            ImageObjectInterface i = windowManager.getImageTrackObjectInterface(track, structureIdx);
            i.setGUIMode(false);
            Image im = i.generateRawImage(structureIdx, true);
            if (roiStructureIdx!=null) {
                for (int roiS : roiStructureIdx) {
                    i = windowManager.getImageTrackObjectInterface(track, roiS);
                    windowManager.displayObjects(im, i.getObjects(), null, false, false);
                }
            }
            if (trackStructureIdx!=null) {
                for (int roiS : trackStructureIdx) {
                    i = windowManager.getImageTrackObjectInterface(track, roiS);
                    Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(track, roiS);
                    windowManager.displayTracks(im, i, allTracks.values(), false);
                }
            }
            res.put(im, windowManager.getDisplayer().getImage(im).getOverlay());
            im.setName(getName(baseFileName, fieldIdx, count++));
        }
        return res;
    }
    
    private static String getName(String baseFileName, int fieldIdx, int mcIdx) {
        return baseFileName+"F"+String.format("%02d", fieldIdx)+"MC"+String.format("%02d", mcIdx);
    }
    
    public static ArrayList<ArrayList<Region>> getObjectsMC(String dbName, int fieldIdx, int structureIdx) {
        MasterDAO m = new Task(dbName).getDB();
        ObjectDAO dao = m.getDao(m.getExperiment().getPosition(fieldIdx).getName());
        StructureObject root = dao.getRoot(0);
        List<StructureObject> mcTH = dao.getTrackHeads(root, 0); // MC
        ArrayList<ArrayList<Region>> res = new ArrayList<ArrayList<Region>>();
        IJImageWindowManager windowManager = new IJImageWindowManager(null);
        for (StructureObject th : mcTH) {
            List<StructureObject> track = dao.getTrack(th);
            ImageObjectInterface i = windowManager.getImageTrackObjectInterface(track, structureIdx);
            i.setGUIMode(false);
            List<Pair<StructureObject, BoundingBox>> so = i.getObjects();
            ArrayList<Region> o3DList = new ArrayList<Region>(so.size());
            for (Pair<StructureObject, BoundingBox> o : so) {
                o3DList.add(o.key.getObject().translate(o.key.getBounds().reverseOffset()).translate(o.value));
            }
            res.add(o3DList);
        }
        return res;
    }
    
    public static void saveImageAndOverlay(String directory, Image image, Overlay overlay) {
        if (overlay==null) throw new Error("overlay required");
        RoiManager rm = getRM();
        if (rm==null) throw new Error("RoiManager could not be instanciated");
        if (overlay.size()>=4 && overlay.get(3).getPosition()!=0) Prefs.showAllSliceOnly = true;
        rm.runCommand("reset");
        rm.setEditMode(null, false);
        for (int i=0; i<overlay.size(); i++) rm.addRoi(overlay.get(i));
        rm.runCommand("save", directory+image.getName()+".zip");
        ImageWriter.writeToFile(image, directory, image.getName(), ImageFormat.TIF);
    }
    
    public static void compareField(String dbName, String directory, String baseFileName, int fieldIdx, int structureIdx, int insideStructureIdx, double distanceThreshold) {
        ArrayList<ArrayList<Region>> objectsMC=getObjectsMC(dbName, fieldIdx, structureIdx);
        ArrayList<ArrayList<Region>> bacteriaMC= insideStructureIdx>=0?getObjectsMC(dbName, fieldIdx, insideStructureIdx):null;
        ImageByte mask=null;
        
        logger.info("Comparison db: {}, field: {}, structure: {}", dbName, fieldIdx, structureIdx);
        int[] total = new int[5];
        for (int mcIdx = 0; mcIdx<objectsMC.size(); ++mcIdx) {
            String name = directory + getName(baseFileName, fieldIdx, mcIdx);
            Image im = ImageReader.openImage(name+".tif");
            if (insideStructureIdx>=0) {
                if (mask==null) mask=new ImageByte("", im);
                draw(bacteriaMC.get(mcIdx), mask);
            }
            String corrected = "-corrected";
            //String corrected = "";
            if (new File(name+corrected+".zip").exists()) {
                double[][] reference = getCenters(getObjects(name+corrected+".zip"), im, mask);
                double[][] observed = getCenters(objectsMC.get(mcIdx), im, mask);
                int[] comparison = compare(observed, reference, distanceThreshold);
                total[0]+=comparison[0];
                total[1]+=comparison[1];
                total[2]+=comparison[2];
                total[3]+=observed.length;
                total[4]+=reference.length;
                logger.info("idx={}: FP: {}, FN: {}, #error: {}, #total: {}, #ref: {}", mcIdx, comparison[0], comparison[1], comparison[2], observed.length, reference.length);
            }
        }
        logger.info("FP: {}({}), FN: {}({}), #error: {}, #total: {}, #total ref: {}", total[0], (double)total[0]/(double)total[3], total[1], (double)total[1]/(double)total[3], total[2], total[3], total[4]);
    }
    
    public static void draw(ArrayList<Region> objects, ImageByte mask) {
        ImageOperations.fill(mask, 0, null);
        for (Region o : objects) o.draw(mask, 1);
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
            } else {
                logger.debug("false pos, x:{}, y:{}", observed[i][0], observed[i][1]);
                ++falsePositive;
            }
        }
        for (int i = 0; i<reference.length; ++i) {
            if (refMatch[i]<0) {
                logger.debug("false neg, x:{}, y:{}", reference[i][0], reference[i][1]);
                ++falseNegative;
            }
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
    
    public static double[][] getCenters(String name, ImageMask mask) {
        Image im = ImageReader.openImage(name+".tif");
        ArrayList<Region> objects = getObjects(name+".zip");
        return getCenters(objects, im, mask);
    }
    
    public static double[][] getCenters(ArrayList<Region> objects, Image image, ImageMask mask) {
        ArrayList<double[]> res=  new ArrayList<double[]>();
        for (int i = 0; i<objects.size(); ++i) {
            Region o = objects.get(i);
            double[] d = o.getMassCenter(image, false);
            Voxel v = getVoxel(d);
            if (mask==null || (mask.contains(v.x, v.y, v.z) && mask.insideMask(v.x, v.y, v.z))) res.add(d);
        }
        return res.toArray(new double[0][0]);
    }
    
    private static Voxel getVoxel(double[] coords) {
        return new Voxel((int)(coords[0]+0.5), (int)(coords[1]+0.5), (int)(coords[2]+0.5));
    }
    
    public static ArrayList<Region> getObjects(String roiListDir) {
        RoiManager rm = getRM();
        rm.runCommand("open", roiListDir);
        Roi[] rois=  rm.getRoisAsArray();
        ArrayList<Region> res = new ArrayList<>();
        for (int i = 0 ; i<rois.length; ++i) { // only for 2D ROI
            if (rois[i] instanceof PointRoi) {
                //if (!points) continue;
                Polygon p = ((PointRoi)rois[i]).getPolygon();
                //logger.debug("ROI: {}, is point and has: {} points", i, p.npoints);
                Voxel v = new Voxel(p.xpoints[0], p.ypoints[0], 0);
                Set<Voxel> vox = new HashSet<>(1);
                vox.add(v);
                res.add(new Region(vox, i+1, true, 1, 1));
            } else {
                //if (points) continue;
                ImageProcessor mask = rois[i].getMask();
                Rectangle bds = rois[i].getBounds();
                res.add(new Region((ImageInteger)IJImageWrapper.wrap(new ImagePlus("", mask)).addOffset(new BoundingBox(bds.x, bds.y, 0)), i+1, true));
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
