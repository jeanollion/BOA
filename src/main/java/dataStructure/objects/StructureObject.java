package dataStructure.objects;

import dataStructure.configuration.Experiment;
import dataStructure.configuration.MicroscopyField;
import dataStructure.containers.ObjectContainer;
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

@Cache
//@Lifecycle -> bug a cause de la structure circulaire
@Entity(collectionName = "Objects")
@Index(value={"field_name, time_point, structure_idx", "parent,structure_idx,idx", "new_track_branch, time_point"}, options={"", "", ""})
public class StructureObject implements StructureObjectPostProcessing, Track {
    private final static Logger logger = LoggerFactory.getLogger(StructureObject.class);
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
    @Reference(lazyLoading=true, automaticStore=false) protected StructureObject previous, next;
    protected boolean newTrackBranch=true;
    
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
        this.object=new Object3D(mask, 1);
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
    public StructureObject getParent() {
        if (parent==null) return null;
        parent.callLazyLoading();
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
    public void setChildObjects(StructureObject[] children, int structureIdx) {
        this.childrenSM.set(children, structureIdx);
    }
    // track-related methods
    /**
     * 
     * @param previous the previous object in the track
     * @param isNewBranch if true, sets this instance as the next of {@param previous} 
     */
    public void setPreviousInTrack(StructureObjectPreProcessing previous, boolean isNewBranch) {
        this.previous=(StructureObject)previous;
        if (!isNewBranch) {
            ((StructureObject)previous).next=this;
            newTrackBranch=false;
        } else this.newTrackBranch=true;
    }
    
    public StructureObject getPrevious() {
        if (previous==null) return null;
        previous.callLazyLoading();
        return previous;
    }
    
    public StructureObject getNext() {
        if (next==null) return null;
        next.callLazyLoading();
        return next;
    }
    
    // object- and image-related methods
    protected Object3D getObject() {
        if (object==null) {
            MicroscopyField f = xp.getMicroscopyField(fieldName);
            objectContainer.setScale(f.getScaleXY(), f.getScaleZ());
            object=objectContainer.getObject();
        }
        return object;
    }
    
    public ImageInteger getMask() {return getObject().getMask();}
    public BoundingBox getBounds() {return getObject().getBounds();}
    protected void createObjectContainer() {this.objectContainer=object.getObjectContainer(this);}
    public void updateObjectContainer(){
        // TODO: only if changes -> transient variable to record changes..
        if (objectContainer==null) createObjectContainer();
        logger.debug("updating object container: {} of object: {}", objectContainer.getClass(), this );
        objectContainer.updateObject(object);
    }
    public Image getRawImage(int structureIdx) {
        int channelIdx = xp.getChannelImageIdx(structureIdx);
        if (rawImagesC.get(channelIdx)==null) { // chercher l'image chez le parent avec les bounds
            if (isRoot()) {
                if (rawImagesC.getAndExtend(channelIdx)==null) rawImagesC.set(xp.getImageDAO().openPreProcessedImage(channelIdx, timePoint, fieldName), channelIdx);
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
        int channelIdx = xp.getChannelImageIdx(structureIdx);
        if (rawImagesC.get(channelIdx)==null) return xp.getImageDAO().openPreProcessedImage(channelIdx, timePoint, fieldName, bounds);
        else return rawImagesC.get(channelIdx).crop(bounds);
    }
    
    public StructureObject getFirstParentWithOpenedRawImage(int structureIdx) {
        if (isRoot()) {
            if (rawImagesC.get(xp.getChannelImageIdx(structureIdx))!=null) return this;
            else return null;
        }
        if (getParent().rawImagesC.get(xp.getChannelImageIdx(structureIdx))!=null) return parent;
        else return parent.getFirstParentWithOpenedRawImage(structureIdx);
    }
    
    public BoundingBox getRelativeBoundingBox(StructureObject stop) {
        if (stop==null) stop=getRoot();
        StructureObject nextParent=this;
        BoundingBox res = object.bounds.duplicate();
        logger.debug("relative bounding box: from: {} to {}", this, stop);
        logger.debug("init bounds: {}", res);
        do {
            nextParent=nextParent.getParent();
            res.addOffset(nextParent.object.bounds);
            logger.debug("bounds + offset {} from {}", res, nextParent);
        } while(nextParent!=stop);
        
        return res;
    }
    
    public Image getFilteredImage(int structureIdx) {
        return preProcessedImageS.get(structureIdx);
    }
    
    public void createPreFilterImage(int structureIdx) {
        Image raw = getRawImage(structureIdx);
        if (raw!=null) preProcessedImageS.set(preFilterImage(getRawImage(structureIdx), this, xp.getStructure(structureIdx).getProcessingChain().getPrefilters()), structureIdx);
    }
    
    public void segmentChildren(int structureIdx) {
        if (getFilteredImage(structureIdx)==null) createPreFilterImage(structureIdx);
        ImageInteger seg = segmentImage(getFilteredImage(structureIdx), this, xp.getStructure(structureIdx).getProcessingChain().getSegmenter());
        if (seg instanceof BlankMask) childrenSM.set(new StructureObject[0], structureIdx);
        else {
            seg = postFilterImage(seg, this, xp.getStructure(structureIdx).getProcessingChain().getPostfilters());
            TreeMap<Integer, BoundingBox> bounds = ObjectFactory.getBounds(seg);
            ObjectFactory.relabelImage(seg, bounds);
            Object3D[] objects = ObjectFactory.getObjectsImage(seg, bounds, true);
            StructureObject[] res = new StructureObject[objects.length];
            childrenSM.set(res, structureIdx);
            for (int i = 0; i<objects.length; ++i) res[i]=new StructureObject(fieldName, timePoint, structureIdx, i, objects[i], this, xp);
        }
    }
    /*@Override
    public String toString() {
        if (isRoot()) return "Root Object: fieldName: "+fieldName + " timePoint: "+timePoint;
        else return "Object: fieldName: "+fieldName+ " timePoint: "+timePoint+ " structureIdx: "+structureIdx+ " parentId: "+parent.id+ " idx: "+idx;
    }*/
    // morphium-related methods
    /*@PreStore public void preStore() {
        createObjectContainer();
    }*/
    public void callLazyLoading(){} // for lazy-loading listener
    
    public StructureObject(){}
}
