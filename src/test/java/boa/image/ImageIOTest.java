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
package boa.image;

import boa.test_utils.TestUtils;
import ij.ImageStack;
import ij.process.ImageProcessor;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.io.ImageIOCoordinates;
import boa.image.ImageShort;
import boa.image.io.ImageWriter;
import boa.image.io.ImageFormat;
import boa.image.io.ImageReader;
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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * @author jollion
 */
public class ImageIOTest {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    
    
    @org.junit.Test
    public void testWriteFile() {
        ImageByte im = new ImageByte("test", 4, 3, 2);
        
        IFormatWriter writer = new loci.formats.ImageWriter();
        writer.setMetadataRetrieve(ImageWriter.generateMetadata(im, 1, 1));

        try {
            writer.setId(testFolder.newFolder("testImage").getAbsolutePath() + File.separator + "testIm.tif");
        } catch (FormatException ex) {
            Logger.getLogger(ImageIOTest.class.getName()).log(Level.SEVERE, null, ex);
            fail("An error occured trying to set ID");
        } catch (IOException ex) {
            Logger.getLogger(ImageIOTest.class.getName()).log(Level.SEVERE, null, ex);
            fail("An error occured trying to set ID");
        }
        try {
            writer.setSeries(0);
        } catch (FormatException ex) {
            Logger.getLogger(ImageIOTest.class.getName()).log(Level.SEVERE, null, ex);
            fail("An error occured trying to set series");
        }
        for (int z = 0; z < 2; z++) {
            try {
                writer.saveBytes(z, new byte[4 * 3]);
            } catch (FormatException ex) {
                Logger.getLogger(ImageIOTest.class.getName()).log(Level.SEVERE, null, ex);
                fail("An error occured trying to write plane");
            } catch (IOException ex) {
                Logger.getLogger(ImageIOTest.class.getName()).log(Level.SEVERE, null, ex);
                fail("An error occured trying to write plane");
            }
        }
        try {
            writer.close();
        } catch (IOException ex) {
            Logger.getLogger(ImageIOTest.class.getName()).log(Level.SEVERE, null, ex);
            fail("An error occured trying to close writer");
        }
    }
    
