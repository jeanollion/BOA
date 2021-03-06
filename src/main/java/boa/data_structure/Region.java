/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.data_structure;

import com.google.common.collect.Sets;
import boa.data_structure.region_container.RegionContainer;
import static boa.data_structure.region_container.RegionContainer.MAX_VOX_3D;
import static boa.data_structure.region_container.RegionContainer.MAX_VOX_2D;
import boa.data_structure.region_container.RegionContainerBlankMask;
import boa.data_structure.region_container.RegionContainerIjRoi;
import boa.data_structure.region_container.RegionContainerVoxels;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.BoundingBox.LoopFunction;
import static boa.image.BoundingBox.getIntersection2D;
import static boa.image.BoundingBox.intersect2D;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.ImageMask2D;
import boa.image.ImageProperties;
import boa.image.Offset;
import boa.image.SimpleBoundingBox;
import boa.image.SimpleImageProperties;
import boa.image.SimpleOffset;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.image.processing.Filters;
import boa.image.processing.RegionFactory;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.utils.ArrayUtil;
import boa.utils.Utils;
import static boa.utils.Utils.comparator;
import static boa.utils.Utils.comparatorInt;
import boa.utils.geom.Point;
import boa.utils.geom.Vector;
import java.util.Arrays;
import java.util.function.Predicate;
/**
 * 
 * @author Jean Ollion
 * 
 */
public class Region {
    public final static Logger logger = LoggerFactory.getLogger(Region.class);
    protected ImageMask mask; //lazy -> use getter // bounds par rapport au root si absoluteLandMark==true, au parent sinon
    protected BoundingBox bounds;
    protected int label;
    protected Set<Voxel> voxels; //lazy -> use getter // coordonnées des voxel = coord dans l'image mask + offset du masque.  
    protected double scaleXY=1, scaleZ=1;
    protected boolean absoluteLandmark=false; // false = coordinates relative to the direct parent
    protected double quality=Double.NaN;
    protected Point center;
    final protected boolean is2D;
    /**
     * @param mask : image containing only the object, and whose bounding box is the same as the one of the object
     * @param label
     * @param is2D
     */
    public Region(ImageMask mask, int label, boolean is2D) {
        this.mask=mask;
        this.bounds=new SimpleBoundingBox(mask);
        this.label=label;
        this.scaleXY=mask.getScaleXY();
        this.scaleZ=mask.getScaleZ();
        this.is2D=is2D;
    }
    
    public Region(Set<Voxel> voxels, int label, boolean is2D, double scaleXY, double scaleZ) {
        if (voxels instanceof Set) this.voxels = (Set)voxels;
        else this.voxels=new HashSet<>(voxels);
        this.label=label;
        this.scaleXY=scaleXY;
        this.scaleZ=scaleXY;
        this.is2D=is2D;
    }
    public Region(final Voxel voxel, int label, boolean is2D, double scaleXY, double scaleZ) {
        this(new HashSet<Voxel>(){{add(voxel);}}, label, is2D, scaleXY, scaleZ);
    }
    
    public Region(Set<Voxel> voxels, int label, BoundingBox bounds, boolean is2D, double scaleXY, double scaleZ) {
        this(voxels, label, is2D, scaleXY, scaleZ);
        this.bounds=bounds;
    }
    
    public Region setIsAbsoluteLandmark(boolean absoluteLandmark) {
        this.absoluteLandmark = absoluteLandmark;
        return this;
    }
    
    public boolean isAbsoluteLandMark() {
        return absoluteLandmark;
    }
    public boolean is2D() {
        return is2D;
    }
    public Region setQuality(double quality) {
        this.quality=quality;
        return this;
    }
    public double getQuality() {
        return quality;
    }
    
    public Region duplicate() {
        if (this.mask!=null) {
            return new Region(getMask().duplicateMask(), label, is2D).setIsAbsoluteLandmark(absoluteLandmark).setQuality(quality).setCenter(center==null ? null : center.duplicate());
        }
        else if (this.voxels!=null) {
            Set<Voxel> vox = new HashSet<> (voxels.size());
            for (Voxel v : voxels) vox.add(v.duplicate());
            if (bounds==null) return new Region(vox, label, is2D, scaleXY, scaleZ).setIsAbsoluteLandmark(absoluteLandmark).setQuality(quality).setCenter(center==null ? null: center.duplicate());
            else return new Region(vox, label, new SimpleBoundingBox(bounds), is2D, scaleXY, scaleZ).setIsAbsoluteLandmark(absoluteLandmark).setQuality(quality).setCenter(center==null ? null: center.duplicate());
        }
        return null;
    }
    
    public int getLabel() {
        return label;
    }

