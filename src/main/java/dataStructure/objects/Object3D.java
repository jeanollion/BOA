package dataStructure.objects;

import com.google.common.collect.Sets;
import dataStructure.containers.ImageDAO;
import dataStructure.containers.ObjectContainerImage;
import dataStructure.containers.ObjectContainer;
import static dataStructure.containers.ObjectContainer.MAX_VOX_3D;
import static dataStructure.containers.ObjectContainer.MAX_VOX_2D;
import static dataStructure.containers.ObjectContainer.MAX_VOX_2D_EMB;
import static dataStructure.containers.ObjectContainer.MAX_VOX_3D_EMB;
import dataStructure.containers.ObjectContainerBlankMask;
import dataStructure.containers.ObjectContainerDB;
import dataStructure.containers.ObjectContainerVoxels;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 
 * @author jollion
 * 
 */
public class Object3D {
    public final static Logger logger = LoggerFactory.getLogger(Object3D.class);
    protected ImageInteger mask; //lazy -> use getter
    protected BoundingBox bounds;
    protected int label;
    protected ArrayList<Voxel> voxels; //lazy -> use getter // coordonnées des voxel -> par rapport au parent
    protected float scaleXY=1, scaleZ=1;
    /**
     * Voxel
     * @param mask : image containing only the object, and whose bounding box is the same as the one of the object
     */
    public Object3D(ImageInteger mask, int label) {
        this.mask=mask;
        this.bounds=mask.getBoundingBox();
        this.label=label;
        this.scaleXY=mask.getScaleXY();
        this.scaleZ=mask.getScaleZ();
    }
    
    public Object3D(ArrayList<Voxel> voxels, int label, float scaleXY, float scaleZ) {
        this.voxels=voxels;
        this.label=label;
    }
    
    public Object3D(ArrayList<Voxel> voxels, int label, BoundingBox bounds, float scaleXY, float scaleZ) {
        this.voxels=voxels;
        this.label=label;
        this.bounds=bounds;
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
    
    public double[] getCenter() {
        double[] center = new double[3];
        for (Voxel v : getVoxels()) {
            center[0] += v.x;
            center[1] += v.y;
            center[1] += v.z;
        }
        double count = voxels.size();
        center[0]/=count;
        center[1]/=count;
        center[2]/=count;
        return center;
    }
    public double[] getCenter(Image image) {
        double[] center = new double[3];
        double count = 0;
        double value;
        for (Voxel v : getVoxels()) {
            value = image.getPixel(v.x, v.y, v.z);
            center[0] += v.x * value;
            center[1] += v.y * value;
            center[1] += v.z * value;
        }
        center[0]/=count;
        center[1]/=count;
        center[2]/=count;
        return center;
    }
    
    public synchronized void addVoxels(List<Voxel> voxelsToAdd) {
        this.voxels.addAll(voxelsToAdd);
        this.bounds=null;
        this.mask=null;
    }
    
    protected void createMask() {
        ImageByte mask_ = new ImageByte("", getBounds().getImageProperties(scaleXY, scaleZ));
        for (Voxel v : voxels) {
            //if (!mask_.containsWithOffset(v.x, v.y, v.z)) logger.error("voxel out of bounds: {}", v); // can happen if bounds were not updated before the object was saved
            mask_.setPixelWithOffset(v.x, v.y, v.z, 1);
        }
        this.mask=mask_;
    }

    protected void createVoxels() {
        ArrayList<Voxel> voxels_=new ArrayList<Voxel>();
        for (int z = 0; z < mask.getSizeZ(); ++z) {
            for (int y = 0; y < mask.getSizeY(); ++y) {
                for (int x = 0; x < mask.getSizeX(); ++x) {
                    if (mask.insideMask(x, y, z)) {
                        voxels_.add( new Voxel(x + mask.getOffsetX(), y + mask.getOffsetY(), z + mask.getOffsetZ()));
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
                if (mask==null && voxels!=null) {
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
    
    public ArrayList<Voxel> getVoxels() {
        if (voxels==null) {
            synchronized(this) { // "Double-Checked Locking"
                if (voxels==null) {
                    createVoxels();
                }
            }
        }
        return voxels;
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
    
    public Set<Voxel> getIntersection(Object3D other) {
        if (!this.getBounds().hasIntersection(other.getBounds())) return Collections.emptySet();
        else return Sets.intersection(Sets.newHashSet(getVoxels()), Sets.newHashSet(other.getVoxels()));
    }
    
    public void merge(Object3D other) {
        //int nb = getVoxels().size();
        this.getVoxels().addAll(other.getVoxels()); // TODO check for duplicates?
        //logger.debug("merge:  {} + {}, nb voxel avant: {}, nb voxels après: {}", this.getLabel(), other.getLabel(), nb,getVoxels().size() );
        this.mask=null; // reset mask
        this.bounds=null; // reset bounds
    }
    
    public ObjectContainer getObjectContainer(StructureObject structureObject) {
        if (mask!=null) {
            if (mask instanceof BlankMask) return new ObjectContainerBlankMask(structureObject);
            else {
                if (voxels!=null) {
                    if (!voxelsSizeOverLimit(true)) return new ObjectContainerVoxels(structureObject);
                    else if (!voxelsSizeOverLimit(false)) return new ObjectContainerDB(structureObject);
                    else return new ObjectContainerImage(structureObject);
                } else {
                    if (!maskSizeOverLimit(true)) return new ObjectContainerVoxels(structureObject);
                    else if (!maskSizeOverLimit(false)) return new ObjectContainerDB(structureObject);
                    else return new ObjectContainerImage(structureObject);
                }
            }
        } else if (voxels!=null) {
            if (voxelsSizeOverLimit(false)) return new ObjectContainerImage(structureObject);
            else if (voxelsSizeOverLimit(true)) return new ObjectContainerDB(structureObject);
            else return new ObjectContainerVoxels(structureObject);
        } else return null;
    }
    
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
    
    public void draw(ImageInteger image, int label, BoundingBox offset) {
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
        int limit = emb? (is3D() ? MAX_VOX_3D_EMB :MAX_VOX_2D_EMB) : (is3D() ? MAX_VOX_3D :MAX_VOX_2D);
        return voxels.size()>limit;
    }
    private boolean maskSizeOverLimit(boolean emb) {
        int limit = emb? (is3D() ? MAX_VOX_3D_EMB :MAX_VOX_2D_EMB) : (is3D() ? MAX_VOX_3D :MAX_VOX_2D);
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
    
    public boolean is3D() {
        return getBounds().getSizeZ()>1;
    }
    
    public Object3D addOffset(int offsetX, int offsetY, int offsetZ) {
        if (mask!=null) mask.addOffset(offsetX, offsetY, offsetZ);
        if (bounds!=null) bounds.translate(offsetX, offsetY, offsetZ);
        if (voxels!=null) for (Voxel v : voxels) v.translate(offsetX, offsetY, offsetZ);
        return this;
    }
    public Object3D addOffset(BoundingBox bounds) {
        return addOffset(bounds.getxMin(), bounds.getyMin(), bounds.getzMin()); 
    }
    public Object3D setLabel(int label) {
        this.label=label;
        return this;
    }
}
