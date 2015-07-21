package dataStructure.objects;

import dataStructure.objects.dao.ObjectDAO;
import image.ImageLabeller;
import dataStructure.containers.ObjectContainerImage;
import dataStructure.configuration.Experiment;
import dataStructure.containers.ObjectContainer;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Transient;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageMask;
import java.io.File;
import java.util.ArrayList;
import org.bson.types.ObjectId;
import static processing.PluginSequenceRunner.*;

public abstract class StructureObjectAbstract implements StructureObjectPostProcessing, Track {
    @Id protected ObjectId id;
    @Transient protected StructureObject[][] childrenSM;
    
    // track-related attributes
    protected int timePoint;
    protected ObjectId previousId, nextId;
    @Transient protected StructureObjectAbstract previous;
    @Transient protected StructureObjectAbstract next;
    protected boolean newTrackBranch=true;
    //@Transient protected boolean parentTrackLoaded=false;
    //@Transient protected boolean childTrackLoaded=false;
    
    // mask and images
    @Transient Object3D object;
    protected ObjectContainer objectContainer;
    @Transient protected Image[] rawImagesS;
    @Transient protected Image[] preProcessedImageS;
    
    public StructureObjectAbstract(int timePoint, Object3D object, Experiment xp) {
        this.timePoint = timePoint;
        this.object=object;
        this.childrenSM=new StructureObject[xp.getStructureNB()][];
        this.rawImagesS=new Image[xp.getStructureNB()];
        this.preProcessedImageS=new Image[xp.getStructureNB()];
        this.objectContainer=object.getObjectContainer(xp.getOutputImageDirectory());
    }
    
    protected abstract String getSubDirectory();
    
    /**
     * 
     * @param parent the parent of the current object in the track
     * @param isChildOfParent if true, sets this instance as the child of {@param parent} 
     */
    public void setParentTrack(StructureObjectPreProcessing parent, boolean isChildOfParent) {
        this.previous=(StructureObjectAbstract)parent;
        if (isChildOfParent) {
            previous.next=this;
            newTrackBranch=false;
        }
        else this.newTrackBranch=true;
    }
    
    @Override
    public StructureObjectAbstract getPrevious() {
        return previous;
    }
    
    @Override
    public StructureObjectAbstract getNext() {
        return next;
    }
    
    
    public ImageMask getMask() {
        return object.getMask();
    }
    
    public BoundingBox getBounds() {
        return object.getBounds();
    }
    
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
        if (childrenSM[structureIdx]==null) childrenSM[structureIdx]=getRoot().objectDAO.getObjects(this.id, structureIdx);
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
    
    public abstract void createObjectContainer();
    
    // DAO methods
    
    public void deleteChildren(ObjectDAO dao, int structureIdx) {
        dao.deleteChildren(id, structureIdx);
    }
    
    public void populateChildren(int structureIdx, ObjectDAO dao) {
        this.childrenSM[structureIdx]=dao.getObjects(id, structureIdx);
    }
    
}
