package boa.data_structure;

import com.google.common.collect.Sets;
import boa.data_structure.region_container.ObjectContainer;
import static boa.data_structure.region_container.ObjectContainer.MAX_VOX_3D;
import static boa.data_structure.region_container.ObjectContainer.MAX_VOX_2D;
import static boa.data_structure.region_container.ObjectContainer.MAX_VOX_2D_EMB;
import static boa.data_structure.region_container.ObjectContainer.MAX_VOX_3D_EMB;
import boa.data_structure.region_container.ObjectContainerBlankMask;
import boa.data_structure.region_container.ObjectContainerIjRoi;
import boa.data_structure.region_container.ObjectContainerVoxels;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.BoundingBox.LoopFunction;
import boa.image.BoundingBox.LoopFunction2;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.ImageMask2D;
import boa.image.ImageProperties;
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
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.utils.ArrayUtil;
import boa.utils.Utils;
import static boa.utils.Utils.comparator;
import static boa.utils.Utils.comparatorInt;
/**
 * 
 * @author jollion
 * 
 */
public class Region {
    public final static Logger logger = LoggerFactory.getLogger(Region.class);
    protected ImageInteger mask; //lazy -> use getter // bounds par rapport au root si absoluteLandMark==true, au parent sinon
    protected BoundingBox bounds;
    protected int label;
    protected List<Voxel> voxels; //lazy -> use getter // coordonnées des voxel = coord dans l'image mask + offset du masque.  
    protected float scaleXY=1, scaleZ=1;
    protected boolean absoluteLandmark=false; // false = coordinates relative to the direct parent
    protected double quality=Double.NaN;
    protected double[] center;
    final protected boolean is2D;
    /**
     * @param mask : image containing only the object, and whose bounding box is the same as the one of the object
     */
    public Region(ImageInteger mask, int label, boolean is2D) {
        this.mask=mask;
        this.bounds=mask.getBoundingBox();
        this.label=label;
        this.scaleXY=mask.getScaleXY();
        this.scaleZ=mask.getScaleZ();
        this.is2D=is2D;
    }
    
    public Region(List<Voxel> voxels, int label, boolean is2D, float scaleXY, float scaleZ) {
        this.voxels=voxels;
        this.label=label;
        this.scaleXY=scaleXY;
        this.scaleZ=scaleXY;
        this.is2D=is2D;
    }
    public Region(final Voxel voxel, int label, boolean is2D, float scaleXY, float scaleZ) {
        this(new ArrayList<Voxel>(){{add(voxel);}}, label, is2D, scaleXY, scaleZ);
    }
    