    //@org.junit.Test
    public void testIOImageData() {
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
    //@org.junit.Test
    public void testIOTIFByte() {
        testIO(ImageFormat.TIF, 0);
    }
    
    /**
     * I/O TIF Short
     */
    @org.junit.Test
    public void testIOTIFShort() {
        testIO(ImageFormat.TIF, 1);
    }
    
    /**
     * I/O TIF Float
     */
    //@org.junit.Test
    public void testIOTIFFloat() {
        testIO(ImageFormat.TIF, 2);
    }
    
    /**
     * I/O APNG Byte
     */
    //@org.junit.Test
    public void testIOPNGByte() {
        testIO(ImageFormat.PNG, 0);
    }
    
    /**
     * I/O APNG Short
     */
    @Test
    public void testIOPNGShort() {
        testIO(ImageFormat.PNG, 1);
    }
    
    /**
     * I/O APNG Short
     */
    @Test(expected=IllegalArgumentException.class)
    public void testIOPNGFloat() {
        testIO(ImageFormat.PNG, 2);
    }
    
    private void testIO(ImageFormat extension, int type) {
        String title = "im3Test"+type;
        Image imTest;
        if (type==0) imTest = new ImageByte(title, 2, 2, 4);
        else if (type==1) imTest = new ImageShort(title, 2, 2, 3);
        else imTest = new ImageFloat(title, 2, 2, 3);
        imTest.setPixel(1, 0, 2, 2);
        imTest.setCalibration(0.1f, 0.23f);
        File folder = testFolder.newFolder("TestImages");
        //File folder = new File("/tmp");
        try {
            ImageWriter.writeToFile(imTest, folder.getAbsolutePath(), null, extension);
        } catch (IllegalArgumentException ex) {
            throw(ex);
        }catch (Exception ex) {
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
        int[][] stc = reader.getSTCXYZNumbers();
        System.out.println("Series:"+stc.length+" time:"+stc[0][0]+" c:"+stc[0][1]);
        
        //Test file dimension: 
        Assert.assertEquals("testing file series number", 1, stc.length);
        Assert.assertEquals("testing file timePoints number", 1, stc[0][0]);
        Assert.assertEquals("testing file channels number", 1, stc[0][1]);
        
        Image im=null;
        try {
            im = reader.openImage(new ImageIOCoordinates());
        } catch (Exception ex) {
            Logger.getLogger(ImageWriter.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            fail("An error occured trying to read image");
        }
        reader.closeReader();
        
        // test file type
        if (type==0) Assert.assertTrue("image type verification", im instanceof ImageByte);
        else if (type==1) Assert.assertTrue("image type verification", im instanceof ImageShort);
        else Assert.assertTrue("image type verification", im instanceof ImageFloat);
        
        // test image dimensions
        Assert.assertEquals("testing image width", imTest.sizeX(), im.sizeX());
        Assert.assertEquals("testing image heigth", imTest.sizeY(), im.sizeY());
        Assert.assertEquals("testing image depth", imTest.sizeZ(), im.sizeZ());
        
        if (!extension.equals(ImageFormat.PNG)) Assert.assertArrayEquals("Retrieve Image Resolution", new float[]{0.1f, 0.23f}, new float[]{(float)im.getScaleXY(), (float)im.getScaleZ()}, 0.001f);
        
        Assert.assertEquals("Retrieve pixel value", 2f, im.getPixel(1, 0, 2), 0.0001f);
        
    }
    
    @Test
    public void testIOView() {
        String title = "imTestShort";
        ImageShort imShort = new ImageShort(title, 5, 6, 7);
        imShort.setPixel(2, 3, 5, 3);
        imShort.setCalibration(0.1f, 0.23f);
        File folder = testFolder.newFolder("TestImages");
        ImageWriter.writeToFile(imShort, folder.getAbsolutePath(), null, ImageFormat.TIF);
        ImageReader reader = new ImageReader(folder.getAbsolutePath(), title, ImageFormat.TIF);
        BoundingBox bb=new SimpleBoundingBox(1, 2, 3, 4, 3, 5);
        ImageShort im = (ImageShort)reader.openImage(new ImageIOCoordinates(bb));
        reader.closeReader();
        BoundingBox retrieveBB = new SimpleBoundingBox(im);
        //logger.debug("bb: {}, retrieve: {}", bb, retrieveBB);
        Assert.assertTrue("Retrieve Image View: Dimensions", bb.sameBounds(retrieveBB));
        Assert.assertEquals("Retrieve pixel value", 3, im.getPixelInt(2-im.xMin(), 3-im.yMin(), 5-im.zMin()));
    }
    
    @Test
    public void testIOViewPNG() {
        String title = "imTestShort";
        ImageShort imShort = new ImageShort(title, 5, 6, 7);
        imShort.setPixel(2, 3, 5, 3);
        imShort.setCalibration(0.1f, 0.23f);
        File folder = testFolder.newFolder("TestImages");
        ImageWriter.writeToFile(imShort, folder.getAbsolutePath(), null, ImageFormat.PNG);
        ImageReader reader = new ImageReader(folder.getAbsolutePath(), title, ImageFormat.PNG);
        BoundingBox bb=new SimpleBoundingBox(1, 2, 3, 4, 3, 5);
        ImageShort im = (ImageShort)reader.openImage(new ImageIOCoordinates(bb));
        reader.closeReader();
        BoundingBox retrieveBB = new SimpleBoundingBox(im);
        Assert.assertTrue("Retrieve Image View: Dimensions", bb.sameDimensions(retrieveBB));
        Assert.assertEquals("Retrieve pixel value", 3, im.getPixelInt(2-im.xMin(), 3-im.yMin(), 5-im.zMin()));
    }
    
    @Test
    public void testWriteMultiple() {
        String title = "imageTestMultiple";
        ImageFormat format = ImageFormat.OMETIF;
        File folder = testFolder.newFolder("TestImages");
        //File folder = new File("/tmp");
        int timePoint = 3;
        int channel = 2;
        ImageByte[][] images = new ImageByte[timePoint][channel];
        for (int t = 0; t<timePoint; t++) {
            for (int c = 0; c<channel;c++) {
                images[t][c] = new ImageByte(title+"t"+t+"c"+c, 6, 5, 4);
                images[t][c].setPixel(t, c, c, 1);
            }
        }
        int timePoint2 = 2;
        int channel2 = 3;
        ImageByte[][] images2 = new ImageByte[timePoint2][channel2];
        for (int t = 0; t<timePoint2; t++) {
            for (int c = 0; c<channel2;c++) {
                images2[t][c] = new ImageByte(title+"t"+t+"c"+c, 6, 5, 4);
                images2[t][c].setPixel(t, c, c, 1);
            }
        }
        ImageWriter.writeToFile(folder.getAbsolutePath(), title, format, images, images2);
        ImageReader reader = new ImageReader(folder.getAbsolutePath(), title, format);
        assertEquals("Retrieve Image series", 2, reader.getSTCXYZNumbers().length);
        assertEquals("Retrieve Image time points", timePoint, reader.getSTCXYZNumbers()[0][0]);
        assertEquals("Retrieve Image channels", channel, reader.getSTCXYZNumbers()[0][1]);
        assertEquals("Retrieve Image time points serie 2", timePoint2, reader.getSTCXYZNumbers()[1][0]);
        assertEquals("Retrieve Image channel serie 2", channel2, reader.getSTCXYZNumbers()[1][1]);
        
        for (int t = 0; t<timePoint; t++) {
            for (int c = 0; c<channel;c++) {
                TestUtils.assertImage(images[t][c], (ImageByte)reader.openImage(new ImageIOCoordinates(0, c, t)), 0);
            }
        }
    }
    
    
}
