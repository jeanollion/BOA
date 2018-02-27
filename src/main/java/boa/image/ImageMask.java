package boa.image;

import boa.image.processing.Filters;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;


public interface ImageMask<I extends ImageMask> extends ImageProperties<I> {

    public boolean insideMask(int x, int y, int z);
    public boolean insideMask(int xy, int z);
    public boolean insideMaskWithOffset(int x, int y, int z);
    public boolean insideMaskWithOffset(int xy, int z);
    public int count();
    public static void loop(ImageMask mask, LoopFunction function) {
        if (function instanceof LoopFunction2) ((LoopFunction2)function).setUp();
        BoundingBox.loop(new SimpleBoundingBox(mask).resetOffset(), (x, y, z)-> {if (mask.insideMask(x, y, z)) function.loop(x, y, z);});
        if (function instanceof LoopFunction2) ((LoopFunction2)function).tearDown();
    }
    public static void loopWithOffset(ImageMask mask, LoopFunction function) {
        if (function instanceof LoopFunction2) ((LoopFunction2)function).setUp();
        BoundingBox.loop(mask, (x, y, z)-> {if (mask.insideMaskWithOffset(x, y, z)) function.loop(x, y, z);});
        if (function instanceof BoundingBox.LoopFunction2) ((LoopFunction2)function).tearDown();
    }
    public ImageMask duplicateMask();
    public static LoopPredicate insideMask(final ImageMask mask, boolean withOffset) {
        if (withOffset) return (x,y, z)->mask.insideMaskWithOffset(x, y, z); 
        else return (x,y, z)->mask.insideMask(x, y, z);
    }
    public static LoopPredicate borderOfMask(final ImageMask mask, boolean withOffset) {
        EllipsoidalNeighborhood n = mask.sizeZ()>1 ? new EllipsoidalNeighborhood(1.5, 1.5, true) : new EllipsoidalNeighborhood(1.5, true);
        if (withOffset) {
            // remove offset to n so that n.hasNullValue works with offset
            for (int i = 0; i<n.getSize(); ++i) {
                n.dx[i] -= mask.xMin();
                n.dy[i] -= mask.yMin();
                n.dz[i] -= mask.zMin();
            }
            return (x,y, z)-> {
                if (!mask.insideMaskWithOffset(x, y, z)) return false;
                return n.hasNullValue(x, y, z, mask, true);
            };
        } else {
            return (x,y, z)-> {
                if (!mask.insideMask(x, y, z)) return false;
                return n.hasNullValue(x, y, z, mask, true);
            };
        }
    }
}
