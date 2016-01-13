package dataStructure.objects;

import image.Image;
import java.util.ArrayList;

public interface StructureObjectProcessing extends StructureObjectPreProcessing {

    public Image getRawImage(int structureIdx);
    @Override public StructureObjectProcessing getNext();
    @Override public StructureObjectProcessing getPrevious();
    public ArrayList<? extends StructureObjectPostProcessing> setChildrenObjects(ObjectPopulation children, int structureIdx);
}
