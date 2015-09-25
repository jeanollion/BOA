package image;

import processing.neighborhood.Neighborhood;

public class ImageByte extends ImageInteger {

    private byte[][] pixels;

    /**
     * Builds a new blank image with same properties as {@param properties}
     * @param name name of the new image
     * @param properties properties of the new image
     */
    public ImageByte(String name, ImageProperties properties) {
        super(name, properties);
        this.pixels=new byte[sizeZ][sizeXY];
    }
    
    public ImageByte(String name, int sizeX, int sizeY, int sizeZ) {
        super(name, sizeX, sizeY, sizeZ);
        this.pixels=new byte[sizeZ][sizeX*sizeY];
    }
    
    public ImageByte(String name, int sizeX, byte[][] pixels) {
        super(name, sizeX, pixels[0].length/sizeX, pixels.length);
        this.pixels=pixels;
    }
    
    public ImageByte(String name, int sizeX, byte[] pixels) {
        super(name, sizeX, pixels.length/sizeX, 1);
        this.pixels=new byte[][]{pixels};
    }
    
    @Override
    public ImageByte getZPlane(int idxZ) {
        if (idxZ>=sizeZ) throw new IllegalArgumentException("Z-plane cannot be superior to sizeZ");
        else {
            ImageByte res = new ImageByte(name, sizeX, pixels[idxZ]);
            res.setCalibration(this);
            res.addOffset(offsetX, offsetY, offsetZ+idxZ);
            return res;
        }
    }
    
    @Override
    public int getPixelInt(int x, int y, int z) {
        return pixels[z][x + y * sizeX] & 0xff;
    }

    @Override
    public int getPixelInt(int xy, int z) {
        return pixels[z][xy] & 0xff;
    }
    
    @Override
    public float getPixel(int xy, int z) {
        return (float) (pixels[z][xy] & 0xff);
    }

    @Override
    public float getPixel(int x, int y, int z) {
        return (float) (pixels[z][x + y * sizeX] & 0xff);
    }

    @Override
    public void setPixel(int x, int y, int z, int value) {
        pixels[z][x + y * sizeX] = (byte) value;
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, int value) {
        pixels[z-offsetZ][x-offsetX + (y-offsetY) * sizeX] = (byte)value;
    }

    @Override
    public void setPixel(int xy, int z, int value) {
        pixels[z][xy] = (byte) value;
    }

    @Override
    public void setPixel(int x, int y, int z, double value) {
        pixels[z][x + y * sizeX] = value<0?0:(value>255?(byte)255:(byte)value);
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, double value) {
        pixels[z-offsetZ][x-offsetX + (y-offsetY) * sizeX] = value<0?0:(value>255?(byte)255:(byte)value);
    }

    @Override
    public void setPixel(int xy, int z, double value) {
        pixels[z][xy] = value<0?0:(value>255?(byte)255:(byte)value);
    }

    @Override
    public ImageByte duplicate(String name) {
        byte[][] newPixels = new byte[sizeZ][sizeXY];
        for (int z = 0; z< sizeZ; ++z) System.arraycopy(pixels[z], 0, newPixels[z], 0, sizeXY);
        return new ImageByte(name, sizeX, newPixels);
    }

    public boolean insideMask(int x, int y, int z) {
        return pixels[z][x+y*sizeX]!=0;
    }

    public boolean insideMask(int xy, int z) {
        return pixels[z][xy]!=0;
    }
    
    @Override
    public byte[][] getPixelArray() {
        return pixels;
    }
    
    @Override
    public ImageByte newImage(String name, ImageProperties properties) {
        return new ImageByte(name, properties);
    }
    
    @Override
    public ImageByte crop(BoundingBox bounds) {
        return (ImageByte) cropI(bounds);
    }
    
    @Override
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
                                pixels[zz][xx + yy * sizeX] = (byte)label;
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Override 
    public int[] getHisto256(ImageMask mask) {
        if (mask==null) mask=new BlankMask("", this);
        int[] histo = new int[256];
        for (int z = 0; z < sizeZ; z++) {
            for (int xy = 0; xy < sizeXY; xy++) {
                if (mask.insideMask(xy, z)) {
                    histo[pixels[z][xy] & 0xff]++;
                }
            }
        }
        return histo;
    }

}
