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
package boa.gui.imageInteraction;

import boa.gui.GUI;
import static boa.gui.imageInteraction.ImageWindowManagerFactory.ImageEnvironnement.IJ;
import boa.image.Image;

/**
 *
 * @author jollion
 */
public class ImageWindowManagerFactory {
    private static ImageWindowManager currentImageManager;
    public static enum ImageEnvironnement {IJ};
    private final static ImageEnvironnement currentImageDisplayerType = IJ;
    public static ImageWindowManager getImageManager() {
        if (currentImageManager==null) {
            if (currentImageDisplayerType.equals(IJ)) currentImageManager = new IJImageWindowManager(GUI.hasInstance()?GUI.getInstance():null);
        }
        return currentImageManager;
    }
    public static Object showImage(Image image) {
        return getImageManager().getDisplayer().showImage(image);
    }
    public static ImageDisplayer instanciateDisplayer() {
        if (currentImageDisplayerType.equals(IJ)) return new IJImageDisplayer();
        else return null;
    }
}
