package boa.image;

import boa.image.processing.neighborhood.Neighborhood;
import boa.utils.ArrayUtil;
import boa.utils.StreamConcatenation;
import boa.utils.Utils;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class BlankMask extends ImageInteger implements ImageMask {

    public BlankMask(String name, int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ, float scaleXY, float scaleZ) {
        super(name, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ, scaleXY, scaleZ);
    }

    public BlankMask(int sizeX, int sizeY, int sizeZ) {
        this("", sizeX, sizeY, sizeZ, 0, 0, 0, 1, 1);
    }
    
    public BlankMask(String name, int sizeX, int sizeY, int sizeZ) {
        this(name, sizeX, sizeY, sizeZ, 0, 0, 0, 1, 1);
    }
    
    public BlankMask(ImageProperties properties) {
        this("", properties);
    }
    
    public BlankMask(String name, ImageProperties properties) {
        this(name, properties.getSizeX(), properties.getSizeY(), properties.getSizeZ(), properties.getOffsetX(), properties.getOffsetY(), properties.getOffsetZ(), properties.getScaleXY(), properties.getScaleZ());
    }
    
    public BlankMask(String name, BoundingBox bounds, float scaleXY, float scaleZ) {
        this(name, bounds.getSizeX(), bounds.getSizeY(), bounds.getSizeZ(), bounds.getxMin(), bounds.getyMin(), bounds.getzMin(), scaleXY, scaleZ);
    }
    @Override
    public DoubleStream streamPlane(int z) {
        return IntStream.range(0, sizeXY).mapToDouble(i->1);
    }
    @Override
    public DoubleStream stream() {
        return IntStream.range(0, sizeXYZ).mapToDouble(i->1);
    }
    @Override public IntStream streamIntPlane(int z) {
        return IntStream.range(0, sizeXY).map(i->1);
    }
    @Override public IntStream streamInt() {
        return IntStream.range(0, sizeXYZ).map(i->1);
    }
    @Override public DoubleStream streamPlane(int z, ImageMask mask, boolean useOffset) {
        if (useOffset) return IntStream.range(offsetXY, sizeXY+offsetXY).mapToDouble(i->mask.containsWithOffset(i, z) && mask.insideMaskWithOffset(i, z)?1:Double.NaN).filter(v->!Double.isNaN(v));
        else return IntStream.range(0, sizeXY).mapToDouble(i->mask.insideMask(i, z)?1:Double.NaN).filter(v->!Double.isNaN(v));
    }
    @Override public IntStream streamIntPlane(int z, ImageMask mask, boolean useOffset) {
        if (useOffset) return IntStream.range(offsetXY, sizeXY+offsetXY).map(i->mask.containsWithOffset(i, z) && mask.insideMaskWithOffset(i, z)?1:Integer.MAX_VALUE).filter(v->!Double.isNaN(v));
        else return IntStream.range(0, sizeXY).map(i->mask.insideMask(i, z)?1:Integer.MAX_VALUE).filter(v->v!=Integer.MAX_VALUE);
    }
    @Override
    public Image getZPlane(int idxZ) {
        return new BlankMask(name, sizeX, sizeY, 1, offsetX, offsetY, offsetZ+idxZ, scaleXY, scaleZ);
    }

    @Override
    public boolean insideMask(int x, int y, int z) {
        return true; // contains should already be checked
        //return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }

    @Override
    public boolean insideMask(int xy, int z) {
        return true; // contains should already be checked
        //return (xy >= 0 && xy < sizeXY && z >= 0 && z < sizeZ);
    }
    
    @Override public int count() {
        return sizeZ * sizeXY;
    }
    
    @Override
    public boolean insideMaskWithOffset(int x, int y, int z) {
        return true; // contains should already be checked
        //x-=offsetX; y-=offsetY; z-=offsetZ;
        //return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }
    @Override
    public boolean insideMaskWithOffset(int xy, int z) {
        return true; // contains should already be checked
        //xy-=offsetXY;  z-=offsetZ;
        //return (xy >= 0 && xy < sizeXY &&  z >= 0 && z < sizeZ);
    }

    @Override
    public float getPixel(int x, int y, int z) {
        return 1;
    }
    
    @Override
    public float getPixel(int xz, int z) {
        return 1;
    }

    @Override
    public void setPixel(int x, int y, int z, double value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setPixel(int xy, int z, double value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    
    @Override
    public int getPixelIntWithOffset(int x, int y, int z) {
        return 1;
    }

    @Override
    public int getPixelIntWithOffset(int xy, int z) {
        return 1;
    }

    @Override
    public void setPixelWithOffset(int xy, int z, int value) {
    }

    @Override
    public float getPixelWithOffset(int x, int y, int z) {
        return 1;
    }

    @Override
    public float getPixelWithOffset(int xy, int z) {
        return 1;
    }

    @Override
    public void setPixelWithOffset(int xy, int z, double value) {
    }

    @Override
    public BlankMask duplicate(String name) {
        return new BlankMask(name, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ, scaleXY, scaleZ);
    }

    @Override
    public Object[] getPixelArray() {
        return TypeConverter.toByteMask(this, null, 1).getPixelArray();
    }

    @Override
    public Image newImage(String name, ImageProperties properties) {
        return new BlankMask(name, properties);
    }

    @Override
    public BlankMask crop(BoundingBox bounds) {
        return (BlankMask) cropI(bounds);
    }
    
    @Override
    public BlankMask cropWithOffset(BoundingBox bounds) {
        return (BlankMask) cropIWithOffset(bounds);
    }
    
    // ImageInteger metods
    @Override
    public int getPixelInt(int x, int y, int z) {
        return 1;
    }

    @Override
    public int getPixelInt(int xy, int z) {
        return 1;
    }

    @Override
    public void setPixel(int x, int y, int z, int value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setPixel(int xy, int z, int value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    public void appendBinaryMasks(int startLabel, ImageMask... masks){
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setPixelWithOffset(int x, int y, int z, int value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setPixelWithOffset(int x, int y, int z, double value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public Histogram getHisto256(ImageMask mask, BoundingBox limits) {
        return new Histogram(new int[256], true, new double[]{0, 1});
    }
    @Override public Histogram getHisto256(double min, double max, ImageMask mask, BoundingBox limits) {return getHisto256(mask, limits);}

    @Override
    public float getPixelLinInterX(int x, int y, int z, float dx) {
        return 1;
    }

    @Override
    public void invert() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override public int getBitDepth() {return 0;}
}
