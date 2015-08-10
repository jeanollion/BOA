package dataStructure.objects;

import dataStructure.containers.ImageDAO;
import dataStructure.containers.ObjectContainerImage;
import dataStructure.containers.ObjectContainer;
import static dataStructure.containers.ObjectContainer.MAX_VOX;
import dataStructure.containers.ObjectContainerBlankMask;
import dataStructure.containers.ObjectContainerVoxels;
import image.BlankMask;
import image.BoundingBox;
import image.ImageByte;
import image.ImageInteger;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 
 * @author jollion
 * 
 */
public class Object3D {
    private final static Logger logger = LoggerFactory.getLogger(Object3D.class);
    protected float scaleXY, scaleZ;
    protected ImageInteger mask; //lazy -> use getter
    BoundingBox bounds;
    protected int label;
    protected ArrayList<Voxel3D> voxels; //lazy -> use getter // coordonnées des voxel -> par rapport au parent
    /**
     * 
     * @param mask : image containing only the object, and whose bounding box is the same as the one of the object
     */
    public Object3D(ImageInteger mask, int label) {
        this.mask=mask;
        this.bounds=mask.getBoundingBox();
        this.scaleXY=mask.getScaleXY();
        this.scaleZ=mask.getScaleZ();
        this.label=label;
    }
    
    public Object3D(ArrayList<Voxel3D> voxels, int label, float scaleXY, float scaleZ) {
        this.voxels=voxels;
        this.scaleXY=scaleXY;
        this.scaleZ=scaleZ;
        this.label=label;
        createBoundsFromVoxels();
    }
    
    public Object3D(ArrayList<Voxel3D> voxels, int label, float scaleXY, float scaleZ, BoundingBox bounds) {
        this.voxels=voxels;
        this.scaleXY=scaleXY;
        this.scaleZ=scaleZ;
        this.label=label;
        this.bounds=bounds;
    }
    
    public int getLabel() {
        return label;
    }
    
    protected void createMask() {
        mask = new ImageByte("", getBounds().getImageProperties("", scaleXY, scaleZ));
        for (Voxel3D v : voxels) mask.setPixelWithOffset(v.x, v.y, v.z, 1);
    }

    protected void createVoxels() {
        voxels=new ArrayList<Voxel3D>();
        for (int z = 0; z < mask.getSizeZ(); ++z) {
            for (int y = 0; y < mask.getSizeY(); ++y) {
                for (int x = 0; x < mask.getSizeX(); ++x) {
                    if (mask.insideMask(x, y, z)) {
                        voxels.add(new Voxel3D(x+mask.getOffsetX(), y+mask.getOffsetY(), z+mask.getOffsetZ()));
                    }
                }
            }
        }
    }
    
    public ImageInteger getMask() {
        if (mask==null && voxels!=null) createMask();
        return mask;
    }
    
    public ArrayList<Voxel3D> getVoxels() {
        if (voxels==null) createVoxels();
        return voxels;
    }

    public float getScaleXY() {
        return scaleXY;
    }

    public float getScaleZ() {
        return scaleZ;
    }
    
    protected void createBoundsFromVoxels() {
        bounds = new BoundingBox();
        for (Voxel3D v : voxels) bounds.expand(v);
        logger.debug("create bounds from voxels: {}", bounds);
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
            if (mask instanceof BlankMask) return new ObjectContainerBlankMask(bounds, scaleXY, scaleZ);
            else {
                if (voxels!=null) {
                    if (voxels.size()<MAX_VOX) return new ObjectContainerVoxels(this);
                    else return new ObjectContainerImage(structureObject, this);
                } else {
                    if (mask.getSizeXYZ()<MAX_VOX*2) {
                        if (getVoxels().size()<MAX_VOX) return new ObjectContainerVoxels(this);
                        else return new ObjectContainerImage(structureObject, this);
                    } else return new ObjectContainerImage(structureObject, this);
                }
            }
        } else if (voxels!=null) {
            if (voxels.size()<MAX_VOX) return new ObjectContainerImage(structureObject, this);
            else return new ObjectContainerImage(structureObject, this);
        } else return null;
    }
    
    public void draw(ImageInteger mask, int label) {
        if (voxels !=null) for (Voxel3D v : getVoxels()) mask.setPixel(v.x, v.y, v.z, label);
        else {
            for (int z = 0; z < mask.getSizeZ(); ++z) {
                for (int y = 0; y < mask.getSizeY(); ++y) {
                    for (int x = 0; x < mask.getSizeX(); ++x) {
                        if (mask.insideMask(x, y, z)) {
                            mask.setPixel(x, y, z, label);
                        }
                    }
                }
            }
        }
    }
    
}
