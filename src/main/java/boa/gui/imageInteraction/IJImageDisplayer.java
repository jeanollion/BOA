/*
 * Copyright (C) 2015 nasique
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
package boa.gui.imageInteraction;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;
import image.IJImageWrapper;
import image.Image;
import static boa.gui.GUI.logger;
import i5d.Image5D;
import i5d.cal.ChannelDisplayProperties;
import i5d.gui.ChannelControl;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.ImageCanvas;
import ij.measure.Calibration;
import image.BoundingBox;
import image.ImageByte;
import image.ImageFloat;
import image.ImageShort;
import image.TypeConverter;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.ColorModel;
import java.util.HashMap;
import javax.swing.SwingUtilities;

/**
 *
 * @author jollion
 */
public class IJImageDisplayer implements ImageDisplayer<ImagePlus> {
    protected HashMap<Image, ImagePlus> displayedImages=new HashMap<Image, ImagePlus>();
    protected HashMap<ImagePlus, Image> displayedImagesInv=new HashMap<ImagePlus, Image>();
    @Override public ImagePlus showImage(Image image, double... displayRange) {
        /*if (IJ.getInstance()==null) {
            ij.ImageJ.main(new String[0]);
            //new ImageJ();
        }*/
        if (imageExistsButHasBeenClosed(image)) {
            displayedImagesInv.remove(displayedImages.get(image));
            displayedImages.remove(image);
        }
        ImagePlus ip = getImage(image);
        if (displayRange.length==0) displayRange = image.getMinAndMax(null);
        else if (displayRange.length==1) {
            double[] minAndMax = image.getMinAndMax(null);
            minAndMax[0]=displayRange[0];
            displayRange=minAndMax;
        } else if (displayRange.length>=2) {
            if (displayRange[1]<=displayRange[0]) {
                double[] minAndMax = image.getMinAndMax(null);
                displayRange[1] = minAndMax[1];
            }
        }
        ip.setDisplayRange(displayRange[0], displayRange[1]);
        //logger.debug("show image:w={}, h={}, disp: {}", ip.getWidth(), ip.getHeight(), displayRange);
        ip.show();
        if (displayRange.length>=3) zoom(ip, displayRange[2]);
        else zoom(ip, ImageDisplayer.zoomMagnitude);
        return ip;
    }
    
    public void flush() {
        for (ImagePlus ip : displayedImages.values()) if (ip.isVisible()) ip.close();
        displayedImages.clear();
        displayedImagesInv.clear();
        WindowManager.closeAllWindows(); // also close all opened windows
    }
    /*@Override public boolean isVisible(Image image) {
        return displayedImages.containsKey(image) && displayedImages.get(image).isVisible();
    }*/
    private boolean imageExistsButHasBeenClosed(Image image) {
        return displayedImages.get(image)!=null && displayedImages.get(image).getCanvas()==null;
    }
    
    private static void zoom(ImagePlus image, double magnitude) {
        Thread t = new Thread(new Runnable() { // invoke later -> if not, linux bug display, bad window size
            @Override
            public void run() {
                //Thread.sleep(10000);
                try {Thread.sleep(500);}
                catch(Exception e) {}
                ImageCanvas ic = image.getCanvas();
                if (ic==null) return;
                ic.zoom100Percent();
                //IJ.runPlugIn("ij.plugin.Zoom", null);
                if (magnitude > 1) {
                    for (int i = 0; i < (int) (magnitude + 0.5); i++) {
                        ic.zoomIn(image.getWidth() / 2, image.getHeight() / 2);
                    }
                } else if (magnitude > 0 && magnitude < 1) {
                    for (int i = 0; i < (int) (1 / magnitude + 0.5); i++) {
                        ic.zoomOut(image.getWidth() / 2, image.getHeight() / 2);
                    }
                }
                image.repaintWindow();
            }
        });
        SwingUtilities.invokeLater(t);
        /*t.start();
        try{t.join();}
        catch(Exception e) {}*/
        /*
        Dimension d = image.getWindow().getSize();
        //Dimension d = image.getCanvas().getSize();
        Rectangle max = GUI.getMaxWindowBounds();
        logger.debug("window size: {}, canvas size: {}, maxSize: {}", image.getWindow().getSize(), image.getCanvas().getSize(), max);
        if (d.width < image.getWidth()) d.width=Math.min(max.width, image.getWidth());
        if (d.height < image.getHeight()+40) d.height=Math.min(max.height, image.getHeight()+40);
        
        //image.getCanvas().setSize(d.width, d.height);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                image.getWindow().setSize(d.width, d.height+100);
                image.updateAndRepaintWindow();
                logger.debug("window size after set: {}, window shape: {}, canvas size: {}", image.getWindow().getSize(), image.getWindow().getShape(), image.getCanvas().getSize());
        
            }
        });
        */
       
        //image.updateAndDraw();
        //image.updateAndRepaintWindow();
        //image.getWindow().repaint();
        
    }
    
