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

import static boa.gui.GUI.logger;
import dataStructure.objects.StructureObject;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ImageProcessor;
import image.IJImageWrapper;
import image.ImageInteger;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Container;
import java.awt.Scrollbar;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 *
 * @author nasique
 */
public class IJImageWindowManager extends ImageWindowManager<ImagePlus> {

    public IJImageWindowManager(ImageObjectListener listener) {
        super(listener);
    }
    
    @Override
    public void addClickListener(final ImagePlus image) {
        final ImageCanvas canvas = image.getWindow().getCanvas();
        canvas.addMouseListener(new MouseListener() {

            public void mouseClicked(MouseEvent e) {
                logger.trace("mouseclicked");
            }

            public void mousePressed(MouseEvent e) {
                int x = e.getX();
		int y = e.getY();
		int offscreenX = canvas.offScreenX(x);
		int offscreenY = canvas.offScreenY(y);
                logger.trace("mousepressed: x={}, y={}", offscreenX, offscreenY);
                ImageObjectInterface i = imageObjectInterfaceMap.get(image);
                if (i!=null) {
                    StructureObject o = i.getClickedObject(offscreenX, offscreenY, image.getSlice()-1);
                    listener.fireObjectSelected(o, i);
                } else logger.trace("no image interface found");
            }

            public void mouseReleased(MouseEvent e) {
                logger.trace("mousereleased");
            }

            public void mouseEntered(MouseEvent e) {
                logger.trace("mouseentered");
            }

            public void mouseExited(MouseEvent e) {
                logger.trace("mousexited");
            }
        });
    }

    @Override
    public void removeClickListener(ImagePlus image) {
        ImageCanvas canvas = image.getWindow().getCanvas();
        for (MouseListener l : canvas.getMouseListeners()) canvas.removeMouseListener(l);
    }

    @Override
    public void selectObjects(ImagePlus image, StructureObject... selectedObjects) {
        ImageObjectInterface i = this.imageObjectInterfaceMap.get(image);
        if (i!=null) {
            ArrayList<ImageInteger> masks = i.getSelectObjectMasksWithOffset(selectedObjects);
            HashMap<Integer, ArrayList<Roi>> rois = new HashMap<Integer, ArrayList<Roi>>();
            if (masks!=null) {
                for (ImageInteger mask : masks) {
                    for (Entry<Integer, Roi> e: getRoi(mask).entrySet()) {
                        ArrayList<Roi> roiSlice = rois.get(e.getKey());
                        if (roiSlice==null) {
                            roiSlice = new ArrayList<Roi>(selectedObjects.length);
                            rois.put(e.getKey(), roiSlice);
                        }
                        roiSlice.add(e.getValue());
                    }
                }
            }
            //HashMap<Integer, Overlay> imOverlays = new HashMap<Integer, Overlay>(rois.size());
            for (Entry<Integer, ArrayList<Roi>> e : rois.entrySet()) {
                Overlay overlay = new Overlay();
                //imOverlays.put(e.getKey(), overlay);
                for (Roi r : e.getValue()) overlay.add(r);
                if (image.getStack()!=null) {
                    image.getStack().getProcessor(e.getKey()+1).setOverlay(overlay);
                } else {
                    image.setOverlay(overlay);
                    if (rois.size()>1) logger.error("cannot display 3D overlay on single-sliced image");
                } 
            }
            //addScrollListener(image, imOverlays);
        }
    }

    @Override
    public void unselectObjects(ImagePlus image) {
        //removeScrollListeners(image);
        if (image.getStack()!=null) {
            for (int z = 1; z<=image.getNSlices(); ++z) image.getStack().getProcessor(z).setOverlay(null);
        } else {
            image.setOverlay(null);
        }
    }
    
    private HashMap<Integer, Roi> getRoi(ImageInteger mask) {
        HashMap<Integer, Roi> res = new HashMap<Integer, Roi>(mask.getSizeZ());
        ThresholdToSelection tts = new ThresholdToSelection();
        ImagePlus maskPlus = IJImageWrapper.getImagePlus(mask);
        tts.setup("", maskPlus);
        int maxLevel = ImageInteger.getMaxValue(mask, true);
        for (int z = 0; z<=mask.getSizeZ(); ++z) {
            ImageProcessor ip = maskPlus.getStack().getProcessor(z+1);
            ip.setThreshold(1, maxLevel, ImageProcessor.NO_LUT_UPDATE);
            tts.run(ip);
            Roi roi = maskPlus.getRoi();
            if (roi!=null) {
                //roi.setPosition(z+1+mask.getOffsetZ());
                roi.setLocation(mask.getOffsetX(), mask.getOffsetZ());
                res.put(z+mask.getOffsetZ(), roi);
            }
        }
        return res;
    }
    
    
    private static void addScrollListener(final ImagePlus img, final HashMap<Integer, Overlay> overlays) {
        AdjustmentListener al = new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                img.setOverlay(overlays.get(img.getSlice() - 1));
                img.updateAndDraw();
            }
        };
        for (Component c : img.getWindow().getComponents()) {
            if (c instanceof Scrollbar) ((Scrollbar)c).addAdjustmentListener(al);
            else if (c instanceof Container) {
                for (Component c2 : ((Container)c).getComponents()) {
                    if (c2 instanceof Scrollbar) ((Scrollbar)c2).addAdjustmentListener(al);
                }
            }
        }
        img.getWindow().addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                img.setOverlay(overlays.get(img.getSlice() - 1));
                img.updateAndDraw();
            }
        });
    }

    private static void removeScrollListeners(ImagePlus img) {
        for (Component c : img.getWindow().getComponents()) {
            if (c instanceof Scrollbar) removeAdjustmentListener(((Scrollbar)c));
            else if (c instanceof Container) {
                for (Component c2 : ((Container)c).getComponents()) {
                    if (c2 instanceof Scrollbar) removeAdjustmentListener(((Scrollbar)c2));
                }
            }
        }
        removeMouseWheelListener(img.getWindow());
    }
    
    private static void removeAdjustmentListener(Scrollbar s) {for (AdjustmentListener l : s.getAdjustmentListeners()) s.removeAdjustmentListener(l);}
    private static void removeMouseWheelListener(ImageWindow w) {for (MouseWheelListener l : w.getMouseWheelListeners()) w.removeMouseWheelListener(l);}
    
    
}
