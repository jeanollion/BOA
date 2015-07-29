package image;

public class ImageInt extends ImageInteger {

    private int[][] pixels;

    /**
     * Builds a new blank image with same properties as {@param properties}
     * @param name name of the new image
     * @param properties properties of the new image
     */
    public ImageInt(String name, ImageProperties properties) {
        super(name, properties);
        this.pixels=new int[sizeZ][sizeXY];
    }
    
    public ImageInt(String name, int sizeX, int sizeY, int sizeZ) {
        super(name, sizeX, sizeY, sizeZ);
        this.pixels=new int[sizeZ][sizeX*sizeY];
    }
    
    public ImageInt(String name, int sizeX, int[][] pixels) {
        super(name, sizeX, pixels[0].length/sizeX, pixels.length);
        this.pixels=pixels;
    }
    
    public ImageInt(String name, int sizeX, int[] pixels) {
        super(name, sizeX, pixels.length/sizeX, 1);
        this.pixels=new int[][]{pixels};
    }
    
    @Override
    public int getPixelInt(int x, int y, int z) {
        return pixels[z][x + y * sizeX];
    }

    @Override
    public int getPixelInt(int xy, int z) {
        return pixels[z][xy];
    }
    
    @Override
    public float getPixel(int xy, int z) {
        return (float) (pixels[z][xy]);
    }

    @Override
    public float getPixel(int x, int y, int z) {
        return (float) (pixels[z][x + y * sizeX]);
    }

    @Override
    public void setPixel(int x, int y, int z, int value) {
        pixels[z][x + y * sizeX] = value;
    }

    @Override
    public void setPixel(int xy, int z, int value) {
        pixels[z][xy] = value;
    }

    @Override
    public void setPixel(int x, int y, int z, Number value) {
        pixels[z][x + y * sizeX] = value.intValue();
    }

    @Override
    public void setPixel(int xy, int z, Number value) {
        pixels[z][xy] = value.intValue();
    }

    @Override
    public ImageInt duplicate(String name) {
        int[][] newPixels = new int[sizeZ][sizeXY];
        for (int z = 0; z< sizeZ; ++z) System.arraycopy(pixels[z], 0, newPixels[z], 0, sizeXY);
        return new ImageInt(name, sizeX, newPixels);
    }

    public boolean insideMask(int x, int y, int z) {
        return pixels[z][x+y*sizeX]!=0;
    }

    public boolean insideMask(int xy, int z) {
        return pixels[z][xy]!=0;
    }
    
    @Override
    public int[][] getPixelArray() {
        return pixels;
    }
    
    @Override
    public ImageInt newImage(String name, ImageProperties properties) {
        return new ImageInt(name, properties);
    }
    
    @Override
    public ImageShort crop(BoundingBox bounds) {
        return (ImageShort) cropI(bounds);
    }
    
    public void appendBinaryMasks(int startLabel, ImageMask... masks) {
        if (startLabel==-1) startLabel = (int)this.getMinAndMax(null)[1]+1;
        if (startLabel<0) startLabel=1;
        if (masks == null ) return;
        for (int idx = 0; idx < masks.length; ++idx) {
            int label = idx+startLabel;
            ImageMask currentImage = masks[idx];
            for (int z = 0; z < currentImage.getSizeZ(); ++z) {
                for (int y = 0; y < currentImage.getSizeY(); ++y) {
                    for (int x = 0; x < currentImage.getSizeX(); ++x) {
                        if (currentImage.contains(x, y, z)) {
                            int xx = x + currentImage.getOffsetX();
                            int yy = y + currentImage.getOffsetY();
                            int zz = z + currentImage.getOffsetZ();
                            if (zz >= 0 && zz < sizeZ && xx >= 0 && xx < sizeX && yy >= 0 && yy < sizeY) {
                                pixels[zz][xx + yy * sizeX] = label;
                            }
                        }
                    }
                }
            }
        }
    }
}
