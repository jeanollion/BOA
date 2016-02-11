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
import de.caluga.morphium.annotations.lifecycle.PostLoad;
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
import java.util.HashMap;
import java.util.TreeMap;
import measurement.MeasurementKey;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.ObjectSplitter;
import processing.ImageFeatures;
import utils.SmallArray;

@Lifecycle
@Entity
@Index(value={"structure_idx, parent"})
public class StructureObject implements StructureObjectPostProcessing, StructureObjectTracker, StructureObjectTrackCorrection {
    public enum TrackFlag{trackError, correctionMerge, correctionMergeToErase, correctionSplit, correctionSplitNew, correctionSplitError};
    public final static Logger logger = LoggerFactory.getLogger(StructureObject.class);
    //structure-related attributes
    @Id protected ObjectId id;
    @Reference(lazyLoading=true, automaticStore=false) protected StructureObject parent;
    protected int structureIdx;
    protected int idx;
    @Transient protected final SmallArray<ArrayList<StructureObject>> childrenSM=new SmallArray<ArrayList<StructureObject>>(); //maps structureIdx to Children (equivalent to hashMap)
    @Transient protected ObjectDAO dao;
    
    // track-related attributes
    protected int timePoint;
    //boolean isNextStored=false;
    @Transient protected StructureObject next; //@Reference(lazyLoading=true, automaticStore=false)  
    @Reference(lazyLoading=true, automaticStore=false) protected StructureObject previous;
    protected ObjectId parentTrackHeadId, trackHeadId;
    @Transient protected StructureObject trackHead;
    protected boolean isTrackHead=true;
    protected TrackFlag flag=null;
    
    // object- and images-related attributes
    @Transient private Object3D object;
    @Transient private boolean objectModified=false;
    protected ObjectContainer objectContainer;
    @Transient protected SmallArray<Image> rawImagesC=new SmallArray<Image>();
    //@Transient protected SmallArray<Image> preProcessedImageS=new SmallArray<Image>();
    
    // measurement-related attributes
    protected ObjectId measurementsId;
    @Transient Measurements measurements;
    
    public StructureObject(int timePoint, int structureIdx, int idx, Object3D object, StructureObject parent) {
        this.timePoint = timePoint;
        this.object=object;
        if (object!=null) this.object.label=idx+1;
        this.structureIdx = structureIdx;
        this.idx = idx;
        this.parent=parent;
        if (this.parent!=null) this.dao=parent.dao;
    }
    /**
     * Constructor for root objects only.
     * @param fieldName
     * @param timePoint
     * @param mask
     * @param xp 
     */
    public StructureObject(int timePoint, BlankMask mask, ObjectDAO dao) {
        this.timePoint=timePoint;
        if (mask!=null) this.object=new Object3D(mask, 1);
        this.structureIdx = -1;
        this.idx = 0;
        this.dao=dao;
    }
    
    // structure-related methods
    public ObjectDAO getDAO() {return dao;}
    public ObjectId getId() {return id;}
    public String getFieldName() {return dao.getFieldName();}
    public int getStructureIdx() {return structureIdx;}
    public int getTimePoint() {return timePoint;}
    public int getIdx() {return idx;}
    
