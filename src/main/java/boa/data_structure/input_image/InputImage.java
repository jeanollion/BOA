/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.data_structure.input_image;

import boa.data_structure.dao.ImageDAO;
import boa.data_structure.image_container.MultipleImageContainer;
import boa.gui.image_interaction.IJImageDisplayer;
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
import java.util.stream.IntStream;

/**
 *
 * @author Jean Ollion
 */

public class InputImage {
    MultipleImageContainer imageSources;
    ImageDAO dao;
    int channelIdx, frame, inputTimePoint;
    String microscopyFieldName;
    Image originalImageType;
    Image image;
    boolean intermediateImageSavedToDAO=false, modified=false;
    ArrayList<Transformation> transformationsToApply;
    
    public InputImage(int channelIdx, int inputTimePoint, int timePoint, String microscopyFieldName, MultipleImageContainer imageSources, ImageDAO dao) {
        this.imageSources = imageSources;
        this.dao = dao;
        this.channelIdx = channelIdx;
        this.frame = timePoint;
        this.inputTimePoint=inputTimePoint;
        this.microscopyFieldName = microscopyFieldName;
        transformationsToApply=new ArrayList<>();
    }
    public boolean modified() {
        return modified;
    }
    public InputImage duplicate() {
        InputImage res = new InputImage(channelIdx, inputTimePoint, frame, microscopyFieldName, imageSources, dao);
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
                    if (intermediateImageSavedToDAO) image = dao.openPreProcessedImage(channelIdx, frame, microscopyFieldName); //try to open from DAO
                    if (image==null) {
                        image = imageSources.getImage(inputTimePoint, channelIdx);
                        if (image==null) throw new RuntimeException("Image not found: position:"+microscopyFieldName+" channel:"+channelIdx+" frame:"+frame);
                        originalImageType = Image.createEmptyImage("source Type", image, new BlankMask( 0, 0, 0));
                    }
                }
            }
        }
        applyTransformations();
        return image;
    }
    
    void deleteFromDAO() {dao.deletePreProcessedImage(channelIdx, frame, microscopyFieldName);}
    
    public void flush() {
        if (image!=null) image=null;
        //imageSources.close();
    }
    
    private void applyTransformations() {
        if (transformationsToApply!=null && !transformationsToApply.isEmpty()) {
            synchronized(transformationsToApply) {
                if (transformationsToApply.isEmpty()) return;
                modified=true;
                Iterator<Transformation> it = transformationsToApply.iterator();
                while(it.hasNext()) {
                    Transformation t = it.next();
                    image =t.applyTransformation(channelIdx, frame, image);
                    it.remove();
                }
            }
        }
    }
    
    public void saveImage() { 
        dao.writePreProcessedImage(image, channelIdx, frame, microscopyFieldName);
    }
    
    void setTimePoint(int timePoint) {
        this.frame=timePoint;
    }
}