    public Region(List<Voxel> voxels, int label, BoundingBox bounds, boolean is2D, float scaleXY, float scaleZ) {
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
            return new Region((ImageInteger)mask.duplicate(""), label, is2D).setIsAbsoluteLandmark(absoluteLandmark).setQuality(quality).setCenter(ArrayUtil.duplicate(center));
        }
        else if (this.voxels!=null) {
            ArrayList<Voxel> vox = new ArrayList<> (voxels.size());
            for (Voxel v : voxels) vox.add(v.duplicate());
            if (bounds==null) return new Region(vox, label, is2D, scaleXY, scaleZ).setIsAbsoluteLandmark(absoluteLandmark).setQuality(quality).setCenter(ArrayUtil.duplicate(center));
            else return new Region(vox, label, bounds.duplicate(), is2D, scaleXY, scaleZ).setIsAbsoluteLandmark(absoluteLandmark).setQuality(quality).setCenter(ArrayUtil.duplicate(center));
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
    
    public Region setCenter(double[] center) {
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
        ArrayList<Voxel> voxels_=new ArrayList<>();
        /*if (is2D()) {
            for (int y = 0; y < mask.getSizeY(); ++y) {
                for (int x = 0; x < mask.getSizeX(); ++x) {
                    if (mask.insideMask(x, y, 0)) {
                        voxels_.add( new Voxel2D(x + mask.getOffsetX(), y + mask.getOffsetY(), mask.getOffsetZ()));
                    }
                }
            }
        } else {*/
            for (int z = 0; z < mask.getSizeZ(); ++z) {
                for (int y = 0; y < mask.getSizeY(); ++y) {
                    for (int x = 0; x < mask.getSizeX(); ++x) {
                        if (mask.insideMask(x, y, z)) {
                            voxels_.add( new Voxel(x + mask.getOffsetX(), y + mask.getOffsetY(), z + mask.getOffsetZ()));
                        }
                    }
                }
            }
        //}
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
    /**
     * 
     * @return subset of object's voxels that are in contact with background, edge or other object
     */
    public List<Voxel> getContour() {
        ImageMask mask = getMask();
        EllipsoidalNeighborhood neigh = !is2D() ? new EllipsoidalNeighborhood(1, 1, true) : new EllipsoidalNeighborhood(1, true); // 1 and not 1.5 -> diagonal
        
        for (int i = 0; i<neigh.dx.length; ++i) {
            neigh.dx[i]-=mask.getOffsetX();
            neigh.dy[i]-=mask.getOffsetY();
            if (!is2D()) neigh.dz[i]-=mask.getOffsetZ();
        }
        ArrayList<Voxel> res = new ArrayList<>();
        for (Voxel v: getVoxels()) if (touchBorder(v, neigh, mask)) res.add(v);
        // TODO : method without getVoxels 
        //logger.debug("contour: {} (total: {})", res.size(), getVoxels().size());
        return res;
    }
    /**
     * 
     * @param v
     * @param neigh with offset
     * @param mask
     * @return 
     */
    private static boolean touchBorder(Voxel v, EllipsoidalNeighborhood neigh, ImageMask mask) {
        int xx, yy, zz;
        for (int i = 0; i<neigh.dx.length; ++i) {
            xx=v.x+neigh.dx[i];
            yy=v.y+neigh.dy[i];
            zz=v.z+neigh.dz[i];
            if (!mask.contains(xx, yy, zz) || !mask.insideMask(xx, yy, zz)) return true;
        }
        return false;
    }
    public void erode(Neighborhood neigh) {
        mask = Filters.min(getMask(), null, neigh);
        voxels = null; // reset voxels
        // TODO reset bounds?
    }
    /**
     * 
     * @param image
     * @param threshold
     * @param removeIfLowerThanThreshold
     * @param contour will be modified if a set
     */
    public void erodeContours(Image image, double threshold, boolean removeIfLowerThanThreshold, Collection<Voxel> contour) {
        TreeSet<Voxel> heap = contour==null ? new TreeSet<>(getContour()) : new TreeSet<>(contour);
        EllipsoidalNeighborhood neigh = !this.is2D() ? new EllipsoidalNeighborhood(1.5, 1.5, true) : new EllipsoidalNeighborhood(1.5, true);
        ImageInteger mask = getMask();
        int xx, yy, zz;
        while(!heap.isEmpty()) {
            Voxel v = heap.pollFirst();
            if (removeIfLowerThanThreshold ? image.getPixel(v.x, v.y, v.z)<=threshold : image.getPixel(v.x, v.y, v.z)>=threshold) {
                mask.setPixel(v.x-mask.getOffsetX(), v.y-mask.getOffsetY(), v.z-mask.getOffsetZ(), 0);
                for (int i = 0; i<neigh.dx.length; ++i) {
                    xx=v.x+neigh.dx[i];
                    yy=v.y+neigh.dy[i];
                    zz=v.z+neigh.dz[i];
                    if (mask.contains(xx-mask.getOffsetX(), yy-mask.getOffsetY(), zz-mask.getOffsetZ()) && mask.insideMask(xx-mask.getOffsetX(), yy-mask.getOffsetY(), zz-mask.getOffsetZ())) {
                        heap.add(new Voxel(xx, yy, zz));
                    }
                }
            }
        }
        // check if 2 objects and erase all but smallest
        List<Region> objects = ImageLabeller.labelImageListLowConnectivity(mask);
        if (objects.size()>1) {
            objects.remove(Collections.max(objects, comparatorInt(o->o.getSize())));
            for (Region toErase: objects) toErase.draw(mask, 0);
        }
        voxels = null; // reset voxels
        // TODO reset bounds ?
    }
    
    public void dilateContours(Image image, double threshold, boolean addIfHigherThanThreshold, Collection<Voxel> contour, ImageInteger labelMap) {
        //if (labelMap==null) labelMap = Collections.EMPTY_SET;
        TreeSet<Voxel> heap = contour==null? new TreeSet<>(getContour()) : new TreeSet<>(contour);
        heap.removeIf(v->{
            int l = labelMap.getPixelInt(v.x, v.y, v.z);
            return l>0 && l!=label;
        });
        //heap.removeAll(labelMap);
        Set<Voxel> voxels = new HashSet<>(getVoxels());
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
        this.voxels = new ArrayList<>(voxels); 
        bounds = null; // reset boudns
        this.mask=null; // reset voxels
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
    /*
    // TODO faire une methode plus optimisée qui utilise les masques uniquement
    public int getIntersectionCountMask(Region other, BoundingBox offset) {
        if (offset==null) offset=new BoundingBox(0, 0, 0);
        if (!this.getBounds().hasIntersection(other.getBounds().duplicate().translate(offset))) return 0;
        else {
            ImageMask otherMask = other.getMask();
            int count = 0;
            int offX = otherMask.getOffsetX()+offset.getxMin();
            int offY = otherMask.getOffsetY()+offset.getyMin();
            int offZ = otherMask.getOffsetZ()+offset.getzMin();
            for (Voxel v : this.getVoxels()) {
                if (otherMask.insideMask(v.x-offX, v.y-offY, v.z-offZ)) ++count;
            }
            return count;
        }
    }*/
    public boolean intersect(Region other) {
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
    public int getIntersectionCountMaskMask(Region other, BoundingBox offset, BoundingBox offsetOther) {
        BoundingBox otherBounds = offsetOther==null? other.getBounds().duplicate() : other.getBounds().duplicate().translate(offsetOther);
        BoundingBox thisBounds = offset==null? getBounds().duplicate() : getBounds().duplicate().translate(offset);
        final boolean inter2D = is2D() || other.is2D();
        if (inter2D) {
            if (!thisBounds.intersect2D(otherBounds)) return 0;
        } else {
            if (!thisBounds.intersect(otherBounds)) return 0;
        }
        
        
        final ImageMask mask = is2D() && !other.is2D() ? new ImageMask2D(getMask()) : getMask();
        final ImageMask otherMask = other.is2D() && !is2D() ? new ImageMask2D(other.getMask()) : other.getMask();
        BoundingBox inter = inter2D ? (!is2D() ? thisBounds.getIntersection2D(otherBounds):otherBounds.getIntersection2D(thisBounds)) : thisBounds.getIntersection(otherBounds);
        //logger.debug("off: {}, otherOff: {}, is2D: {} other Is2D: {}, inter: {}", thisBounds, otherBounds, is2D(), other.is2D(), inter);
        final int count[] = new int[1];
        final int offX = thisBounds.getxMin();
        final int offY = thisBounds.getyMin();
        final int offZ = thisBounds.getzMin();
        final int otherOffX = otherBounds.getxMin();
        final int otherOffY = otherBounds.getyMin();
        final int otherOffZ = otherBounds.getzMin();
        inter.loop((int x, int y, int z) -> {
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
    
    public Region getContainer(Collection<Region> parents, BoundingBox offset, BoundingBox offsetParent) {
        if (parents.isEmpty()) return null;
        Region currentParent=null;
        int currentIntersection=-1;
        for (Region p : parents) {
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
    
    public void merge(Region other) {
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
    public void draw(Image image, double value) {
        if (voxels !=null) {
            //logger.trace("drawing from VOXELS of object: {} with label: {} on image: {} ", this, label, image);
            for (Voxel v : getVoxels()) image.setPixel(v.x, v.y, v.z, value);
        }
        else {
            //logger.trace("drawing from IMAGE of object: {} with label: {} on image: {} mask offsetX: {} mask offsetY: {} mask offsetZ: {}", this, label, mask, mask.getOffsetX(), mask.getOffsetY(), mask.getOffsetZ());
            for (int z = 0; z < mask.getSizeZ(); ++z) {
                for (int y = 0; y < mask.getSizeY(); ++y) {
                    for (int x = 0; x < mask.getSizeX(); ++x) {
                        if (mask.insideMask(x, y, z)) {
                            image.setPixel(x+mask.getOffsetX(), y+mask.getOffsetY(), z+mask.getOffsetZ(), value);
                        }
                    }
                }
            }
        }
    }
    /**
     * Draws with a custom offset 
     * @param image its offset will be taken into account
     * @param value 
     * @param offset will be added to the object absolute position
     */
    public void draw(Image image, double value, BoundingBox offset) {
        if (offset==null) offset = new BoundingBox(0, 0, 0);
        if (voxels !=null) {
            //logger.trace("drawing from VOXELS of object: {} with value: {} on image: {} ", this, value, image);
            int offX = offset.getxMin()-image.getOffsetX();
            int offY = offset.getyMin()-image.getOffsetY();
            int offZ = offset.getzMin()-image.getOffsetZ();
            for (Voxel v : getVoxels()) if (image.contains(v.x+offX, v.y+offY, v.z+offZ)) image.setPixel(v.x+offX, v.y+offY, v.z+offZ, value);
        }
        else {
            int offX = offset.getxMin()+mask.getOffsetX()-image.getOffsetX();
            int offY = offset.getyMin()+mask.getOffsetY()-image.getOffsetY();
            int offZ = offset.getzMin()+mask.getOffsetZ()-image.getOffsetZ();
            //logger.trace("drawing from IMAGE of object: {} with value: {} on image: {} mask offsetX: {} mask offsetY: {} mask offsetZ: {}", this, value, mask, offX, offY, offZ);
            for (int z = 0; z < mask.getSizeZ(); ++z) {
                for (int y = 0; y < mask.getSizeY(); ++y) {
                    for (int x = 0; x < mask.getSizeX(); ++x) {
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
    public void drawWithoutObjectOffset(Image image, double value, BoundingBox offset) {
        if (offset==null) {
            draw(image, value);
            return;
        }
        if (voxels !=null) {
            //logger.trace("drawing from VOXELS of object: {} with value: {} on image: {} ", this, value, image);
            int offX = -getBounds().getxMin()+offset.getxMin();
            int offY = -getBounds().getyMin()+offset.getyMin();
            int offZ = -getBounds().getzMin()+offset.getzMin();
            for (Voxel v : getVoxels()) image.setPixel(v.x+offX, v.y+offY, v.z+offZ, value);
        }
        else {
            int offX = offset.getxMin();
            int offY = offset.getyMin();
            int offZ = offset.getzMin();
            //logger.trace("drawing from IMAGE of object: {} with value: {} on image: {} mask offsetX: {} mask offsetY: {} mask offsetZ: {}", this, value, mask, offX, offY, offZ);
            for (int z = 0; z < mask.getSizeZ(); ++z) {
                for (int y = 0; y < mask.getSizeY(); ++y) {
                    for (int x = 0; x < mask.getSizeX(); ++x) {
                        if (mask.insideMask(x, y, z)) {
                            image.setPixel(x+offX, y+offY, z+offZ, value);
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
    
    public Region translate(int offsetX, int offsetY, int offsetZ) {
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
    public Region translate(BoundingBox bounds) {
        if (bounds.isOffsetNull()) return this;
        else return translate(bounds.getxMin(), bounds.getyMin(), bounds.getzMin()); 
    }

    public Region setLabel(int label) {
        this.label=label;
        return this;
    }
}
