package dataStructure.objects;

import dataStructure.containers.ImageDAO;
import dataStructure.containers.ObjectContainerImage;
import dataStructure.containers.ObjectContainer;
import static dataStructure.containers.ObjectContainer.MAX_VOX_3D;
import static dataStructure.containers.ObjectContainer.MAX_VOX_2D;
import dataStructure.containers.ObjectContainerBlankMask;
import dataStructure.containers.ObjectContainerVoxels;
import image.BlankMask;
import image.BoundingBox;
import image.ImageByte;
import image.ImageInteger;
import image.ImageProperties;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 
 * @author jollion
 * 
 */
public class Object3D<T extends Voxel> {
    public final static Logger logger = LoggerFactory.getLogger(Object3D.class);
    protected ImageInteger mask; //lazy -> use getter
    protected BoundingBox bounds;
    protected int label;
    protected ArrayList<T> voxels; //lazy -> use getter // coordonnées des voxel -> par rapport au parent
    protected Class<T> type;
    /**
     * 
     * @param mask : image containing only the object, and whose bounding box is the same as the one of the object
     */
    public Object3D(ImageInteger mask, int label) {
        this.mask=mask;
        this.bounds=mask.getBoundingBox();
        this.label=label;
    }
    
    public Object3D(ImageInteger mask, int label, Class<T> type) {
        this(mask, label);
        this.type=type;
    }
    
    public Object3D(ArrayList<T> voxels, int label) {
        this.voxels=voxels;
        this.label=label;
    }
    
    public Object3D(ArrayList<T> voxels, int label, BoundingBox bounds) {
        this.voxels=voxels;
        this.label=label;
        this.bounds=bounds;
    }
    
    public int getLabel() {
        return label;
    }
    
    protected void createMask() {
        mask = new ImageByte("", getBounds().getImageProperties("", 1, 1));
        for (T v : voxels) mask.setPixelWithOffset(v.x, v.y, v.getZ(), 1);
    }

    protected void createVoxels() {
        voxels=new ArrayList<T>();
        if (mask.getSizeZ() > 1 || mask.getOffsetZ()>0 || (type!=null && type.equals(Voxel3D.class))) {
            for (int z = 0; z < mask.getSizeZ(); ++z) {
                for (int y = 0; y < mask.getSizeY(); ++y) {
                    for (int x = 0; x < mask.getSizeX(); ++x) {
                        if (mask.insideMask(x, y, z)) {
                            voxels.add((T) new Voxel3D(x + mask.getOffsetX(), y + mask.getOffsetY(), z + mask.getOffsetZ()));
                        }
                    }
                }
            }
        } else {
            for (int y = 0; y < mask.getSizeY(); ++y) {
                for (int x = 0; x < mask.getSizeX(); ++x) {
                    if (mask.insideMask(x, y, 0)) {
                        voxels.add((T) new Voxel2D(x + mask.getOffsetX(), y + mask.getOffsetY()));
                    }
                }
            }
        }
    }
    
    public ImageProperties getImageProperties() {
        if (mask!=null) return mask;
        else return getBounds().getImageProperties("", 1, 1);
    }
    
    public ImageInteger getMask() {
        if (mask==null && voxels!=null) createMask();
        return mask;
    }
    
    public ArrayList<T> getVoxels() {
        if (voxels==null) createVoxels();
        return voxels;
    }
    
    protected void createBoundsFromVoxels() {
        bounds = new BoundingBox();
        for (T v : voxels) bounds.expand(v);
        //logger.trace("create bounds from voxels: {}", bounds);
    }

    public BoundingBox getBounds() {
        if (bounds==null) {
            if (mask!=null) bounds=mask.getBoundingBox();
            else if (voxels!=null) createBoundsFromVoxels(); // pas d'offset
        }
        return bounds;
    }
    
    public ObjectContainer getObjectContainer(StructureObject structureObject) {
        if (mask!=null) {
            if (mask instanceof BlankMask) return new ObjectContainerBlankMask(structureObject);
            else {
                if (voxels!=null) {
                    if (!voxelsSizeOverLimit()) return new ObjectContainerVoxels(structureObject);
                    else return new ObjectContainerImage(structureObject);
                } else {
                    if (!maskSizeOverLimit()) return new ObjectContainerVoxels(structureObject);
                    else return new ObjectContainerImage(structureObject);
                }
            }
        } else if (voxels!=null) {
            if (voxelsSizeOverLimit()) return new ObjectContainerImage(structureObject);
            else return new ObjectContainerVoxels(structureObject);
        } else return null;
    }
    
    public void draw(ImageInteger image, int label) {
        if (voxels !=null) {
            //logger.trace("drawing from VOXELS of object: {} with label: {} on image: {} ", this, label, image);
            for (T v : getVoxels()) image.setPixel(v.x, v.y, v.getZ(), label);
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
            for (T v : getVoxels()) image.setPixel(v.x+offX, v.y+offY, v.getZ()+offZ, label);
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
    
    private boolean voxelsSizeOverLimit() {
        if (is3D()) return voxels.size()>MAX_VOX_3D;
        else return voxels.size()>MAX_VOX_2D;
    }
    private boolean maskSizeOverLimit() {
        int limit = is3D()?MAX_VOX_3D:MAX_VOX_2D;
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
        if (!voxels.isEmpty()) return (voxels.get(0) instanceof Voxel3D);
        else if (mask!=null) return mask.getSizeZ()>1;
        else if (bounds!=null) return bounds.getSizeZ()>1;
        else return false;
    }
    
    public Object3D<T> addOffset(int offsetX, int offsetY, int offsetZ) {
        if (mask!=null) mask.addOffset(offsetX, offsetY, offsetZ);
        if (bounds!=null) bounds.translate(offsetX, offsetY, offsetZ);
        if (voxels!=null) for (Voxel v : voxels) v.translate(offsetX, offsetY, offsetZ);
        return this;
    }
    public Object3D<T> addOffset(BoundingBox bounds) {
        return addOffset(bounds.getxMin(), bounds.getyMin(), bounds.getzMin()); 
    }
    public Object3D<T> setLabel(int label) {
        this.label=label;
        return this;
    }
}
