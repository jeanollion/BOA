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
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Arrow;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.OverlayLabels;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ImageProcessor;
import image.BoundingBox;
import image.IJImageWrapper;
import image.Image;
import image.ImageInteger;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Scrollbar;
import java.awt.event.ActionEvent;
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
        super(listener, new IJImageDisplayer());
    }
    
    @Override
    protected ImagePlus getImage(Image image) {
        return IJImageWrapper.getImagePlus(image);
    }
    
    @Override
    public void addMouseListener(final ImagePlus image) {
        final ImageCanvas canvas = image.getWindow().getCanvas();
        
        canvas.addMouseListener(new MouseListener() {

            public void mouseClicked(MouseEvent e) {
                //logger.trace("mouseclicked");
            }

            public void mousePressed(MouseEvent e) {
                int x = e.getX();
		int y = e.getY();
		int offscreenX = canvas.offScreenX(x);
		int offscreenY = canvas.offScreenY(y);
                boolean ctrl = (e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK;
                //logger.trace("mousepressed: x={}, y={} ctrl: {}", offscreenX, offscreenY, ctrl);
                ImageObjectInterface i = get(image);
                if (i!=null) {
                    StructureObject o = i.getClickedObject(offscreenX, offscreenY, image.getSlice()-1);
                    selectObjects(image, ctrl, o);
                    logger.trace("selected object: "+o);
                    listener.fireObjectSelected(o, i.isTimeImage());
                } else logger.trace("no image interface found");
            }

            public void mouseReleased(MouseEvent e) {
                //logger.trace("mousereleased");
            }

            public void mouseEntered(MouseEvent e) {
                //logger.trace("mouseentered");
            }

            public void mouseExited(MouseEvent e) {
                //logger.trace("mousexited");
            }
        });
    }

    @Override
    public void removeClickListener(ImagePlus image) {
        ImageCanvas canvas = image.getWindow().getCanvas();
        for (MouseListener l : canvas.getMouseListeners()) canvas.removeMouseListener(l);
    }

    @Override
    public void selectObjects(ImagePlus image, boolean addToCurrentSelection, StructureObject... selectedObjects) {
        if (image==null) image = getCurrentImage();
        if (image==null) return;
        if (selectedObjects==null || selectedObjects.length==0 || (selectedObjects.length==1 && selectedObjects[0]==null)) {
            if (!addToCurrentSelection) {
                if (image.getOverlay()!=null) {
                    removeAllRois(image.getOverlay(), false);
                    image.updateAndDraw();
                }
            }
            return;
        }
        ImageObjectInterface i = get(image);
        if (i!=null) {
            Overlay overlay;
            if (image.getOverlay()!=null) {
                overlay=image.getOverlay();
                if (!addToCurrentSelection) removeAllRois(overlay, false);
            } else overlay=new Overlay();
            for (StructureObject o : selectedObjects) {
                if (o==null) continue;
                for (Roi r : getRoi(o.getMask(), i.getObjectOffset(o)).values()) {
                    overlay.add(r);
                    logger.trace("add roi: "+r+ " of bounds : "+r.getBounds()+" to overlay");
                }
            }
            image.setOverlay(overlay);
        }
    }
    
    @Override
    public void displayTrack(ImagePlus image, boolean addToCurrentSelectedTracks, StructureObject[] track, Color color) {
        logger.trace("display selected track: image: {}, addToCurrentTracks: {}, track length: {} color: {}", image,addToCurrentSelectedTracks, track==null?0:track.length, color);
        if (image==null) image = getCurrentImage();
        if (image==null) return;
        if (track==null) {
            if (!addToCurrentSelectedTracks) {
                if (image.getOverlay()!=null) {
                    removeAllRois(image.getOverlay(), true);
                    image.updateAndDraw();
                }
            }
            return;
        }
        ImageObjectInterface i = get(image);
        if (i instanceof TrackMask && ((TrackMask)i).containsTrack(track[0])) {
            TrackMask tm = (TrackMask)i;
            Overlay overlay;
            if (image.getOverlay()!=null) {
                overlay=image.getOverlay();
                if (!addToCurrentSelectedTracks) removeAllRois(overlay, true);
            } else overlay=new Overlay();
            for (int idx = 0; idx<track.length-1; ++idx) {
                BoundingBox b1 = tm.getObjectOffset(track[idx]);
                BoundingBox b2 = tm.getObjectOffset(track[idx+1]);
                
                Arrow arrow = new Arrow(b1.getXMean(), b1.getYMean(), b2.getXMean(), b2.getYMean());
                arrow.setStrokeColor(color);
                arrow.setStrokeWidth(1);
                arrow.setHeadSize(2);
                if (track[idx].getNext()==track[idx+1]) arrow.setDoubleHeaded(true);
                
                int zMin = Math.max(b1.getzMin(), b2.getzMin());
                int zMax = Math.min(b1.getzMax(), b2.getzMax());
                if (zMin==zMax) {
                    arrow.setPosition(zMin+1);
                    overlay.add(arrow);
                    logger.trace("add arrow: {}", arrow);
                } else {
                    if (zMin>zMax) {
                        int tmp = zMax;
                        zMax=zMin<(image.getNSlices()-1)?zMin+1:zMin;
                        zMin=tmp>0?tmp-1:tmp;
                    }
                    for (int z = zMin; z <= zMax; ++z) {
                        Arrow dup = (Arrow)arrow.clone();
                        dup.setPosition(z+1);
                        overlay.add(dup);
                        logger.trace("add arrow (z): {}", arrow);
                    }
                }
            }
            image.setOverlay(overlay);
        }
    } 
    
    private static void removeAllRois(Overlay overlay, boolean arrows) {
        if (overlay==null) return;
        logger.trace("remove all rois.. arrows? {}", arrows);
        if (arrows) {
            for (Roi r : overlay.toArray()) if (r instanceof Arrow) overlay.remove(r);
        }
        else {
            for (Roi r : overlay.toArray()) if (!(r instanceof Arrow)) overlay.remove(r);
        }
    }
    
    private static ImagePlus getCurrentImage() {
        logger.trace("get current image: {}", WindowManager.getCurrentImage());
        return WindowManager.getCurrentImage();
    }

    @Override
    public void unselectObjects(ImagePlus image) {
        //removeScrollListeners(image);
        image.setOverlay(null);
    }
    
    private HashMap<Integer, Roi> getRoi(ImageInteger mask, BoundingBox offset) {
        HashMap<Integer, Roi> res = new HashMap<Integer, Roi>(mask.getSizeZ());
        ThresholdToSelection tts = new ThresholdToSelection();
        ImagePlus maskPlus = IJImageWrapper.getImagePlus(mask);
        tts.setup("", maskPlus);
        int maxLevel = ImageInteger.getMaxValue(mask, true);
        for (int z = 0; z<mask.getSizeZ(); ++z) {
            ImageProcessor ip = maskPlus.getStack().getProcessor(z+1);
            ip.setThreshold(1, maxLevel, ImageProcessor.NO_LUT_UPDATE);
            tts.run(ip);
            Roi roi = maskPlus.getRoi();
            if (roi!=null) {
                //roi.setPosition(z+1+mask.getOffsetZ());
                roi.setLocation(offset.getxMin(), offset.getyMin());
                roi.setPosition(z+1+offset.getzMin());
                //roi.setPosition(0, z+1+offset.getzMin(), 0);
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