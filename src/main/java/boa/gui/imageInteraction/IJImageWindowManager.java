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

import boa.gui.GUI;
import static boa.gui.GUI.logger;
import boa.gui.imageInteraction.IJImageWindowManager.Roi3D;
import boa.gui.imageInteraction.IJImageWindowManager.TrackRoi;
import static boa.gui.imageInteraction.ImageWindowManager.displayTrackMode;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import dataStructure.objects.Voxel;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Arrow;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Toolbar;
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
import static java.awt.event.InputEvent.BUTTON2_DOWN_MASK;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.scijava.vecmath.Vector2d;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author nasique
 */
public class IJImageWindowManager extends ImageWindowManager<ImagePlus, Roi3D, TrackRoi> {
    
           
    public IJImageWindowManager(ImageObjectListener listener) {
        super(listener, new IJImageDisplayer());
        new ImageJ();
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
        MouseListener ml =  new MouseListener() {

            public void mouseClicked(MouseEvent e) {
                //logger.trace("mouseclicked");
            }

            public void mousePressed(MouseEvent e) {
                //logger.debug("mousepressed");
                if (SwingUtilities.isRightMouseButton(e)) {
                    JPopupMenu menu = getMenu(image);
                    menu.show(canvas, canvas.offScreenX(e.getX()), canvas.offScreenY(e.getY()));
                } 
            }

            public void mouseReleased(MouseEvent e) {
                //logger.debug("tool : {}", IJ.getToolName());
                if (IJ.getToolName().equals("zoom") || IJ.getToolName().equals("hand") || IJ.getToolName().equals("multipoint") || IJ.getToolName().equals("point")) return;
                //int m = e.getModifiers();
                //boolean addToSelection = (m & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK;
                //boolean displayTrack = (m & ActionEvent.SHIFT_MASK) == ActionEvent.SHIFT_MASK;
                boolean addToSelection = e.isShiftDown();
                boolean displayTrack = displayTrackMode;
                //logger.debug("button ctrl: {}, shift: {}, alt: {}, meta: {}, altGraph: {}, alt: {}", e.isControlDown(), e.isShiftDown(), e.isAltDown(), e.isMetaDown(), e.isAltGraphDown(), displayTrackMode);
                ImageObjectInterface i = getImageObjectInterface(image);
                if (i==null) {
                    logger.trace("no image interface found");
                    return;
                }
                if (!addToSelection) {
                    if (listener!=null) {
                        //listener.fireObjectDeselected(Pair.unpair(getLabileObjects(image)));
                        //listener.fireTracksDeselected(getLabileTrackHeads(image));
                        listener.fireDeselectAllObjects(i.childStructureIdx);
                        listener.fireDeselectAllTracks(i.childStructureIdx);
                    }
                    hideAllRois(image, true, false);
                }
                List<Pair<StructureObject, BoundingBox>> selectedObjects = new ArrayList<Pair<StructureObject, BoundingBox>>();
                Roi r = ip.getRoi();
                boolean fromSelection = false;
                if (r!=null) {
                    boolean removeAfterwards = r.getType()==Roi.FREELINE || r.getType()==Roi.FREEROI || (r.getType()==Roi.POLYGON && r.getState()==Roi.NORMAL);
                    //logger.debug("Roi: {}/{}, rem: {}", r.getTypeAsString(), r.getClass(), removeAfterwards);
                    if (r.getType()==Roi.RECTANGLE || removeAfterwards) {
                        fromSelection=true;
                        Rectangle rect = r.getBounds();
                        BoundingBox selection = new BoundingBox(rect.x, rect.x+rect.width, rect.y, rect.y+rect.height, ip.getSlice()-1, ip.getSlice());
                        if (selection.getSizeX()==0 && selection.getSizeY()==0) selection=null;
                        i.addClickedObjects(selection, selectedObjects);
                        //logger.debug("before remove, contained: {}", selectedObjects.size());
                        if (removeAfterwards) {
                            Iterator<Pair<StructureObject, BoundingBox>> it = selectedObjects.iterator();
                            while (it.hasNext()) {
                                Pair<StructureObject, BoundingBox> p= it.next();
                                Polygon poly = r.getPolygon();
                                Rectangle oRect = new Rectangle(p.value.getxMin(), p.value.getyMin(), p.key.getBounds().getSizeX(), p.key.getBounds().getSizeY());
                                if (!poly.intersects(oRect)) it.remove();                                
                            }
                            //logger.debug("after remove, contained: {}", selectedObjects.size());
                        }
                        ip.deleteRoi();
                    }
                }
                //if (fromSelection || r==null) 
                if (!fromSelection) {
                    int x = e.getX();
                    int y = e.getY();
                    int offscreenX = canvas.offScreenX(x);
                    int offscreenY = canvas.offScreenY(y);
                    Pair<StructureObject, BoundingBox> o = i.getClickedObject(offscreenX, offscreenY, ip.getSlice()-1);
                    //logger.debug("click {}, {}, object: {}, ctlr:{}", x, y, o, ctrl);
                    if (o!=null) {
                        selectedObjects.add(o);
                        //logger.debug("selected object: "+o.key);
                    } else return;
                    if (r!=null && r.getType()==Roi.TRACED_ROI) {
                        logger.debug("Will delete Roi: type: {}, class: {}", r.getTypeAsString(), r.getClass().getSimpleName());
                        ip.deleteRoi();
                    }
                }
                if (!displayTrack) {
                    displayObjects(image, selectedObjects, ImageWindowManager.defaultRoiColor, true, true);
                    if (listener!=null) {
                        //List<Pair<StructureObject, BoundingBox>> labiles = getSelectedLabileObjects(image);
                        //fire deselected objects
                        listener.fireObjectSelected(Pair.unpairKeys(selectedObjects), true);
                    }
                } else {
                    List<StructureObject> trackHeads = new ArrayList<StructureObject>();
                    for (Pair<StructureObject, BoundingBox> p : selectedObjects) trackHeads.add(p.key.getTrackHead());
                    Utils.removeDuplicates(trackHeads, false);
                    for (StructureObject th : trackHeads) {
                        List<StructureObject> track = StructureObjectUtils.getTrack(th, true);
                        displayTrack(image, i, i.pairWithOffset(track), ImageWindowManager.getColor(), true);
                    }
                    if (listener!=null) listener.fireTracksSelected(trackHeads, true);
                }
            }

            public void mouseEntered(MouseEvent e) {
                //logger.trace("mouseentered");
            }

            public void mouseExited(MouseEvent e) {
                //logger.trace("mousexited");
            }
        };
        canvas.addMouseListener(ml);
        //return ml;
    }
    
