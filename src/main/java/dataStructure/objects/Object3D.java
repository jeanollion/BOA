package dataStructure.objects;

import com.google.common.collect.Sets;
import dataStructure.containers.ObjectContainer;
import static dataStructure.containers.ObjectContainer.MAX_VOX_3D;
import static dataStructure.containers.ObjectContainer.MAX_VOX_2D;
import static dataStructure.containers.ObjectContainer.MAX_VOX_2D_EMB;
import static dataStructure.containers.ObjectContainer.MAX_VOX_3D_EMB;
import dataStructure.containers.ObjectContainerBlankMask;
import dataStructure.containers.ObjectContainerIjRoi;
import dataStructure.containers.ObjectContainerVoxels;
import image.BlankMask;
import image.BoundingBox;
import image.BoundingBox.LoopFunction;
import image.BoundingBox.LoopFunction2;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageMask;
import image.ImageMask2D;
import image.ImageProperties;
import image.TypeConverter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.Filters;
import processing.neighborhood.EllipsoidalNeighborhood;
import processing.neighborhood.Neighborhood;
import utils.ArrayUtil;
import utils.Utils;
/**
 * 
 * @author jollion
 * 
 */
public class Object3D {
    public final static Logger logger = LoggerFactory.getLogger(Object3D.class);
    protected ImageInteger mask; //lazy -> use getter // bounds par rapport au root si absoluteLandMark==true, au parent sinon
    protected BoundingBox bounds;
    protected int label;
    protected List<Voxel> voxels; //lazy -> use getter // coordonnées des voxel = coord dans l'image mask + offset du masque.  
    protected float scaleXY=1, scaleZ=1;
    protected boolean absoluteLandmark=false; // false = coordinates relative to the direct parent
    protected double quality=Double.NaN;
    protected double[] center;
    protected boolean is2D=false;
    /**
     * @param mask : image containing only the object, and whose bounding box is the same as the one of the object
     */
    public Object3D(ImageInteger mask, int label) {
        this.mask=mask;
        this.bounds=mask.getBoundingBox();
        this.label=label;
        this.scaleXY=mask.getScaleXY();
        this.scaleZ=mask.getScaleZ();
    }
    
    public Object3D(List<Voxel> voxels, int label, float scaleXY, float scaleZ) {
        this.voxels=voxels;
        this.label=label;
        this.scaleXY=scaleXY;
        this.scaleZ=scaleXY;
    }
    public Object3D(final Voxel voxel, int label, float scaleXY, float scaleZ) {
        this(new ArrayList<Voxel>(){{add(voxel);}}, label, scaleXY, scaleZ);
    }
    
    public Object3D(List<Voxel> voxels, int label, BoundingBox bounds, float scaleXY, float scaleZ) {
        this(voxels, label, scaleXY, scaleZ);
        this.bounds=bounds;
    }
    
    public Object3D setIsAbsoluteLandmark(boolean absoluteLandmark) {
        this.absoluteLandmark = absoluteLandmark;
        return this;
    }
    public Object3D setIs2D(boolean is2D) {
        this.is2D = is2D;
        return this;
    }
    
    public boolean isAbsoluteLandMark() {
        return absoluteLandmark;
    }
    public Object3D setQuality(double quality) {
        this.quality=quality;
        return this;
    }
    public double getQuality() {
        return quality;
    }
    
    public Object3D duplicate() {
        if (this.mask!=null) return new Object3D((ImageInteger)mask.duplicate(""), label).setIsAbsoluteLandmark(absoluteLandmark).setQuality(quality).setCenter(ArrayUtil.duplicate(center));
        else if (this.voxels!=null) {
            ArrayList<Voxel> vox = new ArrayList<> (voxels.size());
            for (Voxel v : voxels) vox.add(v.duplicate());
            if (bounds==null) return new Object3D(vox, label, scaleXY, scaleZ).setIsAbsoluteLandmark(absoluteLandmark).setQuality(quality).setCenter(ArrayUtil.duplicate(center));
            else return new Object3D(vox, label, bounds.duplicate(), scaleXY, scaleZ).setIsAbsoluteLandmark(absoluteLandmark).setQuality(quality).setCenter(ArrayUtil.duplicate(center));
        }
        return null;
    }
    
    public int getLabel() {
        return label;
    }

    public float getScaleXY() {
        return scaleXY;
    }

    public float getScaleZ() {
        return scaleZ;
    }
    
    public int getSize() {
        if (this.voxelsCreated()) return voxels.size();
        else return mask.count();
    }
    
