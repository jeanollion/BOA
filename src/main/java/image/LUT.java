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
package image;

import java.awt.image.IndexColorModel;

/**
 *
 * @author jollion
 */
public enum LUT {
        RGB332(1),
        Grays(0);

        final private IndexColorModel icm;

        //Constructeur
        LUT(int LUT) {
            if (LUT == 1) { //332RGB
                byte[] reds = new byte[256];
                byte[] greens = new byte[256];
                byte[] blues = new byte[256];
                for (int i = 0; i < 256; i++) {
                    reds[i] = (byte) (i & 0xe0);
                    greens[i] = (byte) ((i << 3) & 0xe0);
                    blues[i] = (byte) ((i << 6) & 0xc0);
                }
                icm = new IndexColorModel(8, 256, reds, greens, blues);
            } else { // grays
                byte[] reds = new byte[256];
                byte[] greens = new byte[256];
                byte[] blues = new byte[256];
                for (int i = 0; i < 256; i++) {
                    reds[i] = (byte) (i);
                    greens[i] = (byte) i;
                    blues[i] = (byte) i;
                }
                icm = new IndexColorModel(8, 256, reds, greens, blues);
            }
        }

        public IndexColorModel getIndexColorModel() {
            return icm;
        }
    }
