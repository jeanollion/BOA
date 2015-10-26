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

import boa.gui.imageInteraction.IJImageDisplayer;
import static dataStructure.objects.Object3D.logger;
import static ij.process.AutoThresholder.Method.Otsu;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageInt;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageOperations;
import image.ImageProperties;
import image.ImageShort;
import image.ObjectFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import static plugins.plugins.thresholders.IJAutoThresholder.runThresholder;
import static plugins.plugins.thresholders.IJAutoThresholder.runThresholder;
import static plugins.plugins.thresholders.IJAutoThresholder.runThresholder;
import plugins.plugins.trackers.ObjectIdxTracker.IndexingOrder;
import processing.Filters;
import processing.WatershedTransform;

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
        if (objects!=null) {
            this.objects = objects;
        } else this.objects = new ArrayList<Object3D>();
        this.properties=properties;
    }
    
    public ObjectPopulation addObjects(Object3D... objects) {
        this.objects.addAll(Arrays.asList(objects));
        return this;
    }
    
    public ObjectPopulation addObjects(ArrayList<Object3D> objects) {
        this.objects.addAll(objects);
        return this;
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
        if (objects==null || objects.isEmpty()) labelImage = ImageInteger.createEmptyLabelImage("labelImage", 0, getImageProperties());
        else {
            labelImage = ImageInteger.createEmptyLabelImage("labelImage", objects.size(), getImageProperties());
            //logger.debug("creating image: properties: {} imagetype: {} number of objects: {}", properties, labelImage.getClass(), objects.size());
            for (Object3D o : objects) {
                o.draw(labelImage, o.getLabel());
            }
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
                BoundingBox box = new BoundingBox();
                for (Object3D o : objects) box.expand(o.getBounds());
                properties = box.getImageProperties(); 
            }
        }
        return properties;
    }
    
    public void relabel() {
        int idx = 1;
        if (hasImage()) {
            ImageOperations.fill(labelImage, 0, null);
            for (Object3D o : getObjects()) {
                o.label = idx++;
                o.draw(labelImage, o.label);
            }
        } else {
            for (Object3D o : getObjects()) o.label = idx++;
        }
    }
    
    public void addOffset(int offsetX, int offsetY, int offsetZ) {
        for (Object3D o : objects) o.addOffset(offsetX, offsetY, offsetZ);
    }
    
    public void addOffset(BoundingBox bounds) {
        for (Object3D o : objects) o.addOffset(bounds);
    }
    
    /*public void fitToEdges(Image edgeMap, ImageMask mask) {
        // 1st pass: increase foreground
        WatershedTransform wt = new WatershedTransform(edgeMap, mask, objects, false, null, null);
        wt.setPropagationCriterion(wt.new MonotonalPropagation());
        wt.run();
        ObjectPopulation pop =  wt.getObjectPopulation();
        this.objects=pop.getObjects();
        //set labelImage
        if (pop.hasImage()) this.labelImage=pop.getLabelImage();
        else this.labelImage=null;
        // 2nd pass: increase background
        ImageInteger background = ImageOperations.xor(getLabelImage(), mask, null);
        wt = new WatershedTransform(edgeMap, mask, ImageLabeller.labelImageList(background), false, null, null);
        wt.setPropagationCriterion(wt.new MonotonalPropagation());
        wt.run();
        ImageInteger segmentedMap = wt.getLabelImage();
        for (Object3D o : objects) {
            Iterator<Voxel> it = o.getVoxels().iterator();
            while(it.hasNext()) {
                Voxel v = it.next();
                if (segmentedMap.insideMask(v.x, v.y, v.z)) it.remove();
            }
        }
        this.labelImage=null; //reset labelImage
    }*/
    public void fitToEdges(Image edgeMap, ImageMask mask) {
        // get seeds outsit label image
        ImageInteger seedMap = Filters.localExtrema(edgeMap, null, false, Filters.getNeighborhood(1, 1, edgeMap));
        this.getLabelImage(); //creates the labelImage        
        // merge background seeds && foreground seeds : background = 1, foreground = label+1
        for (int z = 0; z<seedMap.getSizeZ(); z++) {
            for (int xy = 0; xy<seedMap.getSizeXY(); xy++) {
                if (seedMap.insideMask(xy, z)) {
                    if (mask.insideMask(xy, z)) seedMap.setPixel(xy, z, labelImage.getPixelInt(xy, z)+1);
                    else seedMap.setPixel(xy, z, 0);
                }
            }
        }
        ArrayList<Object3D> seeds = new ArrayList<Object3D>(Arrays.asList(ObjectFactory.getObjectsImage(seedMap, false))); 
        ObjectPopulation pop = WatershedTransform.watershed(edgeMap, mask, seeds, false, null, null);
        this.objects = pop.getObjects();
        objects.remove(0); // remove background object
        this.labelImage=null;
    }
    
    public void localThreshold(Image intensity, int marginX, int marginY, int marginZ) {
        ImageInteger background = ImageOperations.xor(getLabelImage(), new BlankMask("", properties), null);
        for (Object3D o : objects) {
            o.draw(background, 1);
            BoundingBox bounds = o.getBounds().duplicate();
            bounds.expandX(bounds.getxMin()-marginX); bounds.expandX(bounds.getxMax()+marginX);
            bounds.expandY(bounds.getyMin()-marginY); bounds.expandY(bounds.getyMax()+marginY);
            bounds.expandZ(bounds.getzMin()-marginZ); bounds.expandZ(bounds.getzMax()+marginZ);
            bounds = bounds.getIntersection(intensity.getBoundingBox().translateToOrigin());
            double thld = runThresholder(intensity, background, bounds, Otsu, 1);
            logger.debug("local threshold: object: {}, thld: {} with bounds: {}, thld with bcg bounds: {}, 2: {}", o.getLabel(), thld, bounds, runThresholder(intensity, background, bounds, Otsu, 1), runThresholder(intensity, background, bounds, Otsu, 2));
            o.draw(background, 0);
            localThreshold(o, intensity, thld);
        }
    }
    
    protected void localThreshold(Object3D o, Image intensity, double threshold) {
        Iterator<Voxel> it = o.getVoxels().iterator();
        while(it.hasNext()) {
            Voxel v = it.next();
            if (intensity.getPixel(v.x, v.y, v.z)<threshold) {
                it.remove();
                if (hasImage()) labelImage.setPixel(v.x, v.y, v.z, 0);
            }
        }
    }
    
    public void filter(Filter filter) {
        filter(filter, null);
    }
    
    public void filter(Filter filter, ArrayList<Object3D> removedObjects) {
        //int objectNumber = objects.size();
        Iterator<Object3D> it = objects.iterator();
        while(it.hasNext()) {
            Object3D o = it.next();
            if (!filter.keepObject(o)) {
                it.remove();
                if (removedObjects!=null) removedObjects.add(o);
            }
        }
        //logger.debug("filter: {}, total object number: {}, remaning objects: {}", filter.getClass().getSimpleName(), objectNumber, objects.size());
    }
    
    public void keepOnlyLargestObject() {
        if (objects.isEmpty()) return;
        int maxIdx = 0;
        int maxSize = objects.get(0).getVoxels().size();
        for (int i = 1; i<objects.size(); ++i) if (objects.get(i).getVoxels().size()>maxSize) {
            maxSize = objects.get(i).getVoxels().size();
            maxIdx = i;
        }
        ArrayList<Object3D> objectsTemp = new ArrayList<Object3D>(1);
        objectsTemp.add(objects.get(maxIdx));
        objects = objectsTemp;
    }
    
    public void sortBySpatialOrder(final IndexingOrder order) {
        Comparator<Object3D> comp = new Comparator<Object3D>() {
            @Override
            public int compare(Object3D arg0, Object3D arg1) {
                return compareCenters(getCenterArray(arg0.getBounds()), getCenterArray(arg1.getBounds()), order);
            }
        };
        Collections.sort(objects, comp);
        relabel();
    }
    
    private static double[] getCenterArray(BoundingBox b) {
        return new double[]{b.getXMean(), b.getYMean(), b.getZMean()};
    }
    
    private static int compareCenters(double[] o1, double[] o2, IndexingOrder order) {
        if (o1[order.i1]!=o2[order.i1]) return Double.compare(o1[order.i1], o2[order.i1]);
        else if (o1[order.i2]!=o2[order.i2]) return Double.compare(o1[order.i2], o2[order.i2]);
        else return Double.compare(o1[order.i3], o2[order.i3]);
    }
    
    public static interface Filter {
        public boolean keepObject(Object3D object);
    }
    public static class Thickness implements Filter {
        int tX=-1, tY=-1, tZ=-1;
        public Thickness setX(int minX){
            this.tX=minX;
            return this;
        }
        public Thickness setY(int minY){
            this.tY=minY;
            return this;
        }
        public Thickness setZ(int minZ){
            this.tZ=minZ;
            return this;
        }

        @Override public boolean keepObject(Object3D object) {
            return (tX<0 || object.getBounds().getSizeX()>tX) && (tY<0 || object.getBounds().getSizeY()>tY) && (tZ<0 || object.getBounds().getSizeZ()>tZ);
        }
    }
    public static class Size implements Filter {
        int min=-1, max=-1;
        public Size setMin(int min) {
            this.min=min;
            return this;
        }
        public Size setMax(int max) {
            this.max=max;
            return this;
        }

        @Override public boolean keepObject(Object3D object) {
            int size = object.getVoxels().size();
            return (min<0 || size>=min) && (max<0 || size<max);
        }
    }
}
