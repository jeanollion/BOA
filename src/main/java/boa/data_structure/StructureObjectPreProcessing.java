package boa.data_structure;

import boa.image.Image;
import boa.image.ImageMask;

public interface StructureObjectPreProcessing extends Track {

    public Image getRawImage(int structureIdx);
    public ImageMask getMask();
    public Region getRegion();
    @Override public StructureObjectPreProcessing getNext();
    
}
