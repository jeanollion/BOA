package boa.data_structure;

import boa.data_structure.dao.ObjectDAO;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.PostLoadable;
import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.MicroscopyField;
import boa.data_structure.dao.BasicObjectDAO;
import boa.data_structure.region_container.RegionContainer;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.ImageProperties;
import boa.image.SimpleBoundingBox;
import boa.image.SimpleOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.plugins.ObjectSplitter;
import boa.utils.Id;
import boa.utils.JSONSerializable;
import boa.utils.JSONUtils;
import boa.utils.Pair;
import boa.utils.SmallArray;
import boa.utils.Utils;
import boa.utils.geom.Point;


public class StructureObject implements StructureObjectPostProcessing, StructureObjectTracker, StructureObjectTrackCorrection, Comparable<StructureObject>, PostLoadable, JSONSerializable {
    public final static Logger logger = LoggerFactory.getLogger(StructureObject.class);
    //structure-related attributes
    protected String id;
    protected String parentId;
    protected transient StructureObject parent;
    protected int structureIdx;
    protected int idx;
    protected transient final SmallArray<List<StructureObject>> childrenSM=new SmallArray<List<StructureObject>>(); //maps structureIdx to Children (equivalent to hashMap)
    transient ObjectDAO dao;
    
    // track-related attributes
    protected int timePoint;
    protected transient StructureObject previous, next; 
    String nextId, previousId;
    String parentTrackHeadId, trackHeadId; // TODO remove parentTrackHeadId ? useful for getTrackHeads
    protected transient StructureObject trackHead;
    protected boolean isTrackHead=true;
    protected Map<String, Object> attributes;
    // object- and images-related attributes
    private transient Region object;
    private transient boolean objectModified=false;
    protected RegionContainer objectContainer;
    protected transient SmallArray<Image> rawImagesC=new SmallArray<>();
    protected transient SmallArray<Image> preFilteredImagesS=new SmallArray<>();
    protected transient SmallArray<Image> trackImagesC=new SmallArray<>();
    protected transient BoundingBox offsetInTrackImage;
    
    // measurement-related attributes
    Measurements measurements;
    
    public StructureObject(int timePoint, int structureIdx, int idx, Region object, StructureObject parent) {
        this.id= Id.get().toHexString();
        this.timePoint = timePoint;
        this.object=object;
        if (object!=null) this.object.label=idx+1;
        this.structureIdx = structureIdx;
        this.idx = idx;
        this.parent=parent;
        this.parentId=parent.getId();
        if (this.parent!=null) this.dao=parent.dao;
        // attributes
        if (!Double.isNaN(object.getQuality())) setAttribute("Quality", object.getQuality());
        if (object.getCenter()!=null) attributes.put("Center", object.getCenter());
    }
    /**
     * Constructor for root objects only.
     * @param timePoint
     * @param mask
     */
    public StructureObject(int timePoint, BlankMask mask, ObjectDAO dao) {
        this.id= Id.get().toHexString();
        this.timePoint=timePoint;
        if (mask!=null) this.object=new Region(mask, 1, true);
        this.structureIdx = -1;
        this.idx = 0;
        this.dao=dao;
    }
    public StructureObject duplicate() {
        return duplicate(false, false);
    }
    public StructureObject duplicate(boolean generateNewID, boolean duplicateObject) {
        StructureObject res;
        if (isRoot()) res = new StructureObject(timePoint, (BlankMask)(duplicateObject?getMask().duplicateMask():getMask()), dao);
        else res= new StructureObject(timePoint, structureIdx, idx, duplicateObject?getRegion().duplicate():getRegion(), getParent());
        if (!generateNewID) res.id=id;
        res.previousId=previousId;
        res.nextId=nextId;
        res.parentTrackHeadId=parentTrackHeadId;
        res.trackHeadId=trackHeadId;
        res.isTrackHead=isTrackHead;
        res.previous=previous;
        res.next=next;
        res.trackHead=trackHead;
        res.rawImagesC=rawImagesC.duplicate();
        res.trackImagesC=trackImagesC.duplicate();
        res.preFilteredImagesS=preFilteredImagesS.duplicate();
        res.offsetInTrackImage=offsetInTrackImage;
        if (attributes!=null && !attributes.isEmpty()) { // deep copy of attributes
            res.attributes=new HashMap<>(attributes.size());
            for (Entry<String, Object> e : attributes.entrySet()) {
                if (e.getValue() instanceof double[]) res.attributes.put(e.getKey(), Arrays.copyOf((double[])e.getValue(), ((double[])e.getValue()).length));
                else if (e.getValue() instanceof float[]) res.attributes.put(e.getKey(), Arrays.copyOf((float[])e.getValue(), ((float[])e.getValue()).length));
                else if (e.getValue() instanceof Point) res.attributes.put(e.getKey(), ((Point)e.getValue()).duplicate());
                else res.attributes.put(e.getKey(), e.getValue());
            }
        }        
        return res;
    }
    
    
    // structure-related methods
    public ObjectDAO getDAO() {return dao;}
    public void setDAO(ObjectDAO dao) {this.dao=dao;}
    public String getId() {return id;}
    public String getPositionName() {return dao==null? "?":dao.getPositionName();}
    public int getPositionIdx() {return dao==null?-1 : getExperiment().getPosition(getPositionName()).getIndex();}
    public int getStructureIdx() {return structureIdx;}
    @Override public int getFrame() {return timePoint;}
    
