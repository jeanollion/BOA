/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.data_structure.dao;

import boa.core.Processor;
import boa.configuration.experiment.Experiment;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import static boa.image.Image.logger;
import boa.image.io.ImageFormat;
import boa.image.io.ImageIOCoordinates;
import boa.image.ImageInteger;
import boa.image.io.ImageReader;
import boa.image.io.ImageWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import boa.utils.FileIO;
import boa.utils.Pair;
import boa.utils.Utils;

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
    public String getImageExtension() {
        return ".tif";
    }
    @Override
    public InputStream openPreProcessedImageAsStream(int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName);
        File f = new File(path);
        try {
            return new FileInputStream(f);
        } catch (FileNotFoundException ex) {
            logger.trace("Opening pre-processed image:  channel: {} timePoint: {} fieldName: {}", channelImageIdx, timePoint, microscopyFieldName);
        }
        return null;
    }
    @Override
    public Image openPreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName);
        File f = new File(path);
        if (f.exists()) {
            //long t0 = System.currentTimeMillis();
            Image im = ImageReader.openImage(path);
            //long t1 = System.currentTimeMillis();
            //logger.debug("Opening pre-processed image:  channel: {} timePoint: {} position: {}, in {}ms", channelImageIdx, timePoint, microscopyFieldName, t1-t0);
            return im;
        } else {
            logger.trace("pre-processed image: {} not found", path);
            return null;
        }
    }
    @Override
    public Image openPreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName, BoundingBox bounds) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName);
        File f = new File(path);
        if (f.exists()) {
            logger.trace("Opening pre-processed image:  channel: {} timePoint: {} fieldName: {} bounds: {}", channelImageIdx, timePoint, microscopyFieldName, bounds);
            return ImageReader.openImage(path, new ImageIOCoordinates(bounds));
        } else {
            logger.error("pre-processed image: {} not found", path);
            return null;
        }
    }
    @Override
    public void deletePreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName);
        File f = new File(path);
        if (f.exists()) f.delete();
    }
    @Override
    public BlankMask getPreProcessedImageProperties(String microscopyFieldName) {
        String path = getPreProcessedImagePath(0, 0, microscopyFieldName);
        File f = new File(path);
        if (f.exists()) {
            Pair<int[][], double[]> info = ImageReader.getImageInfo(path);
            int[][] STCXYZ = info.key;
            double[] scale = new double[]{info.value[0], info.value[2]};
            return new BlankMask( STCXYZ[0][2], STCXYZ[0][3], STCXYZ[0][4], 0, 0, 0, (float)scale[0], (float)scale[1]);
        } else {
            logger.error("getPreProcessedImageProperties: pre-processed image {} not found", path);
            return null;
        }
    }

    
    @Override
    public void writePreProcessedImage(Image image, int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName);
        File f = new File(path);
        f.mkdirs();
        logger.trace("writing preprocessed image to path: {}", path);
        //if (f.exists()) f.delete();
        ImageWriter.writeToFile(image, path, ImageFormat.TIF);
    }
    
    @Override
    public void writePreProcessedImage(InputStream image, int channelImageIdx, int timePoint, String microscopyFieldName) {
        String path = getPreProcessedImagePath(channelImageIdx, timePoint, microscopyFieldName);
        File f = new File(path);
        f.delete();
        f.getParentFile().mkdirs();
        logger.trace("writing preprocessed image to path: {}", path);
        //if (f.exists()) f.delete();
        FileIO.writeFile(image, path);
    }

    protected String getPreProcessedImagePath(int channelImageIdx, int timePoint, String microscopyFieldName) {
        return directory+File.separator+microscopyFieldName+File.separator+"pre_processed"+File.separator+"t"+Utils.formatInteger(5, timePoint)+"_c"+Utils.formatInteger(2, channelImageIdx)+".tif";
    }
    private String getTrackImageFolder(String position, int parentStructureIdx) {
        return directory+File.separator+position+File.separator+"track_images_"+parentStructureIdx;
    }
    private String getTrackImagePath(StructureObject o, int channelImageIdx) {
        return getTrackImageFolder(o.getPositionName(), o.getStructureIdx())+File.separator+Selection.indicesString(o)+"_"+channelImageIdx+".tif";
    }
    
    @Override
    public void writeTrackImage(StructureObject trackHead, int channelImageIdx, Image image) {
        String path = getTrackImagePath(trackHead, channelImageIdx);
        File f = new File(path);
        f.delete();
        f.getParentFile().mkdirs();
        logger.trace("writing track image to path: {}", path);
        ImageWriter.writeToFile(image, path, ImageFormat.TIF);
    }
    @Override
    public void writeTrackImage(StructureObject trackHead, int channelImageIdx, InputStream image) {
        String path = getTrackImagePath(trackHead, channelImageIdx);
        File f = new File(path);
        f.delete();
        f.getParentFile().mkdirs();
        logger.trace("writing track image to path: {}", path);
        FileIO.writeFile(image, path);
    }
    @Override
    public Image openTrackImage(StructureObject trackHead, int channelImageIdx) {
        String path = getTrackImagePath(trackHead, channelImageIdx);
        File f = new File(path);
        //logger.debug("opening track image: from {} c={}, path: {}, exists? {}", trackHead, channelImageIdx, path, f.exists());
        if (f.exists()) {
            //logger.trace("Opening track image:  trackHead: {}", trackHead);
            return ImageReader.openImage(path);
        } else {
            return null;
        }
    }
    @Override
    public InputStream openTrackImageAsStream(StructureObject trackHead, int channelImageIdx) {
        String path = getTrackImagePath(trackHead, channelImageIdx);
        File f = new File(path);
        try {
            //logger.trace("Opening track image:  trackHead: {}", trackHead);
            return new FileInputStream(f);
        } catch (FileNotFoundException ex) {
            
            logger.debug("Error Opening track image:  trackHead: {} channelImage: {} ({})", trackHead, channelImageIdx, path);
            logger.debug("error", ex);
        } 
        return null;
    }

    @Override
    public void deleteTrackImages(String position, int parentStructureIdx) {
        String folder = getTrackImageFolder(position, parentStructureIdx);
        Utils.deleteDirectory(folder);
    }
    
}
