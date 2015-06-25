package image;

public class ImageBoolean extends Image implements ImageMask {

    final private boolean[][] pixels;

    /**
     * Builds a new blank image with same properties as {@param properties}
     * @param name name of the new image
     * @param properties properties of the new image
     */
    public ImageBoolean(String name, ImageProperties properties) {
        super(name, properties);
        this.pixels=new boolean[sizeZ][sizeXY];
    }
    
    public ImageBoolean(String name, int sizeX, int sizeY, int sizeZ) {
        super(name, sizeX, sizeY, sizeZ);
        this.pixels=new boolean[sizeZ][sizeX*sizeY];
    }
    
    public ImageBoolean(String name, int sizeX, boolean[][] pixels) {
        super(name, sizeX, pixels[0].length/sizeX, pixels.length);
        this.pixels=pixels;
    }
    
    public ImageBoolean(String name, int sizeX, boolean[] pixels) {
        super(name, sizeX, pixels.length/sizeX, 1);
        this.pixels=new boolean[][]{pixels};
    }
    
    @Override
    public float getPixel(int x, int y, int z) {
        return pixels[z][x+y*sizeX] ? 1:0;
    }

    @Override
    public float getPixel(int xy, int z) {
        return pixels[z][xy] ? 1:0;
    }

    @Override
    public void setPixel(int x, int y, int z, Number value) {
        if (value.floatValue()!=0) pixels[z][x+y*sizeX]=true;
    }

    @Override
    public void setPixel(int xy, int z, Number value) {
        if (value.floatValue()!=0) pixels[z][xy]=true;
    }

    @Override
    public ImageBoolean duplicate(String name) {
        boolean[][] newPixels = new boolean[sizeZ][sizeXY];
        for (int z = 0; z< sizeZ; ++z) System.arraycopy(pixels[z], 0, newPixels[z], 0, sizeXY);
        return new ImageBoolean(name, sizeX, newPixels);
    }

    public boolean insideMask(int x, int y, int z) {
        return pixels[z][x+y*sizeX];
    }

    public boolean insideMask(int xy, int z) {
        return pixels[z][xy];
    }
    
    @Override
    public boolean[][] getPixelArray() {
        return pixels;
    }
}
