/*
 * Copyright (C) 2018 jollion
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
package boa.gui.imageInteraction.localZoom;

import ij.gui.ImageWindow;
import ij.process.FloatProcessor;
import image.Histogram;
import image.Image;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 *
 * @author jollion
 */
public class ImageZoom extends JFrame {
    final ImageIcon icon;
    final JLabel lbl=new JLabel();
    final public int size, pixSize;
    final Graphics2D graphics;
    BufferedImage dispImage, buf;
    IndexColorModel cm = makeColoModel();
    public ImageZoom() {
        super();
        this.setAlwaysOnTop(true);
        this.setUndecorated(true);
        
        this.setLayout(new FlowLayout());
        size=151;
        pixSize=25;
        this.setSize(size, size);
        dispImage = new BufferedImage(size , size, BufferedImage.TYPE_INT_RGB);
        buf = new BufferedImage(pixSize , pixSize, BufferedImage.TYPE_INT_RGB);
        graphics = dispImage.createGraphics();
        icon = new ImageIcon(dispImage);
        lbl.setIcon(icon);
        lbl.setBounds(0, 0, size, size);
        add(lbl);
    }
    Image currentImage;
    Histogram histo;
    ImageWindow comp;
    public void setImage(Image image) {
        if (image.equals(currentImage)) return;
        currentImage = image;
        histo = image.getHisto256(null);
        // todo reset color model ? 
    }
    public void setContent(int x, int y, int z) {
        int offX = x-pixSize/2-1;
        int offY = y-pixSize/2-1;
        
        for (int xx = offX; xx<x+pixSize/2; ++xx) {
            for (int yy = offY; yy<y+pixSize/2; ++yy) {
                if (currentImage.contains(xx, yy, z)) {
                    byte value = (byte)histo.getIdxFromValue(currentImage.getPixel(xx, yy, z));
                    buf.setRGB(xx-offX, yy-offY, value * 0x00010101); // todo use color model!!
                }
                else buf.setRGB(xx-offX, yy-offY, 0);
            }
        }
        int cent = pixSize/2;
        for (int xx = 0; xx<pixSize; ++xx) if (Math.abs(xx-cent)>1) buf.setRGB(xx, cent, 255);
        for (int yy = 0; yy<pixSize; ++yy) if (Math.abs(yy-cent)>1) buf.setRGB(cent, yy, 255);
        graphics.drawImage(buf, 0, 0, size, size, null);
        lbl.repaint();
    }

    
    // todo get from current image
    public IndexColorModel makeColoModel() {
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        for(int i=0; i<256; i++) {
                r[i]=(byte)i;
                g[i]=(byte)i;
                b[i]=(byte)i;
        }
        return new IndexColorModel(8, 256, r, g, b);
    }
}
