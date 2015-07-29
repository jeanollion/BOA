package dataStructure.objects;

import core.ImagePath;
import dataStructure.containers.ObjectContainerImage;
import dataStructure.configuration.Experiment;
import dataStructure.containers.ObjectContainer;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.caching.Cache;
import de.caluga.morphium.annotations.lifecycle.PreStore;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import org.bson.types.ObjectId;

@Cache
@Entity(collectionName = "Objects")
@Index(value={"parent_id,structure_idx,idx", "new_track_branch"}, options={"unique:1", ""})
public class StructureObject extends StructureObjectAbstract {
    protected int structureIdx;
    protected int idx;
    protected ObjectId parentId;
    @Transient private StructureObjectAbstract parent;
    //Registrator -> registration locale
    
    public StructureObject(int timePoint, int structureIdx, int idx, Object3D object, StructureObjectAbstract parent, Experiment xp) {
        super(timePoint, object, xp);
        this.structureIdx = structureIdx;
        this.idx = idx;
        this.parent=parent;
    }
    
    protected void setObjectContainer(Experiment xp) {
        this.objectContainer=object.getObjectContainer(xp.getOutputImageDirectory()+File.separator+"processed_t"+timePoint+"_s"+structureIdx+".png");
    }
    
    public int getStructureIdx() {
        return structureIdx;
    }
    
    public int getIdx() {return idx;}
    
    @Override
    public void createObjectContainer() {
        this.objectContainer=object.getObjectContainer(ImagePath.getProcessedImageFile(this));
    }

    @Override
    public Image getRawImage(int structureIdx) {
        int channelIdx = getRoot().getChannelImageIdx(structureIdx);
        if (rawImagesC.get(channelIdx)==null) { // chercher l'image chez le parent avec les bounds
            StructureObjectAbstract parentWithImage=getFirstParentWithOpenedRawImage(structureIdx);
            if (parentWithImage!=null) {
                BoundingBox bb=getRelativeBoundingBox(parentWithImage);
                rawImagesC.set(parentWithImage.getRawImage(structureIdx).crop(bb), channelIdx);
            } else { // opens only the bb of the object
                StructureObjectRoot root = getRoot();
                BoundingBox bb=getRelativeBoundingBox(root);
                rawImagesC.set(root.openRawImage(structureIdx, bb), channelIdx);
            }
        }
        return rawImagesC.get(channelIdx);
    }
    
    @Override
    public StructureObjectAbstract getFirstParentWithOpenedRawImage(int structureIdx) {
        if (parent.rawImagesC.get(getRoot().getChannelImageIdx(structureIdx))!=null) return parent;
        else return parent.getFirstParentWithOpenedRawImage(structureIdx);
    }
    
    protected BoundingBox getRelativeBoundingBox(StructureObjectAbstract stop) {
        StructureObjectAbstract nextParent=this;
        BoundingBox res = object.bounds.duplicate();
        do {
            nextParent=((StructureObject)nextParent).parent;
            res.addOffset(nextParent.object.bounds);
        } while(nextParent!=stop);
        return res;
    }

    public StructureObjectAbstract getParent() {
        return parent;
    }
    
    public void setParent(StructureObjectAbstract parent) {
        this.parent=parent;
    }

    public StructureObjectRoot getRoot() {
        if (parent!=null) {
            if (parent.isRoot()) return (StructureObjectRoot)parent;
            else return parent.getRoot();
        } else return null;
    }
    
    /**
     * @return an array of structure indices, starting from the first structure after the root structure, ending at the structure index (included)
     */
    public int[] getPathToRoot() {
        ArrayList<Integer> pathToRoot = new ArrayList<Integer>();
        pathToRoot.add(this.structureIdx);
        StructureObjectAbstract p = parent;
        while (!(parent instanceof StructureObjectRoot)) {
            pathToRoot.add(((StructureObject)p).structureIdx);
            p=((StructureObject)p).parent;
        }
        int[] res = new int[pathToRoot.size()];
        int i = res.length-1;
        for (int s : pathToRoot) res[i--] = s;
        return res;
    }
    
    // DAO methods
    
    public void save() {
        getRoot().objectDAO.store(this);
    }
    
    public void delete() {
        getRoot().objectDAO.delete(this);
    }
    
    @Override
    public void updateTrackLinks() {
        getRoot().objectDAO.updateTrackLinks(this);
    }
    
    @Override
    public StructureObject getPrevious() {
        if (previous==null && previousId!=null) previous = getRoot().objectDAO.getObject(previousId);
        return (StructureObject)previous;
    }
    
    @Override
    public StructureObject getNext() {
        if (next==null && nextId!=null) next = getRoot().objectDAO.getObject(nextId);
        return (StructureObject)next;
    }
    
    @Override 
    public boolean isRoot() {return false;}
    
    // morphium
    @Override
    @PreStore 
    public void preStore() {
        super.preStore();
        if (parent!=null) parentId=parent.id;
    }
}
