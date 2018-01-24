/*
 * Copyright (C) 2016 jollion
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
package boa.misc;

import static boa.test_utils.TestUtils.logger;
import boa.core.Task;
import boa.configuration.experiment.Experiment;
import boa.data_structure.input_image.InputImage;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.dao.MasterDAO;
import boa.image.Image;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class AnalyseImageCorruption {
    public static void main(String[] args) {
        String[] dbList = new String[]{"boa_phase141107wt"}; //boa_phase141113wt //boa_phase141107wt
        List<Map<String, int[]>> errors = new ArrayList<>();
        List<int[]> errorRange = new ArrayList<>();
        for (String db : dbList) {
            Map<String, int[]> corr = geCorruptedFrame(db);
            errors.add(corr);
            // intersect ranges
            int[] firstRanges=null;
            int[] bestRange = new int[]{0,0};
            Iterator<int[]> it = corr.values().iterator();
            while(it.hasNext() && (firstRanges == null || firstRanges.length==1)) firstRanges = it.next();
            for (int i = 0; i<firstRanges.length; ++i) {
                int[] range = i==0? new int[]{-1, firstRanges[0]} : new int[]{firstRanges[i-1], firstRanges[i]};
                it = corr.values().iterator();
                while(it.hasNext()) intersect(range, it.next());
                if (range[1]-range[0]>bestRange[1]-bestRange[0]) bestRange = range;
            }
            /*errorRange.add(corr.values().stream().reduce(new int[]{Integer.MAX_VALUE, -Integer.MAX_VALUE}, (i1, i2) -> {
            if (i2[0]<i1[0]) i1[0]=i2[0]; 
            if (i2[1]>i1[1]) i1[1]=i2[1]; 
            return i1;
            }));*/
            errorRange.add(bestRange);
            logger.debug("db: {}, bestRange: {}, all ranges: {}", db, bestRange, Utils.toStringArray(corr.values().toArray(new int[0][]), arr -> Utils.toStringArray(arr)));
        }
        
        for (int i = 0; i<errors.size(); ++i) logger.debug("db: {}, bestRange: {}, all ranges: {}", dbList[i], errorRange.get(i), Utils.toStringArray(errors.get(i).values().toArray(new int[0][]), arr -> Utils.toStringArray(arr)));
    }
    
    public static Map<String, int[]> geCorruptedFrame(String dbName) {
        /*Map<String, int[]> res = new HashMap<>();
        res.put("A", new int[]{10, 25, 39});
        res.put("B", new int[]{15, 20, 30, 40});
        if (true) return res;*/
        MasterDAO mDAO = new Task(dbName).getDB();
        Experiment xp = mDAO.getExperiment();
        logger.debug("errors for xp: {}", dbName);
        Map<String, int[]> errors = new HashMap<>();
        int positionCount = 0;
        for (String position : xp.getPositionsAsString()) {
            errors.put(position, getCorruptedFrames(xp, position));
            xp.getPosition(position).flushImages(true, true);
            logger.debug("POSITION: {}/{}", ++positionCount, xp.getPositionCount());
            //if (positionCount>0) return errors;
        }
        return errors;
    }
    private static int[] getCorruptedFrames(Experiment xp, String position) {
        InputImages in = xp.getPosition(position).getInputImages();
        List<Integer> corrupted = new ArrayList<>();
        for (int t = 0; t<in.getFrameNumber(); ++t) {
            for (int c = 0; c<in.getChannelNumber(); ++c) {
                try {
                    Image im = in.getImage(c, t);
                    double[] mm = im.getMinAndMax(null);
                    if (mm[0]==0 && mm[1]==0) {
                        logger.debug("void image @p:{}, F:{}, c:{}", position, t, c);
                        corrupted.add(t);
                    }
                } catch(Exception e) {
                    logger.debug("error @p:{}, F:{}, c:{}", position, t, c);
                    corrupted.add(t);
                }
            }
        }
        corrupted.add(in.getFrameNumber());
        in.flush();
        return Utils.toArray(corrupted, false);
    }
    private static void intersect(int[] range, int[] withRanges) {
        if (withRanges.length==1) return; // no range
        if (range[0]>=0) {
            int idxMin=-1; // first point inferior or equal, -1 => frame 0
            while(idxMin+1<withRanges.length && withRanges[idxMin+1]<range[0]) ++idxMin;
            int idxMax = 0; //first point superior or equal
            while(idxMax<withRanges.length-1 && withRanges[idxMax]<range[1]) ++idxMax;
            int[] bestRange = idxMin>=0 ? new int[]{Math.max(range[0], withRanges[idxMin]), Math.min(range[1], withRanges[idxMin+1])} : new int[]{Math.max(range[0], 0), Math.min(range[1], withRanges[0])};
            for (int i = idxMin+1; i<idxMax; ++i) {
                int[] r = new int[]{Math.max(range[0], withRanges[i]), Math.min(range[1], withRanges[i+1])};
                if (r[1]-r[0]>bestRange[1]-bestRange[0]) bestRange= r; 
            }
            range[0] = bestRange[0];
            range[1] = bestRange[1];
        } else {
            range[1] = Math.min(range[1], withRanges[1]);
        }
        
    }
    
}
