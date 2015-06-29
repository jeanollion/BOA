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
import ij.ImageStack;
import ij.process.ImageProcessor;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;
import ome.units.quantity.Length;


/**
 *
 * @author jollion
 */
public class ImageReader {
    ImageProcessorReader reader;
    IMetadata meta;
    public ImageReader(String imagePath) {
        init(imagePath);
    }
    
    public ImageReader(String path, String imageTitle, WriteFormat extension) {
        System.out.println("path: "+path+File.separator+imageTitle+extension);
        init(path+File.separator+imageTitle+extension);
    }
    
    private void init(String imagePath) {
        reader = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
        ServiceFactory factory;
        try {
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            try {
                meta = service.createOMEXMLMetadata();
                reader.setMetadataStore(meta);
            } catch (ServiceException ex) {
                Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (DependencyException ex) {
            Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        try {
            reader.setId(imagePath);
        } catch (FormatException ex) {
            Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, "An error occurred while opering image: "+imagePath+" "+ex.getMessage(), ex);
        } catch (IOException ex) {
            Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, "An error occurred while opering image: "+imagePath+" "+ex.getMessage(), ex);
        }
    }
    
    public void closeReader() {
        try {
            reader.close();
        } catch (IOException ex) {
            Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
        }
    }
    
    public Image openChannel(ImageIOCoordinates coords) {
        Image res = null;
        
        reader.setSeries(coords.getSerie());
        int width = reader.getSizeX();
        int height = reader.getSizeY();
        int sizeZ = reader.getSizeZ();
        int zMin, zMax;
        if (coords.getBounds()!=null) {
            zMin=Math.max(coords.getBounds().getzMin(), 0);
            zMax=Math.min(coords.getBounds().getzMax(), sizeZ-1);
        } else {
            zMin=0; zMax=sizeZ-1;
        }
        ImageStack stack = new ImageStack(width, height);
        for (int z = zMin; z <= zMax; z++) {
            ImageProcessor ip;
            try {
                if (coords.getBounds()==null) {
                    ip = reader.openProcessors(reader.getIndex(z, coords.getChannel(), coords.getTimePoint()))[0];
                } else {
                    ip = reader.openProcessors(reader.getIndex(z, coords.getChannel(), coords.getTimePoint()), coords.getBounds().getxMin(), coords.getBounds().getyMin(), coords.getBounds().getSizeX(), coords.getBounds().getSizeY())[0];
                }
                stack.addSlice("" + (z + 1), ip);
                res = IJImageWrapper.wrap(new ImagePlus("", stack));
                if (coords.getBounds()!=null) res.setOffset(coords.getBounds().getxMin(), coords.getBounds().getyMin(), coords.getBounds().getzMin());
                if (meta != null) {
                    Length lxy = meta.getPixelsPhysicalSizeX(0);
                    Length lz = meta.getPixelsPhysicalSizeZ(0);
                    if (lxy != null && lz != null) {
                        res.setCalibration(lxy.value().floatValue(), lz.value().floatValue()); //xy.unit().getSymbol()
                    } else {
                        Logger.getLogger(ImageReader.class.getName()).log(Level.WARNING, "No calibration found for image: {0}", reader.getCurrentFile());
                    }
                }
            } catch (FormatException ex) {
                Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, "An error occurred while opering image: " + reader.getCurrentFile() + " channel:" + coords.getChannel() + " t:" + coords.getTimePoint() + " s:" + coords.getSerie() + ex.getMessage(), ex);
            } catch (IOException ex) {
                Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, "An error occurred while opering image: " + reader.getCurrentFile() + " channel:" + coords.getChannel() + " t:" + coords.getTimePoint() + " s:" + coords.getSerie() + ex.getMessage(), ex);
            }
        }
        return res;
    }
    
    public int[] getSTCNumbers() {
        int[] res = new int[3];
        res[0] = reader.getSeriesCount();
        res[1] = reader.getSizeT();
        res[2] = reader.getSizeC();
        return res;
    }
}
