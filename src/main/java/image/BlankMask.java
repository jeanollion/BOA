package image;

import processing.neighborhood.Neighborhood;

public class BlankMask extends ImageInteger implements ImageMask {

    public BlankMask(String name, int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ, float scaleXY, float scaleZ) {
        super(name, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ, scaleXY, scaleZ);
    }

    public BlankMask(int sizeX, int sizeY, int sizeZ) {
        this("", sizeX, sizeY, sizeZ, 0, 0, 0, 1, 1);
    }
    
    public BlankMask(String name, int sizeX, int sizeY, int sizeZ) {
        this(name, sizeX, sizeY, sizeZ, 0, 0, 0, 1, 1);
    }
    
    public BlankMask(ImageProperties properties) {
        this("", properties);
    }
    
    public BlankMask(String name, ImageProperties properties) {
        this(name, properties.getSizeX(), properties.getSizeY(), properties.getSizeZ(), properties.getOffsetX(), properties.getOffsetY(), properties.getOffsetZ(), properties.getScaleXY(), properties.getScaleZ());
    }
    
    public BlankMask(String name, BoundingBox bounds, float scaleXY, float scaleZ) {
        this(name, bounds.getSizeX(), bounds.getSizeY(), bounds.getSizeZ(), bounds.getxMin(), bounds.getyMin(), bounds.getzMin(), scaleXY, scaleZ);
    }
    
    @Override
    public Image getZPlane(int idxZ) {
        return new BlankMask(name, sizeX, sizeY, 1, offsetX, offsetY, offsetZ+idxZ, scaleXY, scaleZ);
    }

    public boolean insideMask(int x, int y, int z) {
        //return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
        return true;
    }

    public boolean insideMask(int xy, int z) {
        return true;
    }
    
    @Override public int count() {
        return sizeZ * sizeXY;
    }
    
    
    public boolean insideMaskWithOffset(int x, int y, int z) {
        //x-=offsetX; y-=offsetY; z-=offsetZ;
        //return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
        return true;
    }

    public boolean insideMaskWithOffset(int xy, int z) {
        return true;
    }
    
    @Override
    public boolean contains(int x, int y, int z) {
        return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }

    @Override
    public boolean containsWithOffset(int x, int y, int z) {
        x-=offsetX; y-=offsetY; z-=offsetZ;
        return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }

    @Override
    public float getPixel(int x, int y, int z) {
        return 1;
    }
    
    @Override
    public float getPixel(int xz, int z) {
        return 1;
    }

    @Override
    public void setPixel(int x, int y, int z, double value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setPixel(int xy, int z, double value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    
    @Override
    public int getPixelIntWithOffset(int x, int y, int z) {
        return 1;
    }

    @Override
    public int getPixelIntWithOffset(int xy, int z) {
        return 1;
    }

    @Override
    public void setPixelWithOffset(int xy, int z, int value) {
    }

    @Override
    public float getPixelWithOffset(int x, int y, int z) {
        return 1;
    }

    @Override
    public float getPixelWithOffset(int xy, int z) {
        return 1;
    }

    @Override
    public void setPixelWithOffset(int xy, int z, double value) {
    }

    @Override
    public BlankMask duplicate(String name) {
        return new BlankMask(name, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ, scaleXY, scaleZ);
    }

    @Override
    public Object[] getPixelArray() {
        return TypeConverter.toByteMask(this, null, 1).getPixelArray();
    }

    @Override
    public Image newImage(String name, ImageProperties properties) {
        return new BlankMask(name, properties);
    }

    @Override
    public BlankMask crop(BoundingBox bounds) {
        return (BlankMask) cropI(bounds);
    }
    
    // ImageInteger metods
    @Override
    public int getPixelInt(int x, int y, int z) {
        return 1;
    }

    @Override
    public int getPixelInt(int xy, int z) {
        return 1;
    }

    @Override
    public void setPixel(int x, int y, int z, int value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setPixel(int xy, int z, int value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    public void appendBinaryMasks(int startLabel, ImageMask... masks){
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setPixelWithOffset(int x, int y, int z, int value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setPixelWithOffset(int x, int y, int z, double value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public int[] getHisto256(ImageMask mask, BoundingBox limits) {
        return new int[256];
    }
    @Override public int[] getHisto256(double min, double max, ImageMask mask, BoundingBox limits) {return getHisto256(mask, limits);}

    @Override
    public float getPixelLinInterX(int x, int y, int z, float dx) {
        return 1;
    }

    @Override
    public void invert() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override public int getBitDepth() {return 0;}
}
