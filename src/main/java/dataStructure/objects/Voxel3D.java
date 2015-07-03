package dataStructure.objects;

import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Transient;

@Embedded
public class Voxel3D implements Comparable<Voxel3D>{

    public int x;
    public int y;
    public int z;
    @Transient float value;

    public Voxel3D(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Voxel3D(int x, int y, int z, float value) {
        this(x, y, z);
        this.value = value;
    }

    @Override
    public int compareTo(Voxel3D other) {
        if (other.value<value) return -1;
        else if (other.value>value) return 1;
        else return 0;
    }
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof Voxel3D) {
            Voxel3D otherV = (Voxel3D)other;
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
    
    //morphia
    private Voxel3D(){};
}
