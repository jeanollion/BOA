/*
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Jean Ollion
 */
public class FrameRange implements Comparable<FrameRange>{
    int min, max; 
    /**
     * 
     * @param fMin min frame, included
     * @param fMax max frame, included
     */
    public FrameRange(int fMin, int fMax) {
        this.min=fMin;
        this.max= fMax;
    }
    public FrameRange merge(FrameRange other) {
        this.min = Math.min(min, other.min);
        this.max = Math.max(max, other.max);
        return this;
    }
    public boolean overlap(FrameRange other) {
        return overlap(other, 0);
    }
    public boolean overlap(FrameRange other, int tolerance) {
        if (other==null) return false;
        int c = compareTo(other);
        if (c==0) return true;
        if (c==1) return other.overlap(this);
        return max+tolerance>=other.min;
    }
    @Override
    public int compareTo(FrameRange o) {
        int c = Integer.compare(min, o.min);
        if (c!=0) return c;
        return Integer.compare(max, o.max);
    }
    @Override
    public String toString() {
        return "["+min+";"+max+"]";
    }
    public static void mergeOverlappingRanges(List<FrameRange> ranges) {
        mergeOverlappingRanges(ranges, 0);
    }
    public static void mergeOverlappingRanges(List<FrameRange> ranges, int tolerance) {
        if (ranges==null || ranges.size()<=1) return;
        Collections.sort(ranges);
        Iterator<FrameRange> it = ranges.iterator();
        FrameRange prev = it.next();
        while(it.hasNext()) {
            FrameRange cur = it.next();
            if (prev.overlap(cur)) {
                prev.merge(cur);
                it.remove();
            } else prev = cur;
        }
    }
}
