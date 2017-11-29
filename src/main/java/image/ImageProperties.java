package image;

public interface ImageProperties {
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
    public boolean contains(int x, int y, int z);
    public boolean containsWithOffset(int x, int y, int z);
    public BoundingBox getBoundingBox();
    public boolean sameSize(ImageProperties image);
    public ImageProperties getProperties();
}
