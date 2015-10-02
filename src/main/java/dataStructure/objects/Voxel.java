package dataStructure.objects;

import java.util.Comparator;


public class Voxel implements Comparable<Voxel> {
    public int x, y;
    public float value;
    public int z;

    public Voxel(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Voxel(int x, int y, int z, float value) {
        this(x, y, z);
        this.value = value;
    }
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof Voxel) {
            Voxel otherV = (Voxel)other;
            return x==otherV.x && y==otherV.y && z==otherV.z;
        } else return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + this.x;
        hash = 97 * hash + this.y;
        hash = 97 * hash + this.z;
        return hash;
    }

    @Override
    public String toString() {
        return "Voxel3D{" + "x=" + x + ", y=" + y + ", z=" + z + ", value=" + value + '}';
    }

    public Voxel copy() {
        return new Voxel(x, y, z);
    }

    public Voxel translate(int dX, int dY, int dZ) {
        x+=dX;
        y+=dY;
        z+=dZ;
        return this;
    }
    
    public static Comparator<Voxel> getInvertedComparator() {
        return new Comparator<Voxel>() {
            @Override
            public int compare(Voxel voxel, Voxel other) {
                if (voxel.value < other.value) {
                    return 1;
                } else if (voxel.value > other.value) {
                    return -1;
                } else {
                    return 0;
                }
            }
        };
    }

    public int compareTo(Voxel other) {
        if (value < other.value) {
            return -1;
        } else if (value > other.value) {
            return 1;
        } else {
            return 0;
        }
    }
    
}
