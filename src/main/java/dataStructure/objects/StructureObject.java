package dataStructure.objects;

import dataStructure.containers.ImageContainer;
import dataStructure.configuration.Experiment;
import dataStructure.containers.ObjectContainer;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PrePersist;
import org.mongodb.morphia.annotations.Reference;
import org.mongodb.morphia.annotations.Transient;
import plugins.Segmenter;
import static processing.PluginSequenceRunner.*;

@Entity(value = "Object", noClassnameStored = true)
public class StructureObject implements StructureObjectMeasurement {
    @Id protected ObjectId id;
    protected int timePoint;
    protected int structureIdx;
    protected int idx;
    protected ObjectId parentId;
    @Transient Object3D object;
    
    @Embedded protected ObjectContainer objectContainer;
    
    @Transient protected StructureObject parent;
    @Transient protected StructureObject[][] childrenSM;
    
    @Transient protected Image[] rawImagesS;
    @Transient protected Image[] preProcessedImageS;
    
    @Transient Experiment experiment;

    public StructureObject(int timePoint, int structureIdx, int idx, Object3D object, StructureObject parent) {
        this.timePoint = timePoint;
        this.structureIdx = structureIdx;
        this.idx = idx;
    }
    
    public Experiment getExperiment() {
        if (experiment==null) {
            getRoot().getExperiment();
        } 
        return experiment;
    }
    
    public Image getRawImage(int structureIdx) {
        if (rawImagesS[structureIdx]==null) { // chercher l'image chez le parent avec les bounds
            StructureObject parentWithImage=getFirstParentWithOpenedRawImage(structureIdx);
            if (parentWithImage!=null) {
                BoundingBox bb=getRelativeBoundingBox(parentWithImage);
                rawImagesS[structureIdx]=parentWithImage.getRawImage(structureIdx).crop(bb);
            } else { // opens only the bb of the object
                StructureObjectRoot root = getRoot();
                BoundingBox bb=getRelativeBoundingBox(root);
                rawImagesS[structureIdx]=root.openRawImage(structureIdx, bb);
            }
        }
        return rawImagesS[structureIdx];
    }
    
    public StructureObject getFirstParentWithOpenedRawImage(int structureIdx) {
        if (parent.rawImagesS[structureIdx]!=null) return parent;
        else return parent.getFirstParentWithOpenedRawImage(structureIdx);
    }
    
    protected BoundingBox getRelativeBoundingBox(StructureObject stop) {
        StructureObject nextParent=this;
        BoundingBox res = object.bounds.duplicate();
        do {
            nextParent=nextParent.parent;
            res.addOffset(nextParent.object.bounds);
        } while(nextParent!=stop);
        
        return res;
    }

    public Image getFilteredImage(int structureIdx) {
        if (preProcessedImageS[structureIdx]==null) {
            preProcessedImageS[structureIdx]=preFilterImage(getRawImage(this.structureIdx), this, getExperiment().getStructure(structureIdx).getProcessingChain().getPrefilters());
        }
        return preProcessedImageS[structureIdx];
    }
    
    public void segmentChildren(int structureIdx) {
        ImageInteger seg = segmentImage(getFilteredImage(structureIdx), this, getExperiment().getStructure(structureIdx).getProcessingChain().getSegmenter());
        if (seg instanceof BlankMask) childrenSM[structureIdx]=new StructureObject[0];
        else {
            seg = postFilterImage(seg, this, getExperiment().getStructure(structureIdx).getProcessingChain().getPostfilters());
            ImageLabeller.labelImage(seg);
        }
    }

    public StructureObject[] getChildObjects(int structureIdx) {
        if (structureIdx==this.structureIdx) return null;
        else return childrenSM[structureIdx];
    }

    public StructureObject getParent() {
        return parent;
    }
    
    public void setParent(StructureObject parent) {
        this.parent=parent;
        this.parentId=parent.id;
    }

    public StructureObjectRoot getRoot() {
        if (this instanceof StructureObjectRoot) return (StructureObjectRoot)this;
        else if (parent!=null) {
            if (parent instanceof StructureObjectRoot) return (StructureObjectRoot)parent;
            else return parent.getRoot();
        } else return null;
    }

    public int getTimePoint() {
        return timePoint;
    }
    
    public void createObjectContainer(String path) {
        this.objectContainer=object.getObjectContainer(path); // Ajouter le nom de l'image..
    }
    
    // morphia
    //@PrePersist void prePersist() {parentId=parent.id; createObjectContainer();}
}
