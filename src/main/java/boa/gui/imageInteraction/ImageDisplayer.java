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

import image.BoundingBox;
import static image.IJImageWrapper.getStackIndex;
import image.Image;
import static image.Image.logger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 *
 * @author jollion
 */
public interface ImageDisplayer<T> {
    public static double zoomMagnitude=1;
    public T showImage(Image image, double... displayRange);
    public T getImage(Image image);
    public Image getImage(T image);
    public void updateImageDisplay(Image image, double... displayRange);
    public void updateImageRoiDisplay(Image image);
    public T showImage5D(String title, Image[][] imageTC);
    public BoundingBox getDisplayRange(Image image);
    public void setDisplayRange(BoundingBox bounds, Image image);
    public T getCurrentImage();
    public Image getCurrentImage2();
    public Image[][] getCurrentImageCT();
    public void flush();
    //public int[] getFCZCount(T image);
    //public boolean isVisible(Image image);
    //public Image[][] reslice(Image image, int[] FCZCount);
    
    static Image[][] reslice(Image image, int[] FCZCount, Function<int[], Integer> getStackIndex) {
        if (image.getSizeZ()!=FCZCount[0]*FCZCount[1]*FCZCount[2]) {
            ImageWindowManagerFactory.showImage(image.setName("slices: "+image.getSizeZ()));
            throw new IllegalArgumentException("Wrong number of images ("+image.getSizeZ()+" instead of "+FCZCount[0]*FCZCount[1]*FCZCount[2]);
        }
        logger.debug("reslice: FCZ:{}", FCZCount);
        Image[][] resTC = new Image[FCZCount[1]][FCZCount[0]];
        for (int f = 0; f<FCZCount[0]; ++f) {
            for (int c = 0; c<FCZCount[1]; ++c) {
                List<Image> imageSlices = new ArrayList<>(FCZCount[2]);
                for (int z = 0; z<FCZCount[2]; ++z) {
                    imageSlices.add(image.getZPlane(getStackIndex.apply(new int[]{f, c, z})));
                }
                resTC[c][f] = Image.mergeZPlanes(imageSlices);
            }
        }
        return resTC;
    }
}
