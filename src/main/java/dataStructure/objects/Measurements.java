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

import static core.Processor.logger;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import org.bson.types.ObjectId;
import utils.Utils;

/**
 *
 * @author jollion
 */

@Entity
@Index(value={"structure_idx"})
public class Measurements implements Comparable<Measurements>{
    protected @Id ObjectId id;
    @Transient protected String fieldName;
    protected int timePoint, structureIdx;
    boolean isTrackHead;
    protected int[] indicies;
    protected HashMap<String, Object> values;
    @Transient boolean modifications=false;
    
    public Measurements(StructureObject o) {
        this.fieldName=o.getFieldName();
        this.timePoint=o.getTimePoint();
        this.structureIdx=o.getStructureIdx();
        this.isTrackHead=o.isTrackHead;
        this.values=new HashMap<String, Object>();
        updateObjectProperties(o);
    }

    
    public ObjectId getId() {
        return id;
    }

    public String getFieldName() {
        return fieldName;
    }

    public int getTimePoint() {
        return timePoint;
    }

    public int getStructureIdx() {
        return structureIdx;
    }

    public int[] getIndices() {
        return indicies;
    }
        
    static String[] getBaseFields() {
        return new String[]{"time_point", "structure_idx", "indicies", "is_track_head"};
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
        int[] newIndicies = StructureObjectUtils.getIndexTree(o);
        if (!Arrays.equals(newIndicies, indicies)) {
            this.indicies=StructureObjectUtils.getIndexTree(o);
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
        else return "NaN";
    }
    
    public void setValue(String key, Number value) {
        if (value == null) values.remove(key);
        else values.put(key, value);
        modifications=true;
    }
    
    public void setValue(String key, String value) {
        if (value == null) values.remove(key);
        else values.put(key, value);
        modifications=true;
    }
    
    public void setValue(String key, boolean value) {
        this.values.put(key, value);
        modifications=true;
    }
    
    public void setValue(String key, double[] value) {
        this.values.put(key, value);
        modifications=true;
    }
    
    public int compareTo(Measurements o) { // fieldName / structureIdx / timePoint / indicies
        int f = fieldName.compareTo(o.fieldName);
        if (f!=0) return f;
        if (structureIdx<o.structureIdx) return -1;
        else if (structureIdx>o.structureIdx) return 1;
        else {
            for (int i  = 0; i<indicies.length; ++i) {
                if (indicies[i]<o.indicies[i]) return -1;
                if (indicies[i]>o.indicies[i]) return 1;
            }
        }
        return 0;
        
    }
    
    @Override 
    public boolean equals(Object o) {
        if (o instanceof Measurements) {
            Measurements m = (Measurements)o;
            if (!fieldName.equals(m.fieldName)) return false;
            if (structureIdx!=m.structureIdx) return false;
            if (timePoint!=m.timePoint) return false;
            return Arrays.equals(indicies, m.indicies);
        } else return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + this.fieldName.hashCode();
        hash = 83 * hash + this.timePoint;
        hash = 83 * hash + this.structureIdx;
        hash = 83 * hash + Arrays.hashCode(this.indicies);
        return hash;
    }
    
    private Measurements(String fieldName, int timePoint, int structureIdx, int[] indicies) { // only for getParentMeasurementKey
        this.fieldName = fieldName;
        this.timePoint = timePoint;
        this.structureIdx = structureIdx;
        this.indicies = indicies;
    }
    
    public Measurements getParentMeasurementKey(int parentOrder) {
        if (indicies.length==0) return this;
        if (parentOrder<=0 || parentOrder> indicies.length) {
            return null;
            //throw new IllegalArgumentException("parent order should be >0 & <="+indicies.length+ "current value: "+parentOrder);
        } 
        return new Measurements(fieldName, timePoint, structureIdx, Arrays.copyOfRange(indicies, 0, indicies.length-parentOrder));
    }
    public Object getValues() {return values;}
}
