package dataStructure.objects;

import java.util.ArrayList;

public interface StructureObjectPostProcessing extends StructureObjectProcessing {
    /**
     * 
     * @param structureIdx index of the children's structure. If this index is the same as the ObjectStructure instance calling the method object null will be returned
     * @return the structureObjects of the structure {@param structureIdx} that have this structureObject as parent
     */
    public StructureObjectPostProcessing[] getChildObjects(int structureIdx);
    @Override public StructureObjectPostProcessing getNext();
}
