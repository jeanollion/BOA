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
        transformationsToApply=new ArrayList<>();
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
    public boolean imageOpened() {
        return image!=null;
    }
    public Image getImage() {
        if (image == null) {
            synchronized (this) {
                if (image==null) {
                    if (intermediateImageSavedToDAO) image = dao.openPreProcessedImage(channelIdx, timePoint, microscopyFieldName); //try to open from DAO
                    if (image==null) {
                        image = imageSources.getImage(inputTimePoint, channelIdx);
                        if (image==null) throw new RuntimeException("Image not found: position:"+microscopyFieldName+" channel:"+channelIdx+" frame:"+timePoint);
                        originalImageType = Image.createEmptyImage("source Type", image, new BlankMask( 0, 0, 0));
                    }
                }
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
                if (transformationsToApply.isEmpty()) return;
                Iterator<Transformation> it = transformationsToApply.iterator();
                //new IJImageDisplayer().showImage(image);
                while(it.hasNext()) {
                    Transformation t = it.next();
                    image =t.applyTransformation(channelIdx, timePoint, image);
                    it.remove();
                }
            }
        }
    }
    
    public void saveImage() { // si modification du bitDepth -> faire la même pour toutes les images. Parfois seulement bruit négatif -> pas besoin
        // cast to initial type
        if (originalImageType!=null && originalImageType.getBitDepth()!=image.getBitDepth()) {
            image = TypeConverter.cast(image, originalImageType);
        }
        dao.writePreProcessedImage(image, channelIdx, timePoint, microscopyFieldName);
    }
    
    void setTimePoint(int timePoint) {
        this.timePoint=timePoint;
    }
}
