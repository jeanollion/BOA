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

import static dataStructure.containers.ImportImageUtils.paseDVLogFile;
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
import static image.Image.logger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import loci.formats.IFormatReader;
import org.joda.time.DateTimeZone;

/**
 *
 * @author jollion
 */
public class ImageReader {
    ImageFormat extension;
    String path;
    String imageTitle;
    private String fullPath;
    List<Double> timePoints;
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
        initReader();
    }
    
    public ImageReader(String fullPath) {
        setFullPath(fullPath);
        initReader();
    }
    private void setFullPath(String fullPath) {
        File f= new File(fullPath);
        path  = f.getParent();
        imageTitle = f.getName();
        int extIdx = imageTitle.indexOf(".");
        if (extIdx<=0) extIdx=imageTitle.length()-1;
        else extension=ImageFormat.getExtension(f.getName().substring(extIdx));
        imageTitle = f.getName().substring(0, extIdx);
        if (extension==null) {
            this.fullPath=fullPath;
            invertTZ=false;
            supportView=true;
        } else {
            invertTZ=extension.getInvertTZ();
            this.supportView=extension.getSupportView();
        }
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
    //loci.formats.ImageReader ifr;
    private void initReader() {
        if (!new File(getImagePath()).exists()) logger.error("File: {} was not found", getImagePath());
        //logger.debug("init reader: {}", getImagePath());
        //ifr = LociPrefs.makeImageReader();
        reader = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
        
        ServiceFactory factory;
        try {
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            try {
                meta = service.createOMEXMLMetadata();
                reader.setMetadataStore(meta);
                //logTimeAnnotations();
            } catch (ServiceException ex) {
                logger.error(ex.getMessage(), ex);
            }
        } catch (DependencyException ex) {
            logger.error(ex.getMessage(), ex);
        }
        setId();
    }
    private void setId() {
        try {
            reader.setId(getImagePath());
            //logger.debug("reader: {}", ifr.getReader().getClass());
        } catch (FormatException | IOException ex) {
            logger.error("An error occurred while setting image id: {}, message: {}", getImagePath(),  ex.getMessage());
            reader=null;
        }
    }
    private void setFile(String fullPath) {
        setFullPath(fullPath);
        setId();
    }
    
    public void closeReader() {
        if (reader==null) return;
        try {
            reader.close();
        } catch (IOException ex) {
            logger.error("An error occurred while closing reader for image: "+getImagePath(),  ex);
        }
    }
    
    public Image openChannel() {
        return openImage(new ImageIOCoordinates());
    }
    public Image openImage(ImageIOCoordinates coords) {
        if (reader==null) return null;
        Image res = null;
        /*if (reader==null && extension==ImageFormat.TIF) { // try IJ's method
            res = ImageReader.openIJTif(fullPath);
            if (coords.getBounds()!=null) res = res.crop(coords.getBounds());
            return res;
        }*/
        reader.setSeries(coords.getSerie());
        int sizeX = reader.getSizeX();
        int sizeY = reader.getSizeY();
        int sizeZ = invertTZ?reader.getSizeT():reader.getSizeZ();
        //if (coords.getBounds()!=null) coords.getBounds().trimToImage(new BlankMask("", sizeX, sizeY, sizeZ));
        
        int zMin, zMax;
        if (coords.getBounds()!=null) {
            zMin=Math.max(coords.getBounds().getzMin(), 0);
            zMax=Math.min(coords.getBounds().getzMax(), sizeZ-1);
            if (zMin>zMax) {zMin=0; zMax=sizeZ-1;}
            if (this.supportView) {
                sizeX = coords.getBounds().getSizeX();
                sizeY = coords.getBounds().getSizeY();
            }
        } else {
            zMin=0; zMax=sizeZ-1;
        }
        //logger.debug("open image: {}, sizeX: {}, sizeY: {}, sizeZ: {}, zMin: {}, zMax: {}", this.getImagePath(), sizeX, sizeY, sizeZ, zMin, zMax);
        ImageStack stack = new ImageStack(sizeX, sizeY);
        for (int z = zMin; z <= zMax; z++) {
            int idx = getIndex(coords.getChannel(), coords.getTimePoint(), z);
            ImageProcessor ip;
            try {
                if (coords.getBounds()==null || !supportView) {
                    ip = reader.openProcessors(idx)[0];
                } else {
                    ip = reader.openProcessors(idx, coords.getBounds().getxMin(), coords.getBounds().getyMin(), coords.getBounds().getSizeX(), coords.getBounds().getSizeY())[0];
                }
                stack.addSlice("" + (z + 1), ip);
                res = IJImageWrapper.wrap(new ImagePlus("", stack));
                if (!supportView && coords.getBounds()!=null) { // crop
                    BoundingBox bounds = coords.getBounds().duplicate();
                    bounds.zMin=0;
                    bounds.zMax=res.sizeZ-1;
                    res=res.crop(bounds);
                }
                if (coords.getBounds()!=null) res.resetOffset().addOffset(coords.getBounds());
                double[] scaleXYZ = getScaleXYZ(1);
                if (scaleXYZ[0]!=1) res.setCalibration((float)scaleXYZ[0], (float)scaleXYZ[2]);
            } catch (FormatException | IOException ex) {
                logger.error("An error occurred while opening image: {}, c:{}, t:{}, s:{}, message: {}", reader.getCurrentFile() , coords.getChannel() , coords.getTimePoint(), coords.getSerie(), ex.getMessage());
            }
        }
        return res;
    }
    
    private int getIndex(int c, int t, int z) {
        return invertTZ ? reader.getIndex(t, c, z) : reader.getIndex(z, c, t);
    }
    
    public double[] getScaleXYZ(double defaultValue) {
        double[] res = new double[3];
        Arrays.fill(res, defaultValue);
        if (meta != null) {
            try {
                Length lx = meta.getPixelsPhysicalSizeX(0);
                Length ly = meta.getPixelsPhysicalSizeY(0);
                Length lz = meta.getPixelsPhysicalSizeZ(0);
                if (lx!=null) res[0] = lx.value().doubleValue();
                if (ly!=null) res[1] = ly.value().doubleValue();
                if (lz!=null) res[2] = lz.value().doubleValue();
                
            } catch(Exception e) {}
        } 
        //logger.debug("image: {} calibration: {}", this.fullPath, res);
        return res;
    }
    //static boolean logeed = false;
    public double getTimePoint(int c, int t, int z) {
        if (timePoints==null) {
            synchronized(this) {
                if (timePoints==null) {
                    if (this.extension == ImageFormat.DV) { // look for log file
                        int deconvIdx = getImagePath().indexOf("_D3D");
                        String logPath = deconvIdx>0 ? getImagePath().substring(0, deconvIdx)+".dv.log" : getImagePath()+".log";
                        timePoints = paseDVLogFile(logPath, "Time Point: ");
                        //logger.debug("timePoints: {}", timePoints);
                    } else {
                        // not supported
                    }
                }
            }
        }
        if (timePoints!=null) {
            //logger.debug("timePoints: {}", timePoints);
            return timePoints.get(getIndex(c, t, z));
        } else return Double.NaN;
    }
    
    public void logTimeAnnotations() {
        if (meta!=null) {
            logger.debug("image count: {}", meta.getImageCount()); 
            for (int i = 0; i<meta.getImageCount(); ++i) {
                logger.debug("i:{}, time: {}, {} {}", i, meta.getImageAcquisitionDate(i), meta.getImageAcquisitionDate(i)==null? "":meta.getImageAcquisitionDate(i).asDateTime(DateTimeZone.UTC), meta.getImageAcquisitionDate(i)==null? "":meta.getImageAcquisitionDate(i).asInstant());
            }
            int c = meta.getTimestampAnnotationCount();
            for (int i = 0; i<c; ++c) {
                logger.debug("time: i={}, time: {}({}/{}), ns={}, id={}, desc={}, annotator={}", i, meta.getTimestampAnnotationValue(i), meta.getTimestampAnnotationValue(i)==null? "":meta.getTimestampAnnotationValue(i).asDateTime(DateTimeZone.UTC), meta.getTimestampAnnotationValue(i), meta.getTimestampAnnotationValue(i)==null? "":meta.getTimestampAnnotationValue(i).asInstant(), meta.getTimestampAnnotationNamespace(i), meta.getTimestampAnnotationID(i), meta.getTimestampAnnotationDescription(i), meta.getTimestampAnnotationAnnotator(i));
                
                int cc = meta.getTimestampAnnotationAnnotationCount(i);
                for (int ii = 0; ii<cc; ++ii) {
                    logger.debug("time: i={}, ref.idx={}, ref={}", i, ii, meta.getTimestampAnnotationAnnotationRef(i, ii));
                }
            }
        }
        
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

    /**
     * 
     * @return dimensions of the image: first dimension of the matrix: series, second dimension: dimensions of the images of the serie: 0=timePoint number, 1 = channel number, 2=sizeX, 3=sizeY, 4=sizeZ
     */
    public int[][] getSTCXYZNumbers() {
        if (reader==null) return new int[0][5];
        int[][] res = new int[reader.getSeriesCount()][5];
        for (int i = 0; i<res.length; i++) {
            reader.setSeries(i);
            res[i][0] = invertTZ?reader.getSizeZ():reader.getSizeT();
            res[i][1] = reader.getSizeC();
            res[i][2]=reader.getSizeX();
            res[i][3]=reader.getSizeY();
            res[i][4]=reader.getSizeZ();
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
    
    public static Image openIJTif(String filePath) { // TODO : 3D files open only some planes
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
            logger.error("An error occured while opening tif image", ex);
        }

        return null;
    }
}
