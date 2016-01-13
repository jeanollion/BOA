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
package boa.gui.imageInteraction;

import static boa.gui.GUI.logger;
import dataStructure.objects.MorphiumMasterDAO;
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
import java.awt.Rectangle;
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
import java.util.List;
import java.util.Map.Entry;

/**
 *
 * @author nasique
 */
public class IJImageWindowManager extends ImageWindowManager<ImagePlus> {
    
    public IJImageWindowManager(ImageObjectListener listener) {
        super(listener, new IJImageDisplayer());
    }
    
    /*@Override
    protected ImagePlus getImage(Image image) {
        return IJImageWrapper.getImagePlus(image);
    }*/
    
    @Override
    public void addMouseListener(final Image image) {
        final ImagePlus ip = displayer.getImage(image);
        final ImageCanvas canvas = ip.getCanvas();
        if (canvas==null) {
            logger.warn("image: {} could not be set interactive", image.getName());
            return;
        }
        canvas.addMouseListener(new MouseListener() {

            public void mouseClicked(MouseEvent e) {
                //logger.trace("mouseclicked");
            }

            public void mousePressed(MouseEvent e) {
                //logger.debug("mousepressed");
                
            }

            public void mouseReleased(MouseEvent e) {
                if (IJ.getToolName().equals("zoom") || IJ.getToolName().equals("hand")) return;
                boolean ctrl = (e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK;
                ImageObjectInterface i = getImageObjectInterface(image);
                if (i==null) {
                    logger.trace("no image interface found");
                    return;
                }
                Roi r = ip.getRoi();
                BoundingBox selection = null;
                if (r!=null) {
                    Rectangle rect = r.getBounds();
                    selection = new BoundingBox(rect.x, rect.x+rect.width, rect.y, rect.y+rect.height, ip.getSlice()-1, ip.getSlice());
                    if (selection.getSizeX()==0 && selection.getSizeY()==0) selection=null;
                }
                if (selection!=null) {
                    ArrayList<StructureObject> selectedObjects = new ArrayList<StructureObject>();
                    i.addClickedObjects(selection, selectedObjects);
                    logger.debug("selection: {}, number of objects: {}", selection, selectedObjects.size());
                    selectObjects(image, ctrl, selectedObjects);
                    listener.fireObjectSelected(selectedObjects, ctrl, i.isTimeImage());
                    if (ctrl) ip.deleteRoi();
                } else {
                    int x = e.getX();
                    int y = e.getY();
                    int offscreenX = canvas.offScreenX(x);
                    int offscreenY = canvas.offScreenY(y);
                    
                    //logger.trace("mousepressed: x={}, y={} ctrl: {}", offscreenX, offscreenY, ctrl);

                    StructureObject o = i.getClickedObject(offscreenX, offscreenY, ip.getSlice()-1);
                    ArrayList<StructureObject> selectedObjects = new ArrayList<StructureObject>(1);
                    selectedObjects.add(o);
                    selectObjects(image, ctrl, selectedObjects);
                    logger.trace("selected object: "+o);
                    listener.fireObjectSelected(selectedObjects, ctrl, i.isTimeImage());
                    
                }
                
            }

            public void mouseEntered(MouseEvent e) {
                //logger.trace("mouseentered");
            }

            public void mouseExited(MouseEvent e) {
                //logger.trace("mousexited");
            }
        });
    }

    /*@Override
    public void removeClickListener(Image image) {
        ImageCanvas canvas = image.getWindow().getCanvas();
        for (MouseListener l : canvas.getMouseListeners()) canvas.removeMouseListener(l);
    }*/

    @Override
    public void selectObjects(Image image, boolean addToCurrentSelection, List<StructureObject> selectedObjects) {
        ImagePlus ip;
        if (image==null) ip = displayer.getCurrentImage();
        else ip = displayer.getImage(image);
        if (ip==null) return;
        if (selectedObjects.isEmpty() || (selectedObjects.get(0)==null)) {
            if (!addToCurrentSelection) {
                if (ip.getOverlay()!=null) {
                    removeAllRois(ip.getOverlay(), false);
                    ip.updateAndDraw();
                }
            }
            return;
        }
        ImageObjectInterface i = getImageObjectInterface(image);
        if (i!=null) {
            Overlay overlay;
            
            if (ip.getOverlay()!=null) {
                overlay=ip.getOverlay();
                if (!addToCurrentSelection) removeAllRois(overlay, false);
            } else overlay=new Overlay();
            for (StructureObject o : selectedObjects) {
                if (o==null) continue;
                //logger.debug("getting mask of object: {}", o);
                for (Roi r : getRoi(o.getMask(), i.getObjectOffset(o), !i.is2D).values()) {
                    overlay.add(r);
                    logger.trace("add roi: "+r+ " of bounds : "+r.getBounds()+" to overlay");
                }
            }
            ip.setOverlay(overlay);
        }
    }
    
    @Override
    public void displayTrack(Image image, boolean addToCurrentSelectedTracks, ArrayList<StructureObject> track, Color color) {
        logger.trace("display selected track: image: {}, addToCurrentTracks: {}, track length: {} color: {}", image,addToCurrentSelectedTracks, track==null?"null":track.size(), color);
        ImagePlus ip;
        if (image==null) {
            ip = displayer.getCurrentImage();
            image = displayer.getImage(ip);
        } else ip = displayer.getImage(image);
        if (ip==null || image==null) {
            logger.warn("no displayed track image found");
            return;
        }
        if (track==null) {
            if (!addToCurrentSelectedTracks) {
                if (ip.getOverlay()!=null) {
                    removeAllRois(ip.getOverlay(), true);
                    ip.updateAndDraw();
                }
            }
            return;
        }
        ImageObjectInterface i = getImageObjectInterface(image);
        if (i instanceof TrackMask && ((TrackMask)i).parent.getTrackHead().equals(track.get(0).getParent().getTrackHead())) {
            if (i.getKey().childStructureIdx!=track.get(0).getStructureIdx()) i = super.imageObjectInterfaces.get(i.getKey().getKey(track.get(0).getStructureIdx()));
            TrackMask tm = (TrackMask)i;
            Overlay overlay;
            if (ip.getOverlay()!=null) {
                overlay=ip.getOverlay();
                if (!addToCurrentSelectedTracks) removeAllRois(overlay, true);
            } else overlay=new Overlay();
            for (int idx = 0; idx<=track.size(); ++idx) {
                StructureObject o1 = idx==0?track.get(0).getPrevious() : track.get(idx-1);
                if (o1==null) continue;
                StructureObject o2 = idx<track.size() ? track.get(idx) : o1.getNext();
                if (o2==null) continue;
                BoundingBox b1 = tm.getObjectOffset(o1);
                BoundingBox b2 = tm.getObjectOffset(o2);
                if (b1 ==null) logger.error("object not found: {}", o1);
                if (b2 ==null) logger.error("object not found: {}", o2);
                if (b1==null || b2==null) continue;
                Arrow arrow = new Arrow(b1.getXMean(), b1.getYMean(), b2.getXMean()-1, b2.getYMean());
                arrow.setStrokeColor(o2.hasTrackLinkError()?ImageWindowManager.trackErrorColor: o2.hasTrackLinkCorrection() ?ImageWindowManager.trackCorrectionColor : color);
                arrow.setStrokeWidth(trackArrowStrokeWidth);
                arrow.setHeadSize(trackArrowStrokeWidth*1.5);
                //if (o1.getNext()==o2) arrow.setDoubleHeaded(true);
                
                int zMin = Math.max(b1.getzMin(), b2.getzMin());
                int zMax = Math.min(b1.getzMax(), b2.getzMax());
                if (zMin==zMax) {
                    if (!i.is2D) arrow.setPosition(zMin+1);
                    overlay.add(arrow);
                    logger.trace("add arrow: {}", arrow);
                } else {
                    // TODO debug
                    //logger.error("Display Track error. objects: {} & {} bounds: {} & {}, image bounds: {} & {}", o1, o2, o1.getBounds(), o2.getBounds(), b1, b2);
                    //if (true) return;
                    if (zMin>zMax) {
                        int tmp = zMax;
                        zMax=zMin<(ip.getNSlices()-1)?zMin+1:zMin;
                        zMin=tmp>0?tmp-1:tmp;
                    }
                    for (int z = zMin; z <= zMax; ++z) {
                        Arrow dup = (Arrow)arrow.clone();
                        dup.setPosition(z+1);
                        overlay.add(dup);
                        logger.debug("add arrow (z): {}", arrow);
                    }
                }
            }
            ip.setOverlay(overlay);
            ip.updateAndDraw();
        } else logger.warn("image cannot display selected track: ImageObjectInterface null? {}, is Track? {}", i==null, i instanceof TrackMask);
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

    @Override
    public void unselectObjects(Image image) {
        //removeScrollListeners(image);
        ImagePlus ip = displayer.getImage(image);
        if (ip!=null) ip.setOverlay(null);
    }
    /**
     * 
     * @param mask
     * @param offset
     * @param is3D
     * @return maping of Roi to Z-slice (taking into account the provided offset)
     */
    public static HashMap<Integer, Roi> getRoi(ImageInteger mask, BoundingBox offset, boolean is3D) {
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
                Rectangle bds = roi.getBounds();
                roi.setLocation(bds.x+offset.getxMin(), bds.y+offset.getyMin());
                if (is3D) roi.setPosition(z+1+offset.getzMin());
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
