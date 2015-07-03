package dataStructure.objects;

import dataStructure.containers.ImageContainer;
import dataStructure.containers.ImageContainer;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import image.BoundingBox;
import image.Image;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Transient;

public class StructureObjectRoot extends StructureObject {
    protected ObjectId experimentId;
    @Embedded protected ImageContainer[] inputImageContainersS ;
    @Transient private Image[] uncorrectedImagesS;
    @Transient private StructureObjectCollection collection;

    public StructureObjectRoot(int timePoint, Object3D object) {
        super(timePoint, -1, -1, object, null);
    }
    
    @Override
    public Image getRawImage(int structureIdx) {
        if (rawImagesS[structureIdx]==null) {
            rawImagesS[structureIdx]=inputImageContainersS[structureIdx].getImage();
        }
        return rawImagesS[structureIdx];
    }
    
    public Image openRawImage(int structureIdx, BoundingBox bounds) {
        if (bounds==null) {
            if (rawImagesS[structureIdx]!=null) return rawImagesS[structureIdx].duplicate(rawImagesS[structureIdx].getName());
            else return inputImageContainersS[structureIdx].getImage();
        } else return inputImageContainersS[structureIdx].getImage(bounds);
    }
    
    @Override
    public StructureObjectRoot getFirstParentWithOpenedRawImage(int structureIdx) {
        if (rawImagesS[structureIdx]!=null) return this;
        else return null;
    }
    
    @Override
    public Experiment getExperiment() {
        if (experiment==null) {
            // create or global get experiment -> need to know how to access morphia, mongoclient etc...
        } 
        return experiment;
    }
    
   // getUncorrectedImage
}
