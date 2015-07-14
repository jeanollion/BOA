package dataStructure.objects;

import image.Image;

public interface StructureObjectProcessing extends StructureObjectPreProcessing {

    public Image getFilteredImage(int structureIdx);
    @Override public StructureObjectProcessing getChildTrack();
}
