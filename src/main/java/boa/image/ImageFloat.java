package boa.image;

import boa.utils.ArrayUtil;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class ImageFloat extends Image<ImageFloat> {

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
            res.translate(xMin, yMin, zMin+idxZ);
            return res;
        }
    }
    @Override public DoubleStream streamPlane(int z) {
        return ArrayUtil.stream(pixels[z]);
    }
    @Override public DoubleStream streamPlane(int z, ImageMask mask, boolean maskHasAbsoluteOffset) {
        if (maskHasAbsoluteOffset) {
            if (z<0 || z>=sizeZ || z+zMin-mask.zMin()<0 || z+zMin-mask.zMin()>=mask.sizeZ()) return DoubleStream.empty();
            SimpleBoundingBox inter = BoundingBox.getIntersection2D(this, mask);
            if (inter.isEmpty()) return DoubleStream.empty();
            if (inter.sameBounds(this) && inter.sameBounds(mask)) {
                if (mask instanceof BlankMask) return this.streamPlane(z);
                else return IntStream.range(0,sizeXY).mapToDouble(i->mask.insideMask(i, z)?pixels[z][i]:Double.NaN).filter(v->!Double.isNaN(v));
            }
            else { // loop within intersection
                int sX = inter.sizeX();
                int offX = inter.xMin();
                int offY = inter.yMin();
                return IntStream.range(0,inter.getSizeXY()).mapToDouble(i->{
                        int x = i%sX+offX;
                        int y = i/sX+offY;
                        return mask.insideMaskWithOffset(x, y, z+zMin)?pixels[z][x+y*sizeX-offsetXY]:Double.NaN;}
                ).filter(v->!Double.isNaN(v));
            }
        }
        else { // masks is relative to image
            if (z<0 || z>=sizeZ || z-mask.zMin()<0 || z-mask.zMin()>mask.sizeZ()) return DoubleStream.empty();
            SimpleBoundingBox inter = BoundingBox.getIntersection2D(new SimpleBoundingBox(this).resetOffset(), mask);
            if (inter.isEmpty()) return DoubleStream.empty();
            if (inter.sameDimensions(mask) && inter.sameDimensions(this)) {
                if (mask instanceof BlankMask) return this.streamPlane(z);
                else return IntStream.range(0, sizeXY).mapToDouble(i->mask.insideMask(i, z)?pixels[z][i]:Double.NaN).filter(v->!Double.isNaN(v));
            }
            else {
                int sX = inter.sizeX();
                int offX = inter.xMin();
                int offY = inter.yMin();
                return IntStream.range(0,inter.getSizeXY()).mapToDouble(i->{
                        int x = i%sX+offX;
                        int y = i/sX+offY;
                        return mask.insideMaskWithOffset(x, y, z)?pixels[z][x+y*sizeX]:Double.NaN;}
                ).filter(v->!Double.isNaN(v));
            }
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
        pixels[z-zMin][x-offsetXY + y * sizeX] = value;
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, double value) {
        pixels[z-zMin][x-offsetXY + y * sizeX] = (float)value;
    }

    public void setPixel(int xy, int z, float value) {
        pixels[z][xy]=value;
    }
    
    @Override
    public float getPixelWithOffset(int x, int y, int z) {
        return pixels[z-zMin][x-offsetXY + y * sizeX];
    }

    @Override
    public float getPixelWithOffset(int xy, int z) {
        return pixels[z-zMin][xy - offsetXY ];
    }

    @Override
    public void setPixelWithOffset(int xy, int z, double value) {
        pixels[z-zMin][xy - offsetXY] = (float)value;
    }

    @Override
    public ImageFloat duplicate(String name) {
        float[][] newPixels = new float[sizeZ][sizeXY];
        for (int z = 0; z< sizeZ; ++z) System.arraycopy(pixels[z], 0, newPixels[z], 0, sizeXY);
        return (ImageFloat)new ImageFloat(name, sizeX, newPixels).setCalibration(this).translate(this);
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
        if (mask == null) mask = new BlankMask(this);
        double[] minAndMax = getMinAndMax(mask);
        return getHisto256(minAndMax[0], minAndMax[1], mask, limit);
    }
    @Override public Histogram getHisto256(double min, double max, ImageMask mask, BoundingBox limits) {
        ImageMask m = mask==null ?  new BlankMask(this) : mask;
        if (limits==null) limits = new SimpleBoundingBox(this).resetOffset();
        double coeff = 256d / (max - min);
        int[] histo = new int[256];
        LoopFunction function = (x, y, z) -> {
            if (m.insideMask(x, y, z)) {
                int idx = (int) ((pixels[z][x+y*sizeX] - min) * coeff);
                histo[idx>=256?255:idx]++;
            }
        };
        BoundingBox.loop(limits, function);
        return new Histogram(histo, false, new double[]{min, max});
    }
    @Override public int getBitDepth() {return 32;}

    // image mask implementation
    
    @Override
    public boolean insideMask(int x, int y, int z) {
        return pixels[z][x+y*sizeX]!=0;
    }

    @Override
    public boolean insideMask(int xy, int z) {
        return pixels[z][xy]!=0;
    }
    
    @Override
    public boolean insideMaskWithOffset(int x, int y, int z) {
        return pixels[z-zMin][x-offsetXY + y * sizeX]!=0;
    }

    @Override
    public boolean insideMaskWithOffset(int xy, int z) {
        return pixels[z-zMin][xy - offsetXY ]!=0;
    }

    @Override
    public int count() {
        int count = 0;
        for (int z = 0; z< sizeZ; ++z) {
            for (int xy=0; xy<sizeXY; ++xy) {
                if (pixels[z][xy]!=0) ++count;
            }
        }
        return count;
    }

    @Override
    public ImageMask duplicateMask() {
        return duplicate("");
    }
}
