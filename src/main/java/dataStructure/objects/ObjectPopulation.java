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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import measurement.BasicMeasurements;
import plugins.ObjectFeature;
import static plugins.plugins.thresholders.IJAutoThresholder.runThresholder;
import static plugins.plugins.thresholders.IJAutoThresholder.runThresholder;
import static plugins.plugins.thresholders.IJAutoThresholder.runThresholder;
import plugins.plugins.trackers.ObjectIdxTracker.IndexingOrder;
import processing.Filters;
import processing.WatershedTransform;
import processing.neighborhood.EllipsoidalNeighborhood;
import processing.neighborhood.Neighborhood;
import utils.HashMapGetCreate;

/**
 *
 * @author nasique
 */
public class ObjectPopulation {

    private ImageInteger labelImage;
    private List<Object3D> objects;
    private ImageProperties properties;
    private boolean absoluteLandmark=false;
    private boolean lowConnectivity = false;
    /**
     * Creates an empty ObjectPopulation instance
     * @param properties 
     */
    public ObjectPopulation(ImageProperties properties) {
        this.properties = new BlankMask("", properties);
        this.objects=new ArrayList<Object3D>();
    }
    
    /**
     *
     * @param image image with values >0 within segmented objects
     * @param isLabeledImage if true, the image is considered as a labeled
     * image, one value per object, if false, the image will be labeled (and
     * thus modified) by Connected Components Labeling
     */
    public ObjectPopulation(ImageInteger image, boolean isLabeledImage) {
        this.properties = image.getProperties();
        labelImage = image;
        if (!isLabeledImage) {
            objects = new ArrayList<>(ImageLabeller.labelImageList(image));
            relabel(false); // in order to have consistent labels between image & object list
        }
    }
    
    public ObjectPopulation setLabelImage(ImageInteger image, boolean isLabeledImage, boolean lowConnectivity) {
        this.lowConnectivity=lowConnectivity;
        labelImage = image;
        if (!isLabeledImage) {
            objects = new ArrayList<>(lowConnectivity ? ImageLabeller.labelImageListLowConnectivity(image) : ImageLabeller.labelImageList(image));
            relabel(false); // in order to have consistent labels between image & object list
        } else objects = null;
        return this;
    }
    public ObjectPopulation setConnectivity(boolean low) {
        this.lowConnectivity = low;
        return this;
    }

    /*public ObjectPopulation(ArrayList<Object3D> objects) {
     this.objects = objects;
     }*/
    public ObjectPopulation(List<Object3D> objects, ImageProperties properties) {
        if (objects != null) {
            this.objects = objects;
        } else {
            this.objects = new ArrayList<Object3D>();
        }
        if (properties!=null) this.properties = new BlankMask("", properties);
    }
    
    public ObjectPopulation(List<Object3D> objects, ImageProperties properties, boolean absoluteLandmark) {
        if (objects != null) {
            this.objects = objects;
        } else {
            this.objects = new ArrayList<Object3D>();
        }
        if (properties!=null) this.properties = new BlankMask("", properties);
        this.absoluteLandmark=absoluteLandmark;
        for (Object3D o : objects) o.setIsAbsoluteLandmark(absoluteLandmark);
        //checkForDuplicateLabel();
    }
    
    public ObjectPopulation(List<Object3D> objects, ImageInteger labelMap, ImageProperties properties, boolean absoluteLandmark) {
        if (!labelMap.sameSize(properties)) throw new IllegalArgumentException("labelMap and ImageProperties should be of same dimensions");
        if (objects != null) {
            this.objects = objects;
        } else {
            this.objects = new ArrayList<Object3D>();
        }
        if (properties!=null) this.properties = new BlankMask("", properties);
        this.absoluteLandmark=absoluteLandmark;
        for (Object3D o : objects) o.setIsAbsoluteLandmark(absoluteLandmark);
        labelImage = labelMap;
        this.relabel(true);
    }
    
