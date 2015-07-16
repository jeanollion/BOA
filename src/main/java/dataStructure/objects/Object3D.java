package dataStructure.objects;

import dataStructure.containers.ImageContainer;
import dataStructure.containers.ImageIntegerContainer;
import dataStructure.containers.ObjectContainer;
import dataStructure.containers.ObjectContainerBlankMask;
import dataStructure.containers.ObjectContainerVoxels;
import image.BlankMask;
import image.BoundingBox;
import image.ImageByte;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.Set;
import org.mongodb.morphia.annotations.Transient;
/**
 * 
 * @author jollion
 * 
 */
public class Object3D {
    static final int MAX_VOX = 5000; //(10 vox ~ 1kb)
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
        for (Voxel3D v : voxels) mask.setPixel(v.x, v.y, v.z, 1);
    }

    protected void createVoxels() {
        for (int z = 0; z < mask.getSizeZ(); ++z) {
            for (int y = 0; y < mask.getSizeY(); ++y) {
                for (int x = 0; x < mask.getSizeX(); ++x) {
                    if (mask.contains(x, y, z)) {
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
    }

    public BoundingBox getBounds() {
        /*if (bounds==null) {
            if (mask!=null) bounds=mask.getBoundingBox();
            else if (voxels!=null) createBoundsFromVoxels(); // pas d'offset
        }*/
        return bounds;
    }
    
    public ObjectContainer getObjectContainer(String path) {
        if (mask!=null) {
            if (mask instanceof BlankMask) return new ObjectContainerBlankMask(bounds);
            else {
                if (voxels!=null) {
                    if (voxels.size()<MAX_VOX) return new ObjectContainerVoxels(this);
                    else return new ImageIntegerContainer(path, mask, label);
                } else {
                    if (mask.getSizeXYZ()<MAX_VOX*2) {
                        if (getVoxels().size()<MAX_VOX) return new ObjectContainerVoxels(this);
                        else return new ImageIntegerContainer(path, mask, label);
                    } else return new ImageIntegerContainer(path, mask, label);
                }
            }
        } else if (voxels!=null) {
            if (voxels.size()<MAX_VOX) return new ObjectContainerVoxels(this);
            else return new ImageIntegerContainer(path, getMask(), label);
        } else return null;
    }
    
    public void draw(ImageInteger mask, int label) {
        for (Voxel3D v : getVoxels()) mask.setPixel(v.x, v.y, v.z, label);
    }
    
}
