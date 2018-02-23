package boa.image;


public interface ImageMask<I extends ImageMask> extends ImageProperties<I> {

    public boolean insideMask(int x, int y, int z);
    public boolean insideMask(int xy, int z);
    public boolean insideMaskWithOffset(int x, int y, int z);
    public boolean insideMaskWithOffset(int xy, int z);
    public int count();
    public static void loop(ImageMask mask, LoopFunction function) {
        if (function instanceof MutableBoundingBox.LoopFunction2) ((MutableBoundingBox.LoopFunction2)function).setUp();
        BoundingBox.loop(new SimpleBoundingBox(mask).resetOffset(), (x, y, z)-> {if (mask.insideMask(x, y, z)) function.loop(x, y, z);});
        if (function instanceof MutableBoundingBox.LoopFunction2) ((MutableBoundingBox.LoopFunction2)function).tearDown();
    }
    public static void loopWithOffset(ImageMask mask, LoopFunction function) {
        if (function instanceof MutableBoundingBox.LoopFunction2) ((MutableBoundingBox.LoopFunction2)function).setUp();
        BoundingBox.loop(mask, (x, y, z)-> {if (mask.insideMaskWithOffset(x, y, z)) function.loop(x, y, z);});
        if (function instanceof MutableBoundingBox.LoopFunction2) ((MutableBoundingBox.LoopFunction2)function).tearDown();
    }
    public ImageMask duplicateMask();
}