    public boolean checkForDuplicateLabel() {
        for (int i = 0; i<objects.size()-1; ++i) {
            for (int j = i+1; j<objects.size(); ++j) {
                if (objects.get(i).getLabel()==objects.get(j).getLabel()) {
                    //throw new IllegalArgumentException("Duplicate label: idx:"+i+"&"+j+" label:"+objects.get(j).getLabel());
                    return true;
                }
            }
        }
        return false;
    }
    
    public ObjectPopulation duplicate() {
        if (objects!=null) {
            ArrayList<Object3D> ob = new ArrayList<Object3D>(objects.size());
            for (Object3D o : objects) ob.add(o.duplicate());
            return new ObjectPopulation(ob, properties, absoluteLandmark).setConnectivity(lowConnectivity);
        } else if (labelImage!=null) {
            return new ObjectPopulation((ImageInteger)labelImage.duplicate(""), true).setConnectivity(lowConnectivity);
        }
        return new ObjectPopulation(null , properties, absoluteLandmark).setConnectivity(lowConnectivity);
    }
    
    /**
     * 
     * @return null if the objects offset are directly from the labelImage, or the value of the offset
     */
    public BoundingBox getObjectOffset() {
        return properties.getBoundingBox();
    }
    
    public boolean isAbsoluteLandmark() {
        return this.absoluteLandmark;
    }
    
    public ObjectPopulation addObjects(boolean updateLabelImage, Object3D... objects) {
        return addObjects(Arrays.asList(objects), updateLabelImage);
    }
    /**
     * Add objects to the list.
     * @param objects
     * @return 
     */
    public ObjectPopulation addObjects(List<Object3D> objects, boolean updateLabelImage) {
        this.objects.addAll(objects);
        if (updateLabelImage) relabel(true);
        return this;
    }
    
    public ImageInteger getLabelMap() {
        if (labelImage == null) constructLabelImage();
        return labelImage;
    }
    
    public List<Object3D> getObjects() {
        if (objects == null) {
            constructObjects();
        }
        return objects;
    }
    
    private void draw(Object3D o, int label) {
        if (this.absoluteLandmark) o.draw(labelImage, label, new BoundingBox(0, 0, 0)); // in order to remove the offset of the image
        else o.draw(labelImage, label);
    }
    
    private void constructLabelImage() {
        if (objects == null || objects.isEmpty()) {
            labelImage = ImageInteger.createEmptyLabelImage("labelImage", 0, getImageProperties());
        } else {
            labelImage = ImageInteger.createEmptyLabelImage("labelImage", objects.size(), getImageProperties());
            //logger.debug("creating image: properties: {} imagetype: {} number of objects: {}", properties, labelImage.getClass(), objects.size());
            for (Object3D o : objects) draw(o, o.getLabel());
        }
    }
        
    private void constructObjects() {
        Object3D[] obs = ObjectFactory.getObjectsImage(labelImage, false);
        objects = new ArrayList<Object3D>(Arrays.asList(obs));
    }
    
    
    public void eraseObject(Object3D o, boolean eraseInList) {
        if (labelImage != null) {
            draw(o, 0);
        }
        if (eraseInList && objects != null) {
            objects.remove(o);
        }
    }

    public boolean hasImage() {
        return labelImage != null;
    }
    
    public ObjectPopulation setProperties(ImageProperties properties, boolean onlyIfSameSize) {
        if (labelImage != null) {
            if (!onlyIfSameSize || labelImage.sameSize(properties)) {
                labelImage.resetOffset().addOffset(properties);
            }
            labelImage.setCalibration(properties);
            this.properties=  properties.getProperties(); 
        } else {
            this.properties = properties.getProperties(); //set aussi la taille de l'image
        }
        return this;
    }
    
