package image;

import ij.process.StackStatistics;
import processing.neighborhood.Neighborhood;

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
    public void setPixel(int x, int y, int z, double value) {
        pixels[z][x + y * sizeX] = value<0?0:(value>65535?(short)65535:(short)value);
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, double value) {
        pixels[z-offsetZ][x-offsetX + (y-offsetY) * sizeX] = value<0?0:(value>65535?(short)65535:(short)value);
    }

    @Override
    public void setPixel(int xy, int z, double value) {
        pixels[z][xy] = value<0?0:(value>65535?(short)65535:(short)value);
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
    
    @Override 
    public int[] getHisto256(ImageMask mask) {
        if (mask==null) mask=new BlankMask("", this);
        float[] minAndMax = getMinAndMax(mask);
        return getHisto256(minAndMax[0], minAndMax[1], mask);
    }
    @Override int[] getHisto256(double min, double max, ImageMask mask) {
        if (mask == null) mask = new BlankMask("", this);
        double coeff = 256d / (max - min);
        int[] histo = new int[256];
        int idx;
        for (int z = 0; z < sizeZ; z++) {
            for (int xy = 0; xy < sizeXY; xy++) {
                if (mask.insideMask(xy, z)) {
                    idx = (int) (((pixels[z][xy] & 0xFFFF) - min) * coeff);
                    histo[idx>=256?255:idx]++;
                }
            }
        }
        return histo;
    }
}
