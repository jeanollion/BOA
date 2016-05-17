package dataStructure.objects;

import static dataStructure.objects.Object3D.logger;
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
    public Voxel(int[] xyz) {
        this.x = xyz[0];
        this.y = xyz[1];
        if (xyz.length>2) this.z = xyz[2];
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

    public Voxel duplicate() {
        return new Voxel(x, y, z);
    }

    public Voxel translate(int dX, int dY, int dZ) {
        //if ((x+dX)<0) logger.debug("voxel neg: dX: {}, v: {}", dX, this);
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
                } else { // consistancy with equals method
                    if (voxel.x<other.x) return 1;
                    else if (voxel.x>other.x) return -1;
                    else if (voxel.y<other.y) return 1;
                    else if (voxel.y>other.y) return -1;
                    else if (voxel.z<other.z) return 1;
                    else if (voxel.z>other.z) return -1;
                    else return 0;
                }
            }
        };
    }
    public static Comparator<Voxel> getComparator() {
        return new Comparator<Voxel>() {
            @Override
            public int compare(Voxel voxel, Voxel other) {
                if (voxel.value < other.value) {
                    return -1;
                } else if (voxel.value > other.value) {
                    return 1;
                } else {// consistancy with equals method
                    if (voxel.x<other.x) return -1;
                    else if (voxel.x>other.x) return 1;
                    else if (voxel.y<other.y) return -1;
                    else if (voxel.y>other.y) return 1;
                    else if (voxel.z<other.z) return -1;
                    else if (voxel.z>other.z) return 1;
                    else return 0;
                }
            }
        };
    }

    public int compareTo(Voxel other) {
        if (value < other.value) {
            return -1;
        } else if (value > other.value) {
            return 1;
        } else {// consistancy with equals method
            if (x<other.x) return -1;
            else if (x>other.x) return 1;
            else if (y<other.y) return -1;
            else if (y>other.y) return 1;
            else if (z<other.z) return -1;
            else if (z>other.z) return 1;
            else return 0;
        }
    }
    
    public double getDistanceSquare(Voxel other, double scaleXY, double scaleZ) {
        return Math.pow((x-other.x) * scaleXY, 2) + Math.pow((y-other.y) * scaleXY, 2) + Math.pow((z-other.z) * scaleZ, 2);
    }
    
    public double getDistanceSquare(Voxel other) {
        return (x-other.x)*(x-other.x) + (y-other.y)*(y-other.y) + (z-other.z)*(z-other.z);
    }
    
    public double getDistance(Voxel other, double scaleXY, double scaleZ) {
        return Math.sqrt(Math.pow((x-other.x) * scaleXY, 2) + Math.pow((y-other.y) * scaleXY, 2) + Math.pow((z-other.z) * scaleZ, 2));
    }
    
    public double getDistance(Voxel other) {
        return Math.sqrt((x-other.x)*(x-other.x) + (y-other.y)*(y-other.y) + (z-other.z)*(z-other.z));
    }
    
}
