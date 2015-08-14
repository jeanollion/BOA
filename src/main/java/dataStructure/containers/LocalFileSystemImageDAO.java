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
import utils.Utils;

/**
 *
 * @author jollion
 */
public class LocalFileSystemImageDAO implements ImageDAO {
    String directory;
    
    public LocalFileSystemImageDAO(String localDirectory) {
        this.directory=localDirectory;
    }
    
    public Image openPreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName, directory);
        File f = new File(path);
        if (f.exists()) {
            logger.debug("Opening pre-processed image:  channel: {} timePoint: {} fieldName: {}", channelImageIdx, timePoint, microscopyFieldName);
            return ImageReader.openImage(path);
        } else {
            logger.error("pre-processed image: {} not found", path);
            //System.out.println("Try to open pre-processed image but file not found:  channel: "+channelImageIdx+ " timePoint: "+timePoint+ " fieldName: "+microscopyFieldName);
            return null;
        }
    }
    
    public Image openPreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName, BoundingBox bounds) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName, directory);
        File f = new File(path);
        if (f.exists()) {
            logger.debug("Opening pre-processed image:  channel: {} timePoint: {} fieldName: {} bounds: {}", channelImageIdx, timePoint, microscopyFieldName, bounds);
            return ImageReader.openImage(path, new ImageIOCoordinates(bounds));
        } else {
            logger.error("pre-processed image: {} not found", path);
            return null;
        }
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
        logger.debug("writing preprocessed image to path: {}", path);
        //if (f.exists()) f.delete();
        ImageWriter.writeToFile(image, path, ImageFormat.TIF);
    }

    public ImageInteger openMask(StructureObject object) {
        String path = getProcessedImageFile(object);
        File f = new File(path);
        if (f.exists()) {
            logger.debug("opening mask of object: {}", object);
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
    
    protected static String getPreProcessedImagePath(int channelImageIdx, int timePoint, String microscopyFieldName, String imageDirectory) {
        return imageDirectory+File.separator+microscopyFieldName+File.separator+"pre_processed"+File.separator+"t"+Utils.formatInteger(5, timePoint)+"_c"+Utils.formatInteger(2, channelImageIdx)+".tif";
    }
    
    private static String getProcessedImageDirectoryRoot(StructureObject root) {
        return root.getExperiment().getOutputImageDirectory()+File.separator+root.getFieldName()+File.separator+"processed"+File.separator+"t"+Utils.formatInteger(5, root.getTimePoint());
    }
    private static String getImageFileName(StructureObject object) {
        return "s"+Utils.formatInteger(2, object.getStructureIdx())+"_idx"+Utils.formatInteger(5, object.getIdx());
    }
    protected static String getProcessedImageDirectory(StructureObject object) {
        if (object.isRoot()) throw new IllegalArgumentException("root objects are not pre-Processed");
        if (object.getParent().isRoot()) return getProcessedImageDirectoryRoot(object.getParent())+File.separator+getImageFileName(object);
        else return getProcessedImageDirectory((StructureObject)object.getParent())+File.separator+getImageFileName(object);
    }
    protected static String getProcessedImageFile(StructureObject object) {
        return getProcessedImageDirectory(object)+".png";
    }

    
    
    
}
