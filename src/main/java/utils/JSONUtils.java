/*
 * Copyright (C) 2017 jollion
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package utils;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBRef;
import com.mongodb.util.JSONParseException;
import configuration.parameters.PostLoadable;
import dataStructure.configuration.Experiment;
import dataStructure.objects.Measurements;
import dataStructure.objects.MeasurementsLegacy;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectLegacy;
import de.caluga.morphium.AnnotationAndReflectionHelper;
import de.caluga.morphium.BinarySerializedObject;
import de.caluga.morphium.PartiallyUpdateable;
import de.caluga.morphium.annotations.AdditionalData;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.PartialUpdate;
import de.caluga.morphium.annotations.ReadOnly;
import de.caluga.morphium.annotations.Reference;
import de.caluga.morphium.annotations.UseIfnull;
import de.caluga.morphium.annotations.WriteOnly;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.LoggerFactory;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;
import sun.reflect.ReflectionFactory;

/**
 *
 * @author jollion
 */
public class JSONUtils {
    public final static org.slf4j.Logger logger = LoggerFactory.getLogger(JSONUtils.class);
    public static JSONObject toJSONObject(Map<String, ?> map) {
        JSONObject res=  new JSONObject();
        for (Map.Entry<String, ?> e : map.entrySet()) res.put(e.getKey(), toJSONEntry(e.getValue()));
        return res;
    }
    public static JSONArray toJSONList(List list) {
        JSONArray res =new JSONArray();
        for (Object o : list) res.add(toJSONEntry(o));
        return res;
    }
    public static Object toJSONEntry(Object o) {
        if (o==null) return "null";
        else if (o instanceof double[]) return toJSONArray((double[])o);
        else if (o instanceof int[]) return toJSONArray((int[])o);
        else if (o instanceof Number) return o;
        else if (o instanceof Boolean) return o;
        else if (o instanceof String) return o;
        else if (o instanceof List) {
            JSONArray l = new JSONArray();
            for (Object oo : ((List)o)) l.add(toJSONEntry(oo));
            return l;
        }
        else if (o instanceof boolean[]) return toJSONArray((boolean[])o);
        else if (o instanceof String[]) return toJSONArray((String[])o);
        else return o.toString();
    }
    public static Map<String, Object> toMap(Map map) {
        HashMap<String, Object> res= new HashMap<>(map.size());
        for (Object e : map.entrySet()) {
            String key = (String)((Entry)e).getKey();
            Object value = ((Entry)e).getValue();
            if (value instanceof List) {
                List array = (List)value;
                if (!array.isEmpty() && (array.get(0) instanceof Integer || array.get(0) instanceof Long)) res.put(key, fromIntArray(array));
                else res.put(key, fromDoubleArray(array));
            } else res.put(key, value);
        }
        return res;
    }
    public static Map<String, Object> toValueMap(Map jsonMap) {
        List<String> keys = new ArrayList<>(jsonMap.keySet());
        for (String k : keys) {
            Object v = jsonMap.get(k);
            if (v instanceof List) {
                List array = (List)v;
                if (!array.isEmpty() && (array.get(0) instanceof Integer || array.get(0) instanceof Long)) jsonMap.put(k, fromIntArray(array));
                else jsonMap.put(k, fromDoubleArray(array));
            } 
        }
        return (Map<String, Object>)jsonMap;
    }
    
