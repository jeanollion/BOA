package image;

public class BlankMask implements ImageProperties, ImageMask {
    protected String name;
    protected int sizeX;
    protected int sizeY;
    protected int sizeZ;
    protected int sizeXY;
    protected int sizeXYZ;
    protected int offsetX;
    protected int offsetY;
    protected int offsetZ;
    protected float scaleXY;
    protected float scaleZ;

    protected BlankMask(String name, int sizeX, int sizeY, int sizeZ) {
        this.name=name;
        this.sizeX=sizeX;
        this.sizeY=sizeY;
        this.sizeZ=sizeZ>=1?sizeZ:1;
        this.sizeXY=sizeX*sizeY;
        this.sizeXYZ=sizeXY*sizeZ;
        this.scaleXY=1;
        this.scaleZ=1;
    }
    
    public BlankMask(String name, ImageProperties properties) {
        this.name=name;
        this.sizeX=properties.getSizeX();
        this.sizeY=properties.getSizeY();
        this.sizeZ=properties.getSizeZ();
        this.sizeXY=sizeX*sizeY;
        this.sizeXYZ=sizeXY*sizeZ;
        this.offsetX=properties.getOffsetX();
        this.offsetY=properties.getOffsetY();
        this.offsetZ=properties.getOffsetZ();
        this.scaleXY=properties.getScaleXY();
        this.scaleZ=properties.getScaleZ();
    }
    
    public String getName() {
        return name;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public int getSizeXY() {
        return sizeXY;
    }

    public int getSizeXYZ() {
        return sizeXYZ;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public int getOffsetZ() {
        return offsetZ;
    }

    public float getScaleXY() {
        return scaleXY;
    }

    public float getScaleZ() {
        return scaleZ;
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
    
}
