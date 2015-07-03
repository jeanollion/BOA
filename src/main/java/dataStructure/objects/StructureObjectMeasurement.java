package dataStructure.objects;

public interface StructureObjectMeasurement extends StructureObjectProcessing {
    /**
     * 
     * @param structureIdx index of the children's structure. If this index is the same as the ObjectStructure instance calling the method object null will be returned
     * @return the structureObjects of the structure {@param structureIdx} that have this structureObject as parent
     */
    public StructureObject[] getChildObjects(int structureIdx);
}
