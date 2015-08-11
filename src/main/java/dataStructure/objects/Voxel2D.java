package dataStructure.objects;


public class Voxel2D extends Voxel implements Comparable<Voxel2D>{
    public Voxel2D(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Voxel2D(int x, int y, float value) {
        this(x, y);
        this.value = value;
    }
    
    public int getZ() {return 0;}

    @Override
    public int compareTo(Voxel2D other) {
        if (other.value<value) return -1;
        else if (other.value>value) return 1;
        else return 0;
    }
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof Voxel2D) {
            Voxel2D otherV = (Voxel2D)other;
            return x==otherV.x && y==otherV.y;
        } else return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + this.x;
        hash = 97 * hash + this.y;
        return hash;
    }

    @Override
    public String toString() {
        return "Voxel3D{" + "x=" + x + ", y=" + y + ", value=" + value + '}';
    }
    
}
