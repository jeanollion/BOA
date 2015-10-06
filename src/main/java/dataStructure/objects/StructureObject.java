package dataStructure.objects;

import dataStructure.configuration.Experiment;
import dataStructure.configuration.MicroscopyField;
import dataStructure.containers.ObjectContainer;
import de.caluga.morphium.MorphiumAccessVetoException;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.annotations.Reference;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.caching.Cache;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PreStore;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageFormat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageProperties;
import image.ImageWriter;
import image.ObjectFactory;
import static image.ObjectFactory.getBounds;
import java.util.ArrayList;
import java.util.TreeMap;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static processing.PluginSequenceRunner.postFilterImage;
import static processing.PluginSequenceRunner.preFilterImage;
import static processing.PluginSequenceRunner.segmentImage;
import utils.SmallArray;

@Lifecycle
@Entity(collectionName = "Objects")
@Index(value={"field_name, time_point, structure_idx", "parent,structure_idx,idx", "track_head_id, time_point", "is_track_head, parent_track_head_id, structure_idx, time_point, idx"})
public class StructureObject implements StructureObjectPostProcessing, StructureObjectTracker {
    public final static Logger logger = LoggerFactory.getLogger(StructureObject.class);
    //structure-related attributes
    @Id protected ObjectId id;
    @Reference(lazyLoading=true, automaticStore=false) protected StructureObject parent;
    protected String fieldName;
    protected int structureIdx;
    protected int idx;
    @Reference(lazyLoading=true, automaticStore=false) protected Experiment xp;
    @Transient protected SmallArray<StructureObject[]> childrenSM=new SmallArray<StructureObject[]>();
    
    // track-related attributes
    protected int timePoint;
    @Reference(lazyLoading=true, automaticStore=false) public StructureObject previous, next;
    protected ObjectId parentTrackHeadId, trackHeadId;
    protected boolean isTrackHead=true;
    protected boolean trackLinkError=false;
    
    // object- and images-related attributes
    @Transient private Object3D object;
    protected ObjectContainer objectContainer;
    @Transient protected SmallArray<Image> rawImagesC=new SmallArray<Image>();
    @Transient protected SmallArray<Image> preProcessedImageS=new SmallArray<Image>();
    
    //Registrator -> registration locale
    
    public StructureObject(String fieldName, int timePoint, int structureIdx, int idx, Object3D object, StructureObject parent, Experiment xp) {
        this.fieldName=fieldName;
        this.timePoint = timePoint;
        this.object=object;
        if (object!=null) this.object.label=idx+1;
        this.structureIdx = structureIdx;
        this.idx = idx;
        this.parent=parent;
        this.xp=xp;
        
    }
    /**
     * Constructor for root objects only.
     * @param fieldName
     * @param timePoint
     * @param mask
     * @param xp 
     */
    public StructureObject(String fieldName, int timePoint, BlankMask mask, Experiment xp) {
        this.fieldName=fieldName;
        this.timePoint=timePoint;
        if (mask!=null) this.object=new Object3D(mask, 1);
        this.structureIdx = -1;
        this.idx = 0;
        this.xp=xp;
    }
    
    // structure-related methods
    public ObjectId getId() {return id;}
    public String getFieldName() {return fieldName;}
    public int getStructureIdx() {return structureIdx;}
    public int getTimePoint() {return timePoint;}
    public int getIdx() {return idx;}
    
    public Experiment getExperiment() {
        if (xp==null) return null;
        xp.callLazyLoading();
        return xp;
    }
    public MicroscopyField getMicroscopyField() {return getExperiment().getMicroscopyField(fieldName);}
    public float getScaleXY() {return getMicroscopyField().getScaleXY();}
    public float getScaleZ() {return getMicroscopyField().getScaleZ();}
    public StructureObject getParent() {
        if (parent==null) return null;
        try {parent.callLazyLoading();}
        catch (MorphiumAccessVetoException e) {}
        return parent;
    }
    public void setParent(StructureObject parent) {this.parent=parent;}
    public StructureObject getRoot() {
        if (isRoot()) return this;
        if (getParent()!=null) {
            if (parent.isRoot()) return parent;
            else return parent.getRoot();
        } else return null;
    }
    
