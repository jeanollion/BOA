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
import ij.gui.ImageCanvas;
import image.BoundingBox;
import image.ImageByte;
import image.ImageFloat;
import image.ImageShort;
import image.TypeConverter;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.ColorModel;
import java.util.HashMap;

/**
 *
 * @author jollion
 */
public class IJImageDisplayer implements ImageDisplayer<ImagePlus> {
    protected HashMap<Image, ImagePlus> displayedImages=new HashMap<Image, ImagePlus>();
    protected HashMap<ImagePlus, Image> displayedImagesInv=new HashMap<ImagePlus, Image>();
    @Override public void showImage(Image image) {
        if (IJ.getInstance()==null) new ImageJ();
        if (imageExistButHasBeenClosed(image)) {
            displayedImagesInv.remove(displayedImages.get(image));
            displayedImages.remove(image);
        }
        ImagePlus ip = getImage(image);
        
        float[] minAndMax = image.getMinAndMax(null);
        ip.setDisplayRange(minAndMax[0], minAndMax[1]);
        ip.show();
        zoom(ip, ImageDisplayer.zoomMagnitude);
    }
    
    private boolean imageExistButHasBeenClosed(Image image) {
        return displayedImages.get(image)!=null && displayedImages.get(image).getCanvas()==null;
    }
    
    private static void zoom(ImagePlus image, double magnitude) {
        ImageCanvas ic = image.getCanvas();
        if (ic==null) return;
       ic.zoom100Percent();
        if (magnitude > 1) {
            for (int i = 0; i < (int) (magnitude + 0.5); i++) {
                ic.zoomIn(image.getWidth() / 2, image.getHeight() / 2);
            }
        } else if (magnitude > 0 && magnitude < 1) {
            for (int i = 0; i < (int) (1 / magnitude + 0.5); i++) {
                ic.zoomOut(image.getWidth() / 2, image.getHeight() / 2);
            }
        }
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
    
    public void showImage5D(String title, Image[][] imageTC) {
        if (IJ.getInstance()==null) new ImageJ();
        Image5D res = new Image5D(title, getImageStack(imageTC), imageTC[0].length, imageTC[0][0].getSizeZ(), imageTC.length);
        for (int i = 0; i < imageTC[0].length; i++) {
            float[] minAndMax = imageTC[0][i].getMinAndMax(null);
            res.setChannelMinMax(i + 1, minAndMax[0], minAndMax[1]);
            res.setDefaultChannelNames();
        }
        /*for (int i = 0; i < images.length; i++) { // set colors of channels
            Color c = tango.gui.util.Colors.colors.get(tango.gui.util.Colors.colorNames[i + 1]);
            ColorModel cm = ChannelDisplayProperties.createModelFromColor(c);
            res.setChannelColorModel(i + 1, cm);
        }*/
        res.setDisplayMode(ChannelControl.OVERLAY);
        res.show();
    }
    
    protected static ImageStack getImageStack(Image[][] imageTC) { // suppose same number of channel & sizeZ for all channels & times
        homogenizeBitDepth(imageTC);
        int sizeZ=imageTC[0][0].getSizeZ();
        int sizeC=imageTC[0].length;
        ImageStack is = new ImageStack(imageTC[0][0].getSizeX(), imageTC[0][0].getSizeY(), sizeZ * imageTC.length * sizeC);
        int count = 1;
        for (int z = 0; z < sizeZ; ++z) {
            for (int c = 0; c < imageTC[0].length; ++c) {
                for (int t = 0; t < imageTC.length; ++t) {
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

    @Override public void updateImageDisplay(Image image) {
        if (this.displayedImages.containsKey(image)) {
            float[] mm = image.getMinAndMax(null);
            ImagePlus ip = displayedImages.get(image);
            ip.setDisplayRange(mm[0], mm[1]);
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
}
