package image;

public class BlankMask extends Image implements ImageMask {

    public BlankMask(String name, int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ, float scaleXY, float scaleZ) {
        super(name, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ, scaleXY, scaleZ);
    }

    public BlankMask(String name, int sizeX, int sizeY, int sizeZ) {
        this(name, sizeX, sizeY, sizeZ, 0, 0, 0, 1, 1);
    }
    
    public BlankMask(String name, ImageProperties properties) {
        this(name, properties.getSizeX(), properties.getSizeY(), properties.getSizeZ(), properties.getOffsetX(), properties.getOffsetY(), properties.getOffsetZ(), properties.getScaleXY(), properties.getScaleZ());
    }
    

    public boolean insideMask(int x, int y, int z) {
        return true;
    }

    public boolean insideMask(int xy, int z) {
        return true;
    }
    
    @Override
    public boolean contains(int x, int y, int z) {
        return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }

    @Override
    public boolean containsWithOffset(int x, int y, int z) {
        x-=offsetX; y-=offsetY; z-=offsetZ;
        return (x >= 0 && x < sizeX && y >= 0 && y-offsetY < sizeY && z >= 0 && z < sizeZ);
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
    public void setPixel(int x, int y, int z, Number value) {
        
    }

    @Override
    public void setPixel(int xy, int z, Number value) {
        
    }

    @Override
    public Image duplicate(String name) {
        return new BlankMask(name, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ, scaleXY, scaleZ);
    }

    @Override
    public Object getPixelArray() {
        return TypeConverter.toByteMask(this).getPixelArray();
    }
    
}