    public double getScaleXY() {
        return scaleXY;
    }

    public double getScaleZ() {
        return scaleZ;
    }
    
    public int size() {
        if (this.voxelsCreated()) return voxels.size();
        else return mask.count();
    }
    /*public static Voxel getExtremumVoxelValue(Region r, boolean max) {
        if (r.getVoxels().isEmpty()) return null;
        Voxel res = r.getVoxels().iterator().next();
        if (max) {
            for (Voxel v : r.getVoxels()) if (v.value>res.value) res = v;
        } else {
            for (Voxel v : r.getVoxels()) if (v.value<res.value) res = v;
        }
        return res;
    }*/
    /*public List<Voxel> getExtrema(boolean max) {
        if (getVoxels().isEmpty()) return null;
        Iterator<Voxel> it = getVoxels().iterator();
        Voxel res = it.next();
        List<Voxel> resList = new ArrayList<>();
        if (max) {
            while(it.hasNext()) {
                Voxel v = it.next();
                if (v.value>res.value) {
                    res = v;
                    resList.clear();
                } else if (v.value==res.value) resList.add(v);
            }
        } else {
            while(it.hasNext()) {
                Voxel v = it.next();
                if (v.value<res.value) {
                    res = v;
                    resList.clear();
                } else if (v.value==res.value) resList.add(v);
            }
        }
        resList.add(res);
        return resList;
    }*/
    
    public Region setCenter(Point center) {
        this.center=center;
        return this;
    }
    
    public Point getCenter() {
        return center;
    }
    
    public Point getGeomCenter(boolean scaled) {
        float[] center = new float[3];
        if (mask instanceof BlankMask) {
            center[0] = (float)((BlankMask)mask).xMean();
            center[1] = (float)((BlankMask)mask).yMean();
            center[2] = (float)((BlankMask)mask).zMean();
        } else if (voxels!=null) {
            for (Voxel v : getVoxels()) {
                center[0] += v.x;
                center[1] += v.y;
                center[2] += v.z;
            }
            double count = voxels.size();
            center[0]/=count;
            center[1]/=count;
            center[2]/=count;
        } else {
            int[] count = new int[1];
            ImageMask.loopWithOffset(mask, (x, y, z)->{
                center[0] += x;
                center[1] += y;
                center[2] += z;
                ++count[0];
            });
            center[0]/=count[0];
            center[1]/=count[0];
            center[2]/=count[0];
        }
        if (scaled) {
            center[0] *=this.getScaleXY();
            center[1] *=this.getScaleXY();
            center[2] *=this.getScaleZ();
        }
        return new Point(center);
    }
    public Point getMassCenter(Image image, boolean scaled) { // TODO also perform from mask
        getVoxels();
        if (voxels!=null) {
            synchronized(voxels) {
                float[] center = new float[3];
                double count = 0;
                if (absoluteLandmark) {
                    for (Voxel v : voxels) {
                        if (image.containsWithOffset(v.x, v.y, v.z)) {
                            v.value=image.getPixelWithOffset(v.x, v.y, v.z);
                        } else v.value = Float.NaN;
                    } 
                } else {
                    for (Voxel v : voxels) {
                        if (image.contains(v.x, v.y, v.z)) {
                            v.value=image.getPixel(v.x, v.y, v.z);
                        } else v.value = Float.NaN;
                    } 
                }
                Voxel minValue = Collections.min(voxels, (v1, v2) -> Double.compare(v1.value, v2.value));
                for (Voxel v : getVoxels()) {
                    if (!Float.isNaN(v.value)) {
                        v.value-=minValue.value;
                        center[0] += v.x * v.value;
                        center[1] += v.y * v.value;
                        center[2] += v.z * v.value;
                        count+=v.value;
                    }
                }
                center[0]/=count;
                center[1]/=count;
                center[2]/=count;
                if (scaled) {
                    center[0] *=this.getScaleXY();
                    center[1] *=this.getScaleXY();
                    center[2] *=this.getScaleZ();
                }
                return new Point(center);
            }
        } else throw new RuntimeException("NOT SUPPORTED YET");
    }
    
