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
package core;

import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectRoot;
import image.BoundingBox;
import image.Image;
import image.ImageIOCoordinates;
import image.ImageReader;
import java.io.File;
import java.text.DecimalFormat;

/**
 *
 * @author jollion
 */
public class ImagePath {
    final static DecimalFormat nf5 = new DecimalFormat("00000");
    final static DecimalFormat nf2 = new DecimalFormat("00");
    
    public static String getPreProcessedImagePath(int channelImageIdx, int timePoint, String microscopyFieldName, String imageDirectory) {
        return imageDirectory+File.separator+microscopyFieldName+File.separator+"pre_processed"+File.separator+"t"+nf5.format(timePoint)+"_"+nf2.format(channelImageIdx); // extension?
    }
    
    public static String getProcessedImageDirectory(StructureObjectRoot root) {
        return root.getOutputFileDirectory()+File.separator+root.getName()+File.separator+"processed"+File.separator+"t"+nf5.format(root.getTimePoint());
    }
    public static String getProcessedImageDirectory(StructureObject object) {
        if (object.getParent().isRoot()) return getProcessedImageDirectory(object.getRoot())+File.separator+getImageFileName(object, false);
        else return getProcessedImageDirectory((StructureObject)object.getParent())+File.separator+getImageFileName(object, false);
    }
    private static String getImageFileName(StructureObject object, boolean extension) {
        return "s"+nf2.format(object.getStructureIdx())+"_idx"+nf5.format(object.getIdx())+(extension?".png":"");
    }
    public static String getProcessedImageFile(StructureObject object) {
        if (object.getParent().isRoot()) return getProcessedImageDirectory(object.getRoot())+File.separator+getImageFileName(object, true);
        else return getProcessedImageDirectory((StructureObject)object.getParent())+File.separator+getImageFileName(object, true);
    }
    
    public static Image openPreProcessedImage(int channelIdx, int timePoint, String fieldName, String outputDirectory) {
        String path = ImagePath.getPreProcessedImagePath(channelIdx, timePoint, fieldName, outputDirectory);
        File f = new File(path);
        if (f.exists()) {
            return ImageReader.openImage(path);
        } else return null;
    }
    public static Image openPreProcessedImage(int channelIdx, int timePoint, String fieldName, String outputDirectory, BoundingBox bounds) {
        String path = ImagePath.getPreProcessedImagePath(channelIdx, timePoint, fieldName, outputDirectory);
        File f = new File(path);
        if (f.exists()) {
            return ImageReader.openImage(path, new ImageIOCoordinates(bounds));
        } else return null;
    }
}
