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

import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectRoot;
import image.BoundingBox;
import image.Image;
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
            return ImageReader.openImage(path);
        } else {
            //log
            return null;
        }
    }

    public Image openPreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName, BoundingBox bounds) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName, directory);
        File f = new File(path);
        if (f.exists()) {
            return ImageReader.openImage(path, new ImageIOCoordinates(bounds));
        } else {
            //log
            return null;
        }
    }

    public void writePreProcessedImage(Image image, int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName, directory);
        File f = new File(path);
        f.mkdirs();
        //if (f.exists()) f.delete();
        ImageWriter.writeToFile(image, path, ImageFormat.TIF);
    }

    public ImageInteger openMask(StructureObject object) {
        String path = getProcessedImageFile(object);
        File f = new File(path);
        if (f.exists()) {
            return (ImageInteger)ImageReader.openImage(path);
        } else {
            //log
            return null;
        }
    }
    
    public void writeMask(ImageInteger mask, StructureObject object) {
        String path = getProcessedImageFile(object);
        File f = new File(path);
        f.mkdirs();
        //if (f.exists()) f.delete();
        ImageWriter.writeToFile(mask, path, ImageFormat.PNG);
    }
    
    protected static String getPreProcessedImagePath(int channelImageIdx, int timePoint, String microscopyFieldName, String imageDirectory) {
        return imageDirectory+File.separator+microscopyFieldName+File.separator+"pre_processed"+File.separator+"t"+Utils.formatInteger(5, timePoint)+"_"+Utils.formatInteger(2, channelImageIdx)+".tif"; // extension?
    }
    
    protected static String getProcessedImageDirectory(StructureObjectRoot root) {
        return root.getOutputFileDirectory()+File.separator+root.getName()+File.separator+"processed"+File.separator+"t"+Utils.formatInteger(5, root.getTimePoint());
    }
    protected static String getProcessedImageDirectory(StructureObject object) {
        if (object.getParent().isRoot()) return getProcessedImageDirectory(object.getRoot())+File.separator+getImageFileName(object, false);
        else return getProcessedImageDirectory((StructureObject)object.getParent())+File.separator+getImageFileName(object, false);
    }
    protected static String getImageFileName(StructureObject object, boolean extension) {
        return "s"+Utils.formatInteger(2, object.getStructureIdx())+"_idx"+Utils.formatInteger(5, object.getIdx())+(extension?".png":"");
    }
    protected static String getProcessedImageFile(StructureObject object) {
        if (object.getParent().isRoot()) return getProcessedImageDirectory(object.getRoot())+File.separator+getImageFileName(object, true);
        else return getProcessedImageDirectory((StructureObject)object.getParent())+File.separator+getImageFileName(object, true);
    }
    
    
}
