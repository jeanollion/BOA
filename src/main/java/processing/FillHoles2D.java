/*
 * Copyright (C) 2016 jollion
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
package processing;

import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import ij.ImageStack;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import image.IJImageWrapper;
import image.ImageInteger;

/**
 *
 * @author jollion
 */
public class FillHoles2D {
    

// Binary fill by Gabriel Landini, G.Landini at bham.ac.uk
    // 21/May/2008

    public static void fillHoles(ObjectPopulation pop) {
        for (Object3D o : pop.getObjects()) {
            fillHoles(o.getMask(), 2);
            o.createVoxels();
        }
        pop.relabel(true);
    }
    
    public static void fillHoles(ImageInteger image, int midValue) {
        if (image.getSizeZ()==1) {
            fillHoles(IJImageWrapper.getImagePlus(image).getProcessor(), midValue);
        } else {
            ImageStack stack = IJImageWrapper.getImagePlus(image).getImageStack();
            for (int i = 1; i<=image.getSizeZ(); i++) { // TODO multithread
                fillHoles(stack.getProcessor(i), midValue);
            }
        }
    }
    
    protected static void fillHoles(ImageProcessor ip, int foreground, int background) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        FloodFiller ff = new FloodFiller(ip);
        ip.setColor(127); // intermediate color
        for (int y=0; y<height; y++) {
            if (ip.getPixel(0,y)==background) ff.fill(0, y);
            if (ip.getPixel(width-1,y)==background) ff.fill(width-1, y);
        }
        for (int x=0; x<width; x++){
            if (ip.getPixel(x,0)==background) ff.fill(x, 0);
            if (ip.getPixel(x,height-1)==background) ff.fill(x, height-1);
        }
        byte[] pix = (byte[])ip.getPixels();
        int n = width*height;
        for (int i=0; i<n; i++) {
        if (pix[i]==127)
            pix[i] = (byte)background;
        else
            pix[i] = (byte)foreground;
        }
    }
    
    protected static void fillHoles(ImageProcessor ip, int midValue) { // set foreground to 1
        int width = ip.getWidth();
        int height = ip.getHeight();
        FloodFiller ff = new FloodFiller(ip);
        ip.setColor(midValue); // intermediate color
        for (int y=0; y<height; y++) {
            if (ip.getPixel(0,y)==0) ff.fill(0, y);
            if (ip.getPixel(width-1,y)==0) ff.fill(width-1, y);
        }
        for (int x=0; x<width; x++){
            if (ip.getPixel(x,0)==0) ff.fill(x, 0);
            if (ip.getPixel(x,height-1)==0) ff.fill(x, height-1);
        }
        byte[] pix = (byte[])ip.getPixels();
        int n = width*height;
        for (int i=0; i<n; i++) {
            if (pix[i]==midValue) pix[i] = 0;
            else pix[i] = 1;
        }
    }
}
