package image;

import java.util.TreeMap;

public abstract class ImageInteger extends Image implements ImageMask {

    protected ImageInteger(String name, int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ, float scaleXY, float scaleZ) {
        super(name, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ, scaleXY, scaleZ);
    }
    
    protected ImageInteger(String name, int sizeX, int sizeY, int sizeZ) {
        super(name, sizeX, sizeY, sizeZ);
    }
    
    protected ImageInteger(String name, ImageProperties properties) {
        super(name, properties);
    } 

    public abstract int getPixelInt(int x, int y, int z);
    public abstract int getPixelInt(int xy, int z);
    public abstract void setPixel(int x, int y, int z, int value);
    public abstract void setPixel(int xy, int z, int value);
    
    /**
     * 
     * @param addBorder
     * @return TreeMap with Key (Integer) = label of the object / Value Bounding Box of the object
     * @see BoundingBox
     */
    public TreeMap<Integer, BoundingBox> getBounds(boolean addBorder) {
        TreeMap<Integer, BoundingBox> bounds = new TreeMap<Integer, BoundingBox>();
        for (int z = 0; z < sizeZ; ++z) {
            for (int y = 0; y < sizeY; ++y) {
                for (int x = 0; x < sizeX; ++x) {
                    int value = getPixelInt(x + y * sizeX, z);
                    if (value != 0) {
                        BoundingBox bds = bounds.get(value);
                        if (bds != null) {
                            bds.expandX(x);
                            bds.expandY(y);
                            bds.expandZ(z);
                            bds.addToCounter();
                        } else {
                            bds= new BoundingBox(x, y, z);
                            bounds.put(value, bds);
                        }
                    }
                }
            }
        }
        if (addBorder) {
            for (BoundingBox bds : bounds.values()) {
                bds.addBorder();
                //bds.trimToImage(this);
            }
        }
        return bounds;
    }

}
