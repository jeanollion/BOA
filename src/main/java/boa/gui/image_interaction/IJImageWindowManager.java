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
package boa.gui.image_interaction;

import boa.ui.GUI;
import static boa.ui.GUI.logger;
import boa.ui.ManualEdition;
import boa.gui.image_interaction.IJImageWindowManager.Roi3D;
import boa.gui.image_interaction.IJImageWindowManager.TrackRoi;
import static boa.gui.image_interaction.ImageWindowManager.displayTrackMode;
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
import boa.image.SimpleOffset;
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
import boa.utils.geom.Point;
import boa.utils.geom.Vector;
import java.awt.Event;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;

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
        if (IJ.getToolName()=="point"||IJ.getToolName()=="multipoint") {
            this.getDisplayer().getCurrentImage().deleteRoi();
            IJ.setTool("rect");
        }
        else IJ.setTool("multipoint");
        ImageCanvas c;
        
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
                InteractiveImage i = getImageObjectInterface(image);
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
                    boolean removeAfterwards = r.getType()==Roi.FREELINE || r.getType()==Roi.FREEROI || r.getType()==Roi.LINE || (r.getType()==Roi.POLYGON && r.getState()==Roi.NORMAL);
                    //logger.debug("Roi: {}/{}, rem: {}", r.getTypeAsString(), r.getClass(), removeAfterwards);
                    if (r.getType()==Roi.RECTANGLE ||  removeAfterwards) {
                        // starts by getting all objects within bounding box of ROI
                        fromSelection=true;
                        Rectangle rect = removeAfterwards ? r.getPolygon().getBounds() : r.getBounds();
                        if (rect.height==0 || rect.width==0) removeAfterwards=false;
                        MutableBoundingBox selection = new MutableBoundingBox(rect.x, rect.x+rect.width, rect.y, rect.y+rect.height, ip.getSlice()-1, ip.getSlice());
                        if (selection.sizeX()==0 && selection.sizeY()==0) selection=null;
                        i.addClickedObjects(selection, selectedObjects);
                        if (removeAfterwards || (selection.sizeX()<=2 && selection.sizeY()<=2)) {
                            FloatPolygon fPoly = r.getInterpolatedPolygon();
                            selectedObjects.removeIf(p -> !intersectMask(p.key.getMask(), p.value, fPoly));
                        }
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
                    ManualEdition.splitObjects(GUI.getDBConnection(), objects, true, false, splitter);
                }
                /*if (strechObjects && r!=null && !selectedObjects.isEmpty()) {
                    Structure s = selectedObjects.get(0).key.getExperiment().getStructure(completionStructureIdx);
                    FloatPolygon p = r.getInterpolatedPolygon(-1, true);
                    ManualObjectStrecher.strechObjects(selectedObjects, completionStructureIdx, ArrayUtil.toInt(p.xpoints), ArrayUtil.toInt(p.ypoints), s.getManualObjectStrechThreshold(), s.isBrightObject());
                }*/
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
    
    private static boolean intersectMask(ImageMask mask, Offset offsetMask, FloatPolygon selection) {
        return IntStream.range(0, selection.npoints).parallel().anyMatch(i -> {
            int x= Math.round(selection.xpoints[i] - offsetMask.xMin());
            int y = Math.round(selection.ypoints[i] - offsetMask.yMin());
            int z = offsetMask.zMin();
            return mask.contains(x, y, z) && mask.insideMask(x, y, z);
        });
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
            
            List<int[]> res = new ArrayList<>(p.npoints);
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
        /*if (image.getNSlices()>1) {
            for (Roi r : roi.values()) {
                o.add(r);
            }
        } else if (roi.containsKey(0)) o.add(roi.get(0));*/
        for (Roi r : roi.values()) {
            r.setStrokeWidth(ROI_STROKE_WIDTH);
            o.add(r);
        }
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
        Roi3D r;       
        if (object.key.hasRegionContainer() && object.key.getRegionContainer() instanceof RegionContainerIjRoi && ((RegionContainerIjRoi)object.key.getRegionContainer()).getRoi()!=null) { // look for existing ROI
            r = ((RegionContainerIjRoi)object.key.getRegionContainer()).getRoi().duplicate()
                    .translate(new SimpleOffset(object.value).translate(new SimpleOffset(object.key.getBounds()).reverseOffset()));
            
        } else r =  RegionContainerIjRoi.createRoi(object.key.getMask(), object.value, !object.key.is2D());
        if (object.key.getAttribute(StructureObject.EDITED_SEGMENTATION, false)) { // also display when segmentation is edited
            double size = TRACK_ARROW_STROKE_WIDTH*1.5;
            Point p = new Point((float)object.key.getBounds().xMean(), (float)object.key.getBounds().yMean());
            object.key.getRegion().translateToFirstPointOutsideRegionInDir(p, new Vector(1, 1));
            p.translate(object.value).translateRev(object.key.getBounds()); // go to kymograph offset
            Arrow arrow = new Arrow(p.get(0)+size, p.get(1)+size, p.get(0), p.get(1));
            arrow.setStrokeColor(trackCorrectionColor);
            arrow.setStrokeWidth(TRACK_ARROW_STROKE_WIDTH);
            arrow.setHeadSize(size);
            new HashSet<>(r.keySet()).forEach((z) -> {
                Arrow arrowS = r.size()>1 ? (Arrow)arrow.clone() : arrow;
                arrowS.setPosition(z);
                r.put(-z-1, arrowS);
            });
        }
        setObjectColor(r, color);
        return r;
    }
    
    @Override
    protected void setObjectColor(Roi3D roi, Color color) {
        roi.entrySet().stream().filter(e->e.getKey()>=0).forEach(e -> e.getValue().setStrokeColor(color));
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
        Predicate<StructureObject> editedprev = o -> o.getAttribute(StructureObject.EDITED_LINK_PREV, false);
        Predicate<StructureObject> editedNext = o -> o.getAttribute(StructureObject.EDITED_LINK_NEXT, false);
        TrackRoi trackRoi= new TrackRoi();
        trackRoi.setIs2D(track.get(0).key.is2D());
        double arrowSize = track.size()==1 ? 1.5 : 0.65;
        IntStream.range(track.size()==1 ? 0 : 1, track.size()).forEach( idx -> {
            Pair<StructureObject, BoundingBox> o1 = idx>0 ? track.get(idx-1) : track.get(0);
            Pair<StructureObject, BoundingBox> o2 = track.get(idx);
            if (o1==null || o2==null) return;
            Arrow arrow;
            if (track.size()==1) {
                double size = TRACK_ARROW_STROKE_WIDTH*arrowSize;
                Point p = new Point((float)o1.key.getBounds().xMean(), (float)o1.key.getBounds().yMean());
                o1.key.getRegion().translateToFirstPointOutsideRegionInDir(p, new Vector(-1, -1));
                p.translate(o1.value).translateRev(o1.key.getBounds()); // go to kymograph offset
                arrow = new Arrow(p.get(0)-size, p.get(1)-size, p.get(0), p.get(1));
            } else {
                Point p1 = new Point((float)o1.value.xMean(), (float)o1.value.yMean());
                Point p2 = new Point((float)o2.value.xMean(), (float)o2.value.yMean());
                double minDist = TRACK_LINK_MIN_SIZE;
                if (p1.dist(p2)>minDist) {  // get coordinates outside regions so that track links do not hide regions
                    Vector dir = Vector.vector2D(p1, p2);
                    double dirFactor = 1d;
                    dir.multiply(dirFactor/dir.norm()); // increment
                    p1.translateRev(o1.value).translate(o1.key.getBounds()); // go to each region offset for the out-of-region test
                    p2.translateRev(o2.value).translate(o2.key.getBounds());
                    o1.key.getRegion().translateToFirstPointOutsideRegionInDir(p1, dir);
                    o2.key.getRegion().translateToFirstPointOutsideRegionInDir(p2, dir.multiply(-1));
                    p1.translate(o1.value).translateRev(o1.key.getBounds()); // go back to kymograph offset
                    p2.translate(o2.value).translateRev(o2.key.getBounds());
                    // ensure there is a minimal distance
                    double d = p1.dist(p2);
                    if (d<minDist) {
                        dir.multiply((minDist-d)/(2*dirFactor));
                        p1.translate(dir);
                        p2.translateRev(dir);
                    }
                }
                arrow = new Arrow(p1.get(0), p1.get(1), p2.get(0), p2.get(1));
                arrow.setDoubleHeaded(true);
            }
            
            boolean error = o2.key.hasTrackLinkError(true, false) || (o1.key.hasTrackLinkError(false, true));
            boolean correction = editedNext.test(o1.key)||editedprev.test(o2.key);
            //arrow.setStrokeColor( (o2.key.hasTrackLinkError() || (o1.key.hasTrackLinkError()&&o1.key.isTrackHead()) )?ImageWindowManager.trackErrorColor: (o2.key.hasTrackLinkCorrection()||(o1.key.hasTrackLinkCorrection()&&o1.key.isTrackHead())) ?ImageWindowManager.trackCorrectionColor : color);
            arrow.setStrokeColor(color);
            arrow.setStrokeWidth(TRACK_ARROW_STROKE_WIDTH);
            arrow.setHeadSize(TRACK_ARROW_STROKE_WIDTH*arrowSize);
            
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
        });
        return trackRoi;
    }
    private static Arrow getErrorArrow(double x1, double y1, double x2, double y2, Color c, Color fillColor) {
        double arrowSize = TRACK_ARROW_STROKE_WIDTH*2;
        double norm = Math.sqrt(Math.pow(x1-x2, 2)+Math.pow(y1-y2, 2));
        double[] vNorm = new double[]{(x2-x1)/norm, (y2-y1)/norm};
        double startLength = norm-2*arrowSize;
        double endLength = norm-arrowSize;
        double[] start = startLength>0 ? new double[]{x1+vNorm[0]*startLength, y1+vNorm[1]*startLength} : new double[]{x1, y1};
        double[] end = startLength>0 ? new double[]{x1+vNorm[0]*endLength, y1+vNorm[1]*endLength} : new double[]{x2, y2};
        Arrow res =  new Arrow(start[0], start[1], end[0], end[1]);
        res.setStrokeColor(c);
        res.setFillColor(fillColor);
        res.setStrokeWidth(TRACK_ARROW_STROKE_WIDTH);
        res.setHeadSize(TRACK_ARROW_STROKE_WIDTH*1.5);
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
        public Roi3D translate(Offset off) {
            if (off.zMin()!=0) { // need to clear map to update z-mapping
                synchronized(this) {
                    HashMap<Integer, Roi> temp = new HashMap<>(this);
                    this.clear();
                    temp.forEach((z, r)->put(z+off.zMin(), r));
                }
            }
            forEach((z, r)-> {
                Rectangle bds = r.getBounds();
                r.setLocation(bds.x+off.xMin(), bds.y+off.yMin());
                r.setPosition(r.getPosition()+off.zMin());
            });
            return this;
        }
        public Roi3D duplicate() {
            Roi3D res = new Roi3D(this.size()).setIs2D(is2D);
            super.forEach((z, r)->res.put(z, (Roi)r.clone()));
            return res;
        }
        public void duplicateROIUntilZ(int zMax) {
            if (size()>1 || !containsKey(0)) return;
            Roi r = this.get(0);
            for (int z = 1; z<zMax; ++z) {
                Roi dup = (Roi)r.clone();
                dup.setPosition(z+1);
                this.put(z, dup);
            }
            if (this.containsKey(-1)) { // segmentation correction arrow
                r = this.get(-1);
                for (int z = 1; z<zMax; ++z) {
                    Roi dup = (Roi)r.clone();
                    dup.setPosition(z+1);
                    this.put(-z-1, dup);
                }
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