    public double getMeanVoxelValue() {
        if (getVoxels().isEmpty()) return Double.NaN;
        else if (voxels.size()==1) return voxels.get(0).value;
        else {
            double sum = 0;
            for (Voxel v : voxels) sum+=v.value;
            return sum/(double)voxels.size();
        }
    }
    public Voxel getExtremum(boolean max) {
        if (getVoxels().isEmpty()) return null;
        Voxel res = getVoxels().get(0);
        if (max) {
            for (Voxel v : getVoxels()) if (v.value>res.value) res = v;
        } else {
            for (Voxel v : getVoxels()) if (v.value<res.value) res = v;
        }
        return res;
    }
    public List<Voxel> getExtrema(boolean max) {
        if (getVoxels().isEmpty()) return null;
        Iterator<Voxel> it = getVoxels().iterator();
        Voxel res = it.next();
        List<Voxel> resList = new ArrayList<Voxel>();
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
    }
    
    public Object3D setCenter(double[] center) {
        this.center=center;
        return this;
    }
    
    public double[] getCenter() {
        return center;
    }
    
    public double[] getGeomCenter(boolean scaled) {
        double[] center = new double[3];
        if (mask instanceof BlankMask) {
            center[0] = mask.getBoundingBox().getXMean();
            center[1] = mask.getBoundingBox().getYMean();
            center[2] = mask.getBoundingBox().getZMean();
        } else {
            for (Voxel v : getVoxels()) {
                center[0] += v.x;
                center[1] += v.y;
                center[2] += v.z;
            }
            double count = voxels.size();
            center[0]/=count;
            center[1]/=count;
            center[2]/=count;
        }
        if (scaled) {
            center[0] *=this.getScaleXY();
            center[1] *=this.getScaleXY();
            center[2] *=this.getScaleZ();
        }
        return center;
    }
    public double[] getMassCenter(Image image, boolean scaled) {
        getVoxels();
        synchronized(voxels) {
            double[] center = new double[3];
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
            return center;
        }
    }
    
    public synchronized void addVoxels(List<Voxel> voxelsToAdd) {
        this.getVoxels().addAll(voxelsToAdd);
        this.bounds=null;
        this.mask=null;
    }
    public synchronized void removeVoxels(List<Voxel> voxelsToRemove) {
        this.getVoxels().removeAll(voxelsToRemove);
        this.bounds=null;
        this.mask=null;
    }
    public synchronized void resetVoxels() {
        if (mask==null) throw new IllegalArgumentException("Cannot reset voxels if no mask is present");
        voxels = null;
    }
    public synchronized void resetMask() {
        if (voxels==null) throw new IllegalArgumentException("Cannot reset mask if no voxels are present");
        mask = null;
        this.bounds=null;
    }
    protected void createMask() {
        ImageByte mask_ = new ImageByte("", getBounds().getImageProperties(scaleXY, scaleZ));
        for (Voxel v : voxels) {
            if (!mask_.containsWithOffset(v.x, v.y, v.z)) logger.error("voxel out of bounds: {}, bounds: {}", v, mask_.getBoundingBox()); // can happen if bounds were not updated before the object was saved
            mask_.setPixelWithOffset(v.x, v.y, v.z, 1);
        }
        //if (currentOffset!=null) mask_.translate(currentOffset);
        this.mask=mask_;
    }

    private void createVoxels() {
        //logger.debug("create voxels: mask offset: {}", mask.getBoundingBox());
        if (mask.getPixelArray()==null) logger.debug("mask pixel null for object: {}", this);
        ArrayList<Voxel> voxels_=new ArrayList<Voxel>();
        if (is2D()) {
            for (int y = 0; y < mask.getSizeY(); ++y) {
                for (int x = 0; x < mask.getSizeX(); ++x) {
                    if (mask.insideMask(x, y, 0)) {
                        voxels_.add( new Voxel2D(x + mask.getOffsetX(), y + mask.getOffsetY(), mask.getOffsetZ()));
                    }
                }
            }
        } else {
            for (int z = 0; z < mask.getSizeZ(); ++z) {
                for (int y = 0; y < mask.getSizeY(); ++y) {
                    for (int x = 0; x < mask.getSizeX(); ++x) {
                        if (mask.insideMask(x, y, z)) {
                            voxels_.add( new Voxel(x + mask.getOffsetX(), y + mask.getOffsetY(), z + mask.getOffsetZ()));
                        }
                    }
                }
            }
        }
        voxels=voxels_;
    }
    
