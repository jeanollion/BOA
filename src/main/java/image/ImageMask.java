package image;

public interface ImageMask extends ImageProperties {

    public boolean insideMask(int x, int y, int z);
    public boolean insideMask(int xy, int z);
}
