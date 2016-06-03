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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
@Index(value={"structure_idx, parent_id"})
public class StructureObject implements StructureObjectPostProcessing, StructureObjectTracker, StructureObjectTrackCorrection, Comparable<StructureObject> {
    public enum TrackFlag{trackError, correctionMerge, correctionMergeToErase, correctionSplit, correctionSplitNew, correctionSplitError};
    public final static Logger logger = LoggerFactory.getLogger(StructureObject.class);
    //structure-related attributes
    @Id protected ObjectId id;
    protected ObjectId parentId;
    @Transient protected StructureObject parent;
    protected int structureIdx;
    protected int idx;
    @Transient protected final SmallArray<List<StructureObject>> childrenSM=new SmallArray<List<StructureObject>>(); //maps structureIdx to Children (equivalent to hashMap)
    @Transient protected ObjectDAO dao;
    
    // track-related attributes
    protected int timePoint;
    @Transient protected StructureObject previous, next; 
    private ObjectId nextId, previousId;
    private ObjectId parentTrackHeadId, trackHeadId;
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
        this.id= new ObjectId();
        this.timePoint = timePoint;
        this.object=object;
        if (object!=null) this.object.label=idx+1;
        this.structureIdx = structureIdx;
        this.idx = idx;
        this.parent=parent;
        this.parentId=parent.getId();
        if (this.parent!=null) this.dao=parent.dao;
    }
    /**
     * Constructor for root objects only.
     * @param timePoint
     * @param mask
     */
    public StructureObject(int timePoint, BlankMask mask, ObjectDAO dao) {
        this.id= new ObjectId();
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
    public int getPositionIdx() {return getExperiment().getMicroscopyField(getFieldName()).getIndex();}
    public int getStructureIdx() {return structureIdx;}
    public int getTimePoint() {return timePoint;}
    public int getIdx() {return idx;}

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof StructureObject) {
            return id==((StructureObject)obj).id;
        }
        return false;
    }
    
    public Experiment getExperiment() {
        if (dao==null) return null;
        return dao.getExperiment();
    }
    public MicroscopyField getMicroscopyField() {return getExperiment()!=null?getExperiment().getMicroscopyField(getFieldName()):null;}
    public float getScaleXY() {return getMicroscopyField()!=null?getMicroscopyField().getScaleXY():1;}
    public float getScaleZ() {return getMicroscopyField()!=null?getMicroscopyField().getScaleZ():1;}
    public StructureObject getParent() {
        if (parent==null) {
            if (parentId!=null && dao instanceof MorphiumObjectDAO) {
                parent = ((MorphiumObjectDAO)dao).getById(parentId);
            }
        }
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
    public void setParent(StructureObject parent) {
        this.parent=parent;
        this.parentId=parent.getId();
    }
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
    public List<? extends StructureObject> getChildObjects(int structureIdx) {return getChildren(structureIdx);} // for overriding purpose
    public List<StructureObject> getChildren(int structureIdx) {
        if (structureIdx<this.structureIdx) throw new IllegalArgumentException("Structure: "+structureIdx+" cannot be child of structure: "+this.structureIdx);
        if (structureIdx == this.structureIdx) {
            final StructureObject o = this;
            return new ArrayList<StructureObject>(){{add(o);}};
        }
        List<StructureObject> res= this.childrenSM.get(structureIdx);
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
                if (path.length == 0) { // structure is not (indirect) child of current structure -> get included objects from first common parent
                    int commonParentIdx = getExperiment().getFirstCommonParentStructureIdx(this.structureIdx, structureIdx);
                    StructureObject commonParent = this.getParent(commonParentIdx);
                    List<StructureObject> candidates = commonParent.getChildren(structureIdx);
                    //if (this.timePoint==0) logger.debug("structure: {}, child: {}, commonParentIdx: {}, object: {}, path: {}, candidates: {}", this.structureIdx, structureIdx, commonParentIdx, commonParent, getExperiment().getPathToStructure(commonParentIdx, structureIdx), candidates.size());
                    return StructureObjectUtils.getIncludedObjects(candidates, this);
                } else return StructureObjectUtils.getAllObjects(this, path);
            }
        }else return res;
    }
    public boolean hasChildren(int structureIdx) {
        return childrenSM.has(structureIdx);
    }

    public void setChildren(List<StructureObject> children, int structureIdx) {
        this.childrenSM.set(children, structureIdx);
        if (children!=null) for (StructureObject o : children) o.setParent(this);
    }
    
    @Override public List<StructureObject> setChildrenObjects(ObjectPopulation population, int structureIdx) {
        population.relabel();
        population.translate(getBounds(), true); // from parent-relative coordinates to absolute coordinates
        for (Object3D o : population.getObjects()) o.setIsAbsoluteLandmark(true);
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
    
    public List<StructureObject> getSiblings() {
        return this.getParent().getChildren(structureIdx);
    }
    public void relabelChildren(int structureIdx) {relabelChildren(structureIdx, null);}
    public void relabelChildren(int structureIdx, List<StructureObject> modifiedObjects) {
        //logger.debug("relabeling: {} number of children: {}", this, getChildren(structureIdx).size());
        // in order to avoid overriding some images, the algorithm is in two passes: ascending and descending indices
        List<StructureObject> c = getChildren(structureIdx);
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
    
    public void setTrackLinks(StructureObject next, boolean setPrev, boolean setNext, TrackFlag flag) {
        if (next==null) unSetTrackLinks(setPrev, setNext, flag);
        else {
            if (next.getTimePoint()<=this.getTimePoint()) throw new RuntimeException("setLink should be of time>= "+(timePoint+1) +" but is: "+next.getTimePoint()+ " current: "+this+", next: "+next);
            if (setPrev && setNext) { // double link: set trackHead
                setNext(next);
                next.setPrevious(this);
                next.setTrackHead(getTrackHead(), false, false, null);
            } else if (setPrev) {
                next.setPrevious(this);
                next.setTrackHead(next, false, false, null);
            } else if (setNext) {
                setNext(next);
            }
        }
    }
    
    public void unSetTrackLinks(boolean prev, boolean next, TrackFlag flag) {
        if (prev) {
            // unset previous's next? 
            setPrevious(null);
            setTrackHead(this, false, false, null);
        }
        if (next) {
            // unset next's previous?
            setNext(null);
        }
    }
    @Override
    public StructureObject resetTrackLinks() {
        if (this.previous!=null && this.previous.next==this) this.previous.setNext(null);
        this.setPrevious(null);
        if (this.next!=null && this.next.previous==this) this.next.setPrevious(null);
        this.setNext(null);
        this.trackHead=null;
        this.trackHeadId=null;
        this.isTrackHead=true;
        this.flag=null;
        return this;
    }
    public StructureObject setTrackFlag(TrackFlag flag) {
        this.flag=flag;
        return this;
    }
    public TrackFlag getTrackFlag() {return this.flag;}
    
    public StructureObject getPrevious() {
        if (previous==null) {
            if (previousId!=null && dao instanceof MorphiumObjectDAO) {
                previous = ((MorphiumObjectDAO)dao).getById(previousId);
            }
        }
        return previous;
    }

    public StructureObject getNext() {
        if (next==null) {
            if (nextId!=null && dao instanceof MorphiumObjectDAO) {
                next = ((MorphiumObjectDAO)dao).getById(nextId);
            }
        }
        return next;
    }
    
    protected void setNext(StructureObject next) {
        this.next=next;
        if (next!=null) this.nextId=next.getId();
        else this.nextId=null;
    }
    
    protected void setPrevious(StructureObject previous) {
        this.previous=previous;
        if (previous!=null) this.previousId=previous.getId();
        else this.previousId=null;
    }
    
    public StructureObject getInTrack(int timePoint) {
        StructureObject current;
        if (timePoint>this.getTimePoint()) {
            current = this;
            while(current!=null && current.getTimePoint()<timePoint) current=current.getNext();
        } else {
            current = this.getPrevious();
            while(current!=null && current.getTimePoint()>timePoint) current=current.getPrevious();
        }
        if (current!=null && current.getTimePoint()==timePoint) return current;
        return null;
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
            if (trackHead==null) { // set trackHead if no trackHead found
                this.isTrackHead=true;
                this.trackHead=this;
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
    
    public StructureObject resetTrackHead() {
        trackHeadId=null;
        trackHead=null;
        getTrackHead();
        StructureObject n = this;
        while (n.getNext()!=null && n.getNext().getPrevious()==n) { // only on main track
            n=n.getNext();
            n.trackHeadId=null;
            n.trackHead=trackHead;
        }
        return this;
    }
    public StructureObject setTrackHead(StructureObject trackHead, boolean resetPreviousIfTrackHead) {
        return setTrackHead(trackHead, resetPreviousIfTrackHead, false, null);
    }
    
    public StructureObject setTrackHead(StructureObject trackHead, boolean resetPreviousIfTrackHead, boolean propagateToNextObjects, Collection<StructureObject> modifiedObjects) {
        if (resetPreviousIfTrackHead && this==trackHead) {
            if (previous!=null && previous.next==this) previous.setNext(null);
            this.setPrevious(null);
        }
        this.isTrackHead=this==trackHead;
        this.trackHead=trackHead;
        this.trackHeadId=trackHead.id;
        if (modifiedObjects!=null) modifiedObjects.add(this);
        if (propagateToNextObjects) {
            StructureObject n = getNext();
            while(n!=null) {
                n.setTrackHead(trackHead, false, false, null);
                if (modifiedObjects!=null) modifiedObjects.add(n);
                n = n.getNext();
            }
        }
        return this;
    }
    
    // track correction-related methods 
    public boolean divisionAtNextTimePoint() {
        int count = 0;
        if (getParent()==null || this.getParent().getNext()==null) return false;
        List<StructureObject> candidates = this.getParent().getNext().getChildren(structureIdx);
        for (StructureObject o : candidates) {
            if (o.getPrevious()==this) ++count;
            if (count>1) return true;
        }
        return false;
    }
    public int getPreviousDivisionTimePoint() {
        StructureObject p = this.getPrevious();
        while (p!=null) {
            if (p.divisionAtNextTimePoint()) return p.getTimePoint()+1;
            p=p.getPrevious();
        }
        return -1;
    }
    public int getNextDivisionTimePoint() {
        StructureObject p = this;
        while (p!=null) {
            if (p.divisionAtNextTimePoint()) return p.getTimePoint()+1;
            p=p.getNext();
        }
        return -1;
    }
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
    public List<StructureObject> getNextDivisionSiblings() {
        List<StructureObject> res= null;
        StructureObject nextDiv = this;
        while(nextDiv.getNext()!=null && res==null) {
            nextDiv = nextDiv.getNext();
            res = nextDiv.getDivisionSiblings(false);
        }
        if (res!=null) res.add(0, nextDiv);
        return res;
    }
    
    /**
     * 
     * @return a list containing the sibling (structureObjects that have the same previous object) at the previous division, null if there are no siblings. If there are siblings, the first object of the list is contained in the track.
     */
    public List<StructureObject> getPreviousDivisionSiblings() {
        List<StructureObject> res= null;
        StructureObject prevDiv = this;
        while(prevDiv.getPrevious()!=null && res==null) {
            prevDiv = prevDiv.getPrevious();
            res = prevDiv.getDivisionSiblings(false);
        }
        if (res!=null) res.add(0, prevDiv);
        return res;
    }
    /**
     * 
     * @param includeCurrentObject if true, current instance will be included at first position of the list
     * @return a list containing the sibling (structureObjects that have the same previous object) at the previous division, null if there are no siblings and {@param includeCurrentObject} is false.
     */
    public List<StructureObject> getDivisionSiblings(boolean includeCurrentObject) {
        ArrayList<StructureObject> res=null;
        List<StructureObject> siblings = getSiblings();
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
        } /*else { // get thespatially closest sibling
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
        }*/
        if (includeCurrentObject) {
            if (res==null) {
                res = new ArrayList<StructureObject>(1);
                res.add(this);
            } else res.add(0, this);
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
        if (prev !=null && prev.getNext()!=null && prev.next==otherO) prev.setNext(this);
        StructureObject next = otherO.getNext(); 
        if (next==null) next = getNext();
        if (next!=null) {
            List<StructureObject> siblings = next.getSiblings();
            for (StructureObject o : siblings) if (o.getPrevious()==otherO) o.setPrevious(this);
        }
        this.getParent().getChildObjects(structureIdx).remove(otherO); // concurent modification..
        // set flags
        setTrackFlag(TrackFlag.correctionMerge);
        otherO.setTrackFlag(TrackFlag.correctionMergeToErase);
        otherO.isTrackHead=false; // so that it won't be detected in the correction
        // update children
        int[] chilIndicies = getExperiment().getAllDirectChildStructuresAsArray(structureIdx);
        for (int cIdx : chilIndicies) {
            List<StructureObject> otherChildren = otherO.getChildren(cIdx);
            if (otherChildren!=null) {
                for (StructureObject o : otherChildren) o.setParent(this);
                //xp.getObjectDAO().updateParent(otherChildren);
                List<StructureObject> ch = this.getChildren(cIdx);
                if (ch!=null) ch.addAll(otherChildren);
            }
        }
    }
    
    public StructureObject split(ObjectSplitter splitter) { // in 2 objects
        // get cropped image
        ObjectPopulation pop = splitter.splitObject(getRawImage(structureIdx),  getObject());
        if (pop==null || pop.getObjects().size()==1) {
            this.flag=TrackFlag.correctionSplitError;
            logger.warn("split error: {}", this);
            return null;
        }
        // first object returned by splitter is updated to current structureObject
        pop.translate(this.getBounds(), true);
        objectModified=true;
        this.object=pop.getObjects().get(0).setLabel(idx+1);
        flushImages();
        if (pop.getObjects().size()>2) pop.mergeWithConnected(pop.getObjects().subList(2, pop.getObjects().size()));
       
        StructureObject res = new StructureObject(timePoint, structureIdx, idx+1, pop.getObjects().get(1).setLabel(idx+2), getParent());
        getParent().getChildren(structureIdx).add(getParent().getChildren(structureIdx).indexOf(this)+1, res);
        setTrackFlag(TrackFlag.correctionSplit);
        res.setTrackFlag(TrackFlag.correctionSplitNew);
        return res;
    }
    
    // object- and image-related methods
    public Object3D getObject() {
        if (object==null) {
            synchronized(this) {
                if (object==null) {
                    object=objectContainer.getObject();
                    object.setIsAbsoluteLandmark(true);
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
                    rawImagesC.get(channelIdx).setCalibration(getScaleXY(), getScaleZ());
                    
                    //logger.debug("{} open channel: {}, use scale? {}, scale: {}", this, channelIdx, this.getMicroscopyField().getPreProcessingChain().useCustomScale(), getScaleXY());
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
        Image res;
        if (rawImagesC.get(channelIdx)==null) {//opens only within bounds
            res =  getExperiment().getImageDAO().openPreProcessedImage(channelIdx, timePoint, getFieldName(), bounds);
            res.setCalibration(getScaleXY(), getScaleZ());
            //if (this.timePoint==0) logger.debug("open from: {} within bounds: {}, resultBounds: {}", this, bounds, res.getBoundingBox());
        } 
        else {
            res = rawImagesC.get(channelIdx).crop(bounds);
            //if (this.timePoint==0) logger.debug("crom from: {} within bounds: {}, input bounds: {}, resultBounds: {}", this, bounds, rawImagesC.get(channelIdx).getBoundingBox(), res.getBoundingBox());
        }
        return res;
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
    public StructureObject getFirstCommonParent(StructureObject other) {
        if (other==null) return null;
        StructureObject object1 = this;
        
        while (object1.getStructureIdx()>=0 && other.getStructureIdx()>=0) {
            if (object1.getStructureIdx()>other.getStructureIdx()) object1 = object1.getParent();
            else if (object1.getStructureIdx()<other.getStructureIdx()) other = other.getParent();
            else if (object1==other) return object1;
            else return null;
        }
        return null;
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
        List<StructureObject> child = this.getChildren(structureIdx);
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
        return "P:"+getPositionIdx()+"/S:"+structureIdx+"/I:"+Selection.indiciesToString(StructureObjectUtils.getIndexTree(this))+"/id:"+id;
        //if (isRoot()) return "F:"+getPositionIdx() + ",T:"+timePoint;
        //else return "F:"+getPositionIdx()+ ",T:"+timePoint+ ",S:"+structureIdx+ ",Idx:"+idx+ ",P:["+getParent().toStringShort()+"]" + (flag==null?"":"{"+flag+"}") ;
    }
    
    protected String toStringShort() {
        if (isRoot()) return "";
        else return "S:"+structureIdx+ ",Idx:"+idx+ ",P:["+getParent().toStringShort()+"]" ;
    }
    
    public int compareTo(StructureObject other) {
        int comp = Integer.compare(getTimePoint(), other.getTimePoint());
        if (comp == 0) {
            comp = Integer.compare(getStructureIdx(), other.getStructureIdx());
            if (comp == 0) {
                if (getParent() != null && other.getParent() != null) {
                    comp = getParent().compareTo(other.getParent());
                    if (comp != 0) {
                        return comp;
                    }
                }
                return Integer.compare(getIdx(), other.getIdx());
            } else {
                return comp;
            }
        } else {
            return comp;
        }
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