    public ImageProperties getImageProperties() {
        if (mask!=null) return mask;
        else return getBounds().getImageProperties("", scaleXY, scaleZ);
    }
    /**
     * 
     * @return an image conatining only the object: its bounds are the one of the object and pixel values >0 where the objects has a voxel. The offset of the image is this offset of the object. 
     */
    public ImageInteger getMask() {
        if (mask==null && voxels!=null) {
            synchronized(this) { // "Double-Checked Locking"
                if (mask==null) {
                    createMask();
                }
            }
        }
        return mask;
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
    
    public List<Voxel> getVoxels() {
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
    
    public List<Voxel> getContour() {
        ArrayList<Voxel> res = new ArrayList<Voxel>();
        ImageMask mask = getMask();
        EllipsoidalNeighborhood neigh = !this.is2D() ? new EllipsoidalNeighborhood(1, 1, true) : new EllipsoidalNeighborhood(1, true); // 1 and not 1.5 -> diagonal
        int xx, yy, zz;
        for (int i = 0; i<neigh.dx.length; ++i) {
            neigh.dx[i]-=mask.getOffsetX();
            neigh.dy[i]-=mask.getOffsetY();
            neigh.dz[i]-=mask.getOffsetZ();
        }
        for (Voxel v : getVoxels()) {
            for (int i = 0; i<neigh.dx.length; ++i) {
                xx=v.x+neigh.dx[i];
                yy=v.y+neigh.dy[i];
                zz=v.z+neigh.dz[i];
                if (!mask.contains(xx, yy, zz) || !mask.insideMask(xx, yy, zz)) {
                    res.add(v);
                    break;
                }
            }
        } // TODO : method without getVoxels 
        Utils.removeDuplicates(res, false);
        //logger.debug("contour: {} (total: {})", res.size(), getVoxels().size());
        return res; 
    }
    public void erode(Neighborhood neigh) {
        mask = Filters.min(getMask(), null, neigh);
        voxels = null; // reset voxels
        // TODO reset bounds?
    }
    public void erodeContours(Image image, double threshold, boolean removeIfLowerThanThreshold, List<Voxel> contour) {
        if (contour==null || contour.isEmpty()) contour = getContour();
        Set<Voxel> heap = new HashSet<>(contour);
        EllipsoidalNeighborhood neigh = !this.is2D() ? new EllipsoidalNeighborhood(1.5, 1.5, true) : new EllipsoidalNeighborhood(1.5, true);
        ImageInteger mask = getMask();
        int xx, yy, zz;
        while(!heap.isEmpty()) {
            Iterator<Voxel> it= heap.iterator();
            Voxel v = it.next();
            it.remove();
            if (removeIfLowerThanThreshold ? image.getPixel(v.x, v.y, v.z)<=threshold : image.getPixel(v.x, v.y, v.z)>=threshold) {
                mask.setPixel(v.x-mask.getOffsetX(), v.y-mask.getOffsetY(), v.z-mask.getOffsetZ(), 0);
                for (int i = 0; i<neigh.dx.length; ++i) {
                    xx=v.x+neigh.dx[i];
                    yy=v.y+neigh.dy[i];
                    zz=v.z+neigh.dz[i];
                    if (mask.contains(xx-mask.getOffsetX(), yy-mask.getOffsetY(), zz-mask.getOffsetZ()) && mask.insideMask(xx-mask.getOffsetX(), yy-mask.getOffsetY(), zz-mask.getOffsetZ())) {
                        if (is2D()) heap.add(new Voxel2D(xx, yy, zz));
                        else heap.add(new Voxel2D(xx, yy, zz));
                    }
                }
            }
        }
        voxels = null; // reset voxels
        // TODO reset bounds ?
    }
    
    public void dilateContours(Image image, double threshold, boolean addIfHigherThanThreshold, List<Voxel> contour) {
        if (contour==null || contour.isEmpty()) contour = getContour();
        Set<Voxel> heap = new HashSet<>(contour);
        EllipsoidalNeighborhood neigh = !this.is2D() ? new EllipsoidalNeighborhood(1.5, 1.5, true) : new EllipsoidalNeighborhood(1.5, true);
        ImageInteger mask = getMask();
        int xx, yy, zz;
        while(!heap.isEmpty()) {
            Iterator<Voxel> it= heap.iterator();
            Voxel v = it.next();
            it.remove();
            if (addIfHigherThanThreshold ? image.getPixel(v.x, v.y, v.z)>=threshold : image.getPixel(v.x, v.y, v.z)<=threshold) {
                mask.setPixel(v.x-mask.getOffsetX(), v.y-mask.getOffsetY(), v.z-mask.getOffsetZ(), 1);
                for (int i = 0; i<neigh.dx.length; ++i) {
                    xx=v.x+neigh.dx[i];
                    yy=v.y+neigh.dy[i];
                    zz=v.z+neigh.dz[i];
                    if (mask.contains(xx-mask.getOffsetX(), yy-mask.getOffsetY(), zz-mask.getOffsetZ()) && !mask.insideMask(xx-mask.getOffsetX(), yy-mask.getOffsetY(), zz-mask.getOffsetZ())) {
                        if (is2D()) heap.add(new Voxel2D(xx, yy, zz));
                        else heap.add(new Voxel(xx, yy, zz));
                    }
                }
            }
        }
        voxels = null; // reset voxels
        // TODO reset bounds ?
    }
    
    protected void createBoundsFromVoxels() {
        BoundingBox bounds_  = new BoundingBox();
        for (Voxel v : voxels) bounds_.expand(v);
        bounds= bounds_;
    }

    public BoundingBox getBounds() {
        if (bounds==null) {
            synchronized(this) { // "Double-Checked Locking"
                if (bounds==null) {
                    if (mask!=null) bounds=mask.getBoundingBox();
                    else if (voxels!=null) createBoundsFromVoxels();
                }
            }
        }
        return bounds;
    }
    
    public void setMask(ImageInteger mask) {
        synchronized(this) {
            this.mask= mask;
            this.bounds=null;
            this.voxels=null;
        }
    }
    
    public Set<Voxel> getIntersection(Object3D other) {
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
    /*
    // TODO faire une methode plus optimisée qui utilise les masques uniquement
    public int getIntersectionCountMask(Object3D other, BoundingBox offset) {
        if (offset==null) offset=new BoundingBox(0, 0, 0);
        if (!this.getBounds().hasIntersection(other.getBounds().duplicate().translate(offset))) return 0;
        else {
            ImageMask m = other.getMask();
            int count = 0;
            int offX = m.getOffsetX()+offset.getxMin();
            int offY = m.getOffsetY()+offset.getyMin();
            int offZ = m.getOffsetZ()+offset.getzMin();
            for (Voxel v : this.getVoxels()) {
                if (m.insideMask(v.x-offX, v.y-offY, v.z-offZ)) ++count;
            }
            return count;
        }
    }*/
    public boolean intersect(Object3D other) {
        if (is2D()||other.is2D()) return getBounds().intersect2D(other.getBounds());
        else return getBounds().intersect(other.getBounds());
    }
    /**
     * 
     * @param other
     * @param offset added to the bounds of {@param this}
     * @param offsetOther added to the bounds of {@param other}
     * @return 
     */
    public int getIntersectionCountMaskMask(Object3D other, BoundingBox offset, BoundingBox offsetOther) {
        BoundingBox otherBounds = offsetOther==null? other.getBounds() : other.getBounds().duplicate().translate(offsetOther);
        BoundingBox thisBounds = offset==null? getBounds() : getBounds().duplicate().translate(offset);
        final boolean inter2D = is2D() || other.is2D();
        if (inter2D) {
            if (!thisBounds.intersect2D(otherBounds)) return 0;
        } else {
            if (!thisBounds.intersect(otherBounds)) return 0;
        }
        //logger.debug("off: {}, otherOff: {}", thisBounds, otherBounds);
        
        final ImageMask mask = is2D() && !other.is2D() ? new ImageMask2D(getMask()) : getMask();
        final ImageMask m = other.is2D() && !is2D() ? new ImageMask2D(other.getMask()) : other.getMask();
        BoundingBox inter = inter2D ? thisBounds.getIntersection2D(otherBounds) : thisBounds.getIntersection(otherBounds);
        final int count[] = new int[1];
        final int offX = thisBounds.getxMin();
        final int offY = thisBounds.getyMin();
        final int offZ = thisBounds.getzMin();
        final int otherOffX = otherBounds.getxMin();
        final int otherOffY = otherBounds.getyMin();
        final int otherOffZ = otherBounds.getzMin();
        inter.loop(new LoopFunction2() {
            int c;
            public void loop(int x, int y, int z) {if (mask.insideMask(x-offX, y-offY, z-offZ) && m.insideMask(x-otherOffX, y-otherOffY, z-otherOffZ)) c++;}
            public void setUp() {c = 0;}
            public void tearDown() {count[0]=c;}
        });
        return count[0];
        
    }
    
    public List<Object3D> getIncludedObjects(List<Object3D> candidates) {
        ArrayList<Object3D> res = new ArrayList<>();
        for (Object3D c : candidates) if (c.intersect(this)) res.add(c); // strict inclusion?
        return res;
    }
    
    public Object3D getContainer(List<Object3D> parents, BoundingBox offset, BoundingBox offsetParent) {
        if (parents.isEmpty()) return null;
        Object3D currentParent=null;
        int currentIntersection=-1;
        for (Object3D p : parents) {
            int inter = getIntersectionCountMaskMask(p, offset, offsetParent);
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
    
    public void merge(Object3D other) {
        //int nb = getVoxels().size();
        this.getVoxels().addAll(other.getVoxels()); // TODO check for duplicates?
        //logger.debug("merge:  {} + {}, nb voxel avant: {}, nb voxels après: {}", this.getLabel(), other.getLabel(), nb,getVoxels().size() );
        this.mask=null; // reset mask
        this.bounds=null; // reset bounds
    }
    
    public ObjectContainer getObjectContainer(StructureObject structureObject) {
        if (mask instanceof BlankMask) return new ObjectContainerBlankMask(structureObject);
        else if (!voxelsSizeOverLimit(true)) return new ObjectContainerVoxels(structureObject);
        else return new ObjectContainerIjRoi(structureObject);
        /*if (mask!=null) {
            if (mask instanceof BlankMask) return new ObjectContainerBlankMask(structureObject);
            else {
                if (voxels!=null) {
                    if (!voxelsSizeOverLimit(true)) return new ObjectContainerVoxels(structureObject);
                    else if (!voxelsSizeOverLimit(false)) return new ObjectContainerVoxelsDB(structureObject);
                    else return new ObjectContainerImage(structureObject);
                } else {
                    if (!maskSizeOverLimit(true)) return new ObjectContainerVoxels(structureObject);
                    else if (!maskSizeOverLimit(false)) return new ObjectContainerVoxelsDB(structureObject);
                    else return new ObjectContainerImage(structureObject);
                }
            }
        } else if (voxels!=null) {
            if (voxelsSizeOverLimit(false)) return new ObjectContainerImage(structureObject);
            else if (voxelsSizeOverLimit(true)) return new ObjectContainerVoxelsDB(structureObject);
            else return new ObjectContainerVoxels(structureObject);
        } else return null;*/
    }
    
    public void setVoxelValues(Image image, boolean useOffset) {
        if (useOffset) {
            for (Voxel v : getVoxels()) v.value=image.getPixelWithOffset(v.x, v.y, v.z);
        } else {
            for (Voxel v : getVoxels()) v.value=image.getPixel(v.x, v.y, v.z);
        }
    }
    /**
     * Draws with the offset of the object, without using the offset of the image
     * @param image
     * @param label 
     */
    public void draw(ImageInteger image, int label) {
        if (voxels !=null) {
            //logger.trace("drawing from VOXELS of object: {} with label: {} on image: {} ", this, label, image);
            for (Voxel v : getVoxels()) image.setPixel(v.x, v.y, v.z, label);
        }
        else {
            //logger.trace("drawing from IMAGE of object: {} with label: {} on image: {} mask offsetX: {} mask offsetY: {} mask offsetZ: {}", this, label, mask, mask.getOffsetX(), mask.getOffsetY(), mask.getOffsetZ());
            for (int z = 0; z < mask.getSizeZ(); ++z) {
                for (int y = 0; y < mask.getSizeY(); ++y) {
                    for (int x = 0; x < mask.getSizeX(); ++x) {
                        if (mask.insideMask(x, y, z)) {
                            image.setPixel(x+mask.getOffsetX(), y+mask.getOffsetY(), z+mask.getOffsetZ(), label);
                        }
                    }
                }
            }
        }
    }
    /**
     * Draws with a custom offset 
     * @param image its offset will be taken into account
     * @param label 
     * @param offset will be added to the object absolute position
     */
    public void draw(ImageInteger image, int label, BoundingBox offset) {
        if (offset==null) offset = new BoundingBox(0, 0, 0);
        if (voxels !=null) {
            //logger.trace("drawing from VOXELS of object: {} with label: {} on image: {} ", this, label, image);
            int offX = offset.getxMin()-image.getOffsetX();
            int offY = offset.getyMin()-image.getOffsetY();
            int offZ = offset.getzMin()-image.getOffsetZ();
            for (Voxel v : getVoxels()) if (image.contains(v.x+offX, v.y+offY, v.z+offZ)) image.setPixel(v.x+offX, v.y+offY, v.z+offZ, label);
        }
        else {
            int offX = offset.getxMin()+mask.getOffsetX()-image.getOffsetX();
            int offY = offset.getyMin()+mask.getOffsetY()-image.getOffsetY();
            int offZ = offset.getzMin()+mask.getOffsetZ()-image.getOffsetZ();
            //logger.trace("drawing from IMAGE of object: {} with label: {} on image: {} mask offsetX: {} mask offsetY: {} mask offsetZ: {}", this, label, mask, offX, offY, offZ);
            for (int z = 0; z < mask.getSizeZ(); ++z) {
                for (int y = 0; y < mask.getSizeY(); ++y) {
                    for (int x = 0; x < mask.getSizeX(); ++x) {
                        if (mask.insideMask(x, y, z)) {
                            if (image.contains(x+offX, y+offY, z+offZ)) image.setPixel(x+offX, y+offY, z+offZ, label);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Draws with a custom offset (the offset of the object and the image is not taken into account)
     * @param image
     * @param label 
     */
    public void drawWithoutObjectOffset(ImageInteger image, int label, BoundingBox offset) {
        if (offset==null) {
            draw(image, label);
            return;
        }
        if (voxels !=null) {
            //logger.trace("drawing from VOXELS of object: {} with label: {} on image: {} ", this, label, image);
            int offX = -getBounds().getxMin()+offset.getxMin();
            int offY = -getBounds().getyMin()+offset.getyMin();
            int offZ = -getBounds().getzMin()+offset.getzMin();
            for (Voxel v : getVoxels()) image.setPixel(v.x+offX, v.y+offY, v.z+offZ, label);
        }
        else {
            int offX = offset.getxMin();
            int offY = offset.getyMin();
            int offZ = offset.getzMin();
            //logger.trace("drawing from IMAGE of object: {} with label: {} on image: {} mask offsetX: {} mask offsetY: {} mask offsetZ: {}", this, label, mask, offX, offY, offZ);
            for (int z = 0; z < mask.getSizeZ(); ++z) {
                for (int y = 0; y < mask.getSizeY(); ++y) {
                    for (int x = 0; x < mask.getSizeX(); ++x) {
                        if (mask.insideMask(x, y, z)) {
                            image.setPixel(x+offX, y+offY, z+offZ, label);
                        }
                    }
                }
            }
        }
    }
    
    private boolean voxelsSizeOverLimit(boolean emb) {
        int limit = emb? (!is2D() ? MAX_VOX_3D_EMB :MAX_VOX_2D_EMB) : (!is2D() ? MAX_VOX_3D :MAX_VOX_2D);
        return getVoxels().size()>limit;
    }
    private boolean maskSizeOverLimit(boolean emb) {
        int limit = emb? (!is2D() ? MAX_VOX_3D_EMB :MAX_VOX_2D_EMB) : (!is2D() ? MAX_VOX_3D :MAX_VOX_2D);
        int count = 0;
        for (int z = 0; z < mask.getSizeZ(); ++z) {
            for (int y = 0; y < mask.getSizeY(); ++y) {
                for (int x = 0; x < mask.getSizeX(); ++x) {
                    if (mask.insideMask(x, y, z)) {
                        if (++count==limit) return true;
                    }
                }
            }
        }
        return false;
    }
    
    public boolean is2D() {
        return is2D;
        //return getBounds().getSizeZ()>1;
    }
    
    public Object3D translate(int offsetX, int offsetY, int offsetZ) {
        if (offsetX==0 && offsetY==0 && offsetZ==0) return this;
        if (mask!=null) mask.addOffset(offsetX, offsetY, offsetZ);
        if (bounds!=null) bounds.translate(offsetX, offsetY, offsetZ);
        if (voxels!=null) for (Voxel v : voxels) v.translate(offsetX, offsetY, offsetZ);
        if (center!=null) {
            center[0]+=offsetX;
            center[1]+=offsetY;
            if (center.length>2) center[2]+=offsetZ;
        }
        return this;
    }
    public Object3D translate(BoundingBox bounds) {
        if (bounds.isOffsetNull()) return this;
        else return translate(bounds.getxMin(), bounds.getyMin(), bounds.getzMin()); 
    }

    public Object3D setLabel(int label) {
        this.label=label;
        return this;
    }
}
