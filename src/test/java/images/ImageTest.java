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
package images;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageIOCoordinates;
import image.ImageInt;
import image.ImageReader;
import image.ImageShort;
import image.ImageWriter;
import image.TypeConverter;
import image.WriteFormat;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static junit.framework.Assert.assertEquals;
import loci.common.DataTools;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatWriter;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;
import org.junit.Assert;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 *
 * @author jollion
 */
public class ImageTest {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    
    /**
     * simple set / get on image byte
     */
    @org.junit.Test
    public void testImageByte() {
        ImageByte imByte = new ImageByte("im test byte", 2, 3, 2);
        imByte.setPixel(3, 1, 10);
        assertEquals("Read voxel value: byte", imByte.getPixelInt(1, 1, 1), 10);
        
        imByte.setPixel(1, 0, 1, 10.5f);
        assertEquals("Read voxel value: float cast to byte", imByte.getPixelInt(1, 0, 1), 10);
    }
    
    /**
     * simple set / get on image int
     */
    @org.junit.Test
    public void testImageInt() {
        ImageInt imInt = new ImageInt("im test int", 2, 2, 2);
        imInt.setPixel(1,0, 1, Integer.MAX_VALUE);
        assertEquals("Read voxel value: integer", imInt.getPixelInt(1, 0, 1), Integer.MAX_VALUE);
    }
    
    /**
     * type conversion test
     */
    @org.junit.Test
    public void testImageSimpleConversion() {
        ImageInt imInt = new ImageInt("im test int", 2, 2, 2);
        imInt.setPixel(0, 1, 1, Integer.MAX_VALUE);
        imInt.setPixel(0, 0, 1, 2);
        ImageByte imByte = TypeConverter.toByteMask(imInt);
        assertEquals("Read voxel value: mask", imByte.getPixelInt(0, 1, 1), 1);
        
        ImageFloat imFloat = TypeConverter.toFloat(imInt);
        assertEquals("Read voxel value: int cast to float", imFloat.getPixel(0, 0, 1), 2f);
    }
    
    @org.junit.Test
    public void testWriteFile() {
        ImageByte im = new ImageByte("test", 4, 3, 2);
        
        IFormatWriter writer = new loci.formats.ImageWriter();
        writer.setMetadataRetrieve(ImageWriter.generateMetadata(im));

        try {
            writer.setId(testFolder.newFolder("testImage").getAbsolutePath() + File.separator + "testIm.tif");
        } catch (FormatException ex) {
            Logger.getLogger(ImageTest.class.getName()).log(Level.SEVERE, null, ex);
            fail("An error occured trying to set ID");
        } catch (IOException ex) {
            Logger.getLogger(ImageTest.class.getName()).log(Level.SEVERE, null, ex);
            fail("An error occured trying to set ID");
        }
        try {
            writer.setSeries(0);
        } catch (FormatException ex) {
            Logger.getLogger(ImageTest.class.getName()).log(Level.SEVERE, null, ex);
            fail("An error occured trying to set series");
        }
        for (int z = 0; z < 2; z++) {
            try {
                writer.saveBytes(z, new byte[4 * 3]);
            } catch (FormatException ex) {
                Logger.getLogger(ImageTest.class.getName()).log(Level.SEVERE, null, ex);
                fail("An error occured trying to write plane");
            } catch (IOException ex) {
                Logger.getLogger(ImageTest.class.getName()).log(Level.SEVERE, null, ex);
                fail("An error occured trying to write plane");
            }
        }
        try {
            writer.close();
        } catch (IOException ex) {
            Logger.getLogger(ImageTest.class.getName()).log(Level.SEVERE, null, ex);
            fail("An error occured trying to close writer");
        }
    }
    