    public static double[] fromDoubleArray(List array) {
        double[] res = new double[array.size()];
        for (int i = 0; i<res.length; ++i) res[i]=((Number)array.get(i)).doubleValue();
        return res;
    }
    public static JSONArray toJSONArray(double[] array) {
        JSONArray res = new JSONArray();
        for (double d : array) res.add(d);
        return res;
    }
    public static List<Integer> fromIntArrayToList(JSONArray array) {
        List<Integer> res = new ArrayList<Integer>(array.size());
        for (Object o : array) res.add(((Number)o).intValue());
        return res;
    }
    public static String[] fromStringArray(List array) {
        String[] res = new String[array.size()];
        res = (String[])array.toArray(res);
        return res;
    }
    public static int[] fromIntArray(List array) {
        int[] res = new int[array.size()];
        for (int i = 0; i<res.length; ++i) res[i]=((Number)array.get(i)).intValue();
        return res;
    }
    public static boolean[] fromBooleanArray(List array) {
        boolean[] res = new boolean[array.size()];
        for (int i = 0; i<res.length; ++i) res[i]=((Boolean)array.get(i));
        return res;
    }
    public static JSONArray toJSONArray(int[] array) {
        JSONArray res = new JSONArray();
        for (int d : array) res.add(d);
        return res;
    }
    public static JSONArray toJSONArray(boolean[] array) {
        JSONArray res = new JSONArray();
        for (boolean d : array) res.add(d);
        return res;
    }
    public static JSONArray toJSONArray(String[] array) {
        JSONArray res = new JSONArray();
        for (String d : array) res.add(d);
        return res;
    }
    public static JSONArray toJSONArray(Collection<? extends Number> collection) {
        JSONArray res = new JSONArray();
        res.addAll(collection);
        return res;
    }
    public static List<Integer> fromIntArrayList(JSONArray array) { // necessaire -> pas directement Integer ? 
        List<Integer> res = new ArrayList<>(array.size());
        for (Object o : array) res.add(((Number)o).intValue());
        return res;
    }
    public static String serialize(Object o) {
        if (o instanceof StructureObject) return ((StructureObject)o).toJSON().toJSONString();
        else if (o instanceof Measurements) return ((Measurements)o).toJSON().toJSONString();
        else if (o instanceof Selection) return ((Selection)o).toJSONEntry().toJSONString();
        else if (o instanceof Experiment) {
            String res= ((Experiment)o).toJSONEntry().toJSONString();
            Experiment xpTest = new Experiment();
            xpTest.initFromJSONEntry(JSONUtils.parse(res));
            logger.debug("check xp serialization: {}", ((Experiment)o).sameContent(xpTest));
            return res;
        }
        DBObject oMarsh = marshall(o);
        return com.mongodb.util.JSON.serialize(oMarsh);
    }
    public static JSONObject parse(String s) {
        try {
            Object res= new JSONParser().parse(s);
            return (JSONObject)res;
        } catch (ParseException ex) {
            logger.trace("Could not parse: "+s, ex);
            return null;
        }
    }
    public static <T> T parse(Class<T> clazz, String s) {
        if (StructureObject.class.equals(clazz)) {
            JSONObject o = parse(s);
            if (o!=null && o.containsKey("sIdx")) return (T)new StructureObject(o);
            else {
                DBObject oParse = (DBObject)com.mongodb.util.JSON.parse(s); 
                StructureObjectLegacy res = unmarshall(StructureObjectLegacy.class, oParse);
                return (T)res.getStructureObject();
            }
        } else if (Measurements.class.equals(clazz)) {
            JSONObject o = parse(s);
            if (o!=null && o.containsKey("sIdx")) return (T)new Measurements(o);
            else {
                DBObject oParse = (DBObject)com.mongodb.util.JSON.parse(s); 
                MeasurementsLegacy res = unmarshall(MeasurementsLegacy.class, oParse);
                return (T)res.getMeasurements();
            }
        } else if (Experiment.class.equals(clazz)) {
            JSONObject o = parse(s);
            if (o!=null && o.containsKey("channelImages")) {
                Experiment xp = new Experiment();
                xp.initFromJSONEntry(o);
                return (T)xp;
            }
        } else if (Selection.class.equals(clazz)) {
            JSONObject o = parse(s);
            if (o!=null && o.containsKey("name")) {
                Selection sel = new Selection();
                sel.initFromJSONEntry(o);
                return (T)sel;
            }
        }
        return parseMorhium(clazz, s);
    }
    private static <T> T parseMorhium(Class<T> clazz, String s) {
        try {
            DBObject oParse = (DBObject)com.mongodb.util.JSON.parse(s); 
            T res = unmarshall(clazz, oParse);
            if (res instanceof PostLoadable) ((PostLoadable)res).postLoad();
            return res;
        }
        catch (JSONParseException e) {
            logger.error("Parse Exception", e);
            String message = e.getMessage();
            int idx = message.length()-2;
            while (idx>0 && message.charAt(idx)==' ') --idx;
            int idx2 = message.length()-idx;
            logger.debug("message length: {}, idx:{}/{}", message.length(), idx, idx2);
            logger.error("problem at: " + message.substring(idx2-10, idx2+10));
            logger.error("contect: " + message.substring(idx2-200, idx2+200));
        }
        return null;
    }
    // offline marshall / unmarshall from Morphium
    
