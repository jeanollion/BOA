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
package dataStructure.objects;

import static dataStructure.objects.Object3D.logger;
import image.BlankMask;
import image.BoundingBox;
import image.ImageByte;
import image.ImageInt;
import image.ImageInteger;
import image.ImageProperties;
import image.ImageShort;
import image.ObjectFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

/**
 *
 * @author nasique
 */
public class ObjectPopulation {
    private ImageInteger labelImage;
    private ArrayList<Object3D> objects;
    private ImageProperties properties;
    
    public ObjectPopulation(ImageInteger labelImage) {
        this.labelImage=labelImage;
    }

    /*public ObjectPopulation(ArrayList<Object3D> objects) {
        this.objects = objects;
    }*/
    
    public ObjectPopulation(ArrayList<Object3D> objects, ImageProperties properties) {
        this.objects = objects;
        this.properties=properties;
    }
    
    public ImageInteger getLabelImage() {
        if (labelImage==null) constructLabelImage();
        return labelImage;
    }
    
    public ArrayList<Object3D> getObjects() {
        if (objects==null) constructObjects();
        return objects;
    }
    
    private void constructLabelImage() {
        if (objects==null || objects.isEmpty()) return;
        getImageProperties();
        if (objects.size()<=255) labelImage = new ImageByte("merge", properties);
        else if (objects.size()<=65535) labelImage = new ImageShort("merge", properties);
        else labelImage = new ImageInt("merge", properties);
        logger.debug("creating image: properties: {} imagetype: {} number of objects: {}", properties, labelImage.getClass(), objects.size());
        for (Object3D o : objects) {
            o.draw(labelImage, o.getLabel());
        }
    }
    
    private void constructObjects() {
        Object3D[] obs = ObjectFactory.getObjectsImage(labelImage, false);
        objects = new ArrayList<Object3D>(Arrays.asList(obs));
    }
    
    public void eraseObject(Object3D o, boolean eraseInList) {
        if (labelImage!=null) o.draw(labelImage, 0);
        if (eraseInList && objects!=null) objects.remove(o);
    }
    public boolean hasImage() {return labelImage!=null;}
    
    public ObjectPopulation setProperties(ImageProperties properties, boolean onlyIfSameSize) {
        if (labelImage!=null) {
            if (!onlyIfSameSize || labelImage.sameSize(properties)) labelImage.resetOffset().addOffset(properties);
            labelImage.setCalibration(properties);
        } else this.properties = new BlankMask("", properties); //set aussi la taille de l'image
        return this;
    }
    
    public ImageProperties getImageProperties() {
        if (properties == null) {
            if (labelImage != null) {
                properties = new BlankMask("", labelImage);
            } else if (!objects.isEmpty()) { //unscaled, no offset for label image..
                BoundingBox box = new BoundingBox(0,0,0,0,0,0);
                for (Object3D o : objects) box.expand(o.getBounds());
                properties = box.getImageProperties(); 
            }
        }
        return properties;
    }
    
    public void relabel() {
        int idx = 1;
        if (hasImage()) {
            for (Object3D o : getObjects()) {
                o.label = idx++;
                o.draw(labelImage, o.label);
            }
        } else {
            for (Object3D o : getObjects()) {
                o.label = idx++;
            }
        }
    }
}
