package dataStructure.objects;

import image.Image;

public interface StructureObjectProcessing extends StructureObjectPreFilter {

    public Image getFilteredImage(int structureIdx);
}
