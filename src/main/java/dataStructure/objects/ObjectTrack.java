package dataStructure.objects;

public class ObjectTrack {

    private int defBucketSize;
    protected ObjectTrack parent;
    protected StructureObject data;
    protected int[] registrationOffset;
    protected Rotation rotation;
    
    public ObjectTrack getNextObject() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int getObjectsAtTimePoint(int timePoint) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public StructureObject getStructureObject() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