    public ImageProperties getImageProperties() {
        if (properties == null) {
            if (labelImage != null) {
                properties = new BlankMask("", labelImage);
            } else if (objects!=null && !objects.isEmpty()) { //unscaled, no offset for label image..
                BoundingBox box = new BoundingBox();
                for (Object3D o : objects) {
                    box.expand(o.getBounds());
                }
                properties = box.getImageProperties();                
            }
        }
        return properties;
    }
    public void relabel() {relabel(true);}
    public void relabel(boolean fillImage) {
        int idx = 1;
        for (Object3D o : getObjects()) o.label = idx++;
        if (objects == null || objects.isEmpty()) return;
        redrawLabelMap(fillImage);
    }
    public void redrawLabelMap(boolean fillImage) {
        if (hasImage()) {
            int maxLabel = Collections.max(getObjects(), (o1, o2) -> Integer.compare(o1.getLabel(), o2.getLabel())).getLabel();
            if (maxLabel > ImageInteger.getMaxValue(labelImage, false)) {
                labelImage = ImageInteger.createEmptyLabelImage(labelImage.getName(), maxLabel, properties);
            } else {
                if (fillImage) ImageOperations.fill(labelImage, 0, null);
            }
            for (Object3D o : getObjects()) draw(o, o.getLabel());
        }
    }
    
    public void translate(int offsetX, int offsetY, int offsetZ , boolean absoluteLandmark) {
        for (Object3D o : getObjects()) {
            o.translate(offsetX, offsetY, offsetZ);
        }
        this.absoluteLandmark=absoluteLandmark;
    }
    
    public void translate(BoundingBox bounds, boolean absoluteLandmark) {
        for (Object3D o : getObjects()) {
            o.translate(bounds);
            if (absoluteLandmark) o.setIsAbsoluteLandmark(true);
        }
        this.absoluteLandmark=absoluteLandmark;
    }
    
    public void setVoxelIntensities(Image intensityMap) {
        for (Object3D o : getObjects()) {
            for (Voxel v : o.getVoxels()) v.value=intensityMap.getPixel(v.x, v.y, v.z);
        }
    }
    
    public List<Object3D> getExtremaSeedList(final boolean maxOfObjectVoxels) {
        int label=1;
        List<Object3D> seeds = new ArrayList<Object3D>(getObjects().size());
        for (final Object3D o : getObjects()) {
            seeds.add(new Object3D(o.getExtremum(maxOfObjectVoxels), label++, o.getScaleXY(), o.getScaleZ()));
        }
        return seeds;
    }
    public Map<Object3D, Object3D> getDilatedObjects(double radiusXY, double radiusZ, boolean onlyDilatedPart) {
        Map<Object3D, Object3D> res = new HashMap<>(objects.size());
        ImageInteger mask = ImageOperations.not(getLabelMap(), new ImageByte("", 0, 0, 0));
        if (!absoluteLandmark) mask.resetOffset();
        for (Object3D o : objects) {
            res.put(o, new Object3D(ImageOperations.getDilatedMask(o.getMask(), radiusXY, radiusZ, mask, onlyDilatedPart), o.getLabel()));
        }
        /*ImageInteger dilLabelMap = Image.createEmptyImage("dilatedObjects", getLabelMap(), getLabelMap());
        for (Object3D o : res.values()) {
            if (this.absoluteLandmark) o.draw(dilLabelMap, o.getLabel(), new BoundingBox(0, 0, 0)); // in order to remove the offset of the image
            else o.draw(dilLabelMap, o.getLabel());
        }
        new IJImageDisplayer().showImage(dilLabelMap);*/
        return res;
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
        ImageInteger seedMap = Filters.localExtrema(edgeMap, null, false, Filters.getNeighborhood(1.5, 1.5, edgeMap));
        this.getLabelMap(); //creates the labelImage        
        // merge background seeds && foreground seeds : background = 1, foreground = label+1
        for (int z = 0; z < seedMap.getSizeZ(); z++) {
            for (int xy = 0; xy < seedMap.getSizeXY(); xy++) {
                if (seedMap.insideMask(xy, z)) {
                    if (mask.insideMask(xy, z)) {
                        seedMap.setPixel(xy, z, labelImage.getPixelInt(xy, z) + 1);
                    } else {
                        seedMap.setPixel(xy, z, 1);
                    }
                }
            }
        }
        ArrayList<Object3D> seeds = new ArrayList<Object3D>(Arrays.asList(ObjectFactory.getObjectsImage(seedMap, false)));        
        ObjectPopulation pop = WatershedTransform.watershed(edgeMap, mask, seeds, false, null, null, lowConnectivity);
        this.objects = pop.getObjects();
        objects.remove(0); // remove background object
        relabel(true);
    }
    
