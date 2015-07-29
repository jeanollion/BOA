package dataStructure.objects;

import dataStructure.containers.ObjectContainerImage;
import dataStructure.containers.ObjectContainerImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.containers.MultipleImageContainer;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.annotations.Transient;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import org.bson.types.ObjectId;


@Entity(collectionName = "RootObjects")
@Index(value={"timePoint", "newTrackBranch"})
public class StructureObjectRoot extends StructureObjectAbstract {
    //protected ObjectId experimentId;
    @Transient protected MultipleImageContainer preProcessedImages;
    @Transient protected int[] structureToImageFileCorrespondenceC;
    @Transient protected RootObjectDAO rootObjectDAO;
    @Transient protected ObjectDAO objectDAO;
    @Transient String outputFileDirectory;
    
    public StructureObjectRoot(int timePoint, Experiment xp, BlankMask mask, MultipleImageContainer preProcessedImages) {
        super(timePoint, new Object3D(mask, 1), xp);
        this.preProcessedImages=preProcessedImages;
        this.structureToImageFileCorrespondenceC=xp.getStructureToChannelCorrespondance();
        this.outputFileDirectory=xp.getOutputImageDirectory();
    }
    
    protected String getOutputFileDirectory() {return outputFileDirectory;}
    
    // for output object
    protected String getSubDirectory() {
        return getOutputFileDirectory()+File.separator+"processed"+File.separator+new DecimalFormat("00000").format(timePoint);
    }
    
    @Override
    public void createObjectContainer() {
        this.objectContainer=object.getObjectContainer(null); // blank masks
    }
    
    /**
     * 
     * @param pathToRoot array of structure indices, in hierachical order, from the root to the given structure
     * @return all the objects of the last structure of the path
     */
    public ArrayList<StructureObject> getAllObjects(int[] pathToRoot) { // copie dans l'objet root??
        if (pathToRoot.length==0) return new ArrayList<StructureObject>(0);
        ArrayList<StructureObject> currentChildren = new ArrayList<StructureObject>(getChildObjects(pathToRoot[0]).length);
        Collections.addAll(currentChildren, getChildObjects(pathToRoot[0]));
        for (int i = 1; i<pathToRoot.length; ++i) {
            currentChildren = getAllChildren(currentChildren, pathToRoot[i]);
        }
        return currentChildren;
    }
    
    public ArrayList<StructureObjectAbstract> getAllParentObjects(int[] pathToRoot) {
        if (pathToRoot.length==0) return new ArrayList<StructureObjectAbstract>(0);
        else if (pathToRoot.length==1) {
            ArrayList<StructureObjectAbstract> res = new ArrayList<StructureObjectAbstract>(1);
            res.add(this);
            return res;
        } else {
            int[] pathToRoot2 = new int[pathToRoot.length-1];
            for (int i = 0; i<pathToRoot2.length; ++i) pathToRoot2[i]=pathToRoot[i];
            ArrayList<StructureObject> allParents = getAllObjects(pathToRoot2);
            ArrayList<StructureObjectAbstract> res = new ArrayList<StructureObjectAbstract>(allParents.size());
            res.addAll(allParents);
            return res;
        }
        
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
            rawImagesS[channelIdx]=preProcessedImages.getImage(0, channelIdx);
        }
        return rawImagesS[channelIdx];
    }
    
    public Image openRawImage(int structureIdx, BoundingBox bounds) {
        int channelIdx = this.structureToImageFileCorrespondenceC[structureIdx];
        if (rawImagesS[channelIdx]==null) return preProcessedImages.getImage(0, channelIdx, bounds);
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
        dao.store(this);
    }
    
    public void delete(RootObjectDAO dao) {
        dao.delete(this);
    }

    
    
}
