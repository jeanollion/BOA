package image;


public abstract class Image implements ImageProperties {

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
    protected ImageWrapper wrapper;
    protected LUT lut=LUT.Grays;
    
    protected Image(String name, int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ, float scaleXY, float scaleZ) {
        this.name = name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ=sizeZ>=1?sizeZ:1;
        this.sizeXY=sizeX*sizeY;
        this.sizeXYZ=sizeXY*sizeZ;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.scaleXY = scaleXY;
        this.scaleZ = scaleZ;
    }
    
    protected Image(String name, int sizeX, int sizeY, int sizeZ) {
        this(name, sizeX, sizeY, sizeZ, 0, 0, 0, 1, 1);
    }
    
    protected Image(String name, ImageProperties properties) {
        this(name, properties.getSizeX(), properties.getSizeY(), properties.getSizeZ(), properties.getOffsetX(), properties.getOffsetY(), properties.getOffsetZ(), properties.getScaleXY(), properties.getScaleZ());
    }
    
    //public abstract float getPixel(float x, float y, float z); // interpolation
    public abstract float getPixel(int x, int y, int z);
    public abstract float getPixel(int xz, int z);
    public abstract void setPixel(int x, int y, int z, Number value);
    public abstract void setPixel(int xy, int z, Number value);
    public abstract Object getPixelArray();
    public abstract Image duplicate(String name);

    @Override
    public boolean contains(int x, int y, int z) {
        return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }

    @Override
    public boolean containsWithOffset(int x, int y, int z) {
        x-=offsetX; y-=offsetY; z-=offsetZ;
        return (x >= 0 && x < sizeX && y >= 0 && y-offsetY < sizeY && z >= 0 && z < sizeZ);
    }

    public void setOffset(ImageProperties properties) {
        this.offsetX=properties.getOffsetX();
        this.offsetY=properties.getOffsetY();
        this.offsetZ=properties.getOffsetZ();
    }
    
    public void setOffset(int offsetX, int offsetY, int offsetZ) {
        this.offsetX=offsetX;
        this.offsetY=offsetY;
        this.offsetZ=offsetZ;
    }

    public void setCalibration(ImageProperties properties) {
        this.scaleXY=properties.getScaleXY();
        this.scaleZ=properties.getScaleZ();
    }
    
    public void setCalibration(float scaleXY, float scaleZ) {
        this.scaleXY=scaleXY;
        this.scaleZ=scaleZ;
    }

    /**
     * 
     * @param mask min and max are computed within the mask, or within the whole image if mask==null 
     * @return float[]{min, max}
     */
    public float[] getMinAndMax(ImageMask mask) {
        if (mask==null) mask = new BlankMask("", this);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int z = 0; z < sizeZ; ++z) {
            for (int xy = 0; xy < sizeXY; ++xy) {
                if (mask.insideMask(xy, z)) {
                    if (getPixel(xy, z) > max) {
                        max = getPixel(xy, z);
                    }
                    if (getPixel(xy, z) < min) {
                        min = getPixel(xy, z);
                    }
                }
            }
        }
        return new float[]{min, max};
    }

    //public abstract Image crop(int[] bounds);
    //public abstract Image[] crop(int[][] bounds);

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

    public ImageWrapper getWrapper() {
        return wrapper;
    }

    public void show() {
        wrapper.show();
    }

    public void show(String name) {
        wrapper.show(name);
    }
    
    public String getName() {
        return name;
    }
    
    
}
