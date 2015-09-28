package dataStructure.objects;


public class Voxel2D extends Voxel {
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
        return "Voxel2D{" + "x=" + x + ", y=" + y + ", value=" + value + '}';
    }

    @Override
    public Voxel2D copy() {
        return new Voxel2D(x, y);
    }
    
    @Override
    public Voxel2D translate(int dX, int dY, int dZ) {
        x+=dX;
        y+=dY;
        return this;
    }
    
}
