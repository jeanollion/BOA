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
import plugins.ObjectSplitter;
import static processing.PluginSequenceRunner.postFilterImage;
import static processing.PluginSequenceRunner.preFilterImage;
import static processing.PluginSequenceRunner.segmentImage;
import utils.SmallArray;

@Lifecycle
@Entity(collectionName = "Objects")
@Index(value={"field_name, time_point, structure_idx", "parent,structure_idx,idx", "track_head_id, time_point", "is_track_head, parent_track_head_id, structure_idx, time_point, idx"})
public class StructureObject implements StructureObjectPostProcessing, StructureObjectTracker, StructureObjectTrackCorrection {
    public enum TrackFlag{trackError, correctionMerge, correctionMergeToErase, correctionSplit, correctionSplitNew, correctionSplitError};
    public final static Logger logger = LoggerFactory.getLogger(StructureObject.class);
    //structure-related attributes
    @Id protected ObjectId id;
    @Reference(lazyLoading=true, automaticStore=false) protected StructureObject parent;
    protected String fieldName;
    protected int structureIdx;
    protected int idx;
    @Transient protected Experiment xp;
    @Transient protected SmallArray<ArrayList<StructureObject>> childrenSM=new SmallArray<ArrayList<StructureObject>>();
    
    // track-related attributes
    protected int timePoint;
    @Reference(lazyLoading=true, automaticStore=false) protected StructureObject previous;
    @Transient protected StructureObject next; // only available when whole track is retrieved
    protected ObjectId parentTrackHeadId, trackHeadId;
    @Transient protected StructureObject trackHead;
    protected boolean isTrackHead=true;
    protected TrackFlag flag=null;
    
    // object- and images-related attributes
    @Transient private Object3D object;
    @Transient private boolean objectModified=false;
    protected ObjectContainer objectContainer;
    @Transient protected SmallArray<Image> rawImagesC=new SmallArray<Image>();
    @Transient protected SmallArray<Image> preProcessedImageS=new SmallArray<Image>();
    
    
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
    public ArrayList<? extends StructureObject> getChildObjects(int structureIdx) {return getChildren(structureIdx);} // for overriding purpose
    public ArrayList<StructureObject> getChildren(int structureIdx) {return this.childrenSM.get(structureIdx);}
    public ArrayList<StructureObject> getChildObjects(int structureIdx, ObjectDAO dao, boolean overrideIfExist) {
        if (overrideIfExist || getChildObjects(structureIdx)==null) setChildObjects(dao.getObjects(id, structureIdx), structureIdx);
        return getChildren(structureIdx);
    }
    public void setChildObjects(ArrayList<StructureObject> children, int structureIdx) {
        this.childrenSM.set(children, structureIdx);
        for (StructureObject o : children) o.setParent(this);
    }
    protected ArrayList<? extends StructureObject> getSiblings() {return this.getParent().getChildObjects(structureIdx, getExperiment().getObjectDAO(), false);}
    // track-related methods
    /**
     * 
     * @param previous the previous object in the track
     * @param isTrackHead if false, sets this instance as the next of { 
     * @param flag flag, can be null
     */
    @Override public void setPreviousInTrack(StructureObjectTracker previous, boolean isTrackHead, boolean signalError) {
        if (((StructureObject)previous).getTimePoint()!=this.getTimePoint()-1) throw new RuntimeException("setPrevious in track should be of time: "+(timePoint-1) +" but is: "+((StructureObject)previous).getTimePoint());
        this.previous=(StructureObject)previous;
        if (signalError) this.flag=TrackFlag.trackError;
        if (!isTrackHead) {
            this.previous.next=this;
            this.isTrackHead=false;
            this.trackHead= this.previous.getTrackHead();
            this.trackHeadId=null;
        } else {
            this.isTrackHead=true;
            this.trackHead=this;
            this.trackHeadId=null;
        }
    }
    public void setTrackFlag(TrackFlag flag) {this.flag=flag;}
    public TrackFlag getTrackFlag() {return this.flag;}
    
    public StructureObject getPrevious() {
        if (previous==null) return null;
        previous.callLazyLoading();
        return previous;
    }
    
    public StructureObject getNext() {
        //if (next==null) return null;
        //next.callLazyLoading();
        return next;
    }
    
    public StructureObject getTrackHead() {
        if (trackHead==null) {
            if (isTrackHead) {
                this.trackHead=this;
                this.trackHeadId=this.id;
            } else if (getPrevious()!=null) {
                this.trackHead=previous.getTrackHead();
                if (this.trackHead!=null) this.trackHeadId=trackHead.id;
            }
        }
        return trackHead;
    }
    
