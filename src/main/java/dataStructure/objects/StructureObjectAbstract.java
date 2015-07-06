package dataStructure.objects;

import dataStructure.containers.ImageContainer;
import dataStructure.configuration.Experiment;
import dataStructure.containers.ObjectContainer;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import java.io.File;
import java.util.ArrayList;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.IndexOptions;
import org.mongodb.morphia.annotations.Indexes;
import org.mongodb.morphia.annotations.PrePersist;
import org.mongodb.morphia.annotations.Reference;
import org.mongodb.morphia.annotations.Transient;
import plugins.Segmenter;
import static processing.PluginSequenceRunner.*;


public abstract class StructureObjectAbstract implements StructureObjectMeasurement {
    @Id protected ObjectId id;
    protected int timePoint;
    @Transient Object3D object;
    
    @Embedded protected ObjectContainer objectContainer;
    
    @Transient protected StructureObject[][] childrenSM;
    
    @Transient protected Image[] rawImagesS;
    @Transient protected Image[] preProcessedImageS;
    
    public StructureObjectAbstract(int timePoint, Object3D object, Experiment xp) {
        this.timePoint = timePoint;
        this.object=object;
        this.childrenSM=new StructureObject[xp.getStructureNB()][];
        this.rawImagesS=new Image[xp.getStructureNB()];
        this.preProcessedImageS=new Image[xp.getStructureNB()];
        
    }
    
    protected abstract void setObjectContainer(Experiment xp);
    
    public abstract Image getRawImage(int structureIdx);
    
    public abstract StructureObjectAbstract getFirstParentWithOpenedRawImage(int structureIdx);
    
    public Image getFilteredImage(int structureIdx) {
        return preProcessedImageS[structureIdx];
    }
    
    public void createPreFilterImage(int structureIdx, Experiment xp) {
        preProcessedImageS[structureIdx]=preFilterImage(getRawImage(structureIdx), this, xp.getStructure(structureIdx).getProcessingChain().getPrefilters());
    }
    
    public void segmentChildren(int structureIdx, Experiment xp) {
        if (getFilteredImage(structureIdx)==null) createPreFilterImage(structureIdx, xp);
        ImageInteger seg = segmentImage(getFilteredImage(structureIdx), this, xp.getStructure(structureIdx).getProcessingChain().getSegmenter());
        if (seg instanceof BlankMask) childrenSM[structureIdx]=new StructureObject[0];
        else {
            seg = postFilterImage(seg, this, xp.getStructure(structureIdx).getProcessingChain().getPostfilters());
            ImageLabeller.labelImage(seg);
        }
    }
    

    public StructureObject[] getChildObjects(int structureIdx) {
        return childrenSM[structureIdx];
    }

    public abstract StructureObjectRoot getRoot();
    
    /**
     * @return an array of structure indices, starting from the first structure after the root structure, ending at the structure index (included)
     */
    public abstract int[] getPathToRoot();

    public int getTimePoint() {
        return timePoint;
    }
    
    public void createObjectContainer(String path) {
        this.objectContainer=object.getObjectContainer(path); // Ajouter le nom de l'image..
    }
    
    // DAO methods
    
    public void deleteChildren(ObjectDAO dao, int structureIdx) {
        dao.deleteChildren(id, structureIdx);
    }
    
    public void populateChildren(int structureIdx, ObjectDAO dao) {
        this.childrenSM[structureIdx]=dao.getObjects(id, structureIdx);
    }
    
    // morphia
    //@PrePersist void prePersist() {parentId=parent.id; createObjectContainer();}
}
