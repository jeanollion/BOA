package dataStructure.objects;

import image.Image;
import image.ImageMask;

public interface StructureObjectPreProcessing extends Track {

    public Image getRawImage(int structureIdx);
    public ImageMask getMask();
    @Override public StructureObjectPreProcessing getNext();
    
}