    private final static AnnotationAndReflectionHelper annotationHelper = new AnnotationAndReflectionHelper(true);
    private final static List<Class<?>> mongoTypes = new CopyOnWriteArrayList<Class<?>>() {{add(String.class); add(Character.class); add(Integer.class); add(Long.class);add(Float.class);add(Double.class);add(Date.class);add(Boolean.class);add(Byte.class);}};
    private final static ReflectionFactory reflection = ReflectionFactory.getReflectionFactory();
    
    public static DBObject marshall(Object o) {
        //recursively map object to mongo-Object...
        if (!annotationHelper.isEntity(o)) {
            if (o instanceof Serializable) {
                try {
                    BinarySerializedObject obj = new BinarySerializedObject();
                    obj.setOriginalClassName(o.getClass().getName());
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ObjectOutputStream oout = new ObjectOutputStream(out);
                    oout.writeObject(o);
                    oout.flush();

                    BASE64Encoder enc = new BASE64Encoder();

                    String str = enc.encode(out.toByteArray());
                    obj.setB64Data(str);
                    return marshall(obj);

                } catch (IOException e) {
                    throw new IllegalArgumentException("Binary serialization failed! " + o.getClass().getName(), e);
                }
            } else {
                throw new IllegalArgumentException("Cannot write object to db that is neither entity, embedded nor serializable!");
            }
        }
        DBObject dbo = new BasicDBObject();
        if (o == null) {
            return dbo;
        }
        Class<?> cls = annotationHelper.getRealClass(o.getClass());
        if (cls == null) {
            throw new IllegalArgumentException("No real class?");
        }
        o = annotationHelper.getRealObject(o);
        List<String> flds = annotationHelper.getFields(cls);
        if (flds == null) {
            throw new IllegalArgumentException("Fields not found? " + cls.getName());
        }
        Entity e = annotationHelper.getAnnotationFromHierarchy(o.getClass(), Entity.class); //o.getClass().getAnnotation(Entity.class);
        Embedded emb = annotationHelper.getAnnotationFromHierarchy(o.getClass(), Embedded.class); //o.getClass().getAnnotation(Embedded.class);

        if (e != null && e.polymorph()) {
            dbo.put("class_name", cls.getName());
        }

        if (emb != null && emb.polymorph()) {
            dbo.put("class_name", cls.getName());
        }

        for (String f : flds) {
            String fName = f;
            try {
                Field fld = annotationHelper.getField(cls, f);
                if (fld == null) {
                    logger.error("Field not found");
                    continue;
                }
                //do not store static fields!
                if (Modifier.isStatic(fld.getModifiers())) {
                    continue;
                }
                if (fld.isAnnotationPresent(ReadOnly.class)) {
                    continue; //do not write value
                }
                AdditionalData ad = fld.getAnnotation(AdditionalData.class);
                if (ad != null) {
                    if (!ad.readOnly()) {
                        //storing additional data
                        if (fld.get(o) != null) {
                            dbo.putAll((Map) createDBMap((Map<String, Object>) fld.get(o)));
                        }
                    }
                    //additional data is usually transient
                    continue;
                }
                if (dbo.containsField(fName)) {
                    //already stored, skip it
                    logger.warn("Field " + fName + " is shadowed - inherited values?");
                    continue;
                }
                if (fld.isAnnotationPresent(Id.class)) {
                    fName = "_id";
                }
                Object v = null;
                Object value = fld.get(o);
                if (fld.isAnnotationPresent(Reference.class)) {
                    Reference r = fld.getAnnotation(Reference.class);
                    //reference handling...
                    //field should point to a certain type - store ObjectID only
                    if (value == null) {
                        //no reference to be stored...
                        v = null;
                    } else {
                        if (Collection.class.isAssignableFrom(fld.getType())) {
                            //list of references....
                            BasicDBList lst = new BasicDBList();
                            for (Object rec : ((Collection) value)) {
                                if (rec != null) {
                                    Object id = annotationHelper.getId(rec);
                                    if (id == null) {
                                        throw new IllegalArgumentException("Cannot store reference to unstored entity if automaticStore in @Reference is set to false!");
                                    }
                                    DBRef ref = new DBRef(annotationHelper.getRealClass(rec.getClass()).getName(), id);
                                    lst.add(ref);
                                } else {
                                    lst.add(null);
                                }
                            }
                            v = lst;
                        } else if (Map.class.isAssignableFrom(fld.getType())) {
                            throw new RuntimeException("Cannot store references in Maps!");
                        } else {
                            //DBRef ref = new DBRef(morphium.getDatabase(), value.getClass().getName(), getId(value));
                            v = annotationHelper.getId(value);
                        }
                    }
                } else {

                    //check, what type field has

                    //Store Entities recursively
                    //TODO: Fix recursion - this could cause a loop!
                    Class<?> valueClass = null;

                    if (value == null) {
                        valueClass = fld.getType();
                    } else {
                        valueClass=value.getClass();
                    }

                    if (annotationHelper.isAnnotationPresentInHierarchy(valueClass, Entity.class)) {
                        if (value != null) {
                            DBObject obj = marshall(value);
                            obj.removeField("_id");  //Do not store ID embedded!
                            v = obj;
                        }
                    } else if (annotationHelper.isAnnotationPresentInHierarchy(valueClass, Embedded.class)) {
                        if (value != null) {
                            v = marshall(value);
                        }
                    } else {
                        v = value;
                        if (v != null) {
                            if (v instanceof Map) {
                                //create MongoDBObject-Map
                                v = createDBMap((Map) v);
                            } else if (v instanceof List) {
                                v = createDBList((List) v);
                            } else if (v instanceof Iterable) {
                                ArrayList lst = new ArrayList();
                                for (Object i : (Iterable) v) {
                                    lst.add(i);
                                }
                                v = createDBList(lst);
                            } else if (v.getClass().equals(GregorianCalendar.class)) {
                                v = ((GregorianCalendar) v).getTime();
                            } else if (v.getClass().isEnum()) {
                                v = ((Enum) v).name();
                            }
                        }
                    }
                }
                if (v == null) {
                    if (!fld.isAnnotationPresent(UseIfnull.class)) {
                        //Do not put null-Values into dbo => not storing null-Values to db
                        continue;
                    }
                }
                dbo.put(fName, v);

            } catch (IllegalAccessException exc) {
                logger.error("Illegal Access to field " + f);
            }

        }
        return dbo;
    }
    
