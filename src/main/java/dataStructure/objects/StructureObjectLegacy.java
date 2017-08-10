package dataStructure.objects;

import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.PostLoadable;
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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import measurement.MeasurementKey;
import org.bson.types.ObjectId;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.ObjectSplitter;
import processing.ImageFeatures;
import utils.JSONUtils;
import utils.Pair;
import utils.SmallArray;
import utils.Utils;

@Lifecycle
@Entity
@Index(value={"structure_idx, parent_id"})
public class StructureObjectLegacy {
    public final static Logger logger = LoggerFactory.getLogger(StructureObjectLegacy.class);
    //structure-related attributes
    @Id protected ObjectId id;
    protected ObjectId parentId;
    protected int structureIdx;
    protected int idx;
    // track-related attributes
    protected int timePoint;
    ObjectId nextId, previousId;
    ObjectId parentTrackHeadId, trackHeadId;
    protected boolean isTrackHead=true;
    protected Map<String, Object> attributes;
    // object- and images-related attributes
    protected ObjectContainer objectContainer;
    
    // measurement-related attributes
    @Transient Measurements measurements;
    public StructureObject getStructureObject() {
        JSONObject obj1=new JSONObject();
        obj1.put("id", id.toHexString());
        if (parentId!=null) obj1.put("pId", parentId.toHexString());
        obj1.put("sIdx", structureIdx);
        obj1.put("idx", idx);
        obj1.put("frame", timePoint);
        if (nextId!=null) obj1.put("nextId", nextId.toHexString());
        if (previousId!=null) obj1.put("prevId", previousId.toHexString());
        if (parentTrackHeadId!=null) obj1.put("parentThId", parentTrackHeadId.toHexString());
        if (trackHeadId!=null) obj1.put("thId", trackHeadId.toHexString());
        obj1.put("isTh", isTrackHead);
        if (attributes!=null && !attributes.isEmpty()) obj1.put("attributes", JSONUtils.toJSONObject(attributes));
        if (objectContainer!=null) obj1.put("object", objectContainer.toJSON());
        return new StructureObject(obj1);
    }
}
