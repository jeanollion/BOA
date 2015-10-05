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

import boa.gui.imageInteraction.IJImageDisplayer;
import static core.Processor.logger;
import image.BlankMask;
import image.Image;
import image.TypeConverter;
import java.util.ArrayList;
import java.util.Iterator;
import plugins.Transformation;
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
    Image originalImageType;
    Image image;
    boolean saveToDAO=true;
    ArrayList<Transformation> transformationsToApply;
    
    public InputImage(int channelIdx, int timePoint, String microscopyFieldName, MultipleImageContainer imageSources, ImageDAO dao, boolean saveToDAO) {
        this.imageSources = imageSources;
        this.dao = dao;
        this.channelIdx = channelIdx;
        this.timePoint = timePoint;
        this.microscopyFieldName = microscopyFieldName;
        this.saveToDAO=saveToDAO;
        transformationsToApply=new ArrayList<Transformation>();
    }
    public void addTransformation(Transformation t) {
        transformationsToApply.add(t);
    }
    
    public Image getImage() {
        if (image == null) {
            //image = dao.openPreProcessedImage(channelIdx, timePoint, microscopyFieldName); //try to open from DAO
            if (image==null) {
                image = imageSources.getImage(timePoint, channelIdx);
                originalImageType = Image.createEmptyImage("source Type", image, new BlankMask("", 0, 0, 0));
            }
        }
        applyTransformations();
        return image;
    }
    
    void deleteFromDAO() {dao.deletePreProcessedImage(channelIdx, timePoint, microscopyFieldName);}
    
    
    private void applyTransformations() {
        Iterator<Transformation> it = transformationsToApply.iterator();
        //new IJImageDisplayer().showImage(image);
        while(it.hasNext()) {
            image = it.next().applyTransformation(channelIdx, timePoint, image);
            it.remove();
            //new IJImageDisplayer().showImage(image);
        }
    }
    
    public void closeImage() {
        if (saveToDAO) {
            // cast to initial type
            if (originalImageType!=null) image = TypeConverter.cast(image, originalImageType);
            dao.writePreProcessedImage(image, channelIdx, timePoint, microscopyFieldName);
        }
        image=null;
    }
}
