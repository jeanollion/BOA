package dataStructure.objects;

import image.ImageLabeller;
import dataStructure.containers.ImageContainer;
import dataStructure.configuration.Experiment;
import dataStructure.containers.ObjectContainer;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageMask;
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

@Indexes(@Index(fields={@Field(value="newTrackBranch")}))
public abstract class StructureObjectAbstract implements StructureObjectPostProcessing, Track {
    @Id protected ObjectId id;
    @Reference(lazy=true, idOnly=true) protected StructureObject[][] childrenSM;
    
    // track-related attributes
    protected int timePoint;
    @Reference(lazy=true) protected StructureObjectAbstract parentTrack;
    @Reference(lazy=true) protected StructureObjectAbstract childTrack;
    protected boolean newTrackBranch=true;
    //@Transient protected boolean parentTrackLoaded=false;
    //@Transient protected boolean childTrackLoaded=false;
    
    // mask and images
    @Transient Object3D object;
    @Embedded protected ObjectContainer objectContainer;
    @Transient protected Image[] rawImagesS;
    @Transient protected Image[] preProcessedImageS;
    
    
    public StructureObjectAbstract(int timePoint, Object3D object, Experiment xp) {
        this.timePoint = timePoint;
        this.object=object;
        this.childrenSM=new StructureObject[xp.getStructureNB()][];
        this.rawImagesS=new Image[xp.getStructureNB()];
        this.preProcessedImageS=new Image[xp.getStructureNB()];
    }
    
    /**
     * 
     * @param parent the parent of the current object in the track
     * @param isChildOfParent if true, sets this instance as the child of {@parent} 
     */
    public void setParentTrack(StructureObjectPreProcessing parent, boolean isChildOfParent) {
        this.parentTrack=(StructureObjectAbstract)parent;
        if (isChildOfParent) {
            parentTrack.childTrack=this;
            newTrackBranch=false;
        }
        else this.newTrackBranch=true;
    }
    
    @Override
    public StructureObjectAbstract getParentTrack() {
        return parentTrack;
    }
    
    @Override
    public StructureObjectAbstract getChildTrack() {
        return childTrack;
    }
    
    
    public ImageMask getMask() {
        return object.getMask();
    }
    
    public BoundingBox getBounds() {
        return object.getBounds();
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