    public synchronized void addVoxels(Collection<Voxel> voxelsToAdd) {
        if (voxels!=null) voxels.addAll(voxelsToAdd);
        if (mask!=null) {
            // check if all voxels are within mask
            boolean within = true;
            for (Voxel v : voxelsToAdd) {if (!mask.containsWithOffset(v.x, v.y, v.z)); within=false; break;}
            if (!within) mask = null;
            else {
                ensureMaskIsImageInteger();
                ImageInteger mask = getMaskAsImageInteger();
                for (Voxel v : voxelsToAdd) mask.setPixelWithOffset(v.x, v.y, v.z, 1);
            }
        }
        this.bounds=null;
    }
    public synchronized void remove(Region r) {
        if (this.mask!=null && r.mask!=null) andNot(r.mask);
        else if (this.voxels!=null && r.voxels!=null) removeVoxels(r.voxels);
        else andNot(r.getMask());
    }
    public synchronized void removeVoxels(Collection<Voxel> voxelsToRemove) {
        if (voxels!=null) voxels.removeAll(voxelsToRemove);
        if (mask!=null) {
            ensureMaskIsImageInteger();
            ImageInteger mask = getMaskAsImageInteger();
            for (Voxel v : voxelsToRemove) mask.setPixelWithOffset(v.x, v.y, v.z, 0);
        }
        this.bounds=null;
    }
    public synchronized void andNot(ImageMask otherMask) {
        getMask();
        ensureMaskIsImageInteger();
        ImageInteger mask = getMaskAsImageInteger();
        LoopFunction function = (x, y, z)-> {
            if (otherMask.insideMaskWithOffset(x, y, z)) {
                mask.setPixelWithOffset(x, y, z, 0);
                if (voxels!=null) voxels.remove(new Voxel(x, y, z));
            }
        };
        BoundingBox.loop(BoundingBox.getIntersection(otherMask, getBounds()), function);
    }
    public synchronized void and(ImageMask otherMask) {
        getMask();
        ensureMaskIsImageInteger();
        ImageInteger mask = getMaskAsImageInteger();
        LoopFunction function = (x, y, z)-> {
            if (!mask.insideMaskWithOffset(x, y, z)) return;
            if (!otherMask.insideMaskWithOffset(x, y, z)) {
                mask.setPixelWithOffset(x, y, z, 0);
                if (voxels!=null) voxels.remove(new Voxel(x, y, z));
            }
        };
        BoundingBox.loop(BoundingBox.getIntersection(otherMask, getBounds()), function);
    }
    public boolean contains(Voxel v) {
        if (voxels!=null) return voxels.contains(v);
        else return mask.containsWithOffset(v.x, v.y, v.z) && mask.insideMaskWithOffset(v.x, v.y, v.z);
    }
    public synchronized void clearVoxels() {
        if (mask==null) getMask();
        voxels = null;
    }
    public synchronized void clearMask() {
        if (voxels==null) getVoxels();
        mask = null;
        this.bounds=null;
    }
    public synchronized void resetMask() {
        if (mask!=null) { // do it from mask
            if (mask instanceof BlankMask) return;
            Region other = RegionFactory.getObjectImage(mask); // landmask = mask
            this.mask=other.mask;
            this.bounds=  other.getBounds();
        } else { // mask will be created from voxels
            mask = null;
            bounds = null;
        }
    }
    protected void createMask() {
        ImageByte mask_ = new ImageByte("", new SimpleImageProperties(getBounds(), scaleXY, scaleZ));
        for (Voxel v : voxels) {
            if (!mask_.containsWithOffset(v.x, v.y, v.z)) logger.error("voxel out of bounds: {}, bounds: {}", v, mask_.getBoundingBox()); // can happen if bounds were not updated before the object was saved
            mask_.setPixelWithOffset(v.x, v.y, v.z, 1);
        }
        //if (currentOffset!=null) mask_.translate(currentOffset);
        this.mask=mask_;
    }

    private void createVoxels() {
        //logger.debug("create voxels: mask offset: {}", mask.getBoundingBox());
        HashSet<Voxel> voxels_=new HashSet<>();
        ImageMask.loopWithOffset(mask, (x, y, z)->voxels_.add(new Voxel(x, y, z)));
        voxels=voxels_;
    }
    
    public ImageProperties getImageProperties() {
        if (mask!=null) return new SimpleImageProperties(mask, scaleXY, scaleZ);
        return new SimpleImageProperties(getBounds(), scaleXY, scaleZ);
    }
    /**
     * 
     * @return an image conatining only the object: its bounds are the one of the object and pixel values >0 where the objects has a voxel. The offset of the image is this offset of the object. 
     */
    public ImageMask<? extends ImageMask> getMask() {
        if (mask==null && voxels!=null) {
            synchronized(this) { // "Double-Checked Locking"
                if (mask==null) {
                    createMask();
                }
            }
        }
        return mask;
    }
    public ImageInteger<? extends ImageInteger> getMaskAsImageInteger() {
        return TypeConverter.toImageInteger(getMask(), null);
    }
    public void ensureMaskIsImageInteger() {
        if (!(getMask() instanceof ImageInteger)) {
            synchronized(this) {
                if (!(getMask() instanceof ImageInteger)) mask = getMaskAsImageInteger();
            }
        }
    }
    /**
     * 
     * @param properties 
     * @return a mask image of the object, with same dimensions as {@param properties}, and the object located within the image according to its offset
     */
    public ImageByte getMask(ImageProperties properties) {
        ImageByte res = new ImageByte("mask", properties);
        draw(res, label);
        return res;
    }
    
