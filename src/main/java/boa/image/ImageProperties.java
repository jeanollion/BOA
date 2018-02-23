package boa.image;

public interface ImageProperties<T extends ImageProperties> extends BoundingBox<T> {
    public String getName();
    public int sizeXY();
    public int sizeXYZ();
    public int getOffsetXY();
    public double getScaleXY();
    public double getScaleZ();
    public T setCalibration(ImageProperties properties);
    public T setCalibration(double scaleXY, double scaleZ);
    public boolean sameDimensions(BoundingBox image);
    public static ImageProperties duplicateProperties(ImageProperties properties) {
        return new BlankMask(properties);
    }
}