    public void localThreshold(Image intensity, int marginX, int marginY, int marginZ) {
        ImageInteger background = ImageOperations.xor(getLabelMap(), new BlankMask("", properties), null);
        for (Object3D o : getObjects()) {
            o.draw(background, 1);
            BoundingBox bounds = o.getBounds().duplicate();
            bounds.expandX(bounds.getxMin() - marginX);
            bounds.expandX(bounds.getxMax() + marginX);
            bounds.expandY(bounds.getyMin() - marginY);
            bounds.expandY(bounds.getyMax() + marginY);
            bounds.expandZ(bounds.getzMin() - marginZ);
            bounds.expandZ(bounds.getzMax() + marginZ);
            bounds = bounds.getIntersection(intensity.getBoundingBox().translateToOrigin());
            double thld = runThresholder(intensity, background, bounds, Otsu, 1);
            //logger.debug("local threshold: object: {}, thld: {} with bounds: {}, thld with bcg bounds: {}, 2: {}", o.getLabel(), thld, bounds, runThresholder(intensity, background, bounds, Otsu, 1), runThresholder(intensity, background, bounds, Otsu, 2));
            o.draw(background, 0);
            localThreshold(o, intensity, thld);
        }
        relabel(false);
    }
    
    protected void localThreshold(Object3D o, Image intensity, double threshold) {
        Iterator<Voxel> it = o.getVoxels().iterator();
        while (it.hasNext()) {
            Voxel v = it.next();
            if (intensity.getPixel(v.x, v.y, v.z) < threshold) {
                it.remove();
                if (hasImage()) labelImage.setPixel(v.x, v.y, v.z, 0);
            }
        }
    }
    
    public ObjectPopulation filter(Filter filter) {
        return filter(filter, null);
    }
    
    public ObjectPopulation filterAndMergeWithConnected(Filter filter) {
        List<Object3D> removed = new ArrayList<Object3D>();
        filter(filter, removed);
        if (!removed.isEmpty()) mergeWithConnected(removed);
        return this;
    }
    
    public ObjectPopulation filter(Filter filter, List<Object3D> removedObjects) {
        //int objectNumber = objects.size();
        filter.init(this);
        Iterator<Object3D> it = getObjects().iterator();
        while (it.hasNext()) {
            Object3D o = it.next();
            if (!filter.keepObject(o)) {
                it.remove();
                if (removedObjects != null) removedObjects.add(o);
                if (hasImage()) draw(o, 0);
            }
        }
        relabel(false);
        //logger.debug("filter: {}, total object number: {}, remaning objects: {}", filter.getClass().getSimpleName(), objectNumber, objects.size());
        return this;
    }
    
    /**
     * 
     * @param otherPopulations populations that will be combined (destructive). Pixels will be added to overlapping objects, and non-overlapping objects will be added 
     * @return  the current instance for convinience
     */
    public ObjectPopulation combine(List<ObjectPopulation> otherPopulations) {
        for (ObjectPopulation pop : otherPopulations) {
            pop.filter(new RemoveAndCombineOverlappingObjects(this));
            this.getObjects().addAll(pop.getObjects());
        }
        relabel(false);
        return this;
    }
    public void combine(ObjectPopulation... otherPopulations) {
        if (otherPopulations.length>0) combine(Arrays.asList(otherPopulations));
    }
    
    public void keepOnlyLargestObject() {
        if (getObjects().isEmpty()) {
            return;
        }
        if (labelImage!=null) {
            for (Object3D o : getObjects()) {
                draw(o, 0);
            }
        }
        int maxIdx = 0;
        int maxSize = objects.get(0).getVoxels().size();
        for (int i = 1; i < objects.size(); ++i) {
            if (objects.get(i).getVoxels().size() > maxSize) {
                maxSize = objects.get(i).getVoxels().size();
                maxIdx = i;
            }
        }
        ArrayList<Object3D> objectsTemp = new ArrayList<Object3D>(1);
        Object3D o = objects.get(maxIdx);
        o.setLabel(1);
        objectsTemp.add(o);
        objects = objectsTemp;
        if (labelImage!=null) draw(o, o.getLabel());
    }
    