    public Set<Voxel> getVoxels() {
        if (voxels==null) {
            synchronized(this) { // "Double-Checked Locking"
                if (voxels==null) {
                    createVoxels();
                }
            }
        }
        return voxels;
    }
    
    public boolean voxelsCreated() {
        return voxels!=null;
    }
    /**
     * 
     * @return subset of object's voxels that are in contact with background, edge or other object
     */
    public Set<Voxel> getContour() {
        getMask();
        EllipsoidalNeighborhood neigh = !is2D() ? new EllipsoidalNeighborhood(1, 1, true) : new EllipsoidalNeighborhood(1, true); // 1 and not 1.5 -> diagonal
        Set<Voxel> res = new HashSet<>();
        if (voxels!=null) {
            /*for (int i = 0; i<neigh.dx.length; ++i) {
                neigh.dx[i]-=mask.xMin();
                neigh.dy[i]-=mask.yMin();
                if (!is2D()) neigh.dz[i]-=mask.zMin();
            }
            for (Voxel v: getVoxels()) if (touchBorder(v.x, v.y, v.z, neigh, mask)) res.add(v);
            */
            for (Voxel v: getVoxels()) if (touchBorderVox(v, neigh)) res.add(v);
        } else ImageMask.loop(mask, (x, y, z)->{ if (touchBorder(x, y, z, neigh, mask)) res.add(new Voxel(x+mask.xMin(), y+mask.yMin(), z+mask.zMin()));});
        return res;
    }
    public Set<Voxel> getOutterContour() {
        ImageMask mask = getMask();
        EllipsoidalNeighborhood neigh = !is2D() ? new EllipsoidalNeighborhood(1, 1, true) : new EllipsoidalNeighborhood(1, true); // 1 and not 1.5 -> diagonal
        for (int i = 0; i<neigh.dx.length; ++i) {
            neigh.dx[i]-=mask.xMin();
            neigh.dy[i]-=mask.yMin();
            if (!is2D()) neigh.dz[i]-=mask.zMin();
        }
        Set<Voxel> res = new HashSet<>();
        Voxel n = new Voxel(0, 0, 0);
        for (Voxel v: getVoxels()) {
            for (int i = 0; i<neigh.dx.length; ++i) {
                n.x=v.x+neigh.dx[i];
                n.y=v.y+neigh.dy[i];
                n.z=v.z+neigh.dz[i];
                if (!mask.contains(n.x, n.y, n.z) || !mask.insideMask(n.x, n.y, n.z)) res.add(n.duplicate());
            }
        }
        return res;
    }
    /**
     * 
     * @param v
     * @param neigh with offset
     * @param mask
     * @return 
     */
    private static boolean touchBorder(int x, int y, int z, EllipsoidalNeighborhood neigh, ImageMask mask) {
        int xx, yy, zz;
        for (int i = 0; i<neigh.dx.length; ++i) {
            xx=x+neigh.dx[i];
            yy=y+neigh.dy[i];
            zz=z+neigh.dz[i];
            if (!mask.contains(xx, yy, zz) || !mask.insideMask(xx, yy, zz)) return true;
        }
        return false;
    }
    private boolean touchBorderVox(Voxel v, EllipsoidalNeighborhood neigh) {
        Voxel next = new Voxel(v.x, v.y, v.z);
        for (int i = 0; i<neigh.dx.length; ++i) {
            next.x=v.x+neigh.dx[i];
            next.y=v.y+neigh.dy[i];
            next.z=v.z+neigh.dz[i];
            if (!getBounds().containsWithOffset(next.x, next.y, next.z)) return true;
            if (!voxels.contains(next)) return true;
        }
        return false;
    }
    public void erode(Neighborhood neigh) {
        mask = Filters.min(getMaskAsImageInteger(), null, neigh, false);
        voxels = null; // reset voxels
        // TODO reset bounds?
    }
    /**
     * 
     * @param image
     * @param threshold
     * @param removeIfLowerThanThreshold
     * @param keepOnlyBiggestObject
     * @param contour will be modified if a set
     * @return if any change was made
     */
    public boolean erodeContours(Image image, double threshold, boolean removeIfLowerThanThreshold, boolean keepOnlyBiggestObject, Collection<Voxel> contour, Predicate<Voxel> stopPropagation) {
        boolean changes = false;
        TreeSet<Voxel> heap = new TreeSet<>(Voxel.getComparator());
        heap.addAll( contour==null ? getContour() : contour);
        EllipsoidalNeighborhood neigh = !this.is2D() ? new EllipsoidalNeighborhood(1, 1, true) : new EllipsoidalNeighborhood(1, true);
        ensureMaskIsImageInteger();
        ImageInteger mask = getMaskAsImageInteger();
        Voxel n = new Voxel(0, 0, 0);
        while(!heap.isEmpty()) {
            Voxel v = heap.pollFirst();
            if (removeIfLowerThanThreshold ? image.getPixel(v.x, v.y, v.z)<=threshold : image.getPixel(v.x, v.y, v.z)>=threshold) {
                changes = true;
                mask.setPixel(v.x-mask.xMin(), v.y-mask.yMin(), v.z-mask.zMin(), 0);
                for (int i = 0; i<neigh.dx.length; ++i) {
                    n.x=v.x+neigh.dx[i];
                    n.y=v.y+neigh.dy[i];
                    n.z=v.z+neigh.dz[i];
                    if (mask.contains(n.x-mask.xMin(), n.y-mask.yMin(), n.z-mask.zMin()) && mask.insideMask(n.x-mask.xMin(), n.y-mask.yMin(), n.z-mask.zMin()) && (stopPropagation==null || !stopPropagation.test(n))) {
                        heap.add(n.duplicate());
                    }
                }
            }
        }
        if (changes && keepOnlyBiggestObject) { // check if 2 objects and erase all but smallest
            List<Region> objects = ImageLabeller.labelImageListLowConnectivity(mask);
            if (objects.size()>1) {
                objects.remove(Collections.max(objects, comparatorInt(o->o.size())));
                for (Region toErase: objects) toErase.draw(mask, 0);
            }
        }
        if (changes)  voxels = null; // reset voxels
        return changes;
    }
    public boolean erodeContoursEdge(Image image, boolean keepOnlyBiggestObject) {
        boolean changes = false;
        TreeSet<Voxel> heap = new TreeSet<>(Voxel.getComparator());
        heap.addAll(getContour());
        EllipsoidalNeighborhood neigh = !this.is2D() ? new EllipsoidalNeighborhood(1, 1, true) : new EllipsoidalNeighborhood(1, true);
        ensureMaskIsImageInteger();
        ImageInteger mask = getMaskAsImageInteger();
        int xx, yy, zz;
        while(!heap.isEmpty()) {
            Voxel v = heap.pollFirst();
            double value = image.getPixel(v.x, v.y, v.z);
            for (int i = 0; i<neigh.dx.length; ++i) {
                xx=v.x+neigh.dx[i];
                yy=v.y+neigh.dy[i];
                zz=v.z+neigh.dz[i];
                if (mask.contains(xx-mask.xMin(), yy-mask.yMin(), zz-mask.zMin()) && mask.insideMask(xx-mask.xMin(), yy-mask.yMin(), zz-mask.zMin())) {
                    double value2 = image.getPixel(xx, yy, zz);
                    if (value2>value) {
                        changes = true;
                        mask.setPixel(v.x-mask.xMin(), v.y-mask.yMin(), v.z-mask.zMin(), 0);
                        heap.add(new Voxel(xx, yy, zz));
                    }
                }
            }
        }
        if (changes && keepOnlyBiggestObject) { // check if 2 objects and erase all but smallest
            List<Region> objects = ImageLabeller.labelImageListLowConnectivity(mask);
            if (objects.size()>1) {
                objects.remove(Collections.max(objects, comparatorInt(o->o.size())));
                for (Region toErase: objects) toErase.draw(mask, 0);
            }
        }
        if (changes) voxels = null; // reset voxels
        return changes;
    }
    
