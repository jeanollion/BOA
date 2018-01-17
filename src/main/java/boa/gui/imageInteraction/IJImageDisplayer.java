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
import core.DefaultWorker;
import i5d.Image5D;
import i5d.cal.ChannelDisplayProperties;
import i5d.gui.ChannelControl;
import ij.ImageStack;
import ij.VirtualStack;
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
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.ColorModel;
import java.util.HashMap;
import javax.swing.SwingUtilities;

/**
 *
 * @author jollion
 */
public class IJImageDisplayer implements ImageDisplayer<ImagePlus> {
    protected HashMap<Image, ImagePlus> displayedImages=new HashMap<>();
    protected HashMap<ImagePlus, Image> displayedImagesInv=new HashMap<>();
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
        if (displayRange.length==0) displayRange = ImageDisplayer.getDisplayRange(image, null);
        else if (displayRange.length==1) {
            double[] dispRange = ImageDisplayer.getDisplayRange(image, null);
            dispRange[0]=displayRange[0];
            displayRange=dispRange;
        } else if (displayRange.length>=2) {
            if (displayRange[1]<=displayRange[0]) {
                double[] dispRange = ImageDisplayer.getDisplayRange(image, null);
                displayRange[1] = dispRange[1];
            }
        }
        ip.setDisplayRange(displayRange[0], displayRange[1]);
        //logger.debug("show image:w={}, h={}, disp: {}", ip.getWidth(), ip.getHeight(), displayRange);
        if (!ip.isVisible()) ip.show();
        if (displayRange.length>=3) zoom(ip, displayRange[2]);
        else zoom(ip, ImageDisplayer.zoomMagnitude);
        ImageWindowManagerFactory.getImageManager().addLocalZoom(ip.getCanvas());
        return ip;
    }
    
    @Override
    public boolean isDisplayed(ImagePlus ip) {
        return ip!=null && ip.isVisible();
    }
    
    public void flush() {
        for (ImagePlus ip : displayedImages.values()) if (ip.isVisible()) ip.close();
        displayedImages.clear();
        displayedImagesInv.clear();
        WindowManager.closeAllWindows(); // also close all opened windows
    }
    @Override public void close(Image image) {
        ImagePlus imp = this.getImage(image);
        this.displayedImages.remove(image);
        if (imp!=null) {
            imp.close();
            this.displayedImagesInv.remove(imp);
        }
    }
    @Override public void close(ImagePlus image) {
        if (image==null) return;
        Image im = this.displayedImagesInv.remove(image);
        if (im!=null) this.displayedImages.remove(im);
        image.close();
    }
    /*@Override public boolean isVisible(Image image) {
        return displayedImages.containsKey(image) && displayedImages.get(image).isVisible();
    }*/
    private boolean imageExistsButHasBeenClosed(Image image) {
        return displayedImages.get(image)!=null && displayedImages.get(image).getCanvas()==null;
    }
    
    private static void zoom(ImagePlus image, double magnitude) {
        DefaultWorker.WorkerTask t= new DefaultWorker.WorkerTask() {

            @Override
            public String run(int workingTaskIndex) {
                ImageCanvas ic = image.getCanvas();
                if (ic==null) return "";
                if (ic.getMagnification()==magnitude) return "";
                try {Thread.sleep(500);} // TODO method that indicated if displayed ? 
                catch(Exception e) {}
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
                image.updateAndRepaintWindow();
                return "";
            }
        };
        DefaultWorker w = DefaultWorker.execute(t, 1, null);
        
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
        /*Image5D res = new Image5D(title, getImagePlus(imageTC), imageTC[0].length, imageTC[0][0].getSizeZ(), imageTC.length);
        for (int i = 0; i < imageTC[0].length; i++) {
            float[] dispRange = imageTC[0][i].getMinAndMax(null);
            res.setChannelMinMax(i + 1, dispRange[0], dispRange[1]);
            res.setDefaultChannelNames();
        }*/
        /*for (int i = 0; i < images.length; i++) { // set colors of channels
            Color c = tango.gui.util.Colors.colors.get(tango.gui.util.Colors.colorNames[i + 1]);
            ColorModel cm = ChannelDisplayProperties.createModelFromColor(c);
            res.setChannelColorModel(i + 1, cm);
        }*/
        //res.setDisplayMode(ChannelControl.OVERLAY);
        //res.show();
        ImagePlus ip = IJImageWrapper.getImagePlus(imageTC, -1);
        ip.setTitle(title);
        // TODO: set display range ?
        ip.show();
        ImageWindowManagerFactory.getImageManager().addLocalZoom(ip.getCanvas());
        logger.debug("image: {}, isDisplayedAsHyperStack: {}, is HP: {}, dim: {}", title, ip.isDisplayedHyperStack(), ip.isHyperStack(), ip.getDimensions());
        displayedImages.put(imageTC[0][0], ip);
        displayedImagesInv.put(ip, imageTC[0][0]);
        return ip;
    }
    
    @Override public void updateImageDisplay(Image image, double... displayRange) {
        if (this.displayedImages.containsKey(image)) {
            if (displayRange.length == 0) {
                displayRange = ImageDisplayer.getDisplayRange(image, null);
            } else if (displayRange.length == 1) {
                double[] minAndMax = ImageDisplayer.getDisplayRange(image, null);
                minAndMax[0] = displayRange[0];
                displayRange = minAndMax;
            }
            ImagePlus ip = displayedImages.get(image);
            synchronized(ip) {
                if (displayRange[0]<displayRange[1]) ip.setDisplayRange(displayRange[0], displayRange[1]);
                ip.updateAndRepaintWindow();
                ip.updateAndDraw();
            }
            
        }
    }
    @Override public void updateImageRoiDisplay(Image image) {
        if (this.displayedImages.containsKey(image)) {
            ImagePlus ip = displayedImages.get(image);
            synchronized(ip) {
                ip.updateAndDraw();
            }
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
        if (ip==null) return null;
        int[] FCZCount = getFCZCount(ip);
        return ImageDisplayer.reslice(IJImageWrapper.wrap(ip), FCZCount, IJImageWrapper.getStackIndexFunction(FCZCount));
    }
}
