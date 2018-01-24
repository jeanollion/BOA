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
package boa.data_structure.input_image;

import boa.data_structure.dao.ImageDAO;
import boa.data_structure.image_container.MultipleImageContainer;
import boa.gui.imageInteraction.IJImageDisplayer;
import static boa.core.Processor.logger;
import boa.image.BlankMask;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageShort;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import boa.plugins.Transformation;
import boa.plugins.TransformationTimeIndependent;

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
    
    public InputImage duplicate() {
        InputImage res = new InputImage(channelIdx, inputTimePoint, timePoint, microscopyFieldName, imageSources, dao);
        if (image!=null) {
            res.image = image.duplicate();
            res.originalImageType=originalImageType.duplicate();
        }
        return res;
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
                if (image==null) throw new RuntimeException("Image not found: position:"+microscopyFieldName+" channel:"+channelIdx+" frame:"+timePoint);
                originalImageType = Image.createEmptyImage("source Type", image, new BlankMask("", 0, 0, 0));
            }
        }
        applyTransformations();
        return image;
    }
    
    void deleteFromDAO() {dao.deletePreProcessedImage(channelIdx, timePoint, microscopyFieldName);}
    
    public void flush() {
        if (image!=null) image=null;
        //imageSources.close();
    }
    
    private void applyTransformations() {
        if (transformationsToApply!=null && !transformationsToApply.isEmpty()) {
            synchronized(transformationsToApply) {
                Iterator<Transformation> it = transformationsToApply.iterator();
                //new IJImageDisplayer().showImage(image);
                while(it.hasNext()) {
                    Transformation t = it.next();
                    
                    try {
                        image =t.applyTransformation(channelIdx, timePoint, image);
                    } catch (Exception ex) {
                        logger.debug("Transformation error: ", ex);
                        image= null;
                        return;
                    }
                    //if (this.timePoint==0) logger.debug("after trans: {}, scale: {}", t.getClass().getSimpleName(), image.getScaleXY());
                    it.remove();
                    //new IJImageDisplayer().showImage(image.setName("after: "+t.getClass().getSimpleName()));
                }
            }
        }
        
    }
    
    public void closeImage() { // si modification du bitDepth -> faire la même pour toutes les images. Parfois seulement bruit négatif -> pas besoin
        // cast to initial type
        if (originalImageType!=null && originalImageType.getBitDepth()!=image.getBitDepth()) {
            /*double[] mm = image.getMinAndMax(null);
            if (mm[0]<0) {
                logger.warn("PreprocessedImage Pos:{}/Fr:{}, original bitDepth:{}, has negative values ({}) -> will be trimmed", microscopyFieldName, this.timePoint, originalImageType.getBitDepth(), mm[0]);
                //originalImageType = new ImageFloat("", 0, 0 ,0);
            }
            else if (mm[1]>255 && originalImageType.getBitDepth()==8) {
                logger.warn("PreprocessedImage Pos:{}/Fr:{}, original bitDepth:{}, has high values ({}) -> will be trimmed", microscopyFieldName, this.timePoint, originalImageType.getBitDepth(), mm[1]);
                //if (mm[1]<65535) originalImageType = new ImageShort("", 0, 0 ,0);
                //else originalImageType = new ImageFloat("", 0, 0 ,0);
            } else if (mm[1]<=1) {
                //originalImageType = new ImageFloat("", 0, 0 ,0);
            }*/
            image = TypeConverter.cast(image, originalImageType);
        }
        dao.writePreProcessedImage(image, channelIdx, timePoint, microscopyFieldName);
        image=null;
    }
    
    void setTimePoint(int timePoint) {
        this.timePoint=timePoint;
    }
}
