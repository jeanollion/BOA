package dataStructure.objects;

import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.containers.ImageDAO;
import dataStructure.containers.MultipleImageContainer;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.annotations.Reference;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.caching.Cache;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageReader;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import org.bson.types.ObjectId;

@Cache
@Entity(collectionName = "RootObjects")
@Index(value={"time_point,new_track_branch", "name,time_point"})
public class StructureObjectRoot extends StructureObjectAbstract {
    @Reference(lazyLoading=true) Experiment xp;
    @Transient protected RootObjectDAO rootObjectDAO;
    @Transient protected ObjectDAO objectDAO;
    String fieldName;
    public StructureObjectRoot(String fieldName, int timePoint, Experiment xp, BlankMask mask, RootObjectDAO rootObjectDAO, ObjectDAO objectDAO) {
        super(timePoint, new Object3D(mask, 1), xp);
        this.fieldName=fieldName;
        setUp(xp, rootObjectDAO, objectDAO);
        
    }
    
    public void setUp(Experiment xp, RootObjectDAO rootObjectDAO, ObjectDAO objectDAO) {
        this.xp=xp;
        //this.structureToImageFileCorrespondenceS=xp.getStructureToChannelCorrespondance();
        //this.outputFileDirectory=xp.getOutputImageDirectory();
        this.rootObjectDAO=rootObjectDAO;
        this.objectDAO=objectDAO;
    }
    
    public int getChannelImageIdx(int structureIdx) {
        return xp.getChannelImageIdx(structureIdx);
    }
    
    public MicroscopyField getMicroscopyField() {
        return xp.getMicroscopyField(fieldName);
    }
    
    public String getOutputFileDirectory() {return xp.getOutputImageDirectory();}
    
    public String getName() {
        return fieldName;
    }
    
    @Override
    public void createObjectContainer() {
        this.objectContainer=getObject().getObjectContainer(null); // blank mask
    }
    
    public ImageDAO getImageDAO() {
        return xp.getImageDAO();
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
        int channelIdx = getChannelImageIdx(structureIdx);
        if (rawImagesC.get(channelIdx)==null) rawImagesC.set(getImageDAO().openPreProcessedImage(channelIdx, timePoint, fieldName), channelIdx);
        return rawImagesC.get(channelIdx);
    }
    
    public Image openRawImage(int structureIdx, BoundingBox bounds) {
        int channelIdx = getChannelImageIdx(structureIdx);
        if (rawImagesC.get(channelIdx)==null) return getImageDAO().openPreProcessedImage(channelIdx, timePoint, fieldName, bounds);
        else return rawImagesC.get(channelIdx).crop(bounds);
    }
    
    @Override
    public StructureObjectRoot getFirstParentWithOpenedRawImage(int structureIdx) {
        if (rawImagesC.get(getChannelImageIdx(structureIdx))!=null) return this;
        else return null;
    }
    
    @Override
    public StructureObjectRoot getRoot() {return this;}
    
    @Override
    public int[] getPathToRoot() {return new int[0];}
    
    @Override 
    public boolean isRoot() {return true;}
    
    // getUncorrectedImage
    
    // DAO methods
    public void save(RootObjectDAO dao) {
        dao.store(this);
    }
    
    public void delete(RootObjectDAO dao) {
        dao.delete(this);
    }

    @Override
    public void updateTrackLinks() {
        rootObjectDAO.updateTrackLinks(this);
    }
    
    @Override
    public StructureObjectRoot getPrevious() {
        if (previous==null && previousId!=null) previous = rootObjectDAO.getObject(previousId);
        return (StructureObjectRoot)previous;
    }
    
    @Override
    public StructureObjectRoot getNext() {
        if (next==null && nextId!=null) {
            next = rootObjectDAO.getObject(nextId);
        }
        return (StructureObjectRoot)next;
    }
    
}
