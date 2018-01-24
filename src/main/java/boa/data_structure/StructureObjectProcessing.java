package boa.data_structure;

import boa.image.Image;
import java.util.ArrayList;
import java.util.List;

public interface StructureObjectProcessing extends StructureObjectPreProcessing {

    public Image getRawImage(int structureIdx);
    @Override public StructureObjectProcessing getNext();
    @Override public StructureObjectProcessing getPrevious();
    public List<? extends StructureObjectPostProcessing> setChildrenObjects(RegionPopulation children, int structureIdx);
}