    /**
     * @return an array of structure indices, starting from the first structure after the root structure, ending at the structure index (included)
     */
    public int[] getPathToRoot() {
        if (isRoot()) return new int[0];
        ArrayList<Integer> pathToRoot = new ArrayList<Integer>();
        pathToRoot.add(this.structureIdx);
        StructureObject p = getParent();
        while (!(parent.isRoot())) {
            pathToRoot.add(p.structureIdx);
            p=p.parent;
        }
        int[] res = new int[pathToRoot.size()];
        int i = res.length-1;
        for (int s : pathToRoot) res[i--] = s;
        return res;
    }
    public boolean isRoot() {return structureIdx==-1;}
    public StructureObject[] getChildObjects(int structureIdx) {return this.childrenSM.get(structureIdx);}
    public StructureObject[] getChildObjects(int structureIdx, ObjectDAO dao) {
        setChildObjects(dao.getObjects(id, structureIdx), structureIdx);
        return getChildObjects(structureIdx);
    }
    public void setChildObjects(StructureObject[] children, int structureIdx) {
        this.childrenSM.set(children, structureIdx);
        for (StructureObject o : children) o.setParent(this);
    }
    // track-related methods
    /**
     * 
     * @param previous the previous object in the track
     * @param isTrackHead if false, sets this instance as the next of {@param previous} 
     */
    @Override public void setPreviousInTrack(StructureObjectTracker previous, boolean isTrackHead, boolean signalError) {
        if (((StructureObject)previous).getTimePoint()!=this.getTimePoint()-1) throw new RuntimeException("setPrevious in track should be of time: "+(timePoint-1) +" but is: "+((StructureObject)previous).getTimePoint());
        this.previous=(StructureObject)previous;
        this.trackLinkError=signalError;
        if (!isTrackHead) {
            this.previous.next=this;
            this.isTrackHead=false;
            this.trackHeadId= this.previous.getTrackHeadId();
        } else {
            this.isTrackHead=true;
            this.trackHeadId=this.id;
        }
    }
    
    public void setParentTrackHeadId(ObjectId parentTrackHeadId) {
        this.parentTrackHeadId=parentTrackHeadId;
    }
    
    public StructureObject getPrevious() {
        if (previous==null) return null;
        try {previous.callLazyLoading();}
        catch (MorphiumAccessVetoException e) {}
        return previous;
    }
    
    public StructureObject getNext() {
        if (next==null) return null;
        try {next.callLazyLoading();}
        catch (MorphiumAccessVetoException e) {}
        return next;
    }
    
    public ObjectId getTrackHeadId() {
        /*if (trackHead==null) {
            if (isTrackHead) return this;
            else return null;
        }
        trackHead.callLazyLoading();
        return trackHead;*/
        if (trackHeadId==null && isTrackHead) trackHeadId = id;
        return trackHeadId;
    }
    
    public ObjectId getParentTrackHeadId() {
        /*if (trackHead==null) {
            if (isTrackHead) return this;
            else return null;
        }
        trackHead.callLazyLoading();
        return trackHead;*/
        return parentTrackHeadId;
    }

    public boolean hasTrackLinkError() {
        return trackLinkError;
    }
    
    
    
    // object- and image-related methods
    public Object3D getObject() {
        if (object==null) {
            objectContainer.setStructureObject(this);
            object=objectContainer.getObject();
        }
        return object;
    }
    public ImageProperties getMaskProperties() {return getObject().getImageProperties();}
    public ImageInteger getMask() {return getObject().getMask();}
    public BoundingBox getBounds() {return getObject().getBounds();}
    protected void createObjectContainer() {this.objectContainer=object.getObjectContainer(this);}
    public void updateObjectContainer(){
        // TODO: only if changes -> transient variable to record changes..
        if (objectContainer==null) createObjectContainer();
        //logger.debug("updating object container: {} of object: {}", objectContainer.getClass(), this );
        objectContainer.updateObject();
    }
    public void deleteMask(){if (objectContainer!=null) objectContainer.deleteObject();};
    
    public Image getRawImage(int structureIdx) {
        int channelIdx = getExperiment().getChannelImageIdx(structureIdx);
        if (rawImagesC.get(channelIdx)==null) { // chercher l'image chez le parent avec les bounds
            if (isRoot()) {
                if (rawImagesC.getAndExtend(channelIdx)==null) rawImagesC.set(getExperiment().getImageDAO().openPreProcessedImage(channelIdx, timePoint, fieldName), channelIdx);
            } else {
                StructureObject parentWithImage=getFirstParentWithOpenedRawImage(structureIdx);
                if (parentWithImage!=null) {
                    BoundingBox bb=getRelativeBoundingBox(parentWithImage);
                    rawImagesC.set(parentWithImage.getRawImage(structureIdx).crop(bb), channelIdx);
                } else { // opens only the bb of the object from the root objects
                    StructureObject root = getRoot();
                    BoundingBox bb=getRelativeBoundingBox(root);
                    rawImagesC.set(root.openRawImage(structureIdx, bb), channelIdx);
                }
            }
        }
        return rawImagesC.get(channelIdx);
    }
    
