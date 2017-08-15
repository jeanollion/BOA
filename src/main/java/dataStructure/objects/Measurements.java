/*
 * Copyright (C) 2015 jollion
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
package dataStructure.objects;


import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import utils.JSONUtils;
import utils.Utils;

/**
 *
 * @author jollion
 */

public class Measurements implements Comparable<Measurements>{
    protected String id;
    protected String positionName;
    protected int frame, structureIdx;
    protected double calibratedTimePoint;
    boolean isTrackHead;
    protected int[] indices;
    protected Map<String, Object> values;
    boolean modifications=false;
    final public static String NA_STRING = "NA";
    public Measurements(StructureObject o) {
        this.id=o.id;
        this.calibratedTimePoint=o.getCalibratedTimePoint();
        this.positionName=o.getPositionName();
        this.frame=o.getFrame();
        this.structureIdx=o.getStructureIdx();
        this.isTrackHead=o.isTrackHead;
        this.values=new HashMap<>();
        updateObjectProperties(o);
    }
    public Measurements(Map json) {
        id = (String)json.get("id");
        structureIdx = ((Number)json.get("sIdx")).intValue();
        frame = ((Number)json.get("frame")).intValue();
        calibratedTimePoint = ((Number)json.get("timePointCal")).doubleValue();
        isTrackHead = (Boolean)json.get("isTh");
        indices = JSONUtils.fromIntArray((JSONArray)json.get("indices"));
        values = JSONUtils.toValueMap((Map)json.get("values"));
    }
    public JSONObject toJSON() {
        JSONObject obj1=new JSONObject();
        obj1.put("id", id);
        obj1.put("frame", frame);
        obj1.put("sIdx", structureIdx);
        obj1.put("timePointCal", calibratedTimePoint);
        obj1.put("isTh", isTrackHead);
        obj1.put("indices", JSONUtils.toJSONArray(indices));
        obj1.put("values", JSONUtils.toJSONObject(values));
        return obj1;
    }
    
    public boolean modified() {return modifications;}
    
    public String getId() {
        return id;
    }

    public String getFieldName() {
        return positionName;
    }

    public int getFrame() {
        return frame;
    }
    
    public double getCalibratedTimePoint() {
        return calibratedTimePoint;
    }

    public int getStructureIdx() {
        return structureIdx;
    }

    public int[] getIndices() {
        return indices;
    }
        
    static String[] getBaseFields() {
        return new String[]{"time_point", "structure_idx", "indices", "is_track_head", "calibrated_time_point"};
    }
    static String[] getReturnedFields(String... measurements) {
        String[] baseReturnedFields = getBaseFields();
        String[] returnedFields = new String[baseReturnedFields.length+measurements.length];
        System.arraycopy(baseReturnedFields, 0, returnedFields, 0, baseReturnedFields.length);
        //logger.debug("getReturned Fields: base length: {}, returnedFields length: {}, measurements length: {}", baseReturnedFields.length,returnedFields.length, measurements.length);
        for (int i = 0; i<measurements.length; ++i) returnedFields[i+baseReturnedFields.length] = "values."+measurements[i];
        return returnedFields;
    }
    
    public void updateObjectProperties(StructureObject o) {
        int[] newIndices = StructureObjectUtils.getIndexTree(o);
        if (!Arrays.equals(newIndices, indices)) {
            this.indices=newIndices;
            modifications=true; // TODO partial update
        }
        if (this.isTrackHead!=o.isTrackHead) {
            this.isTrackHead=o.isTrackHead;
            modifications=true; // TODO partial update
        }
    }
    
    public Object getValue(String name) {
        return values.get(name);
    }
    
    public String getValueAsString(String name) {
        Object o = values.get(name);
        if (o instanceof Number || o instanceof String || o instanceof Boolean) return o.toString();
        else return NA_STRING;
    }
    
    public String getValueAsString(String name, Function<Number, String> numberFormater) {
        return asString(values.get(name), numberFormater);
    }
    public static String asString(Object o, Function<Number, String> numberFormater) {
        if (o instanceof Number) return numberFormater.apply((Number)o);
        else if (o instanceof Boolean) return o.toString();
        else if (o instanceof List) return Utils.toStringList((List<Double>)o,"","","-", oo->numberFormater.apply(oo)).toString();
        else if (o instanceof double[]) return Utils.toStringArray((double[])o, "", "", "-", numberFormater).toString();
        else if (o instanceof String) {
            if ("null".equals(o) || NA_STRING.equals(o)) return NA_STRING;
            else return (String)o;
        } else return NA_STRING;
    }
    
    public void setValue(String key, Number value) {
        synchronized(values) {
            if (value == null || isNA(value)) values.remove(key);
            else values.put(key, value);
            modifications=true;
        }
    }
    private static boolean isNA(Number value) {
        return (value instanceof Double && ((Double)value).isNaN() ||  value instanceof Float && ((Float)value).isNaN());
    }
    public void setValue(String key, String value) {
        synchronized(values) {
            if (value == null) values.remove(key);
            else values.put(key, value);
            modifications=true;
        }
    }
    
    public void setValue(String key, boolean value) {
        synchronized(values) {
            this.values.put(key, value);
            modifications=true;
        }
    }
    
    public void setValue(String key, double[] value) {
        synchronized(values) {
            //this.values.put(key, value);
            this.values.put(key, Arrays.asList(value));
            modifications=true;
        }
    }
    public void setValue(String key, List<Double> value) {
        synchronized(values) {
            this.values.put(key, value);
            modifications=true;
        }
    }
    
    public int compareTo(Measurements o) { // positionName / structureIdx / frame / indices
        int f = positionName.compareTo(o.positionName);
        if (f!=0) return f;
        if (structureIdx<o.structureIdx) return -1;
        else if (structureIdx>o.structureIdx) return 1;
        else {
            //if (indices==null) logger.error("indices null error: {}", this);
            int lMin = Math.min(indices.length, o.indices.length);
            for (int i  = 0; i<lMin; ++i) {
                if (indices[i]<o.indices[i]) return -1;
                if (indices[i]>o.indices[i]) return 1;
            }
            if (indices.length!=o.indices.length) return lMin==indices.length?-1:1;
        }
        return 0;
        
    }
    
    @Override 
    public boolean equals(Object o) {
        if (o instanceof Measurements) {
            Measurements m = (Measurements)o;
            if (!positionName.equals(m.positionName)) return false;
            if (structureIdx!=m.structureIdx) return false;
            if (frame!=m.frame) return false;
            return Arrays.equals(indices, m.indices);
        } else return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + this.positionName.hashCode();
        hash = 83 * hash + this.frame;
        hash = 83 * hash + this.structureIdx;
        hash = 83 * hash + Arrays.hashCode(this.indices);
        return hash;
    }
    @Override public String toString() {
        return "P:"+positionName+"/"+Selection.indicesToString(indices);
    }
    private Measurements(String fieldName, int timePoint, int structureIdx, int[] indices) { // only for getParentMeasurementKey
        this.positionName = fieldName;
        this.frame = timePoint;
        this.structureIdx = structureIdx;
        this.indices = indices;
    }
    
    public Measurements getParentMeasurementKey(int parentOrder) {
        if (indices.length==0) return this;
        if (parentOrder<=0 || parentOrder> indices.length) {
            return null;
            //throw new IllegalArgumentException("parent order should be >0 & <="+indicies.length+ "current value: "+parentOrder);
        } 
        return new Measurements(positionName, frame, structureIdx, Arrays.copyOfRange(indices, 0, indices.length-parentOrder));
    }
    public Map<String, Object> getValues() {return values;}
    
    
}
