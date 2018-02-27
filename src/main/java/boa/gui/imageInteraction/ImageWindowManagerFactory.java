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
package boa.gui.imageInteraction;
import boa.image.Image;
import ij.ImagePlus;
import java.util.function.Supplier;

/**
 *
 * @author jollion
 */
public class ImageWindowManagerFactory {
    private static ImageWindowManager currentImageManager;
    private static ImageDisplayer imageDisplayer;
    public static <I> void setImageDisplayer(ImageDisplayer<I> imageDisplayer, ImageWindowManager<I, ?, ?> windowsManager) {
        ImageWindowManagerFactory.imageDisplayer=imageDisplayer;
        currentImageManager=windowsManager;
    }
    public static ImageWindowManager getImageManager() {
        if (currentImageManager==null) currentImageManager = new IJImageWindowManager(null, getDisplayer());
        return currentImageManager;
    }
    public static Object showImage(Image image) {
        return imageDisplayer.showImage(image);
    }
    public static Object showImage5D(String title, Image[][] imageTC) {
        return getDisplayer().showImage5D(title, imageTC);
    }
    private static ImageDisplayer getDisplayer() {
        if (imageDisplayer==null) imageDisplayer = new IJImageDisplayer();
        return imageDisplayer;
    }
}
