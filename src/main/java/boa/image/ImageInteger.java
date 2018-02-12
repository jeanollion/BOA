package boa.image;

import java.util.TreeMap;
import java.util.stream.IntStream;

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
    
    public static ImageInteger createEmptyLabelImage(String name, int maxLabelNumber, ImageProperties properties) {
        if (maxLabelNumber<=255) return new ImageByte(name, properties);
        else if (maxLabelNumber<=65535) return new ImageShort(name, properties);
        else return new ImageInt(name, properties);
    }
    
    public static int getMaxValue(ImageInteger image, boolean limitToShort) {
        if (image instanceof ImageByte) return 255;
        else if (image instanceof ImageShort || limitToShort) return 65535;
        else return Integer.MAX_VALUE;
    }
    
    @Override public abstract <T extends Image> T duplicate(String name);
    public abstract int getPixelInt(int x, int y, int z);
    public abstract int getPixelInt(int xy, int z);
    public abstract int getPixelIntWithOffset(int x, int y, int z);
    public abstract int getPixelIntWithOffset(int xy, int z);
    public abstract void setPixel(int x, int y, int z, int value);
    public abstract void setPixelWithOffset(int x, int y, int z, int value);
    public abstract void setPixel(int xy, int z, int value);
    public abstract void setPixelWithOffset(int xy, int z, int value);
    public abstract IntStream streamInt();
    public abstract IntStream streamIntPlane(int z);
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

    public ImageByte cropLabel(int label, BoundingBox bounds) {
        //bounds.trimToImage(this);
        ImageByte res = new ImageByte(name, bounds.getImageProperties("", scaleXY, scaleZ));
        byte[][] pixels = res.getPixelArray();
        res.setCalibration(this);
        int x_min = bounds.getxMin();
        int y_min = bounds.getyMin();
        int z_min = bounds.getzMin();
        int x_max = bounds.getxMax();
        int y_max = bounds.getyMax();
        int z_max = bounds.getzMax();
        res.resetOffset().addOffset(bounds);
        int sX = res.getSizeX();
        int oZ = -z_min;
        int oY_i = 0;
        int oX = 0;
        oX=-x_min;
        if (x_min <= -1) {
            x_min = 0;
        }
        if (x_max >= sizeX) {
            x_max = sizeX - 1;
        }
        if (y_min <= -1) {
            oY_i = -sX * y_min;
            y_min = 0;
        }
        if (y_max >= sizeY) {
            y_max = sizeY - 1;
        }
        if (z_min <= -1) {
            z_min = 0;
        }
        if (z_max >= sizeZ) {
            z_max = sizeZ - 1;
        }
        for (int z = z_min; z <= z_max; ++z) {
            int oY = oY_i;
            for (int y = y_min; y <= y_max; ++y) {
                for (int x = x_min; x<=x_max; ++x) {
                    if (getPixelInt(x, y, z) == label) {
                        pixels[z + oZ][oY + x + oX] = (byte) 1;
                    }
                }
                oY += sX;
            }
        }
        return res;
    }
    
    /**
     * 
     * @param startLabel if (startLabel==-1) startLabel = max+1
     * @param masks 
     */
    public abstract void appendBinaryMasks(int startLabel, ImageMask... masks);
    
    public static ImageInteger mergeBinary(ImageProperties properties, ImageMask... masks) {
        if (masks==null || masks.length==0) return new ImageByte("merge", properties);
        ImageInteger res;
        res = createEmptyLabelImage("merge", masks.length, properties);
        res.appendBinaryMasks(1, masks);
        return res;
    }
}
