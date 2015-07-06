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

@Entity(value = "Objects", noClassnameStored = true)
@Indexes(@Index(fields={@Field(value="parentId"), @Field(value="structureIdx"), @Field(value="idx")}, options=@IndexOptions(unique=true)))
public class StructureObject extends StructureObjectAbstract {
    protected int structureIdx;
    protected int idx;
    protected ObjectId parentId;
    
    @Transient protected StructureObjectAbstract parent;
    
    
    public StructureObject(int timePoint, int structureIdx, int idx, Object3D object, StructureObjectAbstract parent, Experiment xp) {
        super(timePoint, object, xp);
        this.structureIdx = structureIdx;
        this.idx = idx;
        this.parent=parent;
        this.parentId=parent.id;
    }
    
    protected void setObjectContainer(Experiment xp) {
        this.objectContainer=object.getObjectContainer(xp.getOutputImagePath()+File.separator+"processed_t"+timePoint+"_s"+structureIdx+".png");
    }
    
    @Override
    public Image getRawImage(int structureIdx) {
        if (rawImagesS[structureIdx]==null) { // chercher l'image chez le parent avec les bounds
            StructureObjectAbstract parentWithImage=getFirstParentWithOpenedRawImage(structureIdx);
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
    
    @Override
    public StructureObjectAbstract getFirstParentWithOpenedRawImage(int structureIdx) {
        if (parent.rawImagesS[structureIdx]!=null) return parent;
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
        this.parentId=parent.id;
    }

    public StructureObjectRoot getRoot() {
        if (parent!=null) {
            if (parent instanceof StructureObjectRoot) return (StructureObjectRoot)parent;
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
    
    public void save(ObjectDAO dao) {
        //TODO tester si objectId!=null -> remplacer ou updater 
        //TODO voir s'il existe une query groupée
        if (this.id!=null) delete(dao); //TODO remove id?
        dao.save(this);
    }
    
    public void update(ObjectDAO dao) {
        //TODO
    }
    
    public void delete(ObjectDAO dao) {
        dao.delete(this);
    }

    @Override
    public StructureObject[] getChildObjects(int structureIdx) {
        return childrenSM[structureIdx];
    }
    
    // morphia
    //@PrePersist void prePersist() {parentId=parent.id; createObjectContainer();}
}