    @org.junit.Test
    public void testWriteFile2() {
        String id = testFolder.newFolder("imageTest").getAbsolutePath()+File.separator+"imageTest.png";

        int w = 512, h = 512, c = 1, z = 2;
        int pixelType = FormatTools.UINT16;
        byte[] img = new byte[w * h * c * FormatTools.getBytesPerPixel(pixelType)];
        for (int i = 0; i < img.length; i++) {
            img[i] = (byte) (256 * Math.random());
        }
        try {
            ServiceFactory factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            MetadataTools.populateMetadata(meta, 0, null, false, "XYZCT", FormatTools.getPixelTypeString(pixelType), w, h, z, c, 1, c);
            System.out.println("Writing image to '" + id + "'...");
            IFormatWriter writer = new loci.formats.ImageWriter();
            writer.setMetadataRetrieve(meta);
            writer.setId(id);
            writer.saveBytes(0, img);
            writer.saveBytes(1, img);

            writer.close();
        } catch (Exception e) {
            fail("problem writing image to disk");
        }
        
        ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
        ImageStack stack=null;
        try {
            r.setId(id);
            int num = r.getImageCount();
            int width = r.getSizeX();
            int height = r.getSizeY();
            stack = new ImageStack(width, height);
            for (int i = 0; i < num; i++) {
                ImageProcessor ip = r.openProcessors(i)[0];
                stack.addSlice("" + (i + 1), ip);
            }
            r.close();
        } catch (FormatException exc) {
            fail("error reading image");
        } catch (IOException exc) {
            fail("error reading image");
        }
        
        Assert.assertArrayEquals("Comparing image", img, DataTools.shortsToBytes((short[])stack.getPixels(2), false));
        
        /*
        ImageReader reader = new ImageReader(id);
        Assert.assertArrayEquals("testing file sct", new int[]{1,1,1}, reader.getSTCNumbers());
        Image im = reader.openChannel(new ImageIOCoordinates());
        Assert.assertArrayEquals("testing image dimensions", new int[]{w, h, z}, new int[]{im.getSizeX(), im.getSizeY(), im.getSizeZ()});
        
        Assert.assertArrayEquals("Comparing image", img, DataTools.shortsToBytes(((short[][])im.getPixelArray())[1], false));
        */
    }
    
    //@org.junit.Test
    public void testIOImageDimensions() {
        String id = testFolder.newFolder("imageTest").getAbsolutePath()+File.separator+"imageTest.png";
        int w = 512, h = 512, c = 1, z = 2;
        int pixelType = FormatTools.UINT16;
        byte[] img = new byte[w * h * c * FormatTools.getBytesPerPixel(pixelType)];
        for (int i = 0; i < img.length; i++) {
            img[i] = (byte) (256 * Math.random());
        }
        try {
            ServiceFactory factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            MetadataTools.populateMetadata(meta, 0, null, false, "XYZCT", FormatTools.getPixelTypeString(pixelType), w, h, z, c, 1, c);
            meta.setPixelsPhysicalSizeX(new Length(0.1f, UNITS.MICROM), 0);
            meta.setPixelsPhysicalSizeY(new Length(0.1f, UNITS.MICROM), 0);
            meta.setPixelsPhysicalSizeZ(new Length(0.23f, UNITS.MICROM), 0);
            
            IFormatWriter writer = new loci.formats.ImageWriter();
            writer.setMetadataRetrieve(meta);
            writer.setId(id);
            writer.saveBytes(0, img);
            writer.saveBytes(1, img);
            writer.close();
        } catch (Exception e) {
            fail("problem writing image to disk");
        }
        
        ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
        try {
            r.setId(id);
            Assert.assertEquals("testing file series number", 1, r.getSeriesCount());
            Assert.assertEquals("testing file timePoints number", 1, r.getSizeT());
            Assert.assertEquals("testing file channels number", 1, r.getSizeC());
            Assert.assertEquals("testing image width", w, r.getSizeX());
            Assert.assertEquals("testing image heigth", h, r.getSizeY());
            Assert.assertEquals("testing image depth", z, r.getSizeZ());
            
            r.close();
        } catch (FormatException exc) {
            fail("error reading image");
        } catch (IOException exc) {
            fail("error reading image");
        } 
    }
    
    //@org.junit.Test
    public void testIOImageCalibration() {
        String id = testFolder.newFolder("imageTest").getAbsolutePath() + File.separator + "imageTest.png";
        int w = 512, h = 512, c = 1, z = 1;
        int pixelType = FormatTools.UINT16;
        byte[] img = new byte[w * h * c * FormatTools.getBytesPerPixel(pixelType)];
        for (int i = 0; i < img.length; i++) {
            img[i] = (byte) (256 * Math.random());
        }
        double calX = 0.1d;
        double calZ = 0.23d;
        try {
            ServiceFactory factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            MetadataTools.populateMetadata(meta, 0, null, false, "XYZCT", FormatTools.getPixelTypeString(pixelType), w, h, z, c, 1, c);
            meta.setPixelsPhysicalSizeX(new Length(calX, UNITS.MICROM), 0);
            meta.setPixelsPhysicalSizeY(new Length(calX, UNITS.MICROM), 0);
            meta.setPixelsPhysicalSizeZ(new Length(calZ, UNITS.MICROM), 0);

            IFormatWriter writer = new loci.formats.ImageWriter();
            writer.setMetadataRetrieve(meta);
            writer.setId(id);
            writer.saveBytes(0, img);
            writer.close();
        } catch (Exception e) {
            fail("problem writing image to disk");
        }

        ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
        IMetadata meta = null;
        try {
            ServiceFactory factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            try {
                meta = service.createOMEXMLMetadata();
                r.setMetadataStore(meta);
            } catch (ServiceException ex) {
                fail("problem setting metadata");
            }
        } catch (DependencyException ex) {
            fail("problem setting metadata");
        }
        try {
            r.setId(id);
            r.setSeries(0);
            Length lx = meta.getPixelsPhysicalSizeX(0);
            Length lz = meta.getPixelsPhysicalSizeZ(0);
            Assert.assertTrue("testing calibration X is not null?", lx!=null);
            Assert.assertTrue("testing calibration Z is not null?", lz!=null);
            Assert.assertEquals("testing calibration X retrieve", calX, lx.value());
            Assert.assertEquals("testing calibration Z retrieve", calZ, lz.value());
            r.close();
        } catch (FormatException exc) {
            fail("error reading image");
        } catch (IOException exc) {
            fail("error reading image");
        }
    }
    