    private static BasicDBList createDBList(List v) {
        BasicDBList lst = new BasicDBList();
        for (Object lo : v) {
            if (lo != null) {
                if (annotationHelper.isAnnotationPresentInHierarchy(lo.getClass(), Entity.class) ||
                        annotationHelper.isAnnotationPresentInHierarchy(lo.getClass(), Embedded.class)) {
                    DBObject marshall = marshall(lo);
                    marshall.put("class_name", lo.getClass().getName());
                    lst.add(marshall);
                } else if (lo instanceof List) {
                    lst.add(createDBList((List) lo));
                } else if (lo instanceof Map) {
                    lst.add(createDBMap(((Map) lo)));
                } else if (lo.getClass().isEnum()) {
                    BasicDBObject obj = new BasicDBObject();
                    obj.put("class_name", lo.getClass().getName());
                    obj.put("name", ((Enum) lo).name());
                    lst.add(obj);
                    //throw new IllegalArgumentException("List of enums not supported yet");
                } else if (lo.getClass().isPrimitive()) {
                    lst.add(lo);
                } else if (mongoTypes.contains(lo.getClass())) {
                    lst.add(lo);
                } else {
                    lst.add(marshall(lo));
                }
            } else {
                lst.add(null);
            }
        }
        return lst;
    }
    