    @Override public ImagePlus getImage(Image image) {
        if (image==null) return null;
        ImagePlus ip = displayedImages.get(image);
        if (ip==null) {
            ip= IJImageWrapper.getImagePlus(image);
            displayedImages.put(image, ip);
            displayedImagesInv.put(ip, image);
        }
        return ip;
    }
    
    @Override public Image getImage(ImagePlus image) {
        if (image==null) return null;
        Image im = displayedImagesInv.get(image);
        if (im==null) {
            im= IJImageWrapper.wrap(image);
            displayedImagesInv.put(image, im);
            displayedImages.put(im, image);
        }
        return im;
    }
    
    

    /*public void showImage(ImagePlus image) {
        if (IJ.getInstance()==null) new ImageJ();
        StackStatistics s = new StackStatistics(image);
        logger.trace("display range: min: {} max: {}", s.min, s.max);
        image.setDisplayRange(s.min, s.max);
        image.show();
    }*/
    
    @Override public ImagePlus showImage5D(String title, Image[][] imageTC) {
        if (IJ.getInstance()==null) new ImageJ();
        /*Image5D res = new Image5D(title, getImageStack(imageTC), imageTC[0].length, imageTC[0][0].getSizeZ(), imageTC.length);
        for (int i = 0; i < imageTC[0].length; i++) {
            float[] minAndMax = imageTC[0][i].getMinAndMax(null);
            res.setChannelMinMax(i + 1, minAndMax[0], minAndMax[1]);
            res.setDefaultChannelNames();
        }*/
        /*for (int i = 0; i < images.length; i++) { // set colors of channels
            Color c = tango.gui.util.Colors.colors.get(tango.gui.util.Colors.colorNames[i + 1]);
            ColorModel cm = ChannelDisplayProperties.createModelFromColor(c);
            res.setChannelColorModel(i + 1, cm);
        }*/
        //res.setDisplayMode(ChannelControl.OVERLAY);
        //res.show();
        ImageStack stack = getImageStack(imageTC);
        ImagePlus ip = new ImagePlus();
        ip.setTitle(title);
        ip.setStack(stack, imageTC[0].length, imageTC[0][0].getSizeZ(), imageTC.length);
        ip.setOpenAsHyperStack(true);
        Calibration cal = new Calibration();
        cal.pixelWidth=imageTC[0][0].getScaleXY();
        cal.pixelHeight=imageTC[0][0].getScaleXY();
        cal.pixelDepth=imageTC[0][0].getScaleZ();
        ip.setCalibration(cal);
        ip.show();
        logger.debug("image: {}, isDisplayedAsHyperStack: {}, is HP: {}, dim: {}", title, ip.isDisplayedHyperStack(), ip.isHyperStack(), ip.getDimensions());
        return ip;
    }
    
    protected static ImageStack getImageStack(Image[][] imageTC) { // suppose same number of channel & sizeZ for all channels & times
        homogenizeBitDepth(imageTC);
        int sizeZ=imageTC[0][0].getSizeZ();
        int sizeC=imageTC[0].length;
        ImageStack is = new ImageStack(imageTC[0][0].getSizeX(), imageTC[0][0].getSizeY(), sizeZ * imageTC.length * sizeC);
        int count = 1;
        for (int z = 0; z < sizeZ; ++z) {
            for (int t = 0; t < imageTC.length; ++t) {
                for (int c = 0; c < imageTC[0].length; ++c) {
                    is.setPixels(imageTC[t][c].getPixelArray()[z], count++);
                }
            }
        }
        return is;
    }
    
