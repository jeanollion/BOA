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
package boa.plugins.plugins.thresholders;

import static boa.plugins.Plugin.logger;

/**
 *
 * @author jollion
 */
public class Percentage {
    public static int getBinAtPercentage(int[] histo, double proportion) {
        int count = 0;
        for (int i : histo) count+=i;
        double limit = count * proportion;
        count = histo[255];
        int idx = 255;
        while (count < limit && idx > 0) {
            idx--;
            count += histo[idx];
        }
        return idx;
            
    }
    public static double getBinAtPercentageApprox(int[] histo, double proportion) {
        int count = 0;
        for (int i : histo) count+=i;
        double limit = count * proportion;
        
        count = histo[255];
        int idx = 255;
        while (count < limit && idx > 0) {
            idx--;
            count += histo[idx];
        }
        double idxInc = (histo[idx] != 0) ? (count - limit) / (histo[idx]) : 0; //lin approx
        return idx+idxInc;
            
    }
}