    public void dilateContours(Image image, double threshold, boolean addIfHigherThanThreshold, Collection<Voxel> contour, ImageInteger labelMap) {
        //if (labelMap==null) labelMap = Collections.EMPTY_SET;
        TreeSet<Voxel> heap = new TreeSet<>(Voxel.getComparator());
        heap.addAll( contour==null ? getContour() : contour);
        heap.removeIf(v->{
            int l = labelMap.getPixelInt(v.x, v.y, v.z);
            return l>0 && l!=label;
        });
        //heap.removeAll(labelMap);
        EllipsoidalNeighborhood neigh = !this.is2D() ? new EllipsoidalNeighborhood(1.5, 1.5, true) : new EllipsoidalNeighborhood(1.5, true);
        //logger.debug("start heap: {},  voxels : {}", heap.size(), voxels.size());
        while(!heap.isEmpty()) {
            Voxel v = heap.pollFirst();
            if (addIfHigherThanThreshold ? image.getPixel(v.x, v.y, v.z)>=threshold : image.getPixel(v.x, v.y, v.z)<=threshold) {
                voxels.add(v);
                for (int i = 0; i<neigh.dx.length; ++i) {
                    Voxel next = new Voxel(v.x+neigh.dx[i], v.y+neigh.dy[i], v.z+neigh.dz[i]);
                    if (image.contains(next.x, next.y, next.z) && !voxels.contains(next)) {
                        int l = labelMap.getPixelInt(next.x, next.y, next.z);
                        if (l==0 || l == label) heap.add(next);
                    }
                }
            }
        }
        bounds = null; // reset boudns
        this.mask=null; // reset voxels
    }
    