    public Image openRawImage(int structureIdx, BoundingBox bounds) {
        int channelIdx = getExperiment().getChannelImageIdx(structureIdx);
        if (rawImagesC.get(channelIdx)==null) return getExperiment().getImageDAO().openPreProcessedImage(channelIdx, timePoint, fieldName, bounds);
        else return rawImagesC.get(channelIdx).crop(bounds);
    }
    
    public StructureObject getFirstParentWithOpenedRawImage(int structureIdx) {
        if (isRoot()) {
            if (rawImagesC.get(getExperiment().getChannelImageIdx(structureIdx))!=null) return this;
            else return null;
        }
        if (getParent().rawImagesC.get(getExperiment().getChannelImageIdx(structureIdx))!=null) return parent;
        else return parent.getFirstParentWithOpenedRawImage(structureIdx);
    }
    
    public BoundingBox getRelativeBoundingBox(StructureObject stop) throws RuntimeException {
        if (stop==null) stop=getRoot();
        BoundingBox res = getObject().getBounds().duplicate();
        if (this.equals(stop)) return res.translateToOrigin();
        StructureObject nextParent=this.getParent();
        while(!stop.equals(nextParent)) {
            res.addOffset(nextParent.getObject().getBounds());
            nextParent=nextParent.getParent();
            if (nextParent==null) throw new RuntimeException("GetRelativeBoundingBoxError: stop structure object is not in parent tree");
            //if (!stop.equals(nextParent) && nextParent.getId().equals(stop.getId())) logger.error("stop condition cannot be reached: stop ({}) and parent ({}) not equals but same object", stop, nextParent);
        }
        return res;
    }
    
    public Image getFilteredImage(int structureIdx) {
        return preProcessedImageS.get(structureIdx);
    }
    
    public void createPreFilterImage(int structureIdx) {
        Image raw = getRawImage(structureIdx);
        if (raw!=null) preProcessedImageS.set(preFilterImage(getRawImage(structureIdx), this, getExperiment().getStructure(structureIdx).getProcessingChain().getPrefilters()), structureIdx);
    }
    
    public void segmentChildren(int structureIdx) {
        if (getFilteredImage(structureIdx)==null) createPreFilterImage(structureIdx);
        ObjectPopulation seg = segmentImage(getFilteredImage(structureIdx), structureIdx, this, getExperiment().getStructure(structureIdx).getProcessingChain().getSegmenter());
        if (seg.getObjects().isEmpty()) childrenSM.set(new StructureObject[0], structureIdx);
        else {
            seg = postFilterImage(seg, this, getExperiment().getStructure(structureIdx).getProcessingChain().getPostfilters());
            seg.relabel();
            StructureObject[] res = new StructureObject[seg.getObjects().size()];
            childrenSM.set(res, structureIdx);
            for (int i = 0; i<res.length; ++i) res[i]=new StructureObject(fieldName, timePoint, structureIdx, i, seg.getObjects().get(i), this, getExperiment());
        }
    }
    
    public ObjectPopulation getObjectPopulation(int structureIdx) {
        StructureObject[] child = this.childrenSM.get(structureIdx);
        if (child==null || child.length==0) return new ObjectPopulation(new ArrayList<Object3D>(0), this.getMaskProperties());
        else {
            ArrayList<Object3D> objects = new ArrayList<Object3D>(child.length);
            for (StructureObject s : child) objects.add(s.getObject());
            return new ObjectPopulation(objects, this.getMaskProperties());
        }
    }
    
    @Override
    public String toString() {
        if (isRoot()) return "Root Object: fieldName: "+fieldName + " timePoint: "+timePoint;
        else return "Object: fieldName: "+fieldName+ " timePoint: "+timePoint+ " structureIdx: "+structureIdx+ " parentId: "+getParent().id+ " idx: "+idx;
    }
    
    // morphium-related methods
    /*@PreStore public void preStore() {
        logger.debug("prestore run for object: {}", this);
        //createObjectContainer();
    }*/
    
    public void callLazyLoading() throws MorphiumAccessVetoException{} // for lazy-loading listener
    
    public StructureObject(){}
}