    public void mergeAll() {
        if (getObjects().isEmpty()) return;
        if (labelImage!=null) {
            for (Object3D o : getObjects()) draw(o, 1);
        }
        for (Object3D o : getObjects()) o.setLabel(1);
        Object3D o = new Object3D(getLabelMap(), 1);
        if (!this.absoluteLandmark) o.translate(o.getBounds().reverseOffset());
        objects.clear();
        objects.add(o);
    }
    public void mergeAllConnected() {
        mergeAllConnected(Integer.MIN_VALUE);
    }
    private void mergeAllConnected(int fromLabel) {
        relabel(); // objects label start from 1 -> idx = label-1
        getObjects();
        List<Object3D> toRemove = new ArrayList<Object3D>();
        ImageInteger inputLabels = getLabelMap();
        int otherLabel;
        int[][] neigh = inputLabels.getSizeZ()>1 ? (lowConnectivity ? ImageLabeller.neigh3DLowHalf : ImageLabeller.neigh3DHalf) : (lowConnectivity ? ImageLabeller.neigh2D4Half : ImageLabeller.neigh2D8Half);
        Voxel n;
        for (int z = 0; z<inputLabels.getSizeZ(); z++) {
            for (int y = 0; y<inputLabels.getSizeY(); y++) {
                for (int x = 0; x<inputLabels.getSizeX(); x++) {
                    int label = inputLabels.getPixelInt(x, y, z);
                    if (label==0) continue;
                    if (label-1>=objects.size()) {
                        new IJImageDisplayer().showImage(inputLabels.duplicate("label map, error: "+label));
                    }
                    Object3D currentRegion = objects.get(label-1);
                    for (int i = 0; i<neigh.length; ++i) {
                        n = new Voxel(x+neigh[i][0], y+neigh[i][1], z+neigh[i][2]);
                        if (inputLabels.contains(n.x, n.y, n.z)) { 
                            otherLabel = inputLabels.getPixelInt(n.x, n.y, n.z);   
                            if (otherLabel>0 && otherLabel!=label) {
                                if (label>=fromLabel || otherLabel>=fromLabel) {
                                    Object3D otherRegion = objects.get(otherLabel-1);
                                    if (otherLabel<label) { // switch
                                        Object3D temp = currentRegion;
                                        currentRegion = otherRegion;
                                        otherRegion = temp;
                                        label = currentRegion.getLabel();
                                    }
                                    currentRegion.addVoxels(otherRegion.getVoxels());
                                    draw(otherRegion, label);
                                    toRemove.add(otherRegion);
                                }
                            }
                        }
                    }
                }
            }
        }
        objects.removeAll(toRemove);
    }
    public void mergeWithConnected(Collection<Object3D> objectsToMerge) {
        // create a new list, with objects to merge at the end, and record the last label to merge
        ArrayList<Object3D> newObjects = new ArrayList<Object3D>();
        Set<Object3D> toMerge=  new HashSet<Object3D>(objectsToMerge);
        for (Object3D o : objects) if (!objectsToMerge.contains(o)) newObjects.add(o);
        int labelToMerge = newObjects.size()+1;
        newObjects.addAll(toMerge);
        this.objects=newObjects;
        mergeAllConnected(labelToMerge);
        // erase unmerged objects
        Iterator<Object3D> it = objects.iterator();
        while(it.hasNext()) {
            Object3D n = it.next();
            if (n.getLabel()>=labelToMerge) {
                eraseObject(n, false);
                it.remove();
            }
        }
    }
    
    public void sortBySpatialOrder(final IndexingOrder order) {
        Comparator<Object3D> comp = new Comparator<Object3D>() {
            @Override
            public int compare(Object3D arg0, Object3D arg1) {
                return compareCenters(getCenterArray(arg0.getBounds()), getCenterArray(arg1.getBounds()), order);
            }
        };
        Collections.sort(objects, comp);
        relabel(false);
    }
    