    public ObjectId getTrackHeadId() {
        if (trackHeadId==null) {
            getTrackHead();
            if (trackHead!=null) trackHeadId = trackHead.id;
        }
        return trackHeadId;
    }
    
    public ObjectId getParentTrackHeadId() {
        if (parentTrackHeadId==null) {
            if (getParent()!=null) {
                parentTrackHeadId = parent.getTrackHeadId();
            }
        }
        
        return parentTrackHeadId;
    }

    public boolean hasTrackLinkError() {
        return TrackFlag.trackError.equals(flag);
    }
    
    public boolean hasTrackLinkCorrection() {
        return TrackFlag.correctionMerge.equals(flag) || TrackFlag.correctionSplit.equals(flag) || TrackFlag.correctionSplitNew.equals(flag);
    }
    
    public boolean isTrackHead() {return this.isTrackHead;}
    
    public void resetTrackHead() {
        trackHeadId=null;
        getTrackHead();
        StructureObject n = getNext();
        while (n!=null) {
            n.trackHeadId=null;
            n.trackHead=trackHead;
            n=n.getNext();
        }
    }
    
    // track correction-related methods 
    /**
     * 
     * @return the next element of the track that contains a track link error, as defined by the tracker; null is there are no next track error;
     */
    public StructureObjectTrackCorrection getNextTrackError() {
        StructureObject error = this.getNext();
        while(error!=null && !error.hasTrackLinkError()) error=error.getNext();
        return error;
    }
    /**
     * 
     * @return a list containing the sibling (structureObjects that have the same previous object) at the next division, null if there are no siblings. If there are siblings, the first object of the list is contained in the track.
     */
    public ArrayList<StructureObjectTrackCorrection> getNextDivisionSiblings() {
        ArrayList<StructureObjectTrackCorrection> res= null;
        StructureObject nextDiv = this;
        while(nextDiv.getNext()!=null && res==null) {
            nextDiv = nextDiv.getNext();
            res = nextDiv.getDivisionSiblings();
        }
        if (res!=null) res.add(0, nextDiv);
        return res;
    }
    
    /**
     * 
     * @return a list containing the sibling (structureObjects that have the same previous object) at the previous division, null if there are no siblings. If there are siblings, the first object of the list is contained in the track.
     */
    public ArrayList<StructureObjectTrackCorrection> getPreviousDivisionSiblings() {
        ArrayList<StructureObjectTrackCorrection> res= null;
        StructureObject prevDiv = this;
        while(prevDiv.getPrevious()!=null && res==null) {
            prevDiv = prevDiv.getPrevious();
            res = prevDiv.getDivisionSiblings();
        }
        if (res!=null) res.add(0, prevDiv);
        return res;
    }
    
    private ArrayList<StructureObjectTrackCorrection> getDivisionSiblings() {
        ArrayList<StructureObjectTrackCorrection> res=null;
        ArrayList<? extends StructureObject> siblings = getSiblings();
        //logger.trace("get div siblings: timePoint: {}, number of siblings: {}", this.getTimePoint(), siblings.size());
        if (this.getPrevious()!=null) {
            for (StructureObject o : siblings) {
                if (o!=this) {
                    if (o.getPrevious()==this.getPrevious()) {
                        if (res==null) res = new ArrayList<StructureObjectTrackCorrection>(siblings.size());
                        res.add(o);
                    }
                } 
            }
            //logger.trace("get div siblings: previous non null, divSiblings: {}", res==null?"null":res.size());
        } else { // get thespatially closest sibling
            double distance = Double.MAX_VALUE;
            StructureObject min = null;
            for (StructureObject o : siblings) {
                if (o!=this) {
                    double d = o.getBounds().getDistance(this.getBounds());
                    if (d<distance) {
                        min=o;
                        distance=d;
                    }
                }
            }
            if (min!=null) {
                res = new ArrayList<StructureObjectTrackCorrection>(2);
                res.add(min);
            }
            //logger.trace("get div siblings: previous null, get spatially closest, divSiblings: {}", res==null?"null":res.size());
        }
        
        return res;
    }
    
