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
package MutationTracker;

import static TestUtils.Utils.logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static junit.framework.Assert.assertEquals;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import static org.junit.Assert.assertArrayEquals;
import org.junit.Before;
import org.junit.Test;
import plugins.plugins.trackers.trackMate.TrackLikelyhoodEstimator;
import plugins.plugins.trackers.trackMate.TrackLikelyhoodEstimator.Track;

/**
 *
 * @author jollion
 */
public class TestTrackLikelyhoodEstimator {
    TrackLikelyhoodEstimator estimator;
    static double distance = 0.005;
    @Before
    public void setUp() {
        estimator = new TrackLikelyhoodEstimator(new NormalDistribution(11.97, 1.76), new BetaDistribution(0.735, 12.69), 7);
    }

    //@Test
    public void testNoSplit() {
        int[] frames = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[][] expected = new int[][]{frames};
        assertMatrix(frames, expected);
    }
    //@Test
    public void test1SplitSymetrical() {
        int[] frames = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        int[][] expected = new int[][]{new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, new int[]{10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20}};
        assertMatrix(frames, expected);
    }
    //@Test
    public void test1SplitSymetricalWithGaps() {
        int[] frames = new int[]{0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22};
        int[][] expected = new int[][]{new int[]{0, 2, 4, 6, 8, 10}, new int[]{12, 14, 16, 18, 20, 22}};
        assertMatrix(frames, expected);
    }
    //@Test
    public void test2SplitSymetrical() {
        int[] frames = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35};
        int[][] expected = new int[][]{new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, new int[]{12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23}, new int[]{24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35}};
        assertMatrix(frames, expected);
    }
    @Test
    public void test2SplitSymetricalWithGaps() {
        int[] frames = new int[]{0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34};
        int[][] expected = new int[][]{new int[]{0, 2, 4, 6, 8, 10}, new int[]{12, 14, 16, 18, 20, 22}, new int[]{24, 26, 28, 30, 32, 34}};
        logger.debug("density for: 0.1={}, 0.01={}, 0.005={}", estimator.getDistanceDistribution().density(0.1), estimator.getDistanceDistribution().density(0.01), estimator.getDistanceDistribution().density(0.005));
        logger.debug("s1: d:{}, l1: {}, l2: {}", Math.pow(estimator.getDistanceDistribution().density(distance), frames.length-2), estimator.getLengthDistribution().density(frames[8]-frames[0]), estimator.getLengthDistribution().density(frames[frames.length-1]-frames[9]));
        logger.debug("s2: d:{}, l1: {}, l2: {}, l3: {}", Math.pow(estimator.getDistanceDistribution().density(distance), frames.length-3), estimator.getLengthDistribution().density(frames[5]-frames[0]), estimator.getLengthDistribution().density(frames[11]-frames[6]), estimator.getLengthDistribution().density(frames[frames.length-1]-frames[12]));
        assertMatrix(frames, expected);
    }
    //@Test
    public void test1SplitAsymetricalDistance() {
        int[] frames = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21};
        double[] distances = getDistanceArray(distance, frames.length);
        distances[10]=10*distance;
        int[][] expected = new int[][]{new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, new int[]{10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21}};
        assertMatrix(frames, expected);
    }
    
    private void assertMatrix(int[] frames, int[][] expected) {
        assertMatrix(frames, getDistanceArray(distance, frames.length), expected);
    }
    
    private void assertMatrix(int[] frames, double[] distances, int[][] expected) {
        int[][] observed = toMatrix(estimator.splitTrack(new Track(frames, distances)).splitTrack(toList(frames)));
        assertEquals("same division number:", expected.length, observed.length);
        for (int i = 0; i<expected.length; ++i) {
            logger.debug("observed array: {}", observed[i]);
            assertArrayEquals(" frames @"+i, expected[i], observed[i]);
        }
    }
    
    private static double[] getDistanceArray(double d, int frameNumber) {
        double[] res= new double[frameNumber-1];
        Arrays.fill(res, d);
        return res;
    }
    private List<Integer> toList(int[] array) {
        List<Integer> res = new ArrayList<Integer>(array.length);
        for (int i : array) res.add(i);
        return res;
    }
    private int[] toPrimitive(List<Integer> list) {
        int[] res = new int[list.size()];
        for (int i = 0; i<res.length; ++i) res[i] = list.get(i);
        return res;
    }
    private int[][] toMatrix(List<List<Integer>> list) {
        int[][] res = new int[list.size()][];
        for (int i = 0; i<list.size(); ++i) {
            res[i] = toPrimitive(list.get(i));
        }
        return res;
    }
}
