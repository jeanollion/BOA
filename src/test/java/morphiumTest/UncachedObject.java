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
package morphiumTest;

import com.mongodb.ReadPreference;
import de.caluga.morphium.annotations.DefaultReadPreference;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.annotations.PartialUpdate;
import de.caluga.morphium.annotations.ReadPreferenceLevel;
import de.caluga.morphium.annotations.SafetyLevel;
import de.caluga.morphium.annotations.WriteSafety;
import de.caluga.morphium.annotations.caching.NoCache;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
@NoCache
@Entity
@WriteSafety(waitForJournalCommit = false, waitForSync = true, timeout = 3000, level = SafetyLevel.WAIT_FOR_ALL_SLAVES)
@DefaultReadPreference(ReadPreferenceLevel.NEAREST)
public class UncachedObject {
    @Index
    private String value;

    @Index
    private int counter;

    private double dval;

    private byte[] binaryData;
    private int[] intData;
    private long[] longData;
    private float[] floatData;
    private double[] doubleData;
    private boolean[] boolData;

    @Id
    private ObjectId mongoId;

    public double getDval() {
        return dval;
    }

    public void setDval(double dval) {
        this.dval = dval;
    }

    public double[] getDoubleData() {
        return doubleData;
    }

    public void setDoubleData(double[] doubleData) {
        this.doubleData = doubleData;
    }

    public int[] getIntData() {
        return intData;
    }

    public void setIntData(int[] intData) {
        this.intData = intData;
    }

    public long[] getLongData() {
        return longData;
    }

    public void setLongData(long[] longData) {
        this.longData = longData;
    }

    public float[] getFloatData() {
        return floatData;
    }

    public void setFloatData(float[] floatData) {
        this.floatData = floatData;
    }

    public boolean[] getBoolData() {
        return boolData;
    }

    public void setBoolData(boolean[] boolData) {
        this.boolData = boolData;
    }

    public byte[] getBinaryData() {
        return binaryData;
    }

    public void setBinaryData(byte[] binaryData) {
        this.binaryData = binaryData;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    @PartialUpdate("value")
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }


    public ObjectId getMongoId() {
        return mongoId;
    }

    public void setMongoId(ObjectId mongoId) {
        this.mongoId = mongoId;
    }

    public String toString() {
        return "Counter: " + counter + " Value: " + value + " MongoId: " + mongoId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UncachedObject that = (UncachedObject) o;

        if (counter != that.counter) return false;
        return !(mongoId != null ? !mongoId.equals(that.mongoId) : that.mongoId != null) && !(value != null ? !value.equals(that.value) : that.value != null);

    }

    @Override
    public int hashCode() {
        int result = value != null ? value.hashCode() : 0;
        result = 31 * result + counter;
        result = 31 * result + (mongoId != null ? mongoId.hashCode() : 0);
        return result;
    }


    public enum Fields {counter, binaryData, intData, longData, floatData, doubleData, boolData, mongoId, value}
}