    @Override
    public void merge(StructureObjectTrackCorrection other) {
        StructureObject otherO = (StructureObject)other;
        // update object
        getObject().merge(otherO.getObject()); 
        objectModified = true;
        // update links
        StructureObject prev = otherO.getPrevious();
        if (prev !=null && prev.getNext()!=null && prev.next==otherO) prev.next=this;
        StructureObject next = otherO.getNext();
        if (next !=null && otherO==next.getPrevious()) next.previous=this;
        //this.getParent().getChildObjects(structureIdx).remove(otherO); // concurent modification..
        // set flags
        setTrackFlag(TrackFlag.correctionMerge);
        otherO.setTrackFlag(TrackFlag.correctionMergeToErase);
        otherO.isTrackHead=false; // so that it won't be detected in the correction
        // update children
        int[] chilIndicies = getExperiment().getChildStructures(structureIdx);
        for (int cIdx : chilIndicies) {
            ArrayList<StructureObject> otherChildren = otherO.getChildObjects(cIdx, xp.getObjectDAO(), false);
            if (otherChildren!=null) {
                for (StructureObject o : otherChildren) o.setParent(this);
                //xp.getObjectDAO().updateParent(otherChildren);
                ArrayList<StructureObject> ch = this.getChildren(cIdx);
                if (ch!=null) ch.addAll(otherChildren);
            }
        }
    }
    
    public StructureObject split(ObjectSplitter splitter) { // in 2 objects
        // get cropped image
        ObjectPopulation pop = splitter.splitObject(getFilteredImage(structureIdx),  getObject());
        if (pop==null) {
            this.flag=TrackFlag.correctionSplitError;
            return null;
        }
        // first object returned by splitter is updated to current structureObject
        objectModified=true;
        this.object=pop.getObjects().get(0);
        if (pop.getObjects().size()>2) { // TODO merge other objects
            logger.warn("split structureObject: {} yield in {} objects, but only two will be considered", this, pop.getObjects().size());
        } 
        
        StructureObject res = new StructureObject(fieldName, timePoint, structureIdx, getSiblings().size(), pop.getObjects().get(1), getParent(), getExperiment());
        /*ArrayList<StructureObject> res = new ArrayList<StructureObject>(pop.getObjects().size()-1);
        for (int i = 1; i<pop.getObjects().size(); ++i) {
            res.add(new StructureObject(fieldName, timePoint, structureIdx, currentIdx++, pop.getObjects().get(i), getParent(), getExperiment()));
        }*/
        //if (res.size()>1) xp.getObjectDAO().store(res);
        //else xp.getObjectDAO().store(res.get(0));
        //this.getParent().getChildren(structureIdx).add(res);
        //res.previous=getPrevious();
        setTrackFlag(TrackFlag.correctionSplit);
        res.setTrackFlag(TrackFlag.correctionSplitNew);
        return res;
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
        //logger.trace("updating object for: {} was modified? {} flag: {}", this, objectModified, flag);
        if (objectContainer==null) {
            createObjectContainer();
            objectContainer.updateObject();
            objectModified=false;
        } else if (objectModified) {
            objectContainer.updateObject();
            objectModified=false;
        }
        //logger.debug("updating object container: {} of object: {}", objectContainer.getClass(), this );
        
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
        if (preProcessedImageS.get(structureIdx)==null) createPreFilterImage(structureIdx);
        return preProcessedImageS.get(structureIdx);
    }
    
    public void createPreFilterImage(int structureIdx) {
        Image raw = getRawImage(structureIdx);
        if (raw!=null) preProcessedImageS.set(preFilterImage(getRawImage(structureIdx), this, getExperiment().getStructure(structureIdx).getProcessingChain().getPrefilters()), structureIdx);
    }
    
    public void segmentChildren(int structureIdx) {
        
        ObjectPopulation seg = segmentImage(getFilteredImage(structureIdx), structureIdx, this, getExperiment().getStructure(structureIdx).getProcessingChain().getSegmenter());
        if (seg.getObjects().isEmpty()) {
            childrenSM.set(new ArrayList<StructureObject>(0), structureIdx);
        }
        else {
            seg = postFilterImage(seg, this, getExperiment().getStructure(structureIdx).getProcessingChain().getPostfilters());
            seg.relabel();
            ArrayList<StructureObject> res = new ArrayList<StructureObject>(seg.getObjects().size());
            childrenSM.set(res, structureIdx);
            for (int i = 0; i<seg.getObjects().size(); ++i) res.add(new StructureObject(fieldName, timePoint, structureIdx, i, seg.getObjects().get(i), this, getExperiment()));
        }
    }
    
    public ObjectPopulation getObjectPopulation(int structureIdx) {
        ArrayList<StructureObject> child = this.childrenSM.get(structureIdx);
        if (child==null || child.size()==0) return new ObjectPopulation(new ArrayList<Object3D>(0), this.getMaskProperties());
        else {
            ArrayList<Object3D> objects = new ArrayList<Object3D>(child.size());
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