    protected void createBoundsFromVoxels() {
        MutableBoundingBox bounds_  = new MutableBoundingBox();
        for (Voxel v : voxels) bounds_.union(v);
        bounds= bounds_;
    }

    public <T extends BoundingBox<T>> BoundingBox<T> getBounds() {
        if (bounds==null) {
            synchronized(this) { // "Double-Checked Locking"
                if (bounds==null) {
                    if (mask!=null) bounds=new SimpleBoundingBox(mask);
                    else if (voxels!=null) createBoundsFromVoxels();
                }
            }
        }
        return bounds;
    }
    
    public void setMask(ImageMask mask) {
        synchronized(this) {
            this.mask= mask;
            this.bounds=null;
            this.voxels=null;
        }
    }
    
    public Set<Voxel> getIntersection(Region other) {
        if (!intersect(other)) return Collections.emptySet(); 
        if (other.is2D()!=is2D()) { // should not take into acount z for intersection -> cast to voxel2D (even for the 2D object to enshure voxel2D), and return voxels from the 3D objects
            Set s1 = Sets.newHashSet(Utils.transform(getVoxels(), v->v.toVoxel2D()));
            Set s2 = Sets.newHashSet(Utils.transform(other.getVoxels(), v->v.toVoxel2D()));
            if (is2D()) {
                s2.retainAll(s1);
                return Sets.newHashSet(Utils.transform(s2, v->((Voxel2D)v).toVoxel()));
            } else {
                s1.retainAll(s2);
                return Sets.newHashSet(Utils.transform(s1, v->((Voxel2D)v).toVoxel()));
            }
        } else return Sets.intersection(Sets.newHashSet(getVoxels()), Sets.newHashSet(other.getVoxels()));
    }

    public boolean intersect(Region other) {
        if (is2D()||other.is2D()) return BoundingBox.intersect2D(getBounds(), other.getBounds());
        return BoundingBox.intersect(getBounds(), other.getBounds());
    }
    /**
     * Counts the overlap (in voxels) between this region and {@param other}, using masks of both region (no creation of voxels)
     * @param other other region
     * @param offset offset to add to this region so that is would be in absolute landmark
     * @param offsetOther offset to add to {@param other} so that is would be in absolute landmark
     * @return overlap (in voxels) between this region and {@param other}
     */
    public int getOverlapMaskMask(Region other, Offset offset, Offset offsetOther) {
        BoundingBox otherBounds = offsetOther==null? new SimpleBoundingBox(other.getBounds()) : new SimpleBoundingBox(other.getBounds()).translate(offsetOther);
        BoundingBox thisBounds = offset==null? new SimpleBoundingBox(getBounds()) : new SimpleBoundingBox(getBounds()).translate(offset);
        final boolean inter2D = is2D() || other.is2D();
        if (inter2D) {
            if (!intersect2D(thisBounds, otherBounds)) return 0;
        } else {
            if (!BoundingBox.intersect(thisBounds, otherBounds)) return 0;
        }
        
        final ImageMask mask = is2D() && !other.is2D() ? new ImageMask2D(getMask()) : getMask();
        final ImageMask otherMask = other.is2D() && !is2D() ? new ImageMask2D(other.getMask()) : other.getMask();
        BoundingBox inter = inter2D ? (!is2D() ? getIntersection2D(thisBounds, otherBounds):getIntersection2D(otherBounds, thisBounds)) : BoundingBox.getIntersection(thisBounds, otherBounds);
        //logger.debug("off: {}, otherOff: {}, is2D: {} other Is2D: {}, inter: {}", thisBounds, otherBounds, is2D(), other.is2D(), inter);
        final int count[] = new int[1];
        final int offX = thisBounds.xMin();
        final int offY = thisBounds.yMin();
        final int offZ = thisBounds.zMin();
        final int otherOffX = otherBounds.xMin();
        final int otherOffY = otherBounds.yMin();
        final int otherOffZ = otherBounds.zMin();
        BoundingBox.loop(inter, (int x, int y, int z) -> {
            if (mask.insideMask(x-offX, y-offY, z-offZ) 
                    && otherMask.insideMask(x-otherOffX, y-otherOffY, z-otherOffZ)) count[0]++;
        });
        return count[0];
    }
    
