package boa.image;

import boa.image.BoundingBox.LoopFunction;

public interface ImageMask extends ImageProperties {

    public boolean insideMask(int x, int y, int z);
    public boolean insideMask(int xy, int z);
    public boolean insideMaskWithOffset(int x, int y, int z);
    public boolean insideMaskWithOffset(int xy, int z);
    public int count();
    public static void loop(ImageMask mask, LoopFunction function) {
        if (function instanceof BoundingBox.LoopFunction2) ((BoundingBox.LoopFunction2)function).setUp();
        mask.getBoundingBox().translateToOrigin().loop((x, y, z)-> {if (mask.insideMask(x, y, z)) function.loop(x, y, z);});
        if (function instanceof BoundingBox.LoopFunction2) ((BoundingBox.LoopFunction2)function).tearDown();
    }
    public static void loopWithOffset(ImageMask mask, LoopFunction function) {
        if (function instanceof BoundingBox.LoopFunction2) ((BoundingBox.LoopFunction2)function).setUp();
        mask.getBoundingBox().loop((x, y, z)-> {if (mask.insideMaskWithOffset(x, y, z)) function.loop(x, y, z);});
        if (function instanceof BoundingBox.LoopFunction2) ((BoundingBox.LoopFunction2)function).tearDown();
    }
}
