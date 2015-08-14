/*
 * Copyright (C) 2015 nasique
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
package dataStructure.objects.userInterface;

import static dataStructure.objects.userInterface.ImageDisplayerFactory.ImageDisplayerType.IJ;

/**
 *
 * @author nasique
 */
public class ImageDisplayerFactory {
    private static ImageDisplayer currentImageDisplayer;
    public static enum ImageDisplayerType {IJ};
    private static ImageDisplayerType currentImageDisplayerType = IJ;
    public static ImageDisplayer getImageDisplayer() {
        if (currentImageDisplayer==null) {
            if (currentImageDisplayerType.equals(IJ)) currentImageDisplayer = new IJImageDisplayer();
        }
        return currentImageDisplayer;
    }
}
