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
    int channelIdx, timePoint, inputTimePoint;
    String microscopyFieldName;
    Image originalImageType;
    Image image;
    boolean intermediateImageSavedToDAO=false;
    ArrayList<Transformation> transformationsToApply;
    
    public InputImage(int channelIdx, int inputTimePoint, int timePoint, String microscopyFieldName, MultipleImageContainer imageSources, ImageDAO dao) {
        this.imageSources = imageSources;
        this.dao = dao;
        this.channelIdx = channelIdx;
        this.timePoint = timePoint;
        this.inputTimePoint=inputTimePoint;
        this.microscopyFieldName = microscopyFieldName;
        transformationsToApply=new ArrayList<Transformation>();
    }
    
    public void addTransformation(Transformation t) {
        transformationsToApply.add(t);
    }
    
    public MultipleImageContainer duplicateContainer() {
        return imageSources.duplicate();
    }
    
    
    
    /*public Image getImage(MultipleImageContainer container) {
        if (image == null) {
            if (intermediateImageSavedToDAO) image = dao.openPreProcessedImage(channelIdx, timePoint, microscopyFieldName); //try to open from DAO
            if (image==null) {
                image = container.getImage(inputTimePoint, channelIdx);
                originalImageType = Image.createEmptyImage("source Type", image, new BlankMask("", 0, 0, 0));
            }
        }
        applyTransformations();
        return image;
    } */
    
    public Image getImage() {
        if (image == null) {
            if (intermediateImageSavedToDAO) image = dao.openPreProcessedImage(channelIdx, timePoint, microscopyFieldName); //try to open from DAO
            if (image==null) {
                image = imageSources.getImage(inputTimePoint, channelIdx);
                originalImageType = Image.createEmptyImage("source Type", image, new BlankMask("", 0, 0, 0));
            }
        }
        applyTransformations();
        return image;
    }
    
    void deleteFromDAO() {dao.deletePreProcessedImage(channelIdx, timePoint, microscopyFieldName);}
    
    public void flush() {
        if (image!=null) image=null;
    }
    
    private void applyTransformations() {
        if (transformationsToApply!=null && !transformationsToApply.isEmpty()) {
            synchronized(transformationsToApply) {
                Iterator<Transformation> it = transformationsToApply.iterator();
                //new IJImageDisplayer().showImage(image);
                while(it.hasNext()) {
                    Transformation t = it.next();
                    image =t.applyTransformation(channelIdx, timePoint, image);
                    //if (this.timePoint==0) logger.debug("after trans: {}, scale: {}", t.getClass().getSimpleName(), image.getScaleXY());
                    it.remove();
                    //new IJImageDisplayer().showImage(image.setName("after: "+t.getClass().getSimpleName()));
                }
            }
        }
        
    }
    
    public void closeImage() {
        // cast to initial type
        if (originalImageType!=null) image = TypeConverter.cast(image, originalImageType);
        dao.writePreProcessedImage(image, channelIdx, timePoint, microscopyFieldName);
        image=null;
    }
    
    void setTimePoint(int timePoint) {
        this.timePoint=timePoint;
    }
}