    private static double[] getCenterArray(BoundingBox b) {
        return new double[]{b.getXMean(), b.getYMean(), b.getZMean()};
    }
    
    private static int compareCenters(double[] o1, double[] o2, IndexingOrder order) {
        if (o1[order.i1] != o2[order.i1]) {
            return Double.compare(o1[order.i1], o2[order.i1]);
        } else if (o1[order.i2] != o2[order.i2]) {
            return Double.compare(o1[order.i2], o2[order.i2]);
        } else {
            return Double.compare(o1[order.i3], o2[order.i3]);
        }
    }
    
    public static interface Filter {
        public void init(ObjectPopulation population);
        public boolean keepObject(Object3D object);
    }

    public static class Feature implements Filter {
        boolean keepOverThreshold, strict;
        ObjectFeature feature;
        double threshold;
        BoundingBox offset;
        public Feature(ObjectFeature feature, double threshold, boolean keepOverThreshold, boolean strict) {
            this.feature=feature;
            this.threshold=threshold;
            this.keepOverThreshold=keepOverThreshold;
            this.strict=strict;
        }
        
        public void init(ObjectPopulation population) {
            this.offset= population.absoluteLandmark ? null : population.getObjectOffset();
        }

        public boolean keepObject(Object3D object) {
            double testValue = feature.performMeasurement(object, offset);
            //logger.debug("FeatureFilter: {}, object: {}, testValue: {}, threshold: {}", feature.getClass().getSimpleName(), object.getLabel(), testValue, threshold);
            if (keepOverThreshold) {
                if (strict) return testValue>threshold;
                else return testValue>=threshold;
            } else {
                if (strict) return testValue<threshold;
                else return testValue<=threshold;
            }
        }
    }
    
    public static class Thickness implements Filter {

        int tX = -1, tY = -1, tZ = -1;

        public Thickness setX(int minX) {
            this.tX = minX;
            return this;
        }

        public Thickness setY(int minY) {
            this.tY = minY;
            return this;
        }

        public Thickness setZ(int minZ) {
            this.tZ = minZ;
            return this;
        }
        
        @Override
        public boolean keepObject(Object3D object) {
            return (tX < 0 || object.getBounds().getSizeX() > tX) && (tY < 0 || object.getBounds().getSizeY() > tY) && (tZ < 0 || object.getBounds().getSizeZ() > tZ);
        }

        public void init(ObjectPopulation population) {}
    }
    
    public static class MeanThickness implements Filter {

        double tX = -1, tY = -1, tZ = -1;

        public MeanThickness setX(double minX) {
            this.tX = minX;
            return this;
        }

        public MeanThickness setY(double minY) {
            this.tY = minY;
            return this;
        }

        public MeanThickness setZ(double minZ) {
            this.tZ = minZ;
            return this;
        }
        
