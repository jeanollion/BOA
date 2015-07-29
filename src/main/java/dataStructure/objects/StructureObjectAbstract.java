package dataStructure.objects;

import core.ImagePath;
import image.ImageLabeller;
import dataStructure.containers.ObjectContainerImage;
import dataStructure.configuration.Experiment;
import dataStructure.containers.ObjectContainer;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import de.caluga.morphium.annotations.lifecycle.PostStore;
import de.caluga.morphium.annotations.lifecycle.PreStore;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageMask;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.bson.types.ObjectId;
import static processing.PluginSequenceRunner.*;
import utils.SmallArray;

@Lifecycle
public abstract class StructureObjectAbstract implements StructureObjectPostProcessing, Track {
    @Id protected ObjectId id;
    @Transient protected SmallArray<StructureObject[]> childrenSM=new SmallArray<StructureObject[]>();
    
    // track-related attributes
    protected int timePoint;
    protected ObjectId previousId, nextId;
    @Transient protected StructureObjectAbstract previous;
    @Transient protected StructureObjectAbstract next;
    protected boolean newTrackBranch=true;
    
    // mask and images
    @Transient Object3D object;
    protected ObjectContainer objectContainer;
    @Transient protected SmallArray<Image> rawImagesC=new SmallArray<Image>();
    @Transient protected SmallArray<Image> preProcessedImageS=new SmallArray<Image>();
    
    public StructureObjectAbstract(int timePoint, Object3D object, Experiment xp) {
        this.timePoint = timePoint;
        this.object=object;
    }
    
    /**
     * 
     * @param parent the parent of the current object in the track
     * @param isNewBranch if true, sets this instance as the child of {@param parent} 
     */
    public void setPreviousInTrack(StructureObjectPreProcessing parent, boolean isNewBranch) {
        this.previous=(StructureObjectAbstract)parent;
        if (isNewBranch) {
            previous.next=this;
            newTrackBranch=false;
        }
        else this.newTrackBranch=true;
    }
    
    @Override
    public abstract StructureObjectAbstract getPrevious();
    
    @Override
    public abstract StructureObjectAbstract getNext();
    
    public ImageMask getMask() {return object.getMask();}
    
    public BoundingBox getBounds() {return object.getBounds();}
    
    public ObjectId getId() {return id;}
    
    public abstract Image getRawImage(int structureIdx);
    
    public abstract StructureObjectAbstract getFirstParentWithOpenedRawImage(int structureIdx);
    
    public Image getFilteredImage(int structureIdx) {
        return preProcessedImageS.get(structureIdx);
    }
    
    public void createPreFilterImage(int structureIdx, Experiment xp) {
        Image raw = getRawImage(structureIdx);
        if (raw!=null) preProcessedImageS.set(preFilterImage(getRawImage(structureIdx), this, xp.getStructure(structureIdx).getProcessingChain().getPrefilters()), structureIdx);
    }
    
    
    public void segmentChildren(int structureIdx, Experiment xp) {
        if (getFilteredImage(structureIdx)==null) createPreFilterImage(structureIdx, xp);
        ImageInteger seg = segmentImage(getFilteredImage(structureIdx), this, xp.getStructure(structureIdx).getProcessingChain().getSegmenter());
        if (seg instanceof BlankMask) childrenSM.set(new StructureObject[0], structureIdx);
        else {
            seg = postFilterImage(seg, this, xp.getStructure(structureIdx).getProcessingChain().getPostfilters());
            Object3D[] objects = ImageLabeller.labelImage(seg);
            StructureObject[] res = new StructureObject[objects.length];
            childrenSM.set(res, structureIdx);
            for (int i = 0; i<objects.length; ++i) res[i]=new StructureObject(timePoint, structureIdx, i, objects[i], this, xp);
        }
    }
    
    public void saveChildren(int structureIdx) {
        getRoot().objectDAO.store(childrenSM.get(structureIdx));
    }
    

    public StructureObject[] getChildObjects(int structureIdx) {
        if (childrenSM.getAndExtend(structureIdx)==null) return populateChildren(structureIdx, getRoot().objectDAO);
        else return childrenSM.getQuick(structureIdx);
    }

    public abstract StructureObjectRoot getRoot();
    
    /**
     * @return an array of structure indices, starting from the first structure after the root structure, ending at the structure index (included)
     */
    public abstract int[] getPathToRoot();

    public int getTimePoint() {
        return timePoint;
    }
    
    public abstract void createObjectContainer();
    
    // DAO methods
    
    public void deleteChildren(ObjectDAO dao, int structureIdx) {
        dao.deleteChildren(id, structureIdx);
    }
    
    public StructureObject[] populateChildren(int structureIdx, ObjectDAO dao) {
        StructureObject[] os =dao.getObjects(id, structureIdx);
        for (StructureObject o : os) o.setParent(this);
        childrenSM.set(os, structureIdx);
        return os;
    }
    
    public abstract void updateTrackLinks();
    
    public abstract boolean isRoot();
    
    // lifecycle
    @PreStore public void preStore() {
        if (this.previous!=null) previousId=previous.id; 
        if (this.next!=null) nextId=next.id;
        createObjectContainer();
    }
    @PostLoad public void postLoad() {
        this.object=objectContainer.getObject();
    }
}
