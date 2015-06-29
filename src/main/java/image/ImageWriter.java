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

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.DataTools;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatWriter;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;

/**
 *
 * @author jollion
 */
public class ImageWriter {
    /**
     * 
     * @param image image that will be written
     * @param path path of the folder
     * @param fileName without the extension
     * @param extension defines the output format
     */
    public static void writeToFile(Image image, String path, String fileName, WriteFormat extension) {
        if (fileName==null) fileName=image.getName();
        image = TypeConverter.toCommonImageType(image);
        String fullPath = path+File.separator+fileName+extension.getExtension();
        try {
            IFormatWriter writer = new loci.formats.ImageWriter();
            writer.setMetadataRetrieve(generateMetadata(image));
            writer.setId(fullPath);
            Logger.getLogger(ImageWriter.class.getName()).info("writing file:"+fullPath);
            Logger.getLogger(ImageWriter.class.getName()).info("image count: "+writer.getMetadataRetrieve().getImageCount());
            Logger.getLogger(ImageWriter.class.getName()).info("color model==null? "+(writer.getColorModel()==null));
            Logger.getLogger(ImageWriter.class.getName()).info("compression "+writer.getCompression());
            boolean littleEndian = !writer.getMetadataRetrieve().getPixelsBinDataBigEndian(0, 0);
            writer.setSeries(0);
            Logger.getLogger(ImageWriter.class.getName()).info("format: "+writer.getFormat());
            for (int z = 0; z<image.sizeZ; z++) {
                writer.saveBytes(z, getBytePlane(image, z, littleEndian));
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
    
    public static IMetadata generateMetadata(Image image) {
        IMetadata meta = MetadataTools.createOMEXMLMetadata();
        String pixelType;
        if (image instanceof ImageByte) pixelType=FormatTools.getPixelTypeString(FormatTools.UINT8);
        else if (image instanceof ImageShort) pixelType=FormatTools.getPixelTypeString(FormatTools.UINT16);
        else if (image instanceof ImageFloat) pixelType=FormatTools.getPixelTypeString(FormatTools.FLOAT); //UINT32?
        else throw new IllegalArgumentException("Image should be of type byte, short or float");
        MetadataTools.populateMetadata(meta, 0, null, false, "XYZCT", pixelType, image.getSizeX(), image.getSizeY(), image.getSizeZ(), 1, 1, 1);
        return meta;
    }
    
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
                /*if (meta.getPixelsBinDataCount(0) == 0 || meta.getPixelsBinDataBigEndian(0, 0) == null) {
                    meta.setPixelsBinDataBigEndian(Boolean.FALSE, 0, 0);
                }*/
                meta.setPixelsBinDataBigEndian(Boolean.TRUE,0,0);
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
                /*meta.setPixelsPhysicalSizeX(new Length(image.getScaleXY(), UNITS.MICROM), 0);
                System.out.println("unit xy:"+image.getScaleXY()+ " name:"+UNITS.MICROM.getSymbol());
                meta.setPixelsPhysicalSizeY(new Length(image.getScaleXY(), UNITS.MICROM), 0);
                meta.setPixelsPhysicalSizeZ(new Length(image.getScaleZ(), UNITS.MICROM), 0);
                System.out.println("unit xy:"+image.getScaleZ()+ " name:"+UNITS.MICROM.getSymbol());*/
            } catch (ServiceException ex) {
                Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (DependencyException ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return meta;
    }
}