    @SuppressWarnings("unchecked")
    private static BasicDBObject createDBMap(Map v) {
        BasicDBObject dbMap = new BasicDBObject();
        for (Map.Entry<Object, Object> es : ((Map<Object, Object>) v).entrySet()) {
            Object k = es.getKey();
            if (!(k instanceof String)) {
                logger.warn("Map in Mongodb needs to have String as keys - using toString");
                k = k.toString();
                if (((String) k).contains(".")) {
                    logger.warn(". not allowed as Key in Maps - converting to _");
                    k = ((String) k).replaceAll("\\.", "_");
                }
            }
            Object mval = es.getValue(); // ((Map) v).get(k);
            if (mval != null) {
                if (annotationHelper.isAnnotationPresentInHierarchy(mval.getClass(), Entity.class) || annotationHelper.isAnnotationPresentInHierarchy(mval.getClass(), Embedded.class)) {
                    DBObject obj = marshall(mval);
                    obj.put("class_name", mval.getClass().getName());
                    mval = obj;
                } else if (mval instanceof Map) {
                    mval = createDBMap((Map) mval);
                } else if (mval instanceof List) {
                    mval = createDBList((List) mval);
                } else if (mval.getClass().isEnum()) {
                    BasicDBObject obj = new BasicDBObject();
                    obj.put("class_name", mval.getClass().getName());
                    obj.put("name", ((Enum) mval).name());
                } else if (!mval.getClass().isPrimitive() && !mongoTypes.contains(mval.getClass())) {
                    mval = marshall(mval);
                }
            }
            dbMap.put((String) k, mval);
        }
        return dbMap;
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T unmarshall(Class<? extends T> theClass, DBObject o) {
        Class cls = theClass;
        try {
            if ( !annotationHelper.isAnnotationPresentInHierarchy(cls, Entity.class) && !(annotationHelper.isAnnotationPresentInHierarchy(cls, Embedded.class))) {
                cls = BinarySerializedObject.class;
            }
            if (o.get("class_name") != null || o.get("className") != null) {
                try {
                    String cN = (String) o.get("class_name");
                    if (cN == null) {
                        cN = (String) o.get("className");
                    }
                    cls = Class.forName(cN);
                } catch (ClassNotFoundException e) {
                    logger.debug("error in unmarshall, class: {}/{}", o.get("class_name"), o.get("className"));
                    throw new RuntimeException(e);
                }
            }
            if (cls.isEnum()) {
                T[] en = (T[]) cls.getEnumConstants();
                for (Enum e : ((Enum[]) en)) {
                    if (e.name().equals(o.get("name"))) {
                        return (T) e;
                    }
                }
            }

            Object ret = null;

            try {
                ret = cls.newInstance();
            } catch (Exception ignored) {
            }
            if (ret == null) {
                final Constructor constructor;
                try {
                    
                    constructor = reflection.newConstructorForSerialization(cls, Object.class.getDeclaredConstructor());
                    ret = constructor.newInstance();
                } catch (Exception e) {
                    logger.error("unmarshall", e);
                }
            }
            if (ret == null) {
                throw new IllegalArgumentException("Could not instanciate " + cls.getName());
            }
            List<String> flds = annotationHelper.getFields(cls);

            for (String f : flds) {

                Object valueFromDb = o.get(f);
                Field fld = annotationHelper.getField(cls, f);
                if (Modifier.isStatic(fld.getModifiers())) {
                    //skip static fields
                    continue;
                }

                if (fld.isAnnotationPresent(WriteOnly.class)) {
                    continue;//do not read from DB
                }
                if (fld.isAnnotationPresent(AdditionalData.class)) {
                    //this field should store all data that is not put to fields
                    if (!Map.class.isAssignableFrom(fld.getType())) {
                        logger.error("Could not unmarshall additional data into fld of type " + fld.getType().toString());
                        continue;
                    }
                    Set<String> keys = o.keySet();
                    Map<String, Object> data = new HashMap<>();
                    for (String k : keys) {
                        if (flds.contains(k)) {
                            continue;
                        }
                        if (k.equals("_id")) {
                            //id already mapped
                            continue;
                        }

                        if (o.get(k) instanceof BasicDBObject) {
                            if (((BasicDBObject) o.get(k)).get("class_name") != null) {
                                data.put(k, unmarshall(Class.forName((String) ((BasicDBObject) o.get(k)).get("class_name")), (BasicDBObject) o.get(k)));
                            } else {
                                data.put(k, createMap((BasicDBObject) o.get(k)));
                            }
                        } else if (o.get(k) instanceof BasicDBList) {
                            data.put(k, createList((BasicDBList) o.get(k)));
                        } else {
                            data.put(k, o.get(k));
                        }

                    }
                    fld.set(ret, data);
                    continue;
                }
                if (valueFromDb == null) {
                    continue;
                }
                Object value = null;
                if (!Map.class.isAssignableFrom(fld.getType()) && !Collection.class.isAssignableFrom(fld.getType()) && fld.isAnnotationPresent(Reference.class)) {
                    //A reference - only id stored
                    throw new IllegalArgumentException("References not managed");
                } else if (fld.isAnnotationPresent(Id.class)) {
                    value = o.get("_id");
                    if (!value.getClass().equals(fld.getType())) {
                        logger.warn("read value and field type differ...");
                        if (fld.getType().equals(ObjectId.class)) {
                            logger.warn("trying objectID conversion");
                            if (value.getClass().equals(String.class)) {
                                try {
                                    value = new ObjectId((String) value);
                                } catch (Exception e) {
                                    logger.error("Id conversion failed - setting returning null", e);
                                    return null;
                                }
                            }
                        } else if (value.getClass().equals(ObjectId.class)) {
                            if (fld.getType().equals(String.class)) {
                                value = value.toString();
                            } else if (fld.getType().equals(Long.class) || fld.getType().equals(long.class)) {
                                value = ((ObjectId) value).getTime();
                            } else {
                                logger.error("cannot convert - ID IS SET TO NULL. Type read from db is " + value.getClass().getName() + " - expected value is " + fld.getType().getName());
                                return null;
                            }
                        }
                    }
                } else if (annotationHelper.isAnnotationPresentInHierarchy(fld.getType(), Entity.class) || annotationHelper.isAnnotationPresentInHierarchy(fld.getType(), Embedded.class)) {
                    //entity! embedded
                    value = unmarshall(fld.getType(), (DBObject) valueFromDb);
                } else if (Map.class.isAssignableFrom(fld.getType())) {
                    BasicDBObject map = (BasicDBObject) valueFromDb;
                    value = createMap(map);
                } else if (Collection.class.isAssignableFrom(fld.getType()) || fld.getType().isArray()) {
                    if (fld.getType().equals(byte[].class)) {
                        //binary data
                        if (logger.isDebugEnabled())
                            logger.debug("Reading in binary data object");
                        value = valueFromDb;

                    } else {

                        List lst = new ArrayList();
                        if (valueFromDb.getClass().isArray()) {
                            //a real array!
                            if (valueFromDb.getClass().getComponentType().isPrimitive()) {
                                if (valueFromDb.getClass().getComponentType().equals(int.class)) {
                                    for (int i : (int[]) valueFromDb) {
                                        lst.add(i);
                                    }
                                } else if (valueFromDb.getClass().getComponentType().equals(double.class)) {
                                    for (double i : (double[]) valueFromDb) {
                                        lst.add(i);
                                    }
                                } else if (valueFromDb.getClass().getComponentType().equals(float.class)) {
                                    for (float i : (float[]) valueFromDb) {
                                        lst.add(i);
                                    }
                                } else if (valueFromDb.getClass().getComponentType().equals(boolean.class)) {
                                    for (boolean i : (boolean[]) valueFromDb) {
                                        lst.add(i);
                                    }
                                } else if (valueFromDb.getClass().getComponentType().equals(byte.class)) {
                                    for (byte i : (byte[]) valueFromDb) {
                                        lst.add(i);
                                    }
                                } else if (valueFromDb.getClass().getComponentType().equals(char.class)) {
                                    for (char i : (char[]) valueFromDb) {
                                        lst.add(i);
                                    }
                                } else if (valueFromDb.getClass().getComponentType().equals(long.class)) {
                                    for (long i : (long[]) valueFromDb) {
                                        lst.add(i);
                                    }
                                }
                            } else {
                                Collections.addAll(lst, (Object[]) valueFromDb);
                            }
                        } else {
                            BasicDBList l = (BasicDBList) valueFromDb;
                            if (l != null) {
                                fillList(fld, l, lst, ret);
                            }
                        }
                        if (fld.getType().isArray()) {
                            Object arr = Array.newInstance(fld.getType().getComponentType(), lst.size());
                            for (int i = 0; i < lst.size(); i++) {
                                if (fld.getType().getComponentType().isPrimitive()) {
                                    if (fld.getType().getComponentType().equals(int.class)) {
                                        Array.set(arr, i, ((Integer) lst.get(i)).intValue());
                                    } else if (fld.getType().getComponentType().equals(long.class)) {
                                        Array.set(arr, i, ((Long) lst.get(i)).longValue());
                                    } else if (fld.getType().getComponentType().equals(float.class)) {
                                        //Driver sends doubles instead of floats
                                        Array.set(arr, i, ((Double) lst.get(i)).floatValue());

                                    } else if (fld.getType().getComponentType().equals(double.class)) {
                                        Array.set(arr, i, ((Double) lst.get(i)).doubleValue());

                                    } else if (fld.getType().getComponentType().equals(boolean.class)) {
                                        Array.set(arr, i, ((Boolean) lst.get(i)).booleanValue());

                                    }
                                } else {
                                    Array.set(arr, i, lst.get(i));
                                }
                            }
                            value = arr;
                        } else {
                            value = lst;
                        }


                    }
                } else if (fld.getType().isEnum()) {
                    value = Enum.valueOf((Class<? extends Enum>) fld.getType(), (String) valueFromDb);
                } else {
                    value = valueFromDb;
                }
                annotationHelper.setValue(ret, value, f);
            }

            if (annotationHelper.isAnnotationPresentInHierarchy(cls, Entity.class)) {
                flds = annotationHelper.getFields(cls, Id.class);
                if (flds.isEmpty()) {
                    throw new RuntimeException("Error - class does not have an ID field!");
                }
                Field field = annotationHelper.getField(cls, flds.get(0));
                if (o.get("_id") != null) {  //Embedded entitiy?
                    if (o.get("_id").getClass().equals(field.getType())) {
                        field.set(ret, o.get("_id"));
                    } else if (field.getType().equals(String.class) && o.get("_id").getClass().equals(ObjectId.class)) {
                        logger.warn("ID type missmatch - field is string but got objectId from mongo - converting");
                        field.set(ret, o.get("_id").toString());
                    } else if (field.getType().equals(ObjectId.class) && o.get("_id").getClass().equals(String.class)) {
                        logger.warn("ID type missmatch - field is objectId but got string from db - trying conversion");
                        field.set(ret, new ObjectId((String) o.get("_id")));
                    } else {
                        logger.error("ID type missmatch");
                        throw new IllegalArgumentException("ID type missmatch. Field in '" + ret.getClass().toString() + "' is '" + field.getType().toString() + "' but we got '" + o.get("_id").getClass().toString() + "' from Mongo!");
                    }
                }
            }
            if (annotationHelper.isAnnotationPresentInHierarchy(cls, PartialUpdate.class) || cls.isInstance(PartiallyUpdateable.class)) {
                throw new IllegalArgumentException("Partial Update not managed");
                //return (T) morphium.createPartiallyUpdateableEntity(ret);
            }
            if (ret instanceof BinarySerializedObject) {
                BinarySerializedObject bso = (BinarySerializedObject) ret;
                BASE64Decoder dec = new BASE64Decoder();
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(dec.decodeBuffer(bso.getB64Data())));
                return (T) in.readObject();
            }
            return (T) ret;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //recursively fill class

    }
    
    private static Map createMap(BasicDBObject dbObject) {
        Map retMap = new HashMap(dbObject);
        if (dbObject != null) {
            for (String n : dbObject.keySet()) {

                if (dbObject.get(n) instanceof BasicDBObject) {
                    Object val = dbObject.get(n);
                    if (((BasicDBObject) val).containsField("class_name") || ((BasicDBObject) val).containsField("className")) {
                        //Entity to map!
                        String cn = (String) ((BasicDBObject) val).get("class_name");
                        if (cn == null) {
                            cn = (String) ((BasicDBObject) val).get("className");
                        }
                        try {
                            Class ecls = Class.forName(cn);
                            Object obj = unmarshall(ecls, (DBObject) dbObject.get(n));
                            if (obj != null) retMap.put(n, obj);
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (((BasicDBObject) val).containsField("_b64data")) {
                        String d = (String) ((BasicDBObject) val).get("_b64data");
                        BASE64Decoder dec = new BASE64Decoder();
                        ObjectInputStream in = null;
                        try {
                            in = new ObjectInputStream(new ByteArrayInputStream(dec.decodeBuffer(d)));
                            Object read = in.readObject();
                            retMap.put(n, read);
                        } catch (IOException | ClassNotFoundException e) {
                            //TODO: Implement Handling
                            throw new RuntimeException(e);
                        }

                    } else {
                        //maybe a map of maps? --> recurse
                        retMap.put(n, createMap((BasicDBObject) val));
                    }
                } else if (dbObject.get(n) instanceof BasicDBList) {
                    BasicDBList lst = (BasicDBList) dbObject.get(n);
                    List mapValue = createList(lst);
                    retMap.put(n, mapValue);
                }
            }
        } else {
            retMap = null;
        }
        return retMap;
    }

    private static List createList(BasicDBList lst) {
        List mapValue = new ArrayList();
        for (Object li : lst) {
            if (li instanceof BasicDBObject) {
                if (((BasicDBObject) li).containsField("class_name") || ((BasicDBObject) li).containsField("className")) {
                    String cn = (String) ((BasicDBObject) li).get("class_name");
                    if (cn == null) {
                        cn = (String) ((BasicDBObject) li).get("className");
                    }
                    try {
                        Class ecls = Class.forName(cn);
                        Object obj = unmarshall(ecls, (DBObject) li);
                        if (obj != null) mapValue.add(obj);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                } else if (((BasicDBObject) li).containsField("_b64data")) {
                    String d = (String) ((BasicDBObject) li).get("_b64data");
                    BASE64Decoder dec = new BASE64Decoder();
                    ObjectInputStream in = null;
                    try {
                        in = new ObjectInputStream(new ByteArrayInputStream(dec.decodeBuffer(d)));
                        Object read = in.readObject();
                        mapValue.add(read);
                    } catch (IOException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }

                } else {


                    mapValue.add(createMap((BasicDBObject) li));
                }
            } else if (li instanceof BasicDBList) {
                mapValue.add(createList((BasicDBList) li));
            } else {
                mapValue.add(li);
            }
        }
        return mapValue;
    }

    @SuppressWarnings({"unchecked", "ConstantConditions"})
    private static void fillList(Field forField, BasicDBList fromDB, List toFillIn, Object containerEntity) {
        for (Object val : fromDB) {
            if (val instanceof BasicDBObject) {
                if (((BasicDBObject) val).containsField("class_name") || ((BasicDBObject) val).containsField("className")) {
                    //Entity to map!
                    String cn = (String) ((BasicDBObject) val).get("class_name");
                    if (cn == null) {
                        cn = (String) ((BasicDBObject) val).get("className");
                    }
                    try {
                        Class ecls = Class.forName(cn);
                        Object um = unmarshall(ecls, (DBObject) val);
                        if (um != null) toFillIn.add(um);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                } else if (((BasicDBObject) val).containsField("_b64data")) {
                    String d = (String) ((BasicDBObject) val).get("_b64data");
                    if (d == null) d = (String) ((BasicDBObject) val).get("b64Data");
                    BASE64Decoder dec = new BASE64Decoder();
                    ObjectInputStream in = null;
                    try {
                        in = new ObjectInputStream(new ByteArrayInputStream(dec.decodeBuffer(d)));
                        Object read = in.readObject();
                        toFillIn.add(read);
                    } catch (IOException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }

                } else {

                    if (forField != null && forField.getGenericType() instanceof ParameterizedType) {
                        //have a list of something
                        ParameterizedType listType = (ParameterizedType) forField.getGenericType();
                        while (listType.getActualTypeArguments()[0] instanceof ParameterizedType) {
                            listType = (ParameterizedType) listType.getActualTypeArguments()[0];
                        }
                        Class cls = (Class) listType.getActualTypeArguments()[0];
                        Entity entity = annotationHelper.getAnnotationFromHierarchy(cls, Entity.class); //(Entity) sc.getAnnotation(Entity.class);
                        Embedded embedded = annotationHelper.getAnnotationFromHierarchy(cls, Embedded.class);//(Embedded) sc.getAnnotation(Embedded.class);
                        if (entity != null || embedded != null)
                            val = unmarshall(cls, (DBObject) val);
                    }
                    //Probably an "normal" map
                    toFillIn.add(val);
                }
            } else if (val instanceof ObjectId) {
                if (forField.getGenericType() instanceof ParameterizedType) {
                    throw new IllegalArgumentException("ParametrizedType not managed: "+val.toString());
                    //have a list of something
                    /*ParameterizedType listType = (ParameterizedType) forField.getGenericType();
                    Class cls = (Class) listType.getActualTypeArguments()[0];
                    Query q = morphium.createQueryFor(cls);
                    q = q.f(annotationHelper.getFields(cls, Id.class).get(0)).eq(val);
                    toFillIn.add(q.get());
                            */
                } else {
                    logger.warn("Cannot de-reference to unknown collection - trying to add Object only");
                    toFillIn.add(val);
                }

            } else if (val instanceof BasicDBList) {
                //list in list
                ArrayList lt = new ArrayList();
                fillList(forField, (BasicDBList) val, lt, containerEntity);
                toFillIn.add(lt);
            } else if (val instanceof DBRef) {
                throw new IllegalArgumentException("Referenced not managed");
            } else {
                toFillIn.add(val);
            }
        }
    }
    public static JSONArray toJSON(Collection<? extends JSONSerializable> coll) {
        JSONArray res = new JSONArray();
        for (JSONSerializable j : coll) res.add(j.toJSONEntry());
        return res;
    }
    public static void fromJSON(List<? extends JSONSerializable> list, JSONArray json) {
        if (list.size()!=json.size()) throw new IllegalArgumentException("Invalid size: list is:"+list.size()+ " JSON is: "+json.size());
        for (int i =0;i<list.size(); ++i) list.get(i).initFromJSONEntry(json.get(i));
    }
}