    public Experiment getExperiment() {
        if (dao==null) return null;
        return dao.getExperiment();
    }
    public MicroscopyField getMicroscopyField() {return getExperiment()!=null?getExperiment().getMicroscopyField(getFieldName()):null;}
    public float getScaleXY() {return getMicroscopyField()!=null?getMicroscopyField().getScaleXY():1;}
    public float getScaleZ() {return getMicroscopyField()!=null?getMicroscopyField().getScaleZ():1;}
    public StructureObject getParent() {
        if (parent==null) return null;
        parent.callLazyLoading();
        return parent;
    }
    public StructureObject getParent(int parentStructureIdx) {
        StructureObject p = this;
        while (p!=null && p.getStructureIdx()!=parentStructureIdx) p = p.getParent();
        if (p.structureIdx!=parentStructureIdx) {
            logger.error("Structure: {} is not in parent-tree of structure: {}", parentStructureIdx, this.structureIdx);
            return null;
        }
        return p;
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
    public ArrayList<StructureObject> getChildren(int structureIdx) {
        if (structureIdx<=this.structureIdx) throw new IllegalArgumentException("Structure: "+structureIdx+" cannot be child of structure: "+this.structureIdx);
        ArrayList<StructureObject> res= this.childrenSM.get(structureIdx);
        if (res==null) {
            if (getExperiment().isDirectChildOf(this.structureIdx, structureIdx)) { // direct child
                synchronized(childrenSM) {
                    res= this.childrenSM.get(structureIdx);
                    if (res==null) {
                        if (dao!=null) {
                            res = dao.getChildren(this, structureIdx);
                            setChildren(res, structureIdx);
                        } else logger.debug("getChildObjects called on {} but DAO null, cannot retrieve objects", this);
                    }
                    return res; 
                }
            } else { // indirect child
                //logger.debug("structure:{} is not direct child of: {}", structureIdx, this.structureIdx);
                int[] path = getExperiment().getPathToStructure(this.getStructureIdx(), structureIdx);
                if (path.length == 0) { // structure is not (indirect) child of current structure
                    //logger.error("getChildObjects called on {} but structure: {} is no an (indirect) child of structure {}", this, structureIdx, this.structureIdx);
                    //return null;
                    // get included objects from first common parent
                    int commonParentIdx = getExperiment().getFirstCommonParentStructureIdx(this.structureIdx, structureIdx);
                    StructureObject commonParent = this.getParent(commonParentIdx);
                    //logger.debug("structure: {}, child: {}, common parent: {}, object: {}, path: {}", this.structureIdx, structureIdx, commonParentIdx, commonParent, getExperiment().getPathToStructure(commonParentIdx, structureIdx));
                    ArrayList<StructureObject> candidates = commonParent.getChildren(structureIdx);
                    return StructureObjectUtils.getIncludedObjects(candidates, this);
                } else return StructureObjectUtils.getAllObjects(this, path);
            }
        }else return res;
    }
    public boolean hasChildren(int structureIdx) {
        return childrenSM.has(structureIdx);
    }

    public void setChildren(ArrayList<StructureObject> children, int structureIdx) {
        this.childrenSM.set(children, structureIdx);
        if (children!=null) for (StructureObject o : children) o.setParent(this);
    }
    
    @Override public ArrayList<StructureObject> setChildrenObjects(ObjectPopulation population, int structureIdx) {
        population.relabel();
        if (!isRoot()) population.translate(getBounds());
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(population.getObjects().size());
        childrenSM.set(res, structureIdx);
        int i = 0;
        for (Object3D o : population.getObjects()) res.add(new StructureObject(timePoint, structureIdx, i++, o, this));
        return res;
    }
    
    /*void setChild(StructureObject o) {
        ArrayList<StructureObject> children = this.childrenSM.get(o.getStructureIdx());
        if (children==null) {
            children=new ArrayList<StructureObject>();
            childrenSM.set(children, o.getStructureIdx());
        }
        children.add(o);
    }*/
    
    public ArrayList<StructureObject> getSiblings() {
        return this.getParent().getChildren(structureIdx);
    }
    
    public void relabelChildren(int structureIdx, ArrayList<StructureObject> modifiedObjects) {
        //logger.debug("relabeling: {} number of children: {}", this, getChildren(structureIdx).size());
        // in order to avoid overriding some images, the algorithm is in two passes: ascending and descending indices
        ArrayList<StructureObject> c = getChildren(structureIdx);
        StructureObject current;
        for (int i = 0; i<c.size(); ++i) {
            current = c.get(i);
            if (current.idx!=i) {
                if (current.idx>i) { // need to decrease index
                    if (i==0 || c.get(i-1).idx!=i)  {
                        //logger.debug("relabeling: {}, newIdx: {}", current, i);
                        current.setIdx(i);
                        if (modifiedObjects!=null) modifiedObjects.add(current);
                    }
                } else { //need to increase idx
                    if (i==c.size()-1 || c.get(i+1).idx!=i)  {
                        //logger.debug("relabeling: {}, newIdx: {}", current, i);
                        current.setIdx(i);
                        if (modifiedObjects!=null) modifiedObjects.add(current);
                    }
                }
            } 
        }
        for (int i = c.size()-1; i>=0; --i) {
            current = c.get(i);
            if (current.idx!=i) {
                if (current.idx>i) { // need to decrease index
                    if (i==0 || c.get(i-1).idx!=i)  {
                        //logger.debug("relabeling: {}, newIdx: {}", current, i);
                        current.setIdx(i);
                        if (modifiedObjects!=null) modifiedObjects.add(current);
                    }
                } else { //need to increase idx
                    if (i==c.size()-1 || c.get(i+1).idx!=i)  {
                        //logger.debug("relabeling: {}, newIdx: {}", current, i);
                        current.setIdx(i);
                        if (modifiedObjects!=null) modifiedObjects.add(current);
                    }
                }
            } 
        }
        
        
    }
    protected void setIdx(int idx) {
        if (objectContainer!=null) objectContainer.relabelObject(idx);
        if (this.object!=null) object.setLabel(idx+1);
        this.idx=idx;
    }

    
    // track-related methods
    @Override public void setPreviousInTrack(StructureObjectTracker previous, boolean isTrackHead) {
        setPreviousInTrack(previous, isTrackHead, null);
    }
    /**
     * 
     * @param previous the previous object in the track
     * @param isTrackHead if false, sets this instance as the next of { 
     * @param flag flag, can be null
     */
    @Override public void setPreviousInTrack(StructureObjectTracker previous, boolean isTrackHead, TrackFlag flag) {
        if (((StructureObject)previous).getTimePoint()>=this.getTimePoint()) throw new RuntimeException("setPrevious in track should be of time: "+(timePoint-1) +" but is: "+((StructureObject)previous).getTimePoint());
        this.previous=(StructureObject)previous;
        if (flag!=null) this.flag=flag;
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
    //public void setNextInTrack(StructureObject next, )
    @Override
    public void resetTrackLinks() {
        this.previous=null;
        this.next=null;
        this.trackHead=null;
        this.trackHeadId=null;
        this.isTrackHead=true;
        this.flag=null;
    }
    public void setTrackFlag(TrackFlag flag) {this.flag=flag;}
    public TrackFlag getTrackFlag() {return this.flag;}
    
    public StructureObject getPrevious() {
        if (previous==null) return null;
        previous.callLazyLoading();
        return previous;
    }
    
    public StructureObject getNext() {
        if (next==null) {
            synchronized(this) {
                if (next==null) {
                    if (isRoot()) {
                        next = dao.getRoot(timePoint+1);
                    } else {
                        StructureObject nextParent = getParent().getNext();
                        if (nextParent==null) return null;
                        ArrayList<StructureObject> nextSiblings = nextParent.getChildren(structureIdx);
                        for (StructureObject o : nextSiblings) if (o.getPrevious()==this) {
                            next = o;
                            break;
                        }
                    }
                }
            }
        }
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
        trackHead=null;
        getTrackHead();
        StructureObject n = this;
        while (n.getNext()!=null && n.getNext().getPrevious()==n) { // only on main track
            n=n.getNext();
            n.trackHeadId=null;
            n.trackHead=trackHead;
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
    public ArrayList<StructureObject> getNextDivisionSiblings() {
        ArrayList<StructureObject> res= null;
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
    public ArrayList<StructureObject> getPreviousDivisionSiblings() {
        ArrayList<StructureObject> res= null;
        StructureObject prevDiv = this;
        while(prevDiv.getPrevious()!=null && res==null) {
            prevDiv = prevDiv.getPrevious();
            res = prevDiv.getDivisionSiblings();
        }
        if (res!=null) res.add(0, prevDiv);
        return res;
    }
    
    public ArrayList<StructureObject> getDivisionSiblings() {
        ArrayList<StructureObject> res=null;
        ArrayList<StructureObject> siblings = getSiblings();
        //logger.trace("get div siblings: timePoint: {}, number of siblings: {}", this.getTimePoint(), siblings.size());
        if (this.getPrevious()!=null) {
            for (StructureObject o : siblings) {
                if (o!=this) {
                    if (o.getPrevious()==this.getPrevious()) {
                        if (res==null) res = new ArrayList<StructureObject>(siblings.size());
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
                res = new ArrayList<StructureObject>(2);
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
        if (other==null) logger.debug("merge: {}, other==null", this);
        if (getObject()==null) logger.debug("merge: {}+{}, object==null", this, other);
        if (otherO.getObject()==null) logger.debug("merge: {}+{}, other object==null", this, other);
        getObject().merge(otherO.getObject()); 
        flushImages();
        objectModified = true;
        // update links
        StructureObject prev = otherO.getPrevious();
        if (prev !=null && prev.getNext()!=null && prev.next==otherO) prev.next=this;
        StructureObject next = otherO.getNext(); if (next==null) next = getNext();
        if (next!=null) {
            ArrayList<StructureObject> siblings = next.getSiblings();
            for (StructureObject o : siblings) if (o.getPrevious()==otherO) o.previous=this;
        }
        this.getParent().getChildObjects(structureIdx).remove(otherO); // concurent modification..
        // set flags
        setTrackFlag(TrackFlag.correctionMerge);
        otherO.setTrackFlag(TrackFlag.correctionMergeToErase);
        otherO.isTrackHead=false; // so that it won't be detected in the correction
        // update children
        int[] chilIndicies = getExperiment().getAllDirectChildStructuresAsArray(structureIdx);
        for (int cIdx : chilIndicies) {
            ArrayList<StructureObject> otherChildren = otherO.getChildren(cIdx);
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
        Image image = ImageFeatures.gaussianSmooth(getRawImage(structureIdx), 2, 2, false);
        ObjectPopulation pop = splitter.splitObject(image,  getObject(), false);
        if (pop==null) {
            this.flag=TrackFlag.correctionSplitError;
            logger.warn("split error: {}", this);
            return null;
        }
        // first object returned by splitter is updated to current structureObject
        pop.translate(this.getBounds());
        objectModified=true;
        this.object=pop.getObjects().get(0).setLabel(idx+1);
        flushImages();
        if (pop.getObjects().size()>2) { // TODO merge other objects
            logger.warn("split structureObject: {} yielded in {} objects, but only two will be considered", this, pop.getObjects().size());
        } 
        
        StructureObject res = new StructureObject(timePoint, structureIdx, idx+1, pop.getObjects().get(1).setLabel(idx+2), getParent());
        /*ArrayList<StructureObject> res = new ArrayList<StructureObject>(pop.getChildren().size()-1);
        for (int i = 1; i<pop.getChildren().size(); ++i) {
            res.add(new StructureObject(fieldName, timePoint, structureIdx, currentIdx++, pop.getChildren().get(i), getParent(), getExperiment()));
        }*/
        //if (res.size()>1) xp.getObjectDAO().storeLater(res);
        //else xp.getObjectDAO().storeLater(res.get(0));
        //logger.debug("split: adding: {} at position: {}/{}", res, getParent().getChildren(structureIdx).indexOf(this)+1, getParent().getChildren(structureIdx).size());
        this.getParent().getChildren(structureIdx).add(getParent().getChildren(structureIdx).indexOf(this)+1, res);
        
        //res.previous=getPrevious();
        setTrackFlag(TrackFlag.correctionSplit);
        res.setTrackFlag(TrackFlag.correctionSplitNew);
        //logger.debug("spit object: {}, new: {}, added @ idx: {}", this, res, this.getParent().getChildren(structureIdx).indexOf(this)+1);
        return res;
    }
    
    // object- and image-related methods
    public Object3D getObject() {
        if (object==null) {
            synchronized(this) {
                if (object==null) {
                    object=objectContainer.getObject();
                }
            }
        }
        return object;
    }
    public ImageProperties getMaskProperties() {return getObject().getImageProperties();}
    public ImageInteger getMask() {return getObject().getMask();}
    public BoundingBox getBounds() {return getObject().getBounds();}
    protected void createObjectContainer() {this.objectContainer=object.getObjectContainer(this);}
    public void updateObjectContainer(){
        //logger.debug("updating object for: {}, container null? {}, was modified? {}, flag: {}", this,objectContainer==null, objectModified, flag);
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
    //public ObjectContainer getObjectContainer() {return objectContainer;}
    public void deleteMask(){if (objectContainer!=null) objectContainer.deleteObject();};
    
    public Image getRawImage(int structureIdx) {
        int channelIdx = getExperiment().getChannelImageIdx(structureIdx);
        if (rawImagesC.get(channelIdx)==null) { // chercher l'image chez le parent avec les bounds
            synchronized(rawImagesC) {
                if (rawImagesC.get(channelIdx)==null) {
                    if (isRoot()) {
                        if (rawImagesC.getAndExtend(channelIdx)==null) rawImagesC.set(getExperiment().getImageDAO().openPreProcessedImage(channelIdx, timePoint, getFieldName()), channelIdx);
                    } else {
                        StructureObject parentWithImage=getFirstParentWithOpenedRawImage(structureIdx);
                        if (parentWithImage!=null) {
                            BoundingBox bb=getRelativeBoundingBox(parentWithImage);
                            extendBoundsInZIfNecessary(channelIdx, bb);
                            rawImagesC.set(parentWithImage.getRawImage(structureIdx).crop(bb), channelIdx);
                        } else { // opens only the bb of the object from the root objects
                            StructureObject root = getRoot();
                            BoundingBox bb=getRelativeBoundingBox(root);
                            extendBoundsInZIfNecessary(channelIdx, bb);
                            rawImagesC.set(root.openRawImage(structureIdx, bb), channelIdx);
                        }
                    }
                }
            }
        }
        return rawImagesC.get(channelIdx);
    }
    private void extendBoundsInZIfNecessary(int channelIdx, BoundingBox bounds) { //when the current structure is 2D but channel is 3D 
        //logger.debug("extends bounds if necessary: is2D: {}, bounds 2D: {}, sizeZ of image to open: {}", is2D(), bounds.getSizeZ(), getExperiment().getMicroscopyField(fieldName).getSizeZ(channelIdx));
        if (bounds.getSizeZ()==1 && is2D() && channelIdx!=this.getExperiment().getChannelImageIdx(structureIdx)) { 
            int sizeZ = getExperiment().getMicroscopyField(getFieldName()).getSizeZ(channelIdx); //TODO no reliable if a transformation removes planes -> need to record the dimensions of the preProcessed Images
            if (sizeZ>1) {
                bounds.expandZ(sizeZ-1);
            }
        }
    }
    public boolean is2D() {
        //return getExperiment().getMicroscopyField(fieldName).getSizeZ(getExperiment().getChannelImageIdx(structureIdx))==1; //TODO no reliable if a transformation removes planes
        return this.getMask().getSizeZ()==1;
    }
    
    public Image openRawImage(int structureIdx, BoundingBox bounds) {
        int channelIdx = getExperiment().getChannelImageIdx(structureIdx);
        if (rawImagesC.get(channelIdx)==null) return getExperiment().getImageDAO().openPreProcessedImage(channelIdx, timePoint, getFieldName(), bounds); //opens only within bounds
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
        /*if (stop==null) stop=getRoot();
        BoundingBox res = getObject().getBounds().duplicate();
        if (this.equals(stop)) return res.translateToOrigin();
        StructureObject nextParent=this.getParent();
        while(!stop.equals(nextParent)) {
            res.translate(nextParent.getObject().getBounds());
            nextParent=nextParent.getParent();
            if (nextParent==null) throw new RuntimeException("GetRelativeBoundingBoxError: stop structure object is not in parent tree");
            
        }
        return res;*/
        BoundingBox res = getObject().getBounds().duplicate();
        if (stop==null || stop == getRoot()) return res;
        else return res.translate(stop.getBounds().duplicate().reverseOffset());
    }
    
    /*public Image getFilteredImage(int structureIdx) {
        if (preProcessedImageS.get(structureIdx)==null) createPreFilterImage(structureIdx);
        return preProcessedImageS.get(structureIdx);
    }
    
    public void createPreFilterImage(int structureIdx) {
        Image raw = getRawImage(structureIdx);
        if (raw!=null) preProcessedImageS.set(preFilterImage(getRawImage(structureIdx), this, getExperiment().getStructure(structureIdx).getProcessingChain().getPrefilters()), structureIdx);
    }*/
    
    public void flushImages() {
        //for (int i = 0; i<preProcessedImageS.getBucketSize(); ++i) preProcessedImageS.setQuick(null, i);
        for (int i = 0; i<rawImagesC.getBucketSize(); ++i) rawImagesC.setQuick(null, i);
    }
    /*
    public void segmentChildren(int structureIdx) {
        
        ObjectPopulation seg = segmentImage(getFilteredImage(structureIdx), structureIdx, this, getExperiment().getStructure(structureIdx).getProcessingChain().getSegmenter());
        if (seg.getChildren().isEmpty()) {
            childrenSM.set(new ArrayList<StructureObject>(0), structureIdx);
        }
        else {
            seg = postFilterImage(seg, this, getExperiment().getStructure(structureIdx).getProcessingChain().getPostfilters());
            seg.relabel();
            ArrayList<StructureObject> res = new ArrayList<StructureObject>(seg.getChildren().size());
            childrenSM.set(res, structureIdx);
            for (int i = 0; i<seg.getChildren().size(); ++i) res.add(new StructureObject(fieldName, timePoint, structureIdx, i, seg.getChildren().get(i), this));
        }
    }*/
    
    
    
    public ObjectPopulation getObjectPopulation(int structureIdx) {
        ArrayList<StructureObject> child = this.getChildren(structureIdx);
        if (child==null || child.size()==0) return new ObjectPopulation(new ArrayList<Object3D>(0), this.getMaskProperties());
        else {
            ArrayList<Object3D> objects = new ArrayList<Object3D>(child.size());
            for (StructureObject s : child) objects.add(s.getObject());
            return new ObjectPopulation(objects, this.getMaskProperties(), true);
        }
    }
    
    public Measurements getMeasurements() {
        if (measurements==null) {
            synchronized(this) {
                if (measurements==null) {
                    if (measurementsId!=null && dao instanceof MorphiumObjectDAO) {
                        measurements=((MorphiumObjectDAO)dao).getMeasurementsDAO().getObject(measurementsId);
                        if (measurements==null) {
                            measurementsId=null;
                            measurements = new Measurements(this);
                        }
                    } else measurements = new Measurements(this);
                }
            }
        }
        return measurements;
    }
    
    public boolean hasMeasurements() {
        return measurementsId!=null || measurements!=null;
    }
    
    void updateMeasurementsIfNecessary() {
        if (measurements!=null) {
            if (measurements.modifications) dao.upsertMeasurement(this);
            else measurements.updateObjectProperties(this);
            this.measurementsId=measurements.id;
        }
    }
    
    @Override
    public String toString() {
        if (isRoot()) return "Root: F:"+getFieldName() + ",T:"+timePoint;
        else return "F:"+getFieldName()+ ",T:"+timePoint+ ",S:"+structureIdx+ ",Idx:"+idx+ ",P:["+getParent().toStringShort()+"]" + (flag==null?"":"{"+flag+"}") ;
    }
    
    protected String toStringShort() {
        if (isRoot()) return "Root";
        else return "S:"+structureIdx+ ",Idx:"+idx+ ",P:["+getParent().toStringShort()+"]" ;
    }
    
    // morphium-related methods
    /*@PreStore public void preStore() {
        logger.debug("prestore run for object: {}", this);
        //createObjectContainer();
    }*/
    
    public void callLazyLoading(){} // for lazy-loading listener
    
    public StructureObject(){}
    
    @PostLoad
    public void postLoad() {
        //logger.debug("post load: {}", this);
        if (objectContainer!=null) objectContainer.setStructureObject(this);
    }

}
