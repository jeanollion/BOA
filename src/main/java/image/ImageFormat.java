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

/**
 *
 * @author jollion
 */
public enum ImageFormat {
        PNG(".png", true, false, false),
        TIF(".tif", false, true, false), // si on n'écrit pas avec le writer de bio-formats
        OMETIF(".ome.tiff", false, true, true);

        final private String extension;
        final private boolean invertTZ;
        final private boolean view;
        final private boolean allowMultipleTimeAndChannel;
        ImageFormat(String extension, boolean invertTZ, boolean view, boolean allowMutlipleTimeAndChannel) {
            this.extension=extension;
            this.invertTZ=invertTZ;
            this.view=view;
            this.allowMultipleTimeAndChannel=allowMutlipleTimeAndChannel;
        }
        public static ImageFormat getExtension(String extension) {
            for (ImageFormat e : ImageFormat.values()) if (e.getExtension().equals(extension)) return e;
            return null;
        }
        public String getExtension() {
            return extension;
        }
        public boolean getInvertTZ() {
            return invertTZ;
        }
        public boolean getSupportView() {
            return view;
        }
        public boolean getSupportMultipleTimeAndChannel() {
            return this.allowMultipleTimeAndChannel;
        }
        @Override public String toString() {return extension;}
    }
