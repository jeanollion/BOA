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
import boa.gui.imageInteraction.IJImageWindowManager.Roi3D;
import boa.gui.imageInteraction.IJImageWindowManager.TrackRoi;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Arrow;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.PointRoi;
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
import java.awt.Polygon;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author nasique
 */
public class IJImageWindowManager extends ImageWindowManager<ImagePlus, Roi3D, TrackRoi> {
    
           
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
                boolean alt = (e.getModifiers() & ActionEvent.ALT_MASK) == ActionEvent.ALT_MASK;
                logger.debug("ctrl: {} alt: {}", ctrl, alt);
                ImageObjectInterface i = getImageObjectInterface(image);
                if (i==null) {
                    logger.trace("no image interface found");
                    return;
                }
                if (!ctrl) {
                    hideLabileObjects(image);
                    hideLabileTracks(image);
                }
                Roi r = ip.getRoi();
                BoundingBox selection = null;
                if (r!=null && r.getType()==Roi.RECTANGLE) {
                    Rectangle rect = r.getBounds();
                    selection = new BoundingBox(rect.x, rect.x+rect.width, rect.y, rect.y+rect.height, ip.getSlice()-1, ip.getSlice());
                    if (selection.getSizeX()==0 && selection.getSizeY()==0) selection=null;
                }
                if (selection!=null) {
                    ArrayList<Pair<StructureObject, BoundingBox>> selectedObjects = new ArrayList<Pair<StructureObject, BoundingBox>>();
                    i.addClickedObjects(selection, selectedObjects);
                    logger.debug("selection: {}, number of objects: {}", selection, selectedObjects.size());
                    if (!alt) {
                        displayObjects(image, selectedObjects, ImageWindowManager.defaultRoiColor, true);
                        if (listener!=null) listener.fireObjectSelected(Pair.unpair(selectedObjects), true);
                    } else {
                        List<StructureObject> trackHeads = new ArrayList<StructureObject>();
                        for (Pair<StructureObject, BoundingBox> p : selectedObjects) trackHeads.add(p.key.getTrackHead());
                        Utils.removeDuplicates(trackHeads, false);
                        for (StructureObject th : trackHeads) {
                            List<StructureObject> track = StructureObjectUtils.getTrack(th, true);
                            displayTrack(image, i, i.pairWithOffset(track), ImageWindowManager.defaultRoiColor, true);
                        }
                        if (listener!=null) listener.fireTracksSelected(trackHeads, true);
                    }
                    if (ctrl) ip.deleteRoi();
                } else {
                    int x = e.getX();
                    int y = e.getY();
                    int offscreenX = canvas.offScreenX(x);
                    int offscreenY = canvas.offScreenY(y);
                    Pair<StructureObject, BoundingBox> o = i.getClickedObject(offscreenX, offscreenY, ip.getSlice()-1);
                    logger.debug("click {}, {}, object: {}, ctlr:{}", x, y, o, ctrl);
                    ArrayList<Pair<StructureObject, BoundingBox>> selectedObjects = new ArrayList<Pair<StructureObject, BoundingBox>>(1);
                    if (o!=null) {
                        selectedObjects.add(o);
                        logger.debug("selected object: "+o.key);
                    } else return;
                    if (!alt) {
                        displayObjects(image, selectedObjects, ImageWindowManager.defaultRoiColor, true);
                        if (listener!=null) listener.fireObjectSelected(Pair.unpair(selectedObjects), true);
                    }
                    else {
                        List<StructureObject> trackHeads = new ArrayList<StructureObject>();
                        for (Pair<StructureObject, BoundingBox> p : selectedObjects) trackHeads.add(p.key.getTrackHead());
                        Utils.removeDuplicates(trackHeads, false);
                        for (StructureObject th : trackHeads) {
                            List<StructureObject> track = StructureObjectUtils.getTrack(th, true);
                            displayTrack(image, i, i.pairWithOffset(track), ImageWindowManager.defaultRoiColor, true);
                        }
                    }
                    
                    
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
    
    @Override
    protected List<int[]> getSelectedPointsOnImage(ImagePlus image) {
        Roi r = image.getRoi();
        if (r instanceof PointRoi) {
            PointRoi pRoi = (PointRoi)r;
            Polygon p = r.getPolygon();
            
            List<int[]> res = new ArrayList<int[]>(p.npoints);
            for (int i = 0; i<p.npoints; ++i) {
                res.add(new int[]{p.xpoints[i], p.ypoints[i], Math.max(0, pRoi.getPointPosition(i)-1)});
            }
            return res;
        } else return Collections.emptyList();
    }
    
    @Override
    public void displayObject(ImagePlus image, Roi3D roi) {
        Overlay o = image.getOverlay();
        if (o==null) {
            o=new Overlay();
            image.setOverlay(o);
        }
        for (Roi r : roi.values()) o.add(r);
    }

    @Override
    public void hideObject(ImagePlus image, Roi3D roi) {
        Overlay o = image.getOverlay();
        if (o!=null) {
            for (Roi r : roi.values()) o.remove(r);
        }
    }

    @Override
    public Roi3D generateObjectRoi(Pair<StructureObject, BoundingBox> object, boolean image2D, Color color) {
        Roi3D r =  createRoi(object.key.getMask(), object.value, !image2D);
        setObjectColor(r, color);
        return r;
    }
    
    @Override
    protected void setObjectColor(Roi3D roi, Color color) {
        for (Roi r : roi.values()) r.setStrokeColor(color);
    }

    /**
     * 
     * @param mask
     * @param offset
     * @param is3D
     * @return maping of Roi to Z-slice (taking into account the provided offset)
     */
    public static Roi3D createRoi(ImageInteger mask, BoundingBox offset, boolean is3D) {
        Roi3D res = new Roi3D(mask.getSizeZ());
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
                if (offset==null) logger.error("ROI creation : offset null for mask: {}", mask.getName());
                if (bds==null) logger.error("ROI creation : bounds null for mask: {}", mask.getName());
                roi.setLocation(bds.x+offset.getxMin(), bds.y+offset.getyMin());
                if (is3D) roi.setPosition(z+1+offset.getzMin());
                res.put(z+mask.getOffsetZ(), roi);
            }
        }
        return res;
    }
    
    // track-related methods
    @Override
    public void displayTrack(ImagePlus image, TrackRoi roi) {
        Overlay o = image.getOverlay();
        if (o==null) {
            o=new Overlay();
            image.setOverlay(o);
        }
        for (Roi r : roi) o.add(r);
    }

    @Override
    public void hideTrack(ImagePlus image, TrackRoi roi) {
        Overlay o = image.getOverlay();
        if (o!=null) {
            for (Roi r : roi) o.remove(r);
        }
    }

    @Override
    public TrackRoi generateTrackRoi(List<Pair<StructureObject, BoundingBox>> track, boolean image2D, Color color) {
        return createTrackRoi(track, color, image2D);
    }
    
    @Override
    protected void setTrackColor(TrackRoi roi, Color color) {
        for (Roi r : roi) r.setStrokeColor(color);
    }
    
    protected static TrackRoi createTrackRoi(List<Pair<StructureObject, BoundingBox>> track, Color color, boolean is2D) {
        TrackRoi trackRoi= new TrackRoi();
        Pair<StructureObject, BoundingBox> o1 = track.get(0);
        Pair<StructureObject, BoundingBox> o2;
        for (int idx = 1; idx<track.size(); ++idx) {
            o2 = track.get(idx);
            if (o1==null || o2==null) continue;
            Arrow arrow = new Arrow(o1.value.getXMean(), o1.value.getYMean(), o2.value.getXMean()-1, o2.value.getYMean());
            arrow.setStrokeColor(o2.key.hasTrackLinkError()?ImageWindowManager.trackErrorColor: o2.key.hasTrackLinkCorrection() ?ImageWindowManager.trackCorrectionColor : color);
            arrow.setStrokeWidth(trackArrowStrokeWidth);
            arrow.setHeadSize(trackArrowStrokeWidth*1.5);

            //if (o1.getNext()==o2) arrow.setDoubleHeaded(true);

            int zMin = Math.max(o1.value.getzMin(), o2.value.getzMin());
            int zMax = Math.min(o1.value.getzMax(), o2.value.getzMax());
            if (zMin==zMax) {
                if (!is2D) arrow.setPosition(zMin+1);
                trackRoi.add(arrow);
                //logger.debug("add arrow: {}", arrow);
            } else {
                // TODO debug
                //logger.error("Display Track error. objects: {} & {} bounds: {} & {}, image bounds: {} & {}", o1, o2, o1.getBounds(), o2.getBounds(), b1, b2);
                //if (true) return;
                if (zMin>zMax) {
                    /*int tmp = zMax;
                    zMax=zMin<(ip.getNSlices()-1)?zMin+1:zMin;
                    zMin=tmp>0?tmp-1:tmp;*/
                    logger.error("DisplayTrack error: Zmin>Zmax: o1: {}, o2: {}", o1.key, o2.key);
                }
                for (int z = zMin; z <= zMax; ++z) {
                    Arrow dup = (Arrow)arrow.clone();
                    dup.setPosition(z+1);
                    trackRoi.add(dup);
                    //logger.debug("add arrow (z): {}", arrow);
                }
            }
            o1=o2;
        }
        return trackRoi;
    }
    
    @Override
    protected void hideAllObjects(ImagePlus image) {
        Overlay o = image.getOverlay();
        if (o!=null) {
            for (Roi r : o.toArray()) if (r instanceof Arrow) o.remove(r);
        }
    }

    @Override
    protected void hideAllTracks(ImagePlus image) {
        Overlay o = image.getOverlay();
        if (o!=null) {
            for (Roi r : o.toArray()) if (!(r instanceof Arrow)) o.remove(r);
        }
    }

    

    
    public static class Roi3D extends HashMap<Integer, Roi> {
        public Roi3D(int bucketSize) {
            super(bucketSize);
        }
    }
    public static class TrackRoi extends ArrayList<Roi> {
        
    }
}
