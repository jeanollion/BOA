package boa.image;

import boa.image.processing.neighborhood.Neighborhood;

public class ImageFloat extends Image {

    final private float[][] pixels;

    /**
     * Builds a new blank image with same properties as {@param properties}
     * @param name name of the new image
     * @param properties properties of the new image
     */
    public ImageFloat(String name, ImageProperties properties) {
        super(name, properties);
        this.pixels=new float[sizeZ][sizeXY];
    }
    
    public ImageFloat(String name, int sizeX, int sizeY, int sizeZ) {
        super(name, sizeX, sizeY, sizeZ);
        if (sizeZ>0 && sizeX>0 && sizeY>0) this.pixels=new float[sizeZ][sizeX*sizeY];
        else pixels = null;
    }
    
    public ImageFloat(String name, int sizeX, float[][] pixels) {
        super(name, sizeX, sizeX>0?pixels[0].length/sizeX:0, pixels.length);
        this.pixels=pixels;
    }
    
    public ImageFloat(String name, int sizeX, float[] pixels) {
        super(name, sizeX, sizeX>0?pixels.length/sizeX:0, 1);
        this.pixels=new float[][]{pixels};
    }
    
    @Override
    public ImageFloat getZPlane(int idxZ) {
        if (idxZ>=sizeZ) throw new IllegalArgumentException("Z-plane cannot be superior to sizeZ");
        else {
            ImageFloat res = new ImageFloat(name, sizeX, pixels[idxZ]);
            res.setCalibration(this);
            res.addOffset(offsetX, offsetY, offsetZ+idxZ);
            return res;
        }
    }
    
    @Override
    public float getPixel(int x, int y, int z) {
        return pixels[z][x+y*sizeX];
    }
    
    
    @Override
    public float getPixelLinInterX(int x, int y, int z, float dx) {
        if (dx==0) return (float) (pixels[z][x + y * sizeX]);
        return (float) ((pixels[z][x + y * sizeX]) * (1-dx) + dx * (pixels[z][x + 1 + y * sizeX]));
    }

    @Override
    public float getPixel(int xy, int z) {
        return pixels[z][xy];
    }
    
    @Override
    public void setPixel(int x, int y, int z, double value) {
        pixels[z][x+y*sizeX]=(float)value;
    }

    @Override
    public void setPixel(int xy, int z, double value) {
        pixels[z][xy]=(float)value;
    }
    
    public void setPixel(int x, int y, int z, float value) {
        pixels[z][x+y*sizeX]=value;
    }
    
    public void setPixelWithOffset(int x, int y, int z, float value) {
        pixels[z-offsetZ][x-offsetXY + y * sizeX] = value;
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, double value) {
        pixels[z-offsetZ][x-offsetXY + y * sizeX] = (float)value;
    }

    public void setPixel(int xy, int z, float value) {
        pixels[z][xy]=value;
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
        pixels[z-offsetZ][xy - offsetXY] = (float)value;
    }

    @Override
    public ImageFloat duplicate(String name) {
        float[][] newPixels = new float[sizeZ][sizeXY];
        for (int z = 0; z< sizeZ; ++z) System.arraycopy(pixels[z], 0, newPixels[z], 0, sizeXY);
        return new ImageFloat(name, sizeX, newPixels).setCalibration(this).addOffset(this);
    }
    
    @Override
    public float[][] getPixelArray() {
        return pixels;
    }

    @Override
    public ImageFloat newImage(String name, ImageProperties properties) {
        return new ImageFloat(name, properties);
    }

    @Override
    public ImageFloat crop(BoundingBox bounds) {
        return (ImageFloat) cropI(bounds);
    }
    
    @Override
    public ImageFloat cropWithOffset(BoundingBox bounds) {
        return (ImageFloat) cropIWithOffset(bounds);
    }
    
    @Override
    public void invert() {
        double[] minAndMax = this.getMinAndMax(null);
        float off = (float)(minAndMax[1] + minAndMax[0]);
        for (int z = 0; z < sizeZ; z++) {
            for (int xy = 0; xy<sizeXY; ++xy) {
                pixels[z][xy] = off - pixels[z][xy];
            }
        }
    }

    @Override
    public Histogram getHisto256(ImageMask mask, BoundingBox limit) {
        if (mask == null) mask = new BlankMask("", this);
        double[] minAndMax = getMinAndMax(mask);
        return getHisto256(minAndMax[0], minAndMax[1], mask, limit);
    }
    @Override public Histogram getHisto256(double min, double max, ImageMask mask, BoundingBox limits) {
        if (mask == null) mask = new BlankMask("", this);
        if (limits==null) limits = mask.getBoundingBox().translateToOrigin();
        double coeff = 256d / (max - min);
        int idx;
        int[] histo = new int[256];
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
        return new Histogram(histo, false, new double[]{min, max});
    }
    @Override public int getBitDepth() {return 32;}
}
