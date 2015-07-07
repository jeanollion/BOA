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
import ij.io.FileInfo;
import ij.io.FileOpener;
import ij.io.Opener;
import ij.io.TiffDecoder;
import ij.process.ImageProcessor;
import static image.ImageFormat.*;
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
    ImageFormat extension;
    String path;
    String imageTitle;
    String fullPath;
    
    //BioFormats
    ImageProcessorReader reader;
    IMetadata meta;
    boolean invertTZ;
    boolean supportView;
    
    public ImageReader(String path, String imageTitle, ImageFormat extension) {
        this.extension=extension;
        this.path=path;
        this.imageTitle=imageTitle;
        this.invertTZ=extension.getInvertTZ();
        this.supportView=extension.getSupportView();
        //System.out.println("path: "+path+File.separator+imageTitle+extension);
        init();
    }
    
    public ImageReader(String fullPath) {
        File f= new File(fullPath);
        path  = f.getParent();
        imageTitle = f.getName();
        int extIdx = imageTitle.indexOf(".");
        imageTitle = f.getName().substring(0, extIdx);
        this.extension=ImageFormat.getExtension(f.getName().substring(extIdx));
        if (extension==null) {
            this.fullPath=fullPath;
            invertTZ=false;
            supportView=true;
        } else {
            invertTZ=extension.getInvertTZ();
            this.supportView=extension.getSupportView();
        }
        init();
    }

    public ImageFormat getExtension() {
        return extension;
    }

    public String getPath() {
        return path;
    }

    public String getImageTitle() {
        return imageTitle;
    }
    
    public String getImagePath() {
        if (fullPath!=null) return fullPath;
        else return path+File.separator+imageTitle+extension;
    }
    
    private void init() {
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
            reader.setId(getImagePath());
        } catch (FormatException ex) {
            Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, "An error occurred while opering image: "+getImagePath()+" "+ex.getMessage(), ex);
        } catch (IOException ex) {
            Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, "An error occurred while opering image: "+getImagePath()+" "+ex.getMessage(), ex);
        }
    }
    
    
    public void closeReader() {
        if (reader==null) return;
        try {
            reader.close();
        } catch (IOException ex) {
            Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
        }
    }
    public Image openChannel() {
        return openImage(new ImageIOCoordinates());
    }
    
    public Image openImage(ImageIOCoordinates coords) {
        Image res = null;
        reader.setSeries(coords.getSerie());
        int sizeX = reader.getSizeX();
        int sizeY = reader.getSizeY();
        int sizeZ = invertTZ?reader.getSizeT():reader.getSizeZ();
        //if (coords.getBounds()!=null) coords.getBounds().trimToImage(new BlankMask("", sizeX, sizeY, sizeZ));
        
        int zMin, zMax;
        if (coords.getBounds()!=null) {
            zMin=Math.max(coords.getBounds().getzMin(), 0);
            zMax=Math.min(coords.getBounds().getzMax(), sizeZ-1);
            if (this.supportView) {
                sizeX = coords.getBounds().getSizeX();
                sizeY = coords.getBounds().getSizeY();
            }
            
        } else {
            zMin=0; zMax=sizeZ-1;
        }
        ImageStack stack = new ImageStack(sizeX, sizeY);
        for (int z = zMin; z <= zMax; z++) {
            int locZ = invertTZ?coords.getTimePoint():z;
            int locT = invertTZ?z:coords.getTimePoint();
            ImageProcessor ip;
            try {
                if (coords.getBounds()==null || PNG.equals(extension)) {
                    ip = reader.openProcessors(reader.getIndex(locZ, coords.getChannel(), locT))[0];
                    
                } else {
                    ip = reader.openProcessors(reader.getIndex(locZ, coords.getChannel(), locT), coords.getBounds().getxMin(), coords.getBounds().getyMin(), coords.getBounds().getSizeX(), coords.getBounds().getSizeY())[0];
                }
                stack.addSlice("" + (z + 1), ip);
                res = IJImageWrapper.wrap(new ImagePlus("", stack));
                if (!supportView && coords.getBounds()!=null) { // crop
                    BoundingBox bounds = coords.getBounds().duplicate();
                    bounds.zMin=0;
                    bounds.zMax=res.sizeZ-1;
                    res=res.crop(bounds);
                }
                if (coords.getBounds()!=null) res.setOffset(coords.getBounds().getxMin(), coords.getBounds().getyMin(), coords.getBounds().getzMin());
                if (meta != null) {
                    Length lxy = meta.getPixelsPhysicalSizeX(0);
                    Length lz = meta.getPixelsPhysicalSizeZ(0);
                    float scaleXY=0, scaleZ=0;
                    
                    if (lxy!=null) {
                        if (lz==null) {
                            lz=lxy;
                            if (res.getSizeZ()>1) {
                                Logger.getLogger(ImageReader.class.getName()).log(Level.WARNING, "No calibration in Z dimension found for image: {0}", reader.getCurrentFile());
                            }
                        }
                        if (scaleXY==0) scaleXY=lxy.value().floatValue();
                        if (scaleZ==0) scaleZ=lz.value().floatValue();
                    }
                    if (scaleXY!=0 && scaleZ!=0) res.setCalibration(scaleXY, scaleZ);
                }
            } catch (FormatException ex) {
                Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, "An error occurred while opering image: " + reader.getCurrentFile() + " channel:" + coords.getChannel() + " t:" + coords.getTimePoint() + " s:" + coords.getSerie() + ex.getMessage(), ex);
            } catch (IOException ex) {
                Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, "An error occurred while opering image: " + reader.getCurrentFile() + " channel:" + coords.getChannel() + " t:" + coords.getTimePoint() + " s:" + coords.getSerie() + ex.getMessage(), ex);
            }
        }
        return res;
    }
    
    /*private float[] getTifCalibrationIJ() {
        try {
            TiffDecoder td = new TiffDecoder(path, this.imageTitle + extension);
            FileInfo[] info = td.getTiffInfo();
            if (info[0].pixelWidth > 0) {
                new FileOpener(info[0]).decodeDescriptionString(info[0]);
                float[] res = new float[]{(float) info[0].pixelWidth, (float)info[0].pixelDepth};
                System.out.println("calibration IJ: xy:" + res[0] + " z:" + res[1]);
                return res;
            } else {
                return new float[]{1, 1};
            }
        } catch (IOException ex) {
            Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, null, ex);
            return new float[]{1, 1};
        }
    }*/

    
    public int[][] getSTCNumbers() {
        int[][] res = new int[reader.getSeriesCount()][2];
        for (int i = 0; i<res.length; i++) {
            reader.setSeries(i);
            res[i][0] = invertTZ?reader.getSizeZ():reader.getSizeT();
            res[i][1] = reader.getSizeC();
        }
        return res;
    }
    
    
    public static Image openImage(String filePath) {
        return ImageReader.openImage(filePath, new ImageIOCoordinates());
    }
    
    public static Image openImage(String filePath, ImageIOCoordinates ioCoords) {
        ImageReader reader = new ImageReader(filePath);
        Image im = reader.openImage(ioCoords);
        reader.closeReader();
        return im;
    }
    
    public static Image openIJTif(String filePath) {
        File file = new File(filePath);
        TiffDecoder td = new TiffDecoder(file.getParent(), file.getName());

        FileInfo[] info = null;
        
        try {
            info = td.getTiffInfo();
            ImagePlus imp = null;
            //System.out.println("opening file: depth:"+info.length+ " info0:"+info[0].toString());
            if (info.length > 1) { // try to open as stack
                Opener o = new Opener();
                o.setSilentMode(true);
                imp = o.openTiffStack(info);

            } else {
                Opener o = new Opener();
                imp = o.openTiff(file.getParent(), file.getName());
            }
            imp.setTitle(file.getName());
            if (imp != null) {
                Image im = IJImageWrapper.wrap(imp);
                if (info[0].pixelWidth>0.0) {
                    im.setCalibration((float)info[0].pixelWidth, info[0].pixelDepth>0.0?(float)info[0].pixelDepth:(float)info[0].pixelWidth);
                }
                return im;
            }
        } catch (IOException ex) {
            Logger.getLogger(ImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }
}
