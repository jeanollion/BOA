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
package image;

import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.io.TiffEncoder;
import static image.Image.logger;
import static image.ImageFormat.*;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.DataTools;
import loci.common.Region;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatWriter;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import ome.units.UNITS;
import ome.units.quantity.Length;

/**
 *
 * @author jollion
 */
public class ImageWriter {
    public static ImageFormat defExtension = ImageFormat.OMETIF;
    
    public static void writeToFile(Image image, String fullPath, ImageFormat extension) {
        File f= new File(fullPath);
        String path  = f.getParent();
        String imageTitle = f.getName();
        int extIdx = imageTitle.indexOf(".");
        if (extIdx>0) {
            imageTitle = f.getName().substring(0, extIdx);
            ImageFormat e=ImageFormat.getExtension(f.getName().substring(extIdx));
            if (e!=null) extension = e;
        } else if (extension==null) extension = defExtension;
        writeToFile(image, path, imageTitle, extension);
    }
    
    /**
     * 
     * @param image image that will be written
     * @param path path of the folder
     * @param fileName without the extension
     * @param extension defines the output format
     */
    public static void writeToFile(Image image, String path, String fileName, ImageFormat extension) {
        if (fileName==null) fileName=image.getName();
        image = TypeConverter.toCommonImageType(image);
        if (image instanceof ImageFloat && extension.equals(PNG)) throw new IllegalArgumentException("Float image cannot be written as PNG");
        String fullPath = path+File.separator+fileName+extension.getExtension();
        File f = new File(fullPath);
        if (f.exists()) f.delete();
        if (extension.equals(ImageFormat.TIF)) writeToFileTIF(image, fullPath);
        else writeToFileBioFormat(image, fullPath);
    }
    /**
     * 
     * @param imageTC matrix of images (type float, short or byte only). First dimension = times, second dimension = channels. Each time must have the same number of channels
     * @param folderPath path of the folder file to be written to;
     * @param fileName name of the file without the extension
     * @param extension the extension of the file that must be compatible with multiple slices, timepoints and channels images. 
     */
    public static void writeToFile(String folderPath, String fileName, ImageFormat extension, Image[][]... imageTC) {
        //if (!extension.getSupportMultipleTimeAndChannel() && imageTC.length>1 || imageTC[0].length>1) throw new IllegalArgumentException("the format does not support multiple time points and channels");
        try {
            //System.out.println("series:"+imageTC.length);
            for (int s = 0; s<imageTC.length; ++s) logger.trace("ImageWriter: serie #:"+s+ " time points: "+imageTC[s].length+ " channels: "+imageTC[s][0].length);
            String fullPath = folderPath+File.separator+fileName+extension;
            IFormatWriter writer = new loci.formats.ImageWriter();
            writer.setMetadataRetrieve(generateMetadata(imageTC));
            new File(folderPath).mkdirs();
            writer.setId(fullPath);
            /*Logger.getLogger(ImageWriter.class.getName()).info("writing file:"+fullPath);
            Logger.getLogger(ImageWriter.class.getName()).info("image count: "+writer.getMetadataRetrieve().getImageCount());
            Logger.getLogger(ImageWriter.class.getName()).info("color model==null? "+(writer.getColorModel()==null));
            Logger.getLogger(ImageWriter.class.getName()).info("compression "+writer.getCompression());*/
            boolean littleEndian = !writer.getMetadataRetrieve().getPixelsBinDataBigEndian(0, 0);
            for (int series = 0; series<imageTC.length; ++series) {
                writer.setSeries(series);
                int i = 0;
                for (int t = 0; t<imageTC[series].length; t++){
                    for (int c = 0; c<imageTC[series][0].length; c++) {
                        logger.trace("save image: time: {} channel: {}", t, c);
                        Image curIm = imageTC[series][t][c];
                        for (int z = 0; z<curIm.sizeZ; z++) {
                            writer.saveBytes(i++, getBytePlane(curIm, z, littleEndian));

                        }
                    }
                }
            }
            writer.close();
        } catch (FormatException ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private static void writeToFileBioFormat(Image image, String fullPath) {
        try {
            IFormatWriter writer = new loci.formats.ImageWriter();
            writer.setMetadataRetrieve(generateMetadata(image, 1, 1));
            writer.setId(fullPath);
            Logger.getLogger(ImageWriter.class.getName()).info("writing file:"+fullPath);
            Logger.getLogger(ImageWriter.class.getName()).info("image count: "+writer.getMetadataRetrieve().getImageCount());
            Logger.getLogger(ImageWriter.class.getName()).info("color model==null? "+(writer.getColorModel()==null));
            Logger.getLogger(ImageWriter.class.getName()).info("compression "+writer.getCompression());
            boolean littleEndian = !writer.getMetadataRetrieve().getPixelsBinDataBigEndian(0, 0);
            writer.setSeries(0);
            Logger.getLogger(ImageWriter.class.getName()).info("format: "+writer.getFormat());
            int i=0;
            for (int z = 0; z<image.sizeZ; z++) {
                writer.saveBytes(i++, getBytePlane(image, z, littleEndian));
            }
            writer.close();
        } catch (FormatException ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private static byte[] getBytePlane(Image image, int z, boolean littleEndian) {
        if (image instanceof ImageByte) {
            return ((ImageByte)image).getPixelArray()[z];
        } else if (image instanceof ImageShort) {
            return DataTools.shortsToBytes( (short[]) ((ImageShort)image).getPixelArray()[z], littleEndian);
        } else if (image instanceof ImageFloat) {
            return DataTools.floatsToBytes( (float[]) ((ImageFloat)image).getPixelArray()[z], littleEndian);
        } else return null;
    }
    
    public static IMetadata generateMetadata(Image image, int channelNumber, int timePointNumber) {
        IMetadata meta = MetadataTools.createOMEXMLMetadata();
        String pixelType;
        if (image instanceof ImageByte) pixelType=FormatTools.getPixelTypeString(FormatTools.UINT8);
        else if (image instanceof ImageShort) pixelType=FormatTools.getPixelTypeString(FormatTools.UINT16);
        else if (image instanceof ImageFloat) pixelType=FormatTools.getPixelTypeString(FormatTools.FLOAT); //UINT32?
        else throw new IllegalArgumentException("Image should be of type byte, short or float");
        
        MetadataTools.populateMetadata(meta, 0, null, false, "XYZCT", pixelType, image.getSizeX(), image.getSizeY(), image.getSizeZ(), channelNumber, timePointNumber, 1);
        //System.out.println("NTimes:"+timePointNumber+" NChannel:"+channelNumber);
        meta.setPixelsPhysicalSizeX(new Length(image.getScaleXY(), UNITS.MICROM), 0);
        meta.setPixelsPhysicalSizeY(new Length(image.getScaleXY(), UNITS.MICROM), 0);
        meta.setPixelsPhysicalSizeZ(new Length(image.getScaleZ(), UNITS.MICROM), 0);
        return meta;
    }
    
    public static IMetadata generateMetadata(Image[][][] imageSTC) {
        IMetadata meta = MetadataTools.createOMEXMLMetadata();
        String pixelType;
        for (int series = 0; series < imageSTC.length; ++series) {
            
            if (imageSTC[series][0][0] instanceof ImageByte) pixelType=FormatTools.getPixelTypeString(FormatTools.UINT8);
            else if (imageSTC[series][0][0] instanceof ImageShort) pixelType=FormatTools.getPixelTypeString(FormatTools.UINT16);
            else if (imageSTC[series][0][0] instanceof ImageFloat) pixelType=FormatTools.getPixelTypeString(FormatTools.FLOAT); //UINT32?
            else throw new IllegalArgumentException("Image should be of type byte, short or float");

            MetadataTools.populateMetadata(meta, series, null, false, "XYZCT", pixelType, imageSTC[series][0][0].getSizeX(), imageSTC[series][0][0].getSizeY(), imageSTC[series][0][0].getSizeZ(), imageSTC[series][0].length, imageSTC[series].length, 1);
            //System.out.println("NTimes:"+timePointNumber+" NChannel:"+channelNumber);
            meta.setPixelsPhysicalSizeX(new Length(imageSTC[series][0][0].getScaleXY(), UNITS.MICROM), 0);
            meta.setPixelsPhysicalSizeY(new Length(imageSTC[series][0][0].getScaleXY(), UNITS.MICROM), 0);
            meta.setPixelsPhysicalSizeZ(new Length(imageSTC[series][0][0].getScaleZ(), UNITS.MICROM), 0);
        }
        return meta;
    }
    
    private static void writeToFileTIF(Image image, String fullPath) {
        ImagePlus img = IJImageWrapper.getImagePlus(image);
        //System.out.println("image cal: x:"+img.getCalibration().pixelWidth+ " z:"+img.getCalibration().pixelDepth);
        FileInfo fi = img.getFileInfo();
        fi.info = img.getInfoProperty();
        FileSaver fs = new FileSaver(img);
        fi.description = fs.getDescriptionString();
        fi.sliceLabels = img.getStack().getSliceLabels();
        //System.out.println("fi image cal: x:"+fi.pixelWidth+ " z:"+fi.pixelDepth+ " nimages:"+fi.nImages);
        TiffEncoder te = new TiffEncoder(fi);
        try {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fullPath)));
            te.write(out);
            out.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    /*
    public static IMetadata generateMetadata2(Image image) {
        ServiceFactory factory;
        IMetadata meta=null;
        try {
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            try {
                meta = service.createOMEXMLMetadata();
                meta.createRoot();
                meta.setImageID("Image:0", 0);
                meta.setPixelsID("Pixels:0", 0);
                if (meta.getPixelsBinDataCount(0) == 0 || meta.getPixelsBinDataBigEndian(0, 0) == null) {
                    meta.setPixelsBinDataBigEndian(Boolean.FALSE, 0, 0);
                }
                //meta.setPixelsBinDataBigEndian(Boolean.TRUE,0,0);
                meta.setPixelsDimensionOrder(DimensionOrder.XYZCT,0);
                if (image instanceof ImageByte) meta.setPixelsType(PixelType.UINT8,0);
                else if (image instanceof ImageShort) meta.setPixelsType(PixelType.UINT16,0);
                else if (image instanceof ImageFloat) meta.setPixelsType(PixelType.FLOAT,0); //UINT32?
                meta.setPixelsSizeX(new PositiveInteger(image.getSizeX()), 0);
                meta.setPixelsSizeY(new PositiveInteger(image.getSizeY()), 0);
                meta.setPixelsSizeZ(new PositiveInteger(image.getSizeZ()), 0);
                meta.setPixelsSizeT(new PositiveInteger(1), 0);
                meta.setPixelsSizeC(new PositiveInteger(1), 0);     
                meta.setChannelID("Channel:0:" + 0, 0, 0);
                meta.setChannelSamplesPerPixel(new PositiveInteger(1),0,0);
                meta.setPixelsPhysicalSizeX(new Length(image.getScaleXY(), UNITS.MICROM), 0);
                meta.setPixelsPhysicalSizeY(new Length(image.getScaleXY(), UNITS.MICROM), 0);
                meta.setPixelsPhysicalSizeZ(new Length(image.getScaleZ(), UNITS.MICROM), 0);
            } catch (ServiceException ex) {
                Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (DependencyException ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return meta;
    }*/
}