    public static void homogenizeBitDepth(Image[][] images) {
        boolean shortIm = false;
        boolean floatIm = false;
        for (Image[] im : images) {
            for (Image i:im) {
                if (i instanceof ImageShort) {
                    shortIm = true;
                } else if (i instanceof ImageFloat) {
                    floatIm = true;
                }
            }
        }
        if (floatIm) {
            for (int i = 0; i < images.length; i++) {
                for (int j = 0; j<images[i].length; j++) {
                    if (images[i][j] instanceof ImageByte || images[i][j] instanceof ImageShort) {
                        images[i][j] = TypeConverter.toFloat(images[i][j], null);
                    }
                }
            }
        } else if (shortIm) {
            for (int i = 0; i < images.length; i++) {
                for (int j = 0; j<images[i].length; j++) {
                    if (images[i][j] instanceof ImageByte) {
                        images[i][j] = TypeConverter.toShort(images[i][j], null);
                    }
                }
            }
        }
    }

    @Override public void updateImageDisplay(Image image, double... displayRange) {
        if (this.displayedImages.containsKey(image)) {
            if (displayRange.length == 0) {
                displayRange = image.getMinAndMax(null);
            } else if (displayRange.length == 1) {
                double[] minAndMax = image.getMinAndMax(null);
                minAndMax[0] = displayRange[0];
                displayRange = minAndMax;
            } else if (displayRange.length >= 2) {
                if (displayRange[1] <= displayRange[0]) {
                    double[] minAndMax = image.getMinAndMax(null);
                    displayRange[1] = minAndMax[1];
                }
            }
            ImagePlus ip = displayedImages.get(image);
            ip.setDisplayRange(displayRange[0], displayRange[1]);
            ip.updateAndDraw();
        }
    }
    @Override public void updateImageRoiDisplay(Image image) {
        if (this.displayedImages.containsKey(image)) {
            ImagePlus ip = displayedImages.get(image);
            ip.updateAndDraw();
        }
    }

    /*public BoundingBox getImageDisplay(Image image) {
        ImagePlus im = image!=null ? this.getImage(image) : WindowManager.getCurrentImage();
        if (im==null) {
            logger.warn("no opened image");
            return null;
        }
        im.getCanvas().get
    }*/
    
    @Override
    public BoundingBox getDisplayRange(Image image) {
        ImagePlus ip = this.getImage(image);
        if (ip!=null) {
            Rectangle r = ip.getCanvas().getSrcRect();
            int z = ip.getCurrentSlice()-1;
            return new BoundingBox(r.x, r.x+r.width-1, r.y, r.y+r.height-1, z, z);
        } else return null;
    }
    
    @Override
    public void setDisplayRange(BoundingBox bounds, Image image) {
        ImagePlus ip = this.getImage(image);
        if (ip!=null) {
            Rectangle r = ip.getCanvas().getSrcRect();
            r.x=bounds.getxMin();
            r.y=bounds.getyMin();
            r.width=bounds.getSizeX();
            r.height=bounds.getSizeY();
            ip.setSlice(bounds.getzMin()+1);
            ip.draw();
            //ip.updateAndDraw();
            //ip.updateAndRepaintWindow();
            
        } 
    }
    @Override
    public ImagePlus getCurrentImage() {
        //logger.trace("get current image: {}", WindowManager.getCurrentImage());
        return WindowManager.getCurrentImage();
    }
    @Override
    public Image getCurrentImage2() {
       ImagePlus curr = getCurrentImage();
       return this.getImage(curr);
    }
    
    public int[] getFCZCount(ImagePlus image) {
        return new int[]{image.getNFrames(), image.getNChannels(), image.getNSlices()};
    }
    
    @Override
    public Image[][] getCurrentImageCT() {
        ImagePlus ip = this.getCurrentImage();
        Image image = this.getCurrentImage2();
        int[] FCZCount = getFCZCount(ip);
        return ImageDisplayer.reslice(image, FCZCount, IJImageWrapper.getStackIndexFunction());
    }
}
