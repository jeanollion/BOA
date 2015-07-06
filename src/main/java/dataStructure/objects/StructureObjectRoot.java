package dataStructure.objects;

import dataStructure.containers.ImageContainer;
import dataStructure.containers.ImageContainer;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import image.BoundingBox;
import image.Image;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.IndexOptions;
import org.mongodb.morphia.annotations.Indexes;
import org.mongodb.morphia.annotations.Transient;

@Entity(value = "RootObjects", noClassnameStored = true)
@Indexes(@Index(fields={@Field(value="timePoint"), @Field(value="structureIdx")}, options=@IndexOptions(unique=true)))
public class StructureObjectRoot extends StructureObjectAbstract {
    //protected ObjectId experimentId;
    @Embedded protected ImageContainer[] correctedImageContainersC;
    @Embedded protected ImageContainer[] uncorrectedImageContainersC;
    @Transient private Image[] uncorrectedImagesC;
    @Transient protected int[] structureToImageFileCorrespondenceC;
    
    public StructureObjectRoot(int timePoint, Object3D object, Experiment xp, ImageContainer[] uncorrectedImagesC) {
        super(timePoint, object, xp);
        this.uncorrectedImageContainersC=uncorrectedImagesC;
        this.structureToImageFileCorrespondenceC=xp.getStructureToChannelCorrespondance();
    }
    
    protected void setObjectContainer(Experiment xp) {
        this.objectContainer=object.getObjectContainer(null); // blankmask
    }
    
    public ArrayList<StructureObject> getAllChildren(int[] pathToRoot) { // copie dans l'objet root??
        ArrayList<StructureObject> currentChildren = new ArrayList<StructureObject>(getChildObjects(pathToRoot[0]).length);
        Collections.addAll(currentChildren, getChildObjects(pathToRoot[0]));
        for (int i = 1; i<pathToRoot.length; ++i) {
            currentChildren = getAllChildren(currentChildren, pathToRoot[i]);
        }
        return currentChildren;
    }
    
    private static ArrayList<StructureObject> getAllChildren(ArrayList<StructureObject> parents, int childrenStructureIdx) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        for (StructureObject parent : parents) Collections.addAll(res, parent.getChildObjects(childrenStructureIdx));
        return res;
    } 
    
    @Override
    public Image getRawImage(int structureIdx) {
        int channelIdx = this.structureToImageFileCorrespondenceC[structureIdx];
        if (rawImagesS[channelIdx]==null) {
            rawImagesS[channelIdx]=correctedImageContainersC[channelIdx].getImage();
        }
        return rawImagesS[channelIdx];
    }
    
    public Image openRawImage(int structureIdx, BoundingBox bounds) {
        int channelIdx = this.structureToImageFileCorrespondenceC[structureIdx];
        if (rawImagesS[channelIdx]==null) return correctedImageContainersC[channelIdx].getImage();
        else return rawImagesS[channelIdx].crop(bounds);
    }
    
    @Override
    public StructureObjectRoot getFirstParentWithOpenedRawImage(int structureIdx) {
        if (rawImagesS[structureIdx]!=null) return this;
        else return null;
    }
    
    @Override
    public StructureObjectRoot getRoot() {return this;}
    
    @Override
    public int[] getPathToRoot() {return new int[0];}
    
    
    // getUncorrectedImage
    
    // DAO methods
    public void save(RootObjectDAO dao) {
        //TODO tester si objectId!=null -> remplacer ou updater 
        //TODO voir s'il existe une query groupée
        if (this.id!=null) delete(dao); //TODO remove id?
        dao.save(this);
    }
    
    public void update(RootObjectDAO dao) {
        //TODO
    }
    
    public void delete(RootObjectDAO dao) {
        dao.delete(this);
    }
    
}