        @Override
        public boolean keepObject(Object3D object) {
            return (tX < 0 || (object.getBounds().getSizeX() > tX && meanThicknessX(object)>tX)) && (tY < 0 || (object.getBounds().getSizeY() > tY && meanThicknessY(object)>tY)) && (tZ < 0 || (object.getBounds().getSizeZ() > tZ && meanThicknessZ(object)>tZ));
        }
        private double meanThicknessX(Object3D object) {
            double mean = 0;
            double count = 0;
            for (int z = 0; z<object.getBounds().getSizeZ(); ++z) {
                for (int y = 0; y<object.getBounds().getSizeY(); ++y) {
                    int min = -1, max = -1;
                    for (int x=0; x<object.getBounds().getSizeX(); ++x) {
                        if (object.getMask().insideMask(x, y, z)) {
                            min=x;
                            break;
                        }
                    }
                    for (int x=object.getBounds().getSizeX()-1; x>=0; --x) {
                        if (object.getMask().insideMask(x, y, z)) {
                            max=x;
                            break;
                        }
                    }
                    if (min>=0) {
                        mean +=max-min+1;
                        ++count;
                    }
                }
            }
            if (count>0) mean/=count;
            //logger.debug("mean thicknessX: {}", mean);
            return mean;
        }
        private double meanThicknessY(Object3D object) {
            double mean = 0;
            double count = 0;
            for (int z = 0; z<object.getBounds().getSizeZ(); ++z) {
                for (int x = 0; x<object.getBounds().getSizeX(); ++x) {
                    int min = -1, max = -1;
                    for (int y=0; y<object.getBounds().getSizeY(); ++y) {
                        if (object.getMask().insideMask(x, y, z)) {
                            min=y;
                            break;
                        }
                    }
                    for (int y=object.getBounds().getSizeY()-1; y>=0; --y) {
                        if (object.getMask().insideMask(x, y, z)) {
                            max=y;
                            break;
                        }
                    }
                    if (min>=0) {
                        mean +=max-min+1;
                        ++count;
                    }
                }
            }
            if (count>0) mean/=count;
            return mean;
        }
        private double meanThicknessZ(Object3D object) {
            double mean = 0;
            double count = 0;
            for (int y = 0; y<object.getBounds().getSizeY(); ++y) {
                for (int x = 0; x<object.getBounds().getSizeX(); ++x) {
                    int min = -1, max = -1;
                    for (int z=0; z<object.getBounds().getSizeZ(); ++z) {
                        if (object.getMask().insideMask(x, y, z)) {
                            min=z;
                            break;
                        }
                    }
                    for (int z=object.getBounds().getSizeZ()-1; z>=0; --z) {
                        if (object.getMask().insideMask(x, y, z)) {
                            max=z;
                            break;
                        }
                    }
                    if (min>=0) {
                        mean +=max-min+1;
                        ++count;
                    }
                }
            }
            if (count>0) mean/=count;
            return mean;
        }

        public void init(ObjectPopulation population) {}
    }

    public static class RemoveFlatObjects extends Thickness {

        public RemoveFlatObjects(Image image) {
            this(image.getSizeZ() > 1);
        }

        public RemoveFlatObjects(boolean is3D) {
            super.setX(1).setY(1);
            if (is3D) {
                super.setZ(1);
            }
        }
    }

    public static class Size implements Filter {

        int min = -1, max = -1;

        public Size setMin(int min) {
            this.min = min;
            return this;
        }

        public Size setMax(int max) {
            this.max = max;
            return this;
        }
        @Override public void init(ObjectPopulation population) {}
        @Override
        public boolean keepObject(Object3D object) {
            int size = object.getVoxels().size();
            return (min < 0 || size >= min) && (max < 0 || size < max);
        }
    }

    public static class ContactBorder implements Filter {

        public static enum Border {

            X, Y, YDown, YUp, Z, XY, XYZ;            
            ImageProperties mask;
            public void setMask(ImageProperties mask) {
                this.mask=mask;
            }
            public boolean contact(Voxel v) {
                if (hasX() && (v.x == 0 || v.x == mask.getSizeX() - 1)) return true;
                if (hasY() && (v.y == 0 || v.y == mask.getSizeY() - 1)) return true;
                if (hasZ() && (v.z == 0 || v.z == mask.getSizeZ() - 1)) return true;
                if (this.equals(YDown) && v.y==mask.getSizeY() - 1) return true;
                else if (this.equals(YUp) && v.y==0) return true;
                return false;
            }
            public boolean hasX() {
                return (this.equals(X) || this.equals(XY) || this.equals(XYZ));
            }

            public boolean hasY() {
                return (this.equals(Y) || this.equals(XY) || this.equals(XYZ));
            }

            public boolean hasZ() {
                return (this.equals(Z) || this.equals(XYZ));
            }
        };
        int contactLimit;
        ImageProperties mask;
        Border border;

        public ContactBorder(int contactLimit, ImageProperties mask, Border border) {
            this.contactLimit = contactLimit;
            this.mask = mask;
            this.border = border;
            border.setMask(mask);
        }
        @Override public void init(ObjectPopulation population) {}
        @Override
        public boolean keepObject(Object3D object) {
            if (contactLimit <= 0) {
                return true;
            }
            int count = 0;
            for (Voxel v : object.getVoxels()) {
                if (border.contact(v)) {
                    ++count;
                    if (count>=contactLimit) return false;
                }
            }
            return true;
        }
    }

