package image;

import processing.neighborhood.Neighborhood;

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
    public ImageInt getZPlane(int idxZ) {
        if (idxZ>=sizeZ) throw new IllegalArgumentException("Z-plane cannot be superior to sizeZ");
        else {
            ImageInt res = new ImageInt(name, sizeX, pixels[idxZ]);
            res.setCalibration(this);
            res.addOffset(offsetX, offsetY, offsetZ+idxZ);
            return res;
        }
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
    public float getPixelLinInterX(int x, int y, int z, float dx) {
        return (float) ((pixels[z][x + y * sizeX]) * (1-dx) + dx * (pixels[z][x + 1 + y * sizeX]));
    }

    @Override
    public void setPixel(int x, int y, int z, int value) {
        pixels[z][x + y * sizeX] = value;
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, int value) {
        pixels[z-offsetZ][x-offsetXY + y * sizeX] = value;
    }

    @Override
    public void setPixel(int xy, int z, int value) {
        pixels[z][xy] = value;
    }

    @Override
    public void setPixel(int x, int y, int z, double value) {
        pixels[z][x + y * sizeX] = (int)value;
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, double value) {
        pixels[z-offsetZ][x-offsetXY + y * sizeX] = (int)value;
    }

    @Override
    public void setPixel(int xy, int z, double value) {
        pixels[z][xy] = (int)value;
    }

    @Override
    public int getPixelIntWithOffset(int x, int y, int z) {
        return pixels[z-offsetZ][x-offsetXY + y * sizeX];
    }

    @Override
    public int getPixelIntWithOffset(int xy, int z) {
        return pixels[z-offsetZ][xy - offsetXY ];
    }

    @Override
    public void setPixelWithOffset(int xy, int z, int value) {
        pixels[z-offsetZ][xy - offsetXY] = value;
    }

    @Override
    public float getPixelWithOffset(int x, int y, int z) {
        return pixels[z-offsetZ][x-offsetXY + y * sizeX];
    }

    @Override
    public float getPixelWithOffset(int xy, int z) {
        return pixels[z-offsetZ][xy - offsetXY ];
    }

    @Override
    public void setPixelWithOffset(int xy, int z, double value) {
        pixels[z-offsetZ][xy - offsetXY] = (int) value;
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
    
    
    public boolean insideMaskWithOffset(int x, int y, int z) {
        return pixels[z-offsetZ][x+y*sizeX-offsetXY]!=0;
    }

    public boolean insideMaskWithOffset(int xy, int z) {
        return pixels[z-offsetZ][xy-offsetXY]!=0;
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
                                pixels[zz][xx + yy * sizeX] = label;
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Override 
    public int[] getHisto256(ImageMask mask, BoundingBox limits) {
        if (mask==null) mask=new BlankMask("", this);
        float[] minAndMax = getMinAndMax(mask);
        return getHisto256(minAndMax[0], minAndMax[1], mask, limits);
    }
    @Override int[] getHisto256(double min, double max, ImageMask mask, BoundingBox limits) {
        if (mask == null) mask = new BlankMask("", this);
        if (limits==null) limits = mask.getBoundingBox().translateToOrigin();
        double coeff = 256d / (max - min);
        int[] histo = new int[256];
        int idx;
        for (int z = limits.zMin; z <= limits.zMax; z++) {
            for (int y = limits.yMin; y<=limits.yMax; ++y) {
                for (int x = limits.xMin; x <= limits.xMax; ++x) {
                    if (mask.insideMask(x, y, z)) {
                    idx = (int) ((getPixel(x, y, z) - min) * coeff);
                    histo[idx>=256?255:idx]++;
                    }
                }
            }
        }
        return histo;
    }
    
}