    @Override public void setActive(Image image) {
        ImagePlus ip = this.displayer.getImage(image);
        if (ip!=null && ip.isVisible()) {
            IJ.selectWindow(image.getName());
        } else { // not visible -> show image
            displayer.showImage(image);
            addMouseListener(image);
            displayer.updateImageRoiDisplay(image);
        }
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
        for (Roi r : roi) if (r.getStrokeColor()!=ImageWindowManager.trackCorrectionColor && r.getStrokeColor()!=ImageWindowManager.trackErrorColor) r.setStrokeColor(color);
    }
    
    protected static TrackRoi createTrackRoi(List<Pair<StructureObject, BoundingBox>> track, Color color, boolean is2D) {
        TrackRoi trackRoi= new TrackRoi();
        Pair<StructureObject, BoundingBox> o1 = track.get(0);
        int idxMin = track.size()==1 ? 0 : 1; // display tracks with only 1 object as arrow head
        Pair<StructureObject, BoundingBox> o2;
        for (int idx = idxMin; idx<track.size(); ++idx) {
            o2 = track.get(idx);
            if (o1==null || o2==null) continue;
            Arrow arrow = new Arrow(o1.value.getXMean(), o1.value.getYMean(), o2.value.getXMean()-1, o2.value.getYMean());
            boolean error = o2.key.hasTrackLinkError(true, false) || (o1.key.hasTrackLinkError(false, true));
            boolean correction = o2.key.hasTrackLinkCorrection()||(o1.key.hasTrackLinkCorrection()&&o1.key.isTrackHead());
            //arrow.setStrokeColor( (o2.key.hasTrackLinkError() || (o1.key.hasTrackLinkError()&&o1.key.isTrackHead()) )?ImageWindowManager.trackErrorColor: (o2.key.hasTrackLinkCorrection()||(o1.key.hasTrackLinkCorrection()&&o1.key.isTrackHead())) ?ImageWindowManager.trackCorrectionColor : color);
            arrow.setStrokeColor(color);
            arrow.setStrokeWidth(trackArrowStrokeWidth);
            arrow.setHeadSize(trackArrowStrokeWidth*1.5);
            
            //if (o1.getNext()==o2) arrow.setDoubleHeaded(true);
            
            // 2D only errors -> TODO 3D also
            if (error || correction) {
                Color c = error ? ImageWindowManager.trackErrorColor : ImageWindowManager.trackCorrectionColor;
                trackRoi.add(getErrorArrow(arrow.x1, arrow.y1, arrow.x2, arrow.y2, c, color));
            } 
            
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
    private static Arrow getErrorArrow(double x1, double y1, double x2, double y2, Color c, Color fillColor) {
        double arrowSize = trackArrowStrokeWidth*2;
        double norm = Math.sqrt(Math.pow(x1-x2, 2)+Math.pow(y1-y2, 2));
        double[] vNorm = new double[]{(x2-x1)/norm, (y2-y1)/norm};
        double startLength = norm-2*arrowSize;
        double endLength = norm-arrowSize;
        double[] start = startLength>0 ? new double[]{x1+vNorm[0]*startLength, y1+vNorm[1]*startLength} : new double[]{x1, y1};
        double[] end = startLength>0 ? new double[]{x1+vNorm[0]*endLength, y1+vNorm[1]*endLength} : new double[]{x2, y2};
        Arrow res =  new Arrow(start[0], start[1], end[0], end[1]);
        res.setStrokeColor(c);
        res.setFillColor(fillColor);
        res.setStrokeWidth(trackArrowStrokeWidth);
        res.setHeadSize(trackArrowStrokeWidth*1.5);
        return res;
        
        // OTHER ARROW
        /*Arrow res =  new Arrow(x1, y1, x2, y2);
        res.setStrokeColor(c);
        double size = trackArrowStrokeWidth+1.5;
        res.setStrokeWidth(size);
        res.setHeadSize(trackArrowStrokeWidth*1.5);
        return res;*/
    }
    
    // not to be called directly!!
    protected void hideAllRois(ImagePlus image) {
        image.setOverlay(new Overlay());
    }

    
    public static class Roi3D extends HashMap<Integer, Roi> {
        public Roi3D(int bucketSize) {
            super(bucketSize);
        }
        public boolean contained(Overlay o) {
            for (Roi r : values()) if (o.contains(r)) return true;
            return false;
        }
    }
    public static class TrackRoi extends ArrayList<Roi> {
        public boolean contained(Overlay o) {
            for (Roi r : this) if (o.contains(r)) return true;
            return false;
        }
    }
}