    /**
     * I/O TIF Byte
     */
    @org.junit.Test
    public void testIOTIFByte() {
        //testIO(WriteFormat.TIF, 0);
    }
    
    /**
     * I/O TIF Short
     */
    @org.junit.Test
    public void testIOTIFShort() {
        //testIO(WriteFormat.TIF, 1);
    }
    
    /**
     * I/O TIF Float
     */
    @org.junit.Test
    public void testIOTIFFloat() {
        //testIO(WriteFormat.TIF, 2);
    }
    
    /**
     * I/O APNG Byte
     */
    @org.junit.Test
    public void testIOPNGByte() {
        //testIO(WriteFormat.PNG, 0);
    }
    
    /**
     * I/O APNG Short
     */
    @org.junit.Test
    public void testIOPNGShort() {
        //testIO(WriteFormat.PNG, 1);
    }
    
    /**
     * I/O APNG Float
     */
    @org.junit.Test
    public void testIOPNGFloat() {
        //testIO(WriteFormat.PNG, 2);
    }
    
    private void testIO(WriteFormat extension, int type) {
        String title = "im2Test"+type;
        Image imTest;
        if (type==0) imTest = new ImageByte(title, 2, 2, 4);
        else if (type==1) imTest = new ImageShort(title, 2, 2, 3);
        else imTest = new ImageFloat(title, 2, 2, 3);
        imTest.setPixel(1, 0, 2, 2);
        imTest.setCalibration(0.1f, 0.23f);
        //File folder = testFolder.newFolder("TestImages");
        File folder = new File("/tmp");
        try {
            ImageWriter.writeToFile(imTest, folder.getAbsolutePath(), null, extension);
        } catch (Exception ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            fail("An error occured trying to write an image in "+extension+" format");
        }
        System.out.println("Write to file done");
        ImageReader reader=null;
        try {
            reader = new ImageReader(folder.getAbsolutePath(), title, extension);
        } catch (Exception ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            fail("An error occured trying to initialize reader");
        }
        int[] stc = reader.getSTCNumbers();
        System.out.println("Series:"+stc[0]+" time:"+stc[1]+" c:"+stc[2]);
        //Assert.assertArrayEquals("Retrive Image Serie/TimePoint/Channel number", new int[]{1, 1, 1}, stc);
        Image im=null;
        try {
            im = reader.openChannel(new ImageIOCoordinates());
        } catch (Exception ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            fail("An error occured trying to read image");
        }
        reader.closeReader();
        if (type==0) Assert.assertTrue("image type verification", im instanceof ImageByte);
        else if (type==1) Assert.assertTrue("image type verification", im instanceof ImageShort);
        else Assert.assertTrue("image type verification", im instanceof ImageFloat);
        
        //Assert.assertArrayEquals("Retrieve Image Resolution", new float[]{0.1f, 0.23f}, new float[]{im.getScaleXY(), im.getScaleZ()}, 0.001f);
        
        Assert.assertEquals("Retrieve pixel value", 2f, im.getPixel(1, 0, 2), 0.0001f);
        
    }
    
    /*@org.junit.Test
    public void testIOView() {
        String title = "im test short";
        ImageShort imShort = new ImageShort(title, 5, 6, 7);
        imShort.setPixel(2, 3, 5, 3);
        imShort.setCalibration(0.1f, 0.23f);
        File folder = testFolder.newFolder("Test Images");
        ImageWriter.writeToFile(imShort, folder.getAbsolutePath(), null, WriteFormat.TIF);
        ImageReader reader = new ImageReader(folder.getAbsolutePath(), title, WriteFormat.TIF);
        BoundingBox bb=new BoundingBox(1, 2, 3, 4, 3, 5);
        ImageShort im = (ImageShort)reader.openChannel(new ImageIOCoordinates(bb));
        reader.closeReader();
        BoundingBox retrieveBB = new BoundingBox(im, true);
        Assert.assertEquals("Retrieve Image View: Dimensions", bb, retrieveBB);
        
        Assert.assertEquals("Retrieve pixel value", 3, im.getPixelInt(2-im.getOffsetX(), 3-im.getOffsetY(), 5-im.getOffsetZ()));
        
    }*/
}