    public List<Region> getIncludedObjects(List<Region> candidates) {
        ArrayList<Region> res = new ArrayList<>();
        for (Region c : candidates) if (c.intersect(this)) res.add(c); // strict inclusion?
        return res;
    }
    /**
     * 
     * @param containerCandidates
     * @param offset offset to add to this region so that it would be in absolute landmark
     * @param containerOffset  offset to add to the container regions so that they would be in absolute landmark
     * @return the container with the most intersection
     */
    public Region getContainer(Collection<Region> containerCandidates, Offset offset, Offset containerOffset) {
        if (containerCandidates.isEmpty()) return null;
        Region currentParent=null;
        int currentIntersection=-1;
        for (Region p : containerCandidates) {
            int inter = getOverlapMaskMask(p, offset, containerOffset);
            if (inter>0) {
                if (currentParent==null) {
                    currentParent = p;
                    currentIntersection = inter;
                } else if (inter>currentIntersection) { // in case of conflict: keep parent that intersect most
                    currentIntersection=inter;
                    currentParent=p;
                }
            }
        }
        return currentParent;
    }
    
    public void merge(Region other) { //TODO do with masks only
        /*if ((voxels==null||other.voxels==null)) {
            if (other.getBounds().isIncluded(getBounds()))
        }*/
        this.getVoxels().addAll(other.getVoxels()); 
        //logger.debug("merge:  {} + {}, nb voxel avant: {}, nb voxels après: {}", this.getLabel(), other.getLabel(), nb,getVoxels().size() );
        this.mask=null; // reset mask
        this.bounds=null; // reset bounds
    }
    public static Region merge(Region... regions) {
        return merge(Arrays.asList(regions));
    }
    public static Region merge(Collection<Region> regions) {
        if (regions==null || regions.isEmpty()) return null;
        if (!Utils.objectsAllHaveSameProperty(regions, r->r.isAbsoluteLandMark())) throw new IllegalArgumentException("Trying to merge regions with different landmarks");
        if (!Utils.objectsAllHaveSameProperty(regions, r->r.is2D)) throw new IllegalArgumentException("Trying to merge 2D with 3D regions");
        Iterator<Region> it = regions.iterator();
        Region ref = it.next();
        MutableBoundingBox bounds = new MutableBoundingBox(ref.getBounds());
        while (it.hasNext()) bounds.union(it.next().getBounds());
        ImageByte mask = new ImageByte("", new SimpleImageProperties(bounds, ref.getScaleXY(), ref.getScaleZ()));
        for (Region r : regions) {
            ImageMask.loopWithOffset(r.getMask(), (x, y, z)-> mask.setPixelWithOffset(x, y, z, 1));
        }
        return new Region(mask, 1, ref.is2D).setIsAbsoluteLandmark(ref.isAbsoluteLandMark());
    }
    
    public RegionContainer createRegionContainer(StructureObject structureObject) {
        if (mask instanceof BlankMask) return new RegionContainerBlankMask(structureObject);
        else if (!overVoxelSizeLimit()) return new RegionContainerVoxels(structureObject);
        else return new RegionContainerIjRoi(structureObject);
    }
    
