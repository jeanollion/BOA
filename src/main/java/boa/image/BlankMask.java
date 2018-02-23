package boa.image;


public class BlankMask extends SimpleImageProperties<BlankMask> implements ImageMask<BlankMask> {
    public BlankMask(int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ, double scaleXY, double scaleZ) {
        super(new SimpleBoundingBox(offsetX, offsetX+sizeX-1, offsetY, offsetY+sizeY-1, offsetZ, offsetZ+sizeZ-1), scaleXY, scaleZ);
    }

    public BlankMask(int sizeX, int sizeY, int sizeZ) {
        this(sizeX, sizeY, sizeZ, 0, 0, 0, 1, 1);
    }
    
    public BlankMask(ImageProperties properties) {
        super(properties);
    }
    
    public BlankMask(BoundingBox bounds, double scaleXY, double scaleZ) {
        super(bounds, scaleXY, scaleZ);
    }
    
    @Override
    public boolean insideMask(int x, int y, int z) {
        return true; // contains should already be checked
        //return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }

    @Override
    public boolean insideMask(int xy, int z) {
        return true; // contains should already be checked
        //return (xy >= 0 && xy < sizeXY && z >= 0 && z < sizeZ);
    }
    
    @Override public int count() {
        return getSizeXYZ();
    }
    
    @Override
    public boolean insideMaskWithOffset(int x, int y, int z) {
        return true; // contains should already be checked
        //x-=offsetX; y-=offsetY; z-=offsetZ;
        //return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }
    @Override
    public boolean insideMaskWithOffset(int xy, int z) {
        return true; // contains should already be checked
        //xy-=offsetXY;  z-=offsetZ;
        //return (xy >= 0 && xy < sizeXY &&  z >= 0 && z < sizeZ);
    }


    @Override
    public BlankMask duplicateMask() {
        return new BlankMask(this, scaleXY, scaleZ);
    }

}
