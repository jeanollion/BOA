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
package dataStructure.containers;

import image.Image;
import java.util.ArrayList;
import java.util.Iterator;
import plugins.TransformationTimeIndependent;

/**
 *
 * @author jollion
 */

public class InputImage {
    MultipleImageContainer imageSources;
    ImageDAO dao;
    int channelIdx, timePoint;
    String microscopyFieldName;
    Image image;
    boolean imageSavedInDAO=false, saveToDAO=true;
    ArrayList<TransformationTimeIndependent> transformationsToApply;
    
    public InputImage(int channelIdx, int timePoint, String microscopyFieldName, MultipleImageContainer imageSources, ImageDAO dao, boolean saveToDAO) {
        this.imageSources = imageSources;
        this.dao = dao;
        this.channelIdx = channelIdx;
        this.timePoint = timePoint;
        this.microscopyFieldName = microscopyFieldName;
        this.saveToDAO=saveToDAO;
        transformationsToApply=new ArrayList<TransformationTimeIndependent>();
    }
    public void addTransformation(TransformationTimeIndependent t) {
        transformationsToApply.add(t);
    }
    
    public Image getImage() {
        if (image!=null) {
            applyTransformations();
            return image;
        }
        else {
            if (imageSavedInDAO) image = dao.openPreProcessedImage(channelIdx, timePoint, microscopyFieldName);
            else image = imageSources.getImage(timePoint, channelIdx);
            applyTransformations();
            return image;
        }
    }
    
    private void applyTransformations() {
        Iterator<TransformationTimeIndependent> it = transformationsToApply.iterator();
        while(it.hasNext()) {
            image = it.next().applyTransformation(image);
            it.remove();
        }
    }
    
    public void closeImage() {
        if (saveToDAO) {
            imageSavedInDAO=true;
            dao.writePreProcessedImage(image, channelIdx, timePoint, microscopyFieldName);
        }
        image=null;
    }
}
