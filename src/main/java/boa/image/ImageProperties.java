package boa.image;

public interface ImageProperties<T extends ImageProperties> {
    public String getName();
    public int getSizeX();
    public int getSizeY();
    public int getSizeZ();
    public int getSizeXY();
    public int getSizeXYZ();
    public int getOffsetX();
    public int getOffsetXY();
    public int getOffsetY();
    public int getOffsetZ();
    public float getScaleXY();
    public float getScaleZ();
    public T setCalibration(ImageProperties properties);
    public T setCalibration(float scaleXY, float scaleZ);
    public boolean contains(int x, int y, int z);
    public boolean containsWithOffset(int x, int y, int z);
    public BoundingBox getBoundingBox();
    public boolean sameDimensions(ImageProperties image);
    public ImageProperties getProperties();
    public T addOffset(BoundingBox bounds);
    public T addOffset(ImageProperties bounds);
}
