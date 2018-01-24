package boa.image;

public interface ImageMask extends ImageProperties {

    public boolean insideMask(int x, int y, int z);
    public boolean insideMask(int xy, int z);
    public boolean insideMaskWithOffset(int x, int y, int z);
    public boolean insideMaskWithOffset(int xy, int z);
    public int count();
}