    public double getCalibratedTimePoint() {
        if (getExperiment()==null) return Double.NaN;
        MicroscopyField f = getExperiment().getPosition(getPositionName());
        int z = (int)Math.round((getRegion().getBounds().zMin()+getRegion().getBounds().zMax())/2);
        double res  = f.getInputImages()==null || isRoot() ? Double.NaN : f.getInputImages().getCalibratedTimePoint(getExperiment().getChannelImageIdx(structureIdx), timePoint, z);
        //double res = Double.NaN; // for old xp TODO change
        if (Double.isNaN(res)) res = timePoint * f.getFrameDuration();
        return res;
    }
    
    public int getIdx() {return idx;}

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof StructureObject) {
            return id.equals(((StructureObject)obj).id);
        }
        return false;
    }

    public Experiment getExperiment() {
        if (dao==null) {
            if (parent!=null) return parent.getExperiment();
            return null;
        }
        return dao.getExperiment();
    }
    public MicroscopyField getMicroscopyField() {return getExperiment()!=null?getExperiment().getPosition(getPositionName()):null;}
    public float getScaleXY() {return getMicroscopyField()!=null?getMicroscopyField().getScaleXY():1;}
    public float getScaleZ() {return getMicroscopyField()!=null?getMicroscopyField().getScaleZ():1;}
    @Override public StructureObject getParent() {
        if (parent==null && parentId!=null) parent = dao.getById(null, getExperiment().getStructure(structureIdx).getParentStructure(), timePoint, parentId);
        return parent;
    }
    public boolean isParentSet() {
        return parent!=null;
    }
    public String getParentId() {return parentId;}
    public StructureObject getParent(int parentStructureIdx) {
        if (structureIdx==parentStructureIdx) return this;
        if (parentStructureIdx<0) return getRoot();
        if (parentStructureIdx == this.getParent().getStructureIdx()) return getParent();
        int common = getExperiment().getFirstCommonParentStructureIdx(structureIdx, parentStructureIdx);
        //logger.debug("common idx: {}", common);
        if (common == parentStructureIdx) {
            StructureObject p = this;
            while (p!=null && p.getStructureIdx()!=parentStructureIdx) p = p.getParent();
            return p;
        } else {
            StructureObject p = this;
            while (p.getStructureIdx()!=common) p = p.getParent();
            List<StructureObject> candidates = p.getChildren(parentStructureIdx);
            logger.debug("{} (2D?:{} so2D?: {}) common parent: {}, candidates: {}", this, getRegion().is2D(), is2D(), p, candidates);
            return StructureObjectUtils.getInclusionParent(getRegion(), candidates, null);
        }
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
    public void loadAllChildren(boolean indirect) {
        List<StructureObject> allChildren = indirect ? new ArrayList<>() : null;
        for (int i : getExperiment().getAllDirectChildStructures(structureIdx)) {
            List<StructureObject> c = getChildren(i);
            if (indirect) allChildren.addAll(c);
        }
        if (indirect) for (StructureObject o : allChildren) o.loadAllChildren(true);
    }
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
                    //if (this.frame==0) logger.debug("structure: {}, child: {}, commonParentIdx: {}, object: {}, path: {}, candidates: {}", this.structureIdx, structureIdx, commonParentIdx, commonParent, getExperiment().getPathToStructure(commonParentIdx, structureIdx), candidates.size());
                    return StructureObjectUtils.getIncludedStructureObjects(candidates, this);
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
    
    @Override public List<StructureObject> setChildrenObjects(RegionPopulation population, int structureIdx) {
        if (population==null) {
            ArrayList<StructureObject> res = new ArrayList<>();
            childrenSM.set(res, structureIdx);
            return res;
        }
        population.relabel();
        if (!population.isAbsoluteLandmark()) population.translate(getBounds(), true); // from parent-relative coordinates to absolute coordinates
        ArrayList<StructureObject> res = new ArrayList<>(population.getRegions().size());
        childrenSM.set(res, structureIdx);
        int i = 0;
        for (Region o : population.getRegions()) res.add(new StructureObject(timePoint, structureIdx, i++, o, this));
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
    public void relabelChildren(int structureIdx, Collection<StructureObject> modifiedObjects) {
        //logger.debug("relabeling: {} number of children: {}", this, getChildren(structureIdx).size());
        
        List<StructureObject> children = getChildren(structureIdx);
        int i = 0;
        for (StructureObject c : children) {
            if (c.idx!=i) {
                c.setIdx(i);
                if (modifiedObjects!=null) modifiedObjects.add(c);
            }
            ++i;
        }
        // For case where objects are stored in order to avoid overriding some images, the algorithm is in two passes: ascending and descending indices
        /*
        for (int i = 0; i<c.size(); ++i) {
            current = c.get(i);
            if (current.idx!=i) {
                if (current.idx>i) { // need to decrease index
                    if (i==0 || c.get(i-1).idx!=i)  {
                        logger.debug("relabeling: {}, newIdx: {}", current, i);
                        current.setIdx(i);
                        if (modifiedObjects!=null) modifiedObjects.add(current);
                    }
                } else { //need to increase idx
                    if (i==c.size()-1 || c.get(i+1).idx!=i)  {
                        logger.debug("relabeling: {}, newIdx: {}", current, i);
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
                        logger.debug("relabeling: {}, newIdx: {}", current, i);
                        current.setIdx(i);
                        if (modifiedObjects!=null) modifiedObjects.add(current);
                    }
                } else { //need to increase idx
                    if (i==c.size()-1 || c.get(i+1).idx!=i)  {
                        logger.debug("relabeling: {}, newIdx: {}", current, i);
                        current.setIdx(i);
                        if (modifiedObjects!=null) modifiedObjects.add(current);
                    }
                }
            } 
        }
        */
    }
    public void setIdx(int idx) {
        if (objectContainer!=null) objectContainer.relabelObject(idx);
        if (this.object!=null) object.setLabel(idx+1);
        this.idx=idx;
    }

    
    // track-related methods
    
    public void setTrackLinks(StructureObject next, boolean setPrev, boolean setNext) {
        if (next==null) resetTrackLinks(setPrev, setNext);
        else {
            if (next.getFrame()<=this.getFrame()) throw new RuntimeException("setLink should be of time>= "+(timePoint+1) +" but is: "+next.getFrame()+ " current: "+this+", next: "+next+ " parent: "+getParent()+", next parent: "+next.getParent());
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
        if (next!=null && setPrev) next.setAttribute(trackErrorPrev, null);
    }
    @Override
    public StructureObject resetTrackLinks(boolean prev, boolean next) {
        return resetTrackLinks(prev, next, false, null);
    }
    public StructureObject resetTrackLinks(boolean prev, boolean next, boolean propagate, Collection<StructureObject> modifiedObjects) {
        if (prev && this.previous!=null && this.previous.next==this) previous.unSetTrackLinksOneWay(false, true, propagate, modifiedObjects);
        if (next && this.next!=null && this.next.previous==this) this.next.unSetTrackLinksOneWay(true, false, propagate, modifiedObjects);
        unSetTrackLinksOneWay(prev, next, propagate, modifiedObjects);
        return this;
    }
    private void unSetTrackLinksOneWay(boolean prev, boolean next, boolean propagate, Collection<StructureObject> modifiedObjects) {
        if (prev) {
            //if (this.previous!=null && this.equals(this.previous.next))
            setPrevious(null);
            setTrackHead(this, false, propagate, modifiedObjects);
            setAttribute(trackErrorPrev, null);
        }
        if (next) {
            setNext(null);
            setAttribute(trackErrorNext, null);
        }
    }

    @Override
    public StructureObject getPrevious() {
        if (previous==null && previousId!=null) previous = dao.getById(parentTrackHeadId, structureIdx, -1, previousId);
        return previous;
    }
    
    @Override
    public StructureObject getNext() {
        if (next==null && nextId!=null) next = dao.getById(parentTrackHeadId, structureIdx, -1, nextId);
        return next;
    }
    public String getNextId() {
        return nextId;
    }
    public String getPreviousId() {
        return this.previousId;
    }
    public void setNext(StructureObject next) {
        this.next=next;
        if (next!=null) this.nextId=next.getId();
        else this.nextId=null;
    }
    
    public void setPrevious(StructureObject previous) {
        this.previous=previous;
        if (previous!=null) this.previousId=previous.getId();
        else this.previousId=null;
    }
    
    public StructureObject getInTrack(int timePoint) {
        StructureObject current;
        if (this.getFrame()==timePoint) return this;
        else if (timePoint>this.getFrame()) {
            current = this;
            while(current!=null && current.getFrame()<timePoint) current=current.getNext();
        } else {
            current = this.getPrevious();
            while(current!=null && current.getFrame()>timePoint) current=current.getPrevious();
        }
        if (current!=null && current.getFrame()==timePoint) return current;
        return null;
    }
    
    public StructureObject getTrackHead() {
        if (trackHead==null) {
            if (isTrackHead) {
                this.trackHead=this;
                this.trackHeadId=this.id;
            } else if (trackHeadId!=null ) {
                trackHead = dao.getById(parentTrackHeadId, structureIdx, -1, trackHeadId);
            } else if (getPrevious()!=null) {
                if (previous.isTrackHead) this.trackHead=previous;
                else if (previous.trackHead!=null) this.trackHead=previous.trackHead;
                else {
                    ArrayList<StructureObject> prevList = new ArrayList<>();
                    prevList.add(this);
                    prevList.add(previous);
                    StructureObject prev = previous;
                    while (prev.getPrevious()!=null && (prev.getPrevious().trackHead==null || !prev.getPrevious().isTrackHead)) {
                        prev=prev.previous;
                        prevList.add(prev);
                    }
                    if (prev.isTrackHead) for (StructureObject o : prevList) o.trackHead=prev;
                    else if (prev.trackHead!=null) for (StructureObject o : prevList) o.trackHead=prev.trackHead;
                }
            }
            if (trackHead==null) { // set trackHead if no trackHead found
                this.isTrackHead=true;
                this.trackHead=this;
            } 
            this.trackHeadId=trackHead.id;
        }
        return trackHead;
    }
    
    public String getTrackHeadId() {
        if (trackHeadId==null) {
            getTrackHead();
            if (trackHead!=null) trackHeadId = trackHead.id;
        }
        return trackHeadId;
    }
    public String getParentTrackHeadIdIfPresent() {
        return parentTrackHeadId;
    }
    public String getTrackHeadIdIfPresent() {
        return trackHeadId;
    }
    public String getParentTrackHeadId() {
        if (parentTrackHeadId==null) {
            if (getParent()!=null) {
                parentTrackHeadId = parent.getTrackHeadId();
            }
        }
        
        return parentTrackHeadId;
    }

    public static final String trackErrorPrev = "TrackErrorPrev";
    public static final String trackErrorNext = "TrackErrorNext";
    public static final String correctionMerge = "correctionMerge";
    public static final String correctionSplit = "correctionSplit";
    public static final String correctionSplitNew = "correctionSplitNew";
    @Override public boolean hasTrackLinkError(boolean prev, boolean next) {
        if (attributes==null) return false;
        if (prev && Boolean.TRUE.equals(getAttribute(trackErrorPrev))) return true;
        else if (next && Boolean.TRUE.equals(getAttribute(trackErrorNext))) return true;
        else return false;
    }
    
    public boolean hasTrackLinkCorrection() {
        return Boolean.TRUE.equals(getAttribute(correctionMerge)) || Boolean.TRUE.equals(getAttribute(correctionSplit)) || Boolean.TRUE.equals(getAttribute(correctionSplitNew));
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
            //this.setPrevious(null); // WAS MODIFIED FOR ManualCorrection linkObjects 191
        }
        this.isTrackHead=this.equals(trackHead);
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
            if (p.divisionAtNextTimePoint()) return p.getFrame()+1;
            p=p.getPrevious();
        }
        return -1;
    }
    public int getNextDivisionTimePoint() {
        StructureObject p = this;
        while (p!=null) {
            if (p.divisionAtNextTimePoint()) return p.getFrame()+1;
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
        while(error!=null && !error.hasTrackLinkError(true, true)) error=error.getNext();
        return error;
    }
    /*
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
    */
    /**
     * 
     * @param includeCurrentObject if true, current instance will be included at first position of the list
     * @return a list containing the sibling (structureObjects that have the same previous object) at the previous division, null if there are no siblings and {@param includeCurrentObject} is false.
     */
    
    public List<StructureObject> getDivisionSiblings(boolean includeCurrentObject) {
        ArrayList<StructureObject> res=null;
        List<StructureObject> siblings = getSiblings();
        //logger.trace("get div siblings: frame: {}, number of siblings: {}", this.getTimePoint(), siblings.size());
        if (this.getPrevious()!=null) {
            for (StructureObject o : siblings) {
                if (o!=this) {
                    if (o.getPrevious()==this.getPrevious()) {
                        if (res==null) res = new ArrayList<>(siblings.size());
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
                res = new ArrayList<>(1);
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
        if (getRegion()==null) logger.debug("merge: {}+{}, object==null", this, other);
        if (otherO.getRegion()==null) logger.debug("merge: {}+{}, other object==null", this, other);
        getRegion().merge(otherO.getRegion()); 
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
        setAttribute(correctionMerge, true);
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
        RegionPopulation pop = splitter.splitObject(getParent(), structureIdx, getRegion()); //getRawImage(structureIdx)
        if (pop==null || pop.getRegions().size()==1) {
            logger.warn("split error: {}", this);
            return null;
        }
        // first object returned by splitter is updated to current structureObject
        if (!pop.isAbsoluteLandmark()) pop.translate(this.getBounds(), true); 
        objectModified=true;
        this.object=pop.getRegions().get(0).setLabel(idx+1);
        flushImages();
        if (pop.getRegions().size()>2) pop.mergeWithConnected(pop.getRegions().subList(2, pop.getRegions().size()));
       
        StructureObject res = new StructureObject(timePoint, structureIdx, idx+1, pop.getRegions().get(1).setLabel(idx+2), getParent());
        getParent().getChildren(structureIdx).add(getParent().getChildren(structureIdx).indexOf(this)+1, res);
        setAttribute(correctionSplit, true);
        res.setAttribute(correctionSplitNew, true);
        return res;
    }
    public boolean hasObject() {return object!=null;}
    // object- and image-related methods
    public Region getRegion() {
        if (object==null) {
            if (objectContainer==null) return null;
            synchronized(this) {
                if (object==null) {
                    object=objectContainer.getObject().setIsAbsoluteLandmark(true); 
                    if (attributes!=null) {
                        if (attributes.containsKey("Quality")) object.setQuality((Double)attributes.get("Quality"));
                        if (attributes.containsKey("Center")) object.setCenter(new Point(JSONUtils.fromFloatArray((List)attributes.get("Center"))));
                    }
                }
            }
        }
        return object;
    }
    public void setObject(Region o) {
        synchronized(this) {
            objectContainer=null;
            object=o;
            object.label=idx+1;
            flushImages();
        }
    }
    public ImageProperties getMaskProperties() {return getRegion().getImageProperties();}
    @Override public ImageMask getMask() {return getRegion().getMask();}
    public BoundingBox getBounds() {return getRegion().getBounds();}
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
    public void setRawImage(int structureIdx, Image image) {
        int channelIdx = getExperiment().getChannelImageIdx(structureIdx);
        rawImagesC.set(image, channelIdx);
    }
    @Override
    public Image getRawImage(int structureIdx) {
        int channelIdx = getExperiment().getChannelImageIdx(structureIdx);
        if (rawImagesC.get(channelIdx)==null) { // chercher l'image chez le parent avec les bounds
            synchronized(rawImagesC) {
                if (rawImagesC.get(channelIdx)==null) {
                    if (isRoot()) {
                        if (rawImagesC.getAndExtend(channelIdx)==null) {
                            if (getMicroscopyField().singleFrame(structureIdx) && timePoint>0 && trackHead!=null) { // getImage from trackHead
                                rawImagesC.set(trackHead.getRawImage(structureIdx), channelIdx);
                            } else {
                                Image im = getExperiment().getImageDAO().openPreProcessedImage(channelIdx, getMicroscopyField().singleFrame(structureIdx) ? 0 : timePoint, getPositionName());
                                rawImagesC.set(im, channelIdx);
                                if (im==null) logger.error("Could not find preProcessed Image for: {}", this);
                            }
                        }
                    } else { // look in parent
                        StructureObject parentWithImage=getFirstParentWithOpenedRawImage(structureIdx);
                        if (parentWithImage!=null) {
                            //logger.debug("object: {}, channel: {}, open from parent with open image: {}", this, channelIdx, parentWithImage);
                            BoundingBox bb=getRelativeBoundingBox(parentWithImage);
                            extendBoundsInZIfNecessary(channelIdx, bb);
                            rawImagesC.set(parentWithImage.getRawImage(structureIdx).crop(bb), channelIdx);    
                        } else { // check track image
                            Image trackImage = getTrackImage(structureIdx);
                            if (trackImage!=null) {
                                //logger.debug("object: {}, channel: {}, open from trackImage: offset:{}", this, channelIdx, offsetInTrackImage);
                                BoundingBox bb = new SimpleBoundingBox(getBounds()).resetOffset().translate(offsetInTrackImage);
                                extendBoundsInZIfNecessary(channelIdx, bb);
                                Image image = trackImage.crop(bb);
                                image.resetOffset().translate(getBounds());
                                rawImagesC.set(image, channelIdx);
                            } else { // open root and crop
                                Image rootImage = getRoot().getRawImage(structureIdx);
                                //logger.debug("object: {}, channel: {}, no trackImage try to open root and crop... null ? {}", this, channelIdx, rootImage==null);
                                if (rootImage!=null) {
                                    BoundingBox bb = getRelativeBoundingBox(getRoot());
                                    extendBoundsInZIfNecessary(channelIdx, bb);
                                    Image image = rootImage.crop(bb);
                                    rawImagesC.set(image, channelIdx);
                                } else if (!this.equals(getRoot())) {
                                    // try to open parent image (if trackImage present...)
                                    Image pImage = this.getParent().getRawImage(structureIdx);
                                    //logger.debug("try to open parent image: null?{}", pImage==null);
                                    if (pImage!=null) {
                                        BoundingBox bb = getRelativeBoundingBox(getParent());
                                        extendBoundsInZIfNecessary(channelIdx, bb);
                                        Image image = pImage.crop(bb);
                                        rawImagesC.set(image, channelIdx);
                                    }
                                }                                
                            }
                            // no speed gain in opening only tiles
                            /*StructureObject root = getRoot();
                            BoundingBox bb=getRelativeBoundingBox(root);
                            extendBoundsInZIfNecessary(channelIdx, bb);
                            rawImagesC.set(root.openRawImage(structureIdx, bb), channelIdx);*/
                        }
                    }
                    if (rawImagesC.has(channelIdx)) rawImagesC.get(channelIdx).setCalibration(getScaleXY(), getScaleZ());
                    
                    //logger.debug("{} open channel: {}, use scale? {}, scale: {}", this, channelIdx, this.getMicroscopyField().getPreProcessingChain().useCustomScale(), getScaleXY());
                }
            }
        }
        return rawImagesC.get(channelIdx);
    }
    public Image getPreFilteredImage(int structureIdx) {
        return this.preFilteredImagesS.get(structureIdx);
    }
    public void setPreFilteredImage(Image image, int structureIdx) {
        if (image!=null) {
            if (!image.sameDimensions(getMask())) throw new IllegalArgumentException("PreFiltered Image should have same dimensions as object");
            image.setCalibration(getMask());
            image.resetOffset().translate(getBounds()); // ensure same offset
        }
        this.preFilteredImagesS.set(image, structureIdx);
    }
    public Image getTrackImage(int structureIdx) {
        //logger.debug("get Track image for : {}, id: {}, thId: {}, isTH?: {}, th: {}", this, id, this.trackHeadId, isTrackHead, this.trackHead);
        //logger.debug("get Track Image for: {} th {}", this, getTrackHead());
        if (this.isTrackHead) {
            int channelIdx = getExperiment().getChannelImageIdx(structureIdx);
            if (this.trackImagesC.get(channelIdx)==null) {
                synchronized(trackImagesC) {
                    if (trackImagesC.getAndExtend(channelIdx)==null) {
                        Image im = getExperiment().getImageDAO().openTrackImage(this, channelIdx);
                        if (im!=null) { // set image && set offsets for all track
                            im.setCalibration(getScaleXY(), getScaleZ());
                            List<StructureObject> track = StructureObjectUtils.getTrack(this, false);
                            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().generateTrackMask(track, structureIdx); // not saved in image window manager to avoid memory leaks
                            List<Pair<StructureObject, BoundingBox>> off = i.pairWithOffset(track);
                            for (Pair<StructureObject, BoundingBox> p : off) p.key.offsetInTrackImage=p.value;
                            //logger.debug("get track image: track:{}(id: {}/trackImageCId: {}) length: {}, chId: {}", this, this.hashCode(), trackImagesC.hashCode(), track.size(), channelIdx);
                            //logger.debug("offsets: {}", Utils.toStringList(track, o->o+"->"+o.offsetInTrackImage));
                            trackImagesC.setQuick(im, channelIdx); // set after offset is set if not offset could be null
                        }
                    }
                }
            }
            return trackImagesC.get(channelIdx);
        } else {
            return getTrackHead().getTrackImage(structureIdx);
        }
    }
    
    public BoundingBox getOffsetInTrackImage() {
        return this.offsetInTrackImage;
    }
    
    private BoundingBox extendBoundsInZIfNecessary(int channelIdx, BoundingBox bounds) { //when the current structure is 2D but channel is 3D 
        //logger.debug("extends bounds Z if necessary: is2D: {}, bounds: {}, sizeZ of image to open: {}", is2D(), bounds, getExperiment().getPosition(getPositionName()).getSizeZ(channelIdx));
        if (bounds.sizeZ()==1 && is2D() && channelIdx!=this.getExperiment().getChannelImageIdx(structureIdx)) { 
            int sizeZ = getExperiment().getPosition(getPositionName()).getSizeZ(channelIdx); //TODO no reliable if a transformation removes planes -> need to record the dimensions of the preProcessed Images
            if (sizeZ>1) {
                //logger.debug("extends bounds Z: is2D: {}, bounds: {}, sizeZ of image to open: {}, new bounds: {}", is2D(), bounds, getExperiment().getPosition(getPositionName()).getSizeZ(channelIdx), new MutableBoundingBox(bounds).expandZ(sizeZ-1));
                if (bounds instanceof MutableBoundingBox) ((MutableBoundingBox)bounds).expandZ(sizeZ-1);
                else return new MutableBoundingBox(bounds).expandZ(sizeZ-1);
            }
        }
        return bounds;
    }
    
    public boolean is2D() {
        if (getRegion()!=null) return getRegion().is2D();
        if (isRoot()) return true;
        return getExperiment().getPosition(getPositionName()).getSizeZ(getExperiment().getChannelImageIdx(structureIdx))==1;
    }
    
    public Image openRawImage(int structureIdx, MutableBoundingBox bounds) {
        int channelIdx = getExperiment().getChannelImageIdx(structureIdx);
        Image res;
        if (rawImagesC.get(channelIdx)==null) {//opens only within bounds
            if (getMicroscopyField().singleFrame(structureIdx) && timePoint>0 && trackHead!=null && trackHead.getBounds().sameBounds(getBounds())) {
                res = trackHead.openRawImage(structureIdx, bounds);
            } else {
                res =  getExperiment().getImageDAO().openPreProcessedImage(channelIdx, getMicroscopyField().singleFrame(structureIdx) ? 0 : timePoint, getPositionName(), bounds);
                if (res==null) throw new RuntimeException("No image found for object: "+this+" structure: "+structureIdx);
                res.setCalibration(getScaleXY(), getScaleZ());
                //if (this.frame==0) logger.debug("open from: {} within bounds: {}, resultBounds: {}", this, bounds, res.getBoundingBox());
            }
        } 
        else {
            res = rawImagesC.get(channelIdx).crop(bounds);
            //if (this.frame==0) logger.debug("crom from: {} within bounds: {}, input bounds: {}, resultBounds: {}", this, bounds, rawImagesC.get(channelIdx).getBoundingBox(), res.getBoundingBox());
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
    
    public <T extends BoundingBox<T>> BoundingBox<T> getRelativeBoundingBox(StructureObject stop) throws RuntimeException {
        SimpleBoundingBox res = new SimpleBoundingBox(getRegion().getBounds());
        if (stop==null || stop == getRoot()) return res;
        else return res.translate(new SimpleOffset(stop.getBounds()).reverseOffset());
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
    
    public void flushImages() {
        for (int i = 0; i<rawImagesC.getBucketSize(); ++i) rawImagesC.setQuick(null, i);
        for (int i = 0; i<trackImagesC.getBucketSize(); ++i) trackImagesC.setQuick(null, i);
        for (int i = 0; i<preFilteredImagesS.getBucketSize(); ++i) preFilteredImagesS.setQuick(null, i);
        this.offsetInTrackImage=null;
    }
    
    public RegionPopulation getObjectPopulation(int structureIdx) {
        List<StructureObject> child = this.getChildren(structureIdx);
        if (child==null || child.isEmpty()) return new RegionPopulation(new ArrayList<>(0), this.getMaskProperties());
        else {
            ArrayList<Region> objects = new ArrayList<>(child.size());
            for (StructureObject s : child) objects.add(s.getRegion());
            return new RegionPopulation(objects, this.getMaskProperties());
        }
    }
    public void setAttributeList(String key, List<Double> value) {
        if (this.attributes==null) attributes = new HashMap<>();
        attributes.put(key, value);
    }
    public void setAttributeArray(String key, double[] value) {
        if (this.attributes==null) attributes = new HashMap<>();
        attributes.put(key, Utils.toList(value));
    }
    public void setAttribute(String key, boolean value) {
        if (this.attributes==null) attributes = new HashMap<>();
        attributes.put(key, value);
    }
    public void setAttribute(String key, double value) {
        if (this.attributes==null) attributes = new HashMap<>();
        attributes.put(key, value);
    }
    public void setAttribute(String key, String value) {
        if (value==null) {
            if (attributes==null) return;
            attributes.remove(key);
            if (attributes.isEmpty()) attributes=null;
        } else {
            if (this.attributes==null) attributes = new HashMap<>();
            attributes.put(key, value);
        }
    }
    public Object getAttribute(String key) {
        if (attributes==null) return null;
        return attributes.get(key);
    }
    public Object getAttribute(String key, Object defaultValue) {
        if (attributes==null) return defaultValue;
        return attributes.getOrDefault(key, defaultValue);
    }
    public Map<String, Object> getAttributes() {
        if (this.attributes==null) attributes = new HashMap<>();
        return attributes;
    }
    public void setMeasurements(Measurements m) {
        this.measurements=m;
    }
    public Measurements getMeasurements() {
        if (measurements==null) {
            synchronized(this) {
                if (measurements==null) {
                    if (dao!=null && !(dao instanceof BasicObjectDAO)) measurements = dao.getMeasurements(this);
                    if (measurements==null) measurements = new Measurements(this);
                }
            }
        }
        return measurements;
    }
    
    public boolean hasMeasurements() {
        return measurements!=null && !getMeasurements().values.isEmpty();
    }
    public boolean hasMeasurementModifications() {
        return measurements!=null && measurements.modifications;
    }
    public boolean updateMeasurementsIfNecessary() {
        if (measurements!=null) {
            if (measurements.modifications) dao.upsertMeasurement(this);
            else measurements.updateObjectProperties(this); // upsert always update objectProperties
            return measurements.modifications;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return "P:"+getPositionIdx()+"/S:"+structureIdx+"/I:"+Selection.indicesToString(StructureObjectUtils.getIndexTree(this));//+"/id:"+id;
        //if (isRoot()) return "F:"+getPositionIdx() + ",T:"+frame;
        //else return "F:"+getPositionIdx()+ ",T:"+frame+ ",S:"+structureIdx+ ",Idx:"+idx+ ",P:["+getParent().toStringShort()+"]" + (flag==null?"":"{"+flag+"}") ;
    }
    
    public String toStringShort() {
        if (isRoot()) return "";
        else return "S:"+structureIdx+ ",Idx:"+idx+ ",P:["+getParent().toStringShort()+"]" ;
    }
    
    @Override
    public int compareTo(StructureObject other) {
        int comp = Integer.compare(getFrame(), other.getFrame());
        if (comp!=0) return comp;
        comp = Integer.compare(getStructureIdx(), other.getStructureIdx());
        if (comp!=0) return comp;
        if (getParent() != null && other.getParent() != null && !getParent().equals(other.getParent())) {
            comp = getParent().compareTo(other.getParent());
            if (comp!=0) return comp;
        }
        return Integer.compare(getIdx(), other.getIdx());
    }
    
    @Override
    public JSONObject toJSONEntry() {
        JSONObject obj1=new JSONObject();
        obj1.put("id", id);
        if (parentId!=null) obj1.put("pId", parentId);
        obj1.put("sIdx", structureIdx);
        obj1.put("idx", idx);
        obj1.put("frame", timePoint);
        if (nextId!=null) obj1.put("nextId", nextId);
        if (previousId!=null) obj1.put("prevId", previousId);
        if (parentTrackHeadId!=null) obj1.put("parentThId", parentTrackHeadId);
        if (trackHeadId!=null) obj1.put("thId", trackHeadId);
        obj1.put("isTh", isTrackHead);
        if (attributes!=null && !attributes.isEmpty()) obj1.put("attributes", JSONUtils.toJSONObject(attributes));
        if (objectContainer!=null) obj1.put("object", objectContainer.toJSON());
        return obj1;
    }
    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        Map json = (JSONObject)jsonEntry;
        id = (String)json.get("id");
        Object pId = json.get("pId");
        if (pId!=null) parentId = (String)pId;
        structureIdx = ((Number)json.get("sIdx")).intValue();
        idx = ((Number)json.get("idx")).intValue();
        timePoint = ((Number)json.get("frame")).intValue();
        Object nId = json.get("nextId");
        if (nId!=null) nextId = (String)nId;
        Object prevId = json.get("prevId");
        if (prevId!=null) previousId = (String)prevId;
        Object parentThId = json.get("parentThId");
        if (parentThId!=null) parentTrackHeadId = (String)parentThId;
        Object thId = json.get("thId");
        if (thId!=null) trackHeadId = (String)thId;
        isTrackHead = (Boolean)json.get("isTh");
        
        if (json.containsKey("attributes")) {
            attributes = (Map<String, Object>)json.get("attributes");
            //attributes = JSONUtils.toValueMap((Map)json.get("attributes")); // leave list for better efficiency ?
        } 
        if (json.containsKey("object")) {
            Map objectJ = (Map)json.get("object");
            objectContainer = RegionContainer.createFromMap(this, objectJ);
        }
    }
    public StructureObject(Map json) {
        this.initFromJSONEntry(json);
    }
    
    public StructureObject(){}
    
    public void postLoad() {
        //logger.debug("post load: {}", this);
        if (objectContainer!=null) objectContainer.setStructureObject(this);
    }

}