    public static class MeanIntensity implements Filter {

        double threshold;
        Image intensityMap;
        boolean keepOverThreshold;
        
        public MeanIntensity(double threshold, boolean keepOverThreshold, Image intensityMap) {
            this.threshold = threshold;
            this.intensityMap = intensityMap;
            this.keepOverThreshold=keepOverThreshold;
        }
        @Override public void init(ObjectPopulation population) {}
        @Override
        public boolean keepObject(Object3D object) {
            double mean = BasicMeasurements.getMeanValue(object, intensityMap, false);
            return mean >= threshold == keepOverThreshold;
        }
    }
        
    public static class GaussianFit implements Filter {
        public static boolean disp = false;
        double typicalSigma, sigmaMin,sigmaMax, precision, errorThreshold, sigmaThreshold; 
        Image image;
        Map<Object3D, double[]> fit;
        public GaussianFit(Image image, double typicalSigma, double sigmaMin, double sigmaMax, double precision, double errorThreshold, double sigmaThreshold) {
            this.typicalSigma=typicalSigma;
            this.sigmaMin=sigmaMin;
            this.sigmaMax=sigmaMax;
            this.errorThreshold=errorThreshold;
            this.precision=precision;
            this.image=image;
        }
        @Override public void init(ObjectPopulation population) {
            fit = processing.gaussianFit.GaussianFit.run(image, population.getObjects(), typicalSigma, sigmaMin, sigmaMax, precision, 300, 0.001, 0.01);
            if (disp) processing.gaussianFit.GaussianFit.display2DImageAndRois(image, fit);
        }
        @Override
        public boolean keepObject(Object3D object) {
            double[] params = fit.get(object);
            return params[params.length-1]<errorThreshold;// && params[params.length-2]<sigmaThreshold;
        }
    }
    
    public static class Or implements Filter {
        Filter[] filters;
        public Or(Filter... filters) {
            this.filters=filters;
        }
        public void init(ObjectPopulation population) {
            for (Filter f : filters) f.init(population);
        }

        public boolean keepObject(Object3D object) {
            for (Filter f : filters) if (f.keepObject(object)) return true;
            return false;
        }
        
    }

    public static class Overlap implements Filter {

        ImageInteger labelMap;
        Neighborhood n;

        public Overlap(ImageInteger labelMap, double... radius) {
            this.labelMap = labelMap;
            double rad, radZ;
            if (radius.length == 0) {
                rad = radZ = 1.5;
            } else {
                rad = radius[0];
                if (radius.length >= 2) {
                    radZ = radius[1];
                } else {
                    radZ = rad;
                }
            }            
            n = labelMap.getSizeZ() > 1 ? new EllipsoidalNeighborhood(rad, radZ, false) : new EllipsoidalNeighborhood(rad, false);
        }
        @Override public void init(ObjectPopulation population) {}
        @Override
        public boolean keepObject(Object3D object) {
            for (Voxel v : object.getVoxels()) {
                n.setPixels(v, labelMap);
                for (float f : n.getPixelValues()) {
                    if (f > 0) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static class RemoveAndCombineOverlappingObjects implements Filter { // suppress objects that are already in other and combine the voxels

        ObjectPopulation other;
        boolean distanceTolerance;

        public RemoveAndCombineOverlappingObjects(ObjectPopulation other) { //, boolean distanceTolerance
            this.other = other;
            //this.distanceTolerance = distanceTolerance;
            
        }
        @Override public void init(ObjectPopulation population) {}
        @Override
        public boolean keepObject(Object3D object) {
            Object3D maxInterO = null;
            int maxInter = 0;
            for (Object3D o : other.getObjects()) {
                int inter = o.getIntersection(object).size();
                if (inter > maxInter) {
                    maxInter = inter;
                    maxInterO = o;
                }
            }
            /*if (maxInterO == null && distanceTolerance) {
                //TODO cherche l'objet le plus proche modulo une distance de 1 de distance et assigner les voxels
            }*/
            if (maxInterO != null) {
                maxInterO.addVoxels(object.getVoxels());
                return false;
            }            
            return true;
        }
    }
}
