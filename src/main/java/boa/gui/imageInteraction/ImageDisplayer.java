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
import image.Image;

/**
 *
 * @author jollion
 */
public interface ImageDisplayer<T> {
    public static double zoomMagnitude=1;
    public T showImage(Image image, float... displayRange);
    public T getImage(Image image);
    public Image getImage(T image);
    public void updateImageDisplay(Image image, float... displayRange);
    public void updateImageRoiDisplay(Image image);
    public void showImage5D(String title, Image[][] imageTC);
    public BoundingBox getDisplayRange(Image image);
    public void setDisplayRange(BoundingBox bounds, Image image);
    public abstract T getCurrentImage();
}
