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
package dataStructure.containers;

import core.Processor;
import dataStructure.configuration.Experiment;
import dataStructure.objects.StructureObject;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import static image.Image.logger;
import image.ImageFormat;
import image.ImageIOCoordinates;
import image.ImageInteger;
import image.ImageReader;
import image.ImageWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import utils.FileIO;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class LocalFileSystemImageDAO implements ImageDAO {
    String directory;
    static final int idxZeros = 5;
    public LocalFileSystemImageDAO(String localDirectory) {
        this.directory=localDirectory;
    }
    @Override
    public InputStream openStream(int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName, directory);
        File f = new File(path);
        try {
            return new FileInputStream(f);
        } catch (FileNotFoundException ex) {
            logger.trace("Opening pre-processed image:  channel: {} timePoint: {} fieldName: {}", channelImageIdx, timePoint, microscopyFieldName);
        }
        return null;
    }
    public Image openPreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName, directory);
        File f = new File(path);
        if (f.exists()) {
            logger.trace("Opening pre-processed image:  channel: {} timePoint: {} fieldName: {}", channelImageIdx, timePoint, microscopyFieldName);
            return ImageReader.openImage(path);
        } else {
            logger.trace("pre-processed image: {} not found", path);
            return null;
        }
    }
    
    public Image openPreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName, BoundingBox bounds) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName, directory);
        File f = new File(path);
        if (f.exists()) {
            logger.trace("Opening pre-processed image:  channel: {} timePoint: {} fieldName: {} bounds: {}", channelImageIdx, timePoint, microscopyFieldName, bounds);
            return ImageReader.openImage(path, new ImageIOCoordinates(bounds));
        } else {
            logger.error("pre-processed image: {} not found", path);
            return null;
        }
    }
    
    public void deletePreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName, directory);
        File f = new File(path);
        if (f.exists()) f.delete();
    }
    
    public BlankMask getPreProcessedImageProperties(String microscopyFieldName) {
        String path = getPreProcessedImagePath(0, 0, microscopyFieldName, directory);
        File f = new File(path);
        if (f.exists()) {
            ImageReader reader = new ImageReader(path);
            int[][] STCXYZ = reader.getSTCXYZNumbers();
            reader.closeReader();
            return new BlankMask("", STCXYZ[0][2], STCXYZ[0][3], STCXYZ[0][4], 0, 0, 0, 0, 0);
        } else {
            logger.error("getPreProcessedImageProperties: pre-processed image {} not found", path);
            return null;
        }
    }

    

    public void writePreProcessedImage(Image image, int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName, directory);
        File f = new File(path);
        f.mkdirs();
        logger.trace("writing preprocessed image to path: {}", path);
        //if (f.exists()) f.delete();
        ImageWriter.writeToFile(image, path, ImageFormat.TIF);
    }
    
    public void writePreProcessedImage(InputStream image, int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName, directory);
        File f = new File(path);
        f.delete();
        f.getParentFile().mkdirs();
        logger.trace("writing preprocessed image to path: {}", path);
        //if (f.exists()) f.delete();
        FileIO.writeFile(image, path);
    }

    public ImageInteger openMask(StructureObject object) {
        String path = getProcessedImageFile(object);
        File f = new File(path);
        if (f.exists()) {
            logger.trace("opening mask of object: {}", object);
            return (ImageInteger)ImageReader.openImage(path);
        } else {
            logger.error("mask {} not found", path);
            return null;
        }
    }
    
    public void writeMask(ImageInteger mask, StructureObject object) {
        String path = getProcessedImageFile(object);
        File f = new File(path);
        f.mkdirs();
        logger.debug("writing mask image to path: {}", path);
        //if (f.exists()) f.delete();
        ImageWriter.writeToFile(mask, path, ImageFormat.PNG);
    }
    /*
    @Override
    public void deleteMask(StructureObject object) {
        String path = getProcessedImageFile(object);
        File f = new File(path);
        if (f.exists()) f.delete();
    }
    @Override
    public void deleteFieldMasks(Experiment xp, String fieldName) {
        String path = getFieldDirectory(xp, fieldName);
        File f = new File(path);
        Utils.deleteDirectory(f);
    }
    @Override 
    public void deleteChildren(StructureObject parent, final int structureIdx) {
        String path = getProcessedImageDirectory(parent);
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File arg0, String arg1) {
                return Integer.parseInt(arg1.substring(1, 3))==structureIdx;
            }
        };
        File dir = new File(path);
        if (dir.exists()) for (File f : dir.listFiles(filter)) Utils.deleteDirectory(f);
    }
    @Override 
    public void renameMask(StructureObject object, int newIdx) {
        String fileName1 = getProcessedImageFile(object);
        File file1 = new File (fileName1);
        String fileName2 = getProcessedImageDirectory(object);
        File file2 = new File (fileName2);
        String oldS=null, newS=null;
        if (file1.exists()) {
            oldS = "_idx"+Utils.formatInteger(idxZeros, object.getIdx());
            newS = "_idx"+Utils.formatInteger(idxZeros, newIdx);
            file1.renameTo(new File(fileName1.replace(oldS, newS)));
        }
        if (file2.exists()) {
            if (oldS==null) {
                oldS = "_idx"+Utils.formatInteger(idxZeros, object.getIdx());
                newS = "_idx"+Utils.formatInteger(idxZeros, newIdx);
            }
            file2.renameTo(new File(fileName2.replace(oldS, newS)));
        }
    }*/
    protected static String getPreProcessedImagePath(int channelImageIdx, int timePoint, String microscopyFieldName, String imageDirectory) {
        return imageDirectory+File.separator+microscopyFieldName+File.separator+"pre_processed"+File.separator+"t"+Utils.formatInteger(5, timePoint)+"_c"+Utils.formatInteger(2, channelImageIdx)+".tif";
    }
    private static String getFieldDirectory(Experiment xp, String fieldName) {
        return xp.getOutputImageDirectory()+File.separator+fieldName+File.separator+"processed";
    }
    private static String getProcessedImageDirectoryRoot(StructureObject root) {
        return getFieldDirectory(root.getExperiment(), root.getPositionName())+File.separator+"t"+Utils.formatInteger(5, root.getFrame());
    }
    private static String getImageFileName(StructureObject object, int idx) {
        return "s"+Utils.formatInteger(2, object.getStructureIdx())+"_idx"+Utils.formatInteger(idxZeros, idx);
    }
    protected static String getProcessedImageDirectory(StructureObject object) {
        if (object.isRoot()) return getProcessedImageDirectoryRoot(object);
        if (object.getParent().isRoot()) return getProcessedImageDirectoryRoot(object.getParent())+File.separator+getImageFileName(object, object.getIdx());
        else return getProcessedImageDirectory((StructureObject)object.getParent())+File.separator+getImageFileName(object, object.getIdx());
    }
    protected static String getProcessedImageFile(StructureObject object) {
        return getProcessedImageDirectory(object)+".png";
    }
    
    
    
    
    
}
