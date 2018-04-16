/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.gui.imageInteraction;

import boa.gui.GUI;
import static boa.gui.GUI.logger;
import boa.gui.ManualCorrection;
import boa.gui.imageInteraction.IJImageWindowManager.Roi3D;
import boa.gui.imageInteraction.IJImageWindowManager.TrackRoi;
import static boa.gui.imageInteraction.ImageWindowManager.displayTrackMode;
import boa.configuration.experiment.Structure;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import boa.data_structure.region_container.RegionContainerIjRoi;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Arrow;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.gui.Toolbar;
import ij.plugin.OverlayLabels;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.MutableBoundingBox;
import boa.image.IJImageWrapper;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.Offset;
import boa.image.SimpleBoundingBox;
import boa.image.TypeConverter;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import static java.awt.event.InputEvent.BUTTON2_DOWN_MASK;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
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
import boa.plugins.ObjectSplitter;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;
import java.awt.Event;
import java.awt.Point;
import java.util.function.Consumer;

/**
 *
 * @author nasique
 */
public class IJImageWindowManager extends ImageWindowManager<ImagePlus, Roi3D, TrackRoi> {
    
           
    public IJImageWindowManager(ImageObjectListener listener, ImageDisplayer<ImagePlus> displayer) {
        super(listener, displayer);
        //new ImageJ();
    }
    /*@Override
    protected ImagePlus getImage(Image image) {
        return IJImageWrapper.getImagePlus(image);
    }*/
    @Override 
    public void addWindowListener(Object image, WindowListener wl) {
        if (image instanceof Image) image = displayer.getImage((Image)image);
        if (image instanceof ImagePlus) ((ImagePlus)image).getWindow().addWindowListener(wl);
    }
    @Override
    public void toggleSetObjectCreationTool() {
        if (IJ.getToolName()=="point"||IJ.getToolName()=="multipoint") IJ.setTool("rect");
        else IJ.setTool("multipoint");
    }
    
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
                    if (menu!=null) {
                        menu.show(canvas, e.getX(), e.getY());
                        e.consume();
                    }
                } 
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                //logger.debug("tool : {}", IJ.getToolName());
                if (IJ.getToolName().equals("zoom") || IJ.getToolName().equals("hand") || IJ.getToolName().equals("multipoint") || IJ.getToolName().equals("point")) return;            
                boolean ctrl = e.isControlDown();
                //boolean ctrl = (IJ.isMacOSX() || IJ.isMacintosh()) ? e.isAltDown() : e.isControlDown(); // for mac: ctrl + clik = right click -> alt instead of ctrl
                boolean freeHandSplit = ( IJ.getToolName().equals("freeline")) && ctrl;
                boolean strechObjects = (IJ.getToolName().equals("line")) && ctrl;
                //logger.debug("ctrl: {}, tool : {}, freeHandSplit: {}", ctrl, IJ.getToolName(), freeHandSplit);
                boolean addToSelection = e.isShiftDown() && (!freeHandSplit || !strechObjects);
                boolean displayTrack = displayTrackMode;
                //logger.debug("button ctrl: {}, shift: {}, alt: {}, meta: {}, altGraph: {}, alt: {}", e.isControlDown(), e.isShiftDown(), e.isAltDown(), e.isMetaDown(), e.isAltGraphDown(), displayTrackMode);
                ImageObjectInterface i = getImageObjectInterface(image);
                int completionStructureIdx=-1;
                if (strechObjects) { // select parents
                    completionStructureIdx = i.getChildStructureIdx();
                    if (i.getChildStructureIdx()!=i.parentStructureIdx) i = IJImageWindowManager.super.getImageObjectInterface(image, i.parentStructureIdx);
                    //logger.debug("Strech: children: {}, current IOI: {}", completionStructureIdx, i.getChildStructureIdx());
                }
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
                List<Pair<StructureObject, BoundingBox>> selectedObjects = new ArrayList<>();
                Roi r = ip.getRoi();
                boolean fromSelection = false;
                // get all objects with intersection with ROI
                if (r!=null) {
                    boolean removeAfterwards = r.getType()==Roi.FREELINE || r.getType()==Roi.FREEROI || (r.getType()==Roi.POLYGON && r.getState()==Roi.NORMAL);
                    //logger.debug("Roi: {}/{}, rem: {}", r.getTypeAsString(), r.getClass(), removeAfterwards);
                    if (r.getType()==Roi.RECTANGLE || r.getType()==Roi.LINE || removeAfterwards) {
                        fromSelection=true;
                        Rectangle rect = removeAfterwards ? r.getPolygon().getBounds() : r.getBounds();
                        if (rect.height==0 || rect.width==0) removeAfterwards=false;
                        MutableBoundingBox selection = new MutableBoundingBox(rect.x, rect.x+rect.width, rect.y, rect.y+rect.height, ip.getSlice()-1, ip.getSlice());
                        if (selection.sizeX()==0 && selection.sizeY()==0) selection=null;
                        long t0 = System.currentTimeMillis();
                        i.addClickedObjects(selection, selectedObjects);
                        long t1 = System.currentTimeMillis();
                        //logger.debug("before remove, contained: {}, rect: {}, selection: {}", selectedObjects.size(), rect, selection);
                        if (removeAfterwards) {
                            Polygon poly = r.getPolygon();
                            Iterator<Pair<StructureObject, BoundingBox>> it = selectedObjects.iterator();
                            while (it.hasNext()) {
                                Pair<StructureObject, BoundingBox> p= it.next();
                                //logger.debug("poly {}, nPoints: {}, x:{}, y:{}", poly, poly.npoints, poly.xpoints, poly.ypoints);
                                Rectangle oRect = new Rectangle(p.value.xMin(), p.value.yMin(), p.key.getBounds().sizeX(), p.key.getBounds().sizeY());
                                if ((poly.npoints>1 && !poly.intersects(oRect)) || !insideMask(p.key.getMask(), p.value, poly)) it.remove();
                            }
                            //logger.debug("interactive selection after remove, contained: {}", selectedObjects.size());
                        }
                        long t2 = System.currentTimeMillis();
                        logger.debug("select objects: find indices: {} remove afterwards: {}", t1-t0, t2-t1);
                        if (!freeHandSplit || !strechObjects) ip.deleteRoi();
                    }
                }
                // get clicked object
                if (!fromSelection && !strechObjects) {
                    int offscreenX = canvas.offScreenX(e.getX());
                    int offscreenY = canvas.offScreenY(e.getY());
                    Pair<StructureObject, BoundingBox> o = i.getClickedObject(offscreenX, offscreenY, ip.getSlice()-1);
                    //logger.debug("click {}, {}, object: {} (total: {}, parent: {}), ctlr:{}", x, y, o, i.getObjects().size(), ctrl);
                    if (o!=null) {
                        selectedObjects.add(o);
                        //logger.debug("selected object: "+o.key);
                    } else return;
                    if (r!=null && r.getType()==Roi.TRACED_ROI) {
                        //logger.debug("Will delete Roi: type: {}, class: {}", r.getTypeAsString(), r.getClass().getSimpleName());
                        if (!freeHandSplit) ip.deleteRoi();
                    }
                }
                if (!displayTrack && !strechObjects) {
                    displayObjects(image, selectedObjects, ImageWindowManager.defaultRoiColor, true, true);
                    if (listener!=null) {
                        //List<Pair<StructureObject, BoundingBox>> labiles = getSelectedLabileObjects(image);
                        //fire deselected objects
                        listener.fireObjectSelected(Pair.unpairKeys(selectedObjects), true);
                    }
                } else if (!strechObjects) {
                    List<StructureObject> trackHeads = new ArrayList<>();
                    for (Pair<StructureObject, BoundingBox> p : selectedObjects) trackHeads.add(p.key.getTrackHead());
                    Utils.removeDuplicates(trackHeads, false);
                    for (StructureObject th : trackHeads) {
                        List<StructureObject> track = StructureObjectUtils.getTrack(th, true);
                        displayTrack(image, i, i.pairWithOffset(track), ImageWindowManager.getColor(), true);
                    }
                    if (listener!=null) listener.fireTracksSelected(trackHeads, true);
                }
                if (freeHandSplit && r!=null && !selectedObjects.isEmpty()) {
                    // remove if there are several objects per parent
                    List<StructureObject> objects = Pair.unpairKeys(selectedObjects);
                    Map<StructureObject, List<StructureObject>> byParent = StructureObjectUtils.splitByParent(objects);
                    objects.removeIf(o -> byParent.get(o.getParent()).size()>1);
                    // get line & split
                    FloatPolygon p = r.getInterpolatedPolygon(-1, true);
                    ObjectSplitter splitter = new FreeLineSplitter(selectedObjects, ArrayUtil.toInt(p.xpoints), ArrayUtil.toInt(p.ypoints));
                    ManualCorrection.splitObjects(GUI.getDBConnection(), objects, true, false, splitter);
                }
                if (strechObjects && r!=null && !selectedObjects.isEmpty()) {
                    Structure s = selectedObjects.get(0).key.getExperiment().getStructure(completionStructureIdx);
                    FloatPolygon p = r.getInterpolatedPolygon(-1, true);
                    ManualObjectStrecher.strechObjects(selectedObjects, completionStructureIdx, ArrayUtil.toInt(p.xpoints), ArrayUtil.toInt(p.ypoints), s.getManualObjectStrechThreshold(), s.isBrightObject());
                }
            }

            public void mouseEntered(MouseEvent e) {
                //logger.trace("mouseentered");
            }

            public void mouseExited(MouseEvent e) {
                //logger.trace("mousexited");
            }
        };
        canvas.disablePopupMenu(true); 
        MouseListener[] mls = canvas.getMouseListeners();
        for (MouseListener m : mls) canvas.removeMouseListener(m);
        canvas.addMouseListener(ml);
        for (MouseListener m : mls) canvas.addMouseListener(m);
    }
    
    @Override public void closeNonInteractiveWindows() {
        super.closeNonInteractiveWindows();
        String[] names = WindowManager.getImageTitles();
        if (names==null) return;
        for (String s : names) {
            ImagePlus ip = WindowManager.getImage(s);
            Image im = this.displayer.getImage(ip);
            if (im ==null) ip.close();
            if (!imageObjectInterfaceMap.keySet().contains(im)) ip.close();
        }
    }
    private boolean insideMask(ImageMask mask, Offset offsetMask, Polygon selection) {
        for (int i = 0; i<selection.npoints; ++i) {
            int x= selection.xpoints[i] - offsetMask.xMin();
            int y = selection.ypoints[i] - offsetMask.yMin();
            int z = offsetMask.zMin();
            if (mask.contains(x, y, z) && mask.insideMask(x, y, z)) return true;
        }
        return false;
    }
    @Override public void setActive(Image image) {
        super.setActive(image);
        ImagePlus ip = this.displayer.getImage(image);
        if (displayer.isDisplayed(ip)) {
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
        if (image.getNSlices()>1 && roi.is2D) roi.duplicateROIUntilZ(image.getNSlices());
        if (image.getNSlices()>1) {
            for (Roi r : roi.values()) {
                o.add(r);
                //if (r instanceof TextRoi) logger.debug("add text roi: {}", ((TextRoi)r).getText());
                //logger.debug("add Roi loc: [{}, {}], type: {}", r.getBounds().x, r.getBounds().y, r.getTypeAsString());
            }
        } else if (roi.containsKey(0)) o.add(roi.get(0));
    }

    @Override
    public void hideObject(ImagePlus image, Roi3D roi) {
        Overlay o = image.getOverlay();
        if (o!=null) {
            for (Roi r : roi.values()) o.remove(r);
        }
    }

    @Override
    public Roi3D generateObjectRoi(Pair<StructureObject, BoundingBox> object, Color color) {
        if (object.key.getMask().sizeZ()<=0 || object.key.getMask().sizeXY()<=0) logger.error("wrong object dim: o:{} {}", object.key, object.key.getBounds());
        Roi3D r =  RegionContainerIjRoi.createRoi(object.key.getMask(), object.value, !object.key.is2D());
        setObjectColor(r, color);
        return r;
    }
    
    @Override
    protected void setObjectColor(Roi3D roi, Color color) {
        for (Roi r : roi.values()) {
            if (r instanceof TextRoi) continue;
            r.setStrokeColor(color);
        }
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
        if (roi.is2D && image.getZ()>1) {
            for (int z = 1; z<image.getNSlices(); ++z) {
                for (Roi r : roi.duplicateForZ(z)) o.add(r);
            }
        }
    }

    @Override
    public void hideTrack(ImagePlus image, TrackRoi roi) {
        Overlay o = image.getOverlay();
        if (o!=null) {
            for (Roi r : roi) o.remove(r);
            if (image.getNSlices()>1) {
                for (TrackRoi tr : roi.sliceDuplicates.values()) {
                    for (Roi r : tr) o.remove(r);
                }
            }
        }
    }

    @Override
    public TrackRoi generateTrackRoi(List<Pair<StructureObject, BoundingBox>> track, Color color) {
        return createTrackRoi(track, color);
    }
    
    @Override
    protected void setTrackColor(TrackRoi roi, Color color) {
        for (Roi r : roi) if (r.getStrokeColor()!=ImageWindowManager.trackCorrectionColor && r.getStrokeColor()!=ImageWindowManager.trackErrorColor) r.setStrokeColor(color);
    }
    
    protected static TrackRoi createTrackRoi(List<Pair<StructureObject, BoundingBox>> track, Color color) {
        TrackRoi trackRoi= new TrackRoi();
        Pair<StructureObject, BoundingBox> o1 = track.get(0);
        trackRoi.setIs2D(o1.key.is2D());
        int idxMin = track.size()==1 ? 0 : 1; // display tracks with only 1 object as arrow head
        Pair<StructureObject, BoundingBox> o2;
        double arrowSize = track.size()==1 ? 1 : 0.5;
        for (int idx = idxMin; idx<track.size(); ++idx) {
            o2 = track.get(idx);
            if (o1==null || o2==null) continue;
            Arrow arrow = new Arrow(o1.value.xMean(), o1.value.yMean(), o2.value.xMean(), o2.value.yMean());
            boolean error = o2.key.hasTrackLinkError(true, false) || (o1.key.hasTrackLinkError(false, true));
            boolean correction = o2.key.hasTrackLinkCorrection()||(o1.key.hasTrackLinkCorrection()&&o1.key.isTrackHead());
            //arrow.setStrokeColor( (o2.key.hasTrackLinkError() || (o1.key.hasTrackLinkError()&&o1.key.isTrackHead()) )?ImageWindowManager.trackErrorColor: (o2.key.hasTrackLinkCorrection()||(o1.key.hasTrackLinkCorrection()&&o1.key.isTrackHead())) ?ImageWindowManager.trackCorrectionColor : color);
            arrow.setStrokeColor(color);
            arrow.setStrokeWidth(trackArrowStrokeWidth);
            arrow.setHeadSize(trackArrowStrokeWidth*arrowSize);
            
            //if (o1.getNext()==o2) arrow.setDoubleHeaded(true);
            
            // 2D only errors -> TODO 3D also
            if (error || correction) {
                Color c = error ? ImageWindowManager.trackErrorColor : ImageWindowManager.trackCorrectionColor;
                trackRoi.add(getErrorArrow(arrow.x1, arrow.y1, arrow.x2, arrow.y2, c, color));
            } 
            if (!trackRoi.is2D) { // in 3D -> display on all slices between slice min & slice max
                int zMin = Math.max(o1.value.zMin(), o2.value.zMin());
                int zMax = Math.min(o1.value.zMax(), o2.value.zMax());
                if (zMin==zMax) {
                    arrow.setPosition(zMin+1);
                    trackRoi.add(arrow);
                    //logger.debug("add arrow: {}", arrow);
                } else {
                    // TODO debug
                    //logger.error("Display Track error. objects: {} & {} bounds: {} & {}, image bounds: {} & {}", o1, o2, o1.getBounds(), o2.getBounds(), b1, b2);
                    //if (true) return;
                    if (zMin>zMax) {

                        logger.error("DisplayTrack error: Zmin>Zmax: o1: {}, o2: {}", o1.key, o2.key);
                    }
                    for (int z = zMin; z <= zMax; ++z) {
                        Arrow dup = (Arrow)arrow.clone();
                        dup.setPosition(z+1);
                        trackRoi.add(dup);
                        //logger.debug("add arrow (z): {}", arrow);
                    }
                }
            } else {
                trackRoi.add(arrow);
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
        boolean is2D;
        public Roi3D(int bucketSize) {
            super(bucketSize);
        }
        public Roi3D setIs2D(boolean is2D) {this.is2D=is2D; return this;}
        public boolean contained(Overlay o) {
            for (Roi r : values()) if (o.contains(r)) return true;
            return false;
        }
        public void duplicateROIUntilZ(int zMax) {
            if (size()>1 || !containsKey(0)) return;
            Roi r = this.get(0);
            for (int z = 1; z<zMax; ++z) {
                Roi dup = (Roi)r.clone();
                dup.setPosition(z+1);
                this.put(z, dup);
            }
        }
    }
    public static class TrackRoi extends ArrayList<Roi> {
        boolean is2D;
        Map<Integer, TrackRoi> sliceDuplicates= new HashMap<>(); // if Roi from 2D ref displayed on 3D image
        public boolean contained(Overlay o) {
            for (Roi r : this) if (o.contains(r)) return true;
            return false;
        }
        public TrackRoi setIs2D(boolean is2D) {this.is2D=is2D; return this;}
        public TrackRoi duplicateForZ(int z) {
            if (!sliceDuplicates.containsKey(z)) {
                TrackRoi res = new TrackRoi();
                for (Roi r : this) {
                    Roi dup = (Roi)r.clone();
                    dup.setPosition(z+1);
                    res.add(dup);
                }
                sliceDuplicates.put(z, res);
            }
            return sliceDuplicates.get(z);
        }
    }
}
