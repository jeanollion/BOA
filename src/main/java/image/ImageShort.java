package image;

public class ImageShort extends ImageInteger {

    private short[][] pixels;

    /**
     * Builds a new blank image with same properties as {@param properties}
     * @param name name of the new image
     * @param properties properties of the new image
     */
    public ImageShort(String name, ImageProperties properties) {
        super(name, properties);
        this.pixels=new short[sizeZ][sizeXY];
    }
    
    public ImageShort(String name, int sizeX, int sizeY, int sizeZ) {
        super(name, sizeX, sizeY, sizeZ);
        this.pixels=new short[sizeZ][sizeX*sizeY];
    }
    
    public ImageShort(String name, int sizeX, short[][] pixels) {
        super(name, sizeX, pixels[0].length/sizeX, pixels.length);
        this.pixels=pixels;
    }
    
    public ImageShort(String name, int sizeX, short[] pixels) {
        super(name, sizeX, pixels.length/sizeX, 1);
        this.pixels=new short[][]{pixels};
    }
    
    @Override
    public ImageShort getZPlane(int idxZ) {
        if (idxZ>=sizeZ) throw new IllegalArgumentException("Z-plane cannot be superior to sizeZ");
        else {
            ImageShort res = new ImageShort(name, sizeX, pixels[idxZ]);
            res.setCalibration(this);
            res.addOffset(offsetX, offsetY, offsetZ+idxZ);
            return res;
        }
    }
    
    @Override
    public int getPixelInt(int x, int y, int z) {
        return pixels[z][x + y * sizeX] & 0xffff;
    }

    @Override
    public int getPixelInt(int xy, int z) {
        return pixels[z][xy] & 0xffff;
    }
    
    @Override
    public float getPixel(int xy, int z) {
        return (float) (pixels[z][xy] & 0xffff);
    }

    @Override
    public float getPixel(int x, int y, int z) {
        return (float) (pixels[z][x + y * sizeX] & 0xffff);
    }

    @Override
    public void setPixel(int x, int y, int z, int value) {
        pixels[z][x + y * sizeX] = (short) value;
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, int value) {
        pixels[z-offsetZ][x-offsetX + (y-offsetY) * sizeX] = (short)value;
    }

    @Override
    public void setPixel(int xy, int z, int value) {
        pixels[z][xy] = (short) value;
    }

    @Override
    public void setPixel(int x, int y, int z, Number value) {
        pixels[z][x + y * sizeX] = value.shortValue();
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, Number value) {
        pixels[z-offsetZ][x-offsetX + (y-offsetY) * sizeX] = value.shortValue();
    }

    @Override
    public void setPixel(int xy, int z, Number value) {
        pixels[z][xy] = value.shortValue();
    }

    @Override
    public ImageShort duplicate(String name) {
        short[][] newPixels = new short[sizeZ][sizeXY];
        for (int z = 0; z< sizeZ; ++z) System.arraycopy(pixels[z], 0, newPixels[z], 0, sizeXY);
        return new ImageShort(name, sizeX, newPixels);
    }

    public boolean insideMask(int x, int y, int z) {
        return pixels[z][x+y*sizeX]!=0;
    }

    public boolean insideMask(int xy, int z) {
        return pixels[z][xy]!=0;
    }
    
    @Override
    public short[][] getPixelArray() {
        return pixels;
    }
    
    @Override
    public ImageShort newImage(String name, ImageProperties properties) {
        return new ImageShort(name, properties);
    }
    
    @Override
    public ImageShort crop(BoundingBox bounds) {
        return (ImageShort) cropI(bounds);
    }
    
    public void appendBinaryMasks(int startLabel, ImageMask... masks) {
        if (masks == null || masks.length==0) return;
        if (startLabel==-1) startLabel = (int)this.getMinAndMax(null)[1]+1;
        //if (startLabel<0) startLabel=1;
        for (int idx = 0; idx < masks.length; ++idx) {
            int label = idx+startLabel;
            ImageMask currentImage = masks[idx];
            for (int z = 0; z < currentImage.getSizeZ(); ++z) {
                for (int y = 0; y < currentImage.getSizeY(); ++y) {
                    for (int x = 0; x < currentImage.getSizeX(); ++x) {
                        if (currentImage.insideMask(x, y, z)) {
                            int xx = x + currentImage.getOffsetX();
                            int yy = y + currentImage.getOffsetY();
                            int zz = z + currentImage.getOffsetZ();
                            if (contains(xx, yy, zz)) {
                                pixels[zz][xx + yy * sizeX] = (short)label;
                            }
                        }
                    }
                }
            }
        }
    }
    
}
