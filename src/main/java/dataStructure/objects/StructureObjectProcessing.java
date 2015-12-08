package dataStructure.objects;

import image.Image;

public interface StructureObjectProcessing extends StructureObjectPreProcessing {

    public Image getRawImage(int structureIdx);
    @Override public StructureObjectProcessing getNext();
    @Override public StructureObjectProcessing getPrevious();
    public void setChildren(ObjectPopulation children, int structureIdx);
}