    public void setVoxelValues(Image image, boolean useOffset) {
        if (useOffset) {
            for (Voxel v : getVoxels()) v.value=image.getPixelWithOffset(v.x, v.y, v.z);
        } else {
            for (Voxel v : getVoxels()) v.value=image.getPixel(v.x, v.y, v.z);
        }
    }
    /**
     * Draws with the offset of the object, using the offset of the image if the object is in absolute landmark
     * @param image
     * @param label 
     */
    public void draw(Image image, double value) {
        if (voxels !=null) {
            //logger.trace("drawing from VOXELS of object: {} with label: {} on image: {} ", this, label, image);
            if (isAbsoluteLandMark()) for (Voxel v : getVoxels()) image.setPixelWithOffset(v.x, v.y, v.z, value);
            else for (Voxel v : getVoxels()) image.setPixel(v.x, v.y, v.z, value);
        }
        else {
            //logger.trace("drawing from IMAGE of object: {} with label: {} on image: {} mask offsetX: {} mask offsetY: {} mask offsetZ: {}", this, label, mask, mask.getOffsetX(), mask.getOffsetY(), mask.getOffsetZ());
            if (isAbsoluteLandMark()) ImageMask.loopWithOffset(mask, (x, y, z)-> { image.setPixelWithOffset(x, y, z, value); });
            else ImageMask.loopWithOffset(mask, (x, y, z)-> { image.setPixel(x, y, z, value); });
        }
    }
    /**
     * Draws with a custom offset 
     * @param image its offset will be taken into account
     * @param value 
     * @param offset will be added to the object absolute position
     */
    public void draw(Image image, double value, Offset offset) {
        if (offset==null) offset = new SimpleOffset(0, 0, 0);
        if (voxels !=null) {
            //logger.trace("drawing from VOXELS of object: {} with value: {} on image: {} ", this, value, image);
            int offX = offset.xMin()-image.xMin();
            int offY = offset.yMin()-image.yMin();
            int offZ = offset.zMin()-image.zMin();
            for (Voxel v : getVoxels()) if (image.contains(v.x+offX, v.y+offY, v.z+offZ)) image.setPixel(v.x+offX, v.y+offY, v.z+offZ, value);
        }
        else {
            int offX = offset.xMin()+mask.xMin()-image.xMin();
            int offY = offset.yMin()+mask.yMin()-image.yMin();
            int offZ = offset.zMin()+mask.zMin()-image.zMin();
            //logger.trace("drawing from IMAGE of object: {} with value: {} on image: {} mask offsetX: {} mask offsetY: {} mask offsetZ: {}", this, value, mask, offX, offY, offZ);
            for (int z = 0; z < mask.sizeZ(); ++z) {
                for (int y = 0; y < mask.sizeY(); ++y) {
                    for (int x = 0; x < mask.sizeX(); ++x) {
                        if (mask.insideMask(x, y, z)) {
                            if (image.contains(x+offX, y+offY, z+offZ)) image.setPixel(x+offX, y+offY, z+offZ, value);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Draws with a custom offset (the offset of the object and the image is not taken into account)
     * @param image
     * @param value 
     */
    public void drawWithoutObjectOffset(Image image, double value, Offset offset) {
        if (offset==null) {
            draw(image, value);
            return;
        }
        if (voxels !=null) {
            //logger.trace("drawing from VOXELS of object: {} with value: {} on image: {} ", this, value, image);
            int offX = -getBounds().xMin()+offset.xMin();
            int offY = -getBounds().yMin()+offset.yMin();
            int offZ = -getBounds().zMin()+offset.zMin();
            for (Voxel v : getVoxels()) image.setPixel(v.x+offX, v.y+offY, v.z+offZ, value);
        }
        else {
            int offX = offset.xMin();
            int offY = offset.yMin();
            int offZ = offset.zMin();
            //logger.trace("drawing from IMAGE of object: {} with value: {} on image: {} mask offsetX: {} mask offsetY: {} mask offsetZ: {}", this, value, mask, offX, offY, offZ);
            for (int z = 0; z < mask.sizeZ(); ++z) {
                for (int y = 0; y < mask.sizeY(); ++y) {
                    for (int x = 0; x < mask.sizeX(); ++x) {
                        if (mask.insideMask(x, y, z)) {
                            image.setPixel(x+offX, y+offY, z+offZ, value);
                        }
                    }
                }
            }
        }
    }
    
    private boolean overVoxelSizeLimit() {
        int limit =  (!is2D() ? MAX_VOX_3D :MAX_VOX_2D);
        if (mask==null) return voxels.size()>limit;
        if (mask instanceof BlankMask) return true;
        int count =0;
        for (int z = 0; z < mask.sizeZ(); ++z) {
            for (int xy = 0; xy < mask.sizeXY(); ++xy) {
                if (mask.insideMask(xy, z)) {
                    if (++count==limit) return true;
                }
            }
        }
        return false;
    }
    
    public Region translate(Offset offset) {
        if (Offset.offsetNull(offset)) return this;
        else {
            if (mask!=null) mask.translate(offset);
            if (bounds!=null) bounds.translate(offset);
            if (voxels!=null) for (Voxel v : voxels) v.translate(offset);
            if (center!=null) center.translate(offset);
        }
        return this;
    }
    
    public Point translateToFirstPointOutsideRegionInDir(Point start, Vector normedDir) {
        Voxel v = start.asVoxel();
        if (!contains(v)) return start;
        start.translate(normedDir);
        start.copyLocationToVoxel(v);
        while(contains(v)) {
            start.translate(normedDir);
            start.copyLocationToVoxel(v);
        }
        return start;
    }

    public Region setLabel(int label) {
        this.label=label;
        return this;
    }
    @Override
    public String toString() {
        return ""+this.label;
    }
}
