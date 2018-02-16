/*
 * Copyright (C) 2016 jollion
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
package boa.image.processing;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import ij.ImageStack;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import boa.image.IJImageWrapper;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static boa.plugins.Plugin.logger;
import boa.image.processing.clustering.ClusterCollection;
import boa.image.processing.clustering.SimpleInterfaceVoxelSet;
import boa.image.processing.clustering.RegionCluster;

/**
 *
 * @author jollion
 */
public class FillHoles2D {
    public static boolean debug=false;
    
    public static boolean fillHolesClosing(ImageInteger image, double closeRadius, double backgroundProportion, double minSizeFusion) {
        ImageInteger close = Filters.binaryCloseExtend(image, Filters.getNeighborhood(closeRadius, closeRadius, image));
        FillHoles2D.fillHoles(close, 2); // binary close generate an image with only 1's
        ImageDisplayer disp = debug ? new IJImageDisplayer() : null;
        ImageOperations.xor(close, image, close);
        RegionPopulation foregroundPop = new RegionPopulation(image, false);
        RegionPopulation closePop = new RegionPopulation(close, false);
        if (debug) disp.showImage(closePop.getLabelMap().duplicate("close XOR"));
        closePop.filter(new InterfaceSizeFilter(foregroundPop, minSizeFusion, backgroundProportion));
        if (!closePop.getRegions().isEmpty()) {
            if (debug) {
                closePop.relabel(true);
                disp.showImage(closePop.getLabelMap().duplicate("close XOR after filter"));
                disp.showImage(foregroundPop.getLabelMap().duplicate("seg map before close"));
            }
            for (Region o : closePop.getRegions()) o.draw(image, 1);
            return true;
        } else return false;
    }
    

// Binary fill by Gabriel Landini, G.Landini at bham.ac.uk
    // 21/May/2008

    public static void fillHoles(RegionPopulation pop) {
        for (Region o : pop.getRegions()) {
            fillHoles(o.getMask(), 2);
            o.clearVoxels();
            o.getVoxels();
        }
        pop.relabel(true);
    }
    // TODO fix for other than byte processor!!
    public static void fillHoles(ImageInteger image, int midValue) {
        if (image.getSizeZ()==1) {
            fillHoles(IJImageWrapper.getImagePlus(image).getProcessor(), midValue);
        } else {
            ImageStack stack = IJImageWrapper.getImagePlus(image).getImageStack();
            for (int i = 1; i<=image.getSizeZ(); i++) { // TODO multithread
                fillHoles(stack.getProcessor(i), midValue);
            }
        }
    }
    
    protected static void fillHoles(ImageProcessor ip, int foreground, int background) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        FloodFiller ff = new FloodFiller(ip);
        ip.setColor(127); // intermediate color
        for (int y=0; y<height; y++) {
            if (ip.getPixel(0,y)==background) ff.fill(0, y);
            if (ip.getPixel(width-1,y)==background) ff.fill(width-1, y);
        }
        for (int x=0; x<width; x++){
            if (ip.getPixel(x,0)==background) ff.fill(x, 0);
            if (ip.getPixel(x,height-1)==background) ff.fill(x, height-1);
        }
        ImageInteger im = (ImageInteger)Image.createImageFrom2DPixelArray("", ip.getPixels(), width);
        int n = width*height;
        for (int xy = 0;xy<n; ++xy) {
            if (im.getPixelInt(xy, 0)==127) im.setPixel(xy, 0, background);
            else im.setPixel(xy, 0, foreground);
        }
    }
    
    protected static void fillHoles(ImageProcessor ip, int midValue) { // set foreground to 1
        int width = ip.getWidth();
        int height = ip.getHeight();
        FloodFiller ff = new FloodFiller(ip);
        ip.setColor(midValue); // intermediate color
        for (int y=0; y<height; y++) {
            if (ip.getPixel(0,y)==0) ff.fill(0, y);
            if (ip.getPixel(width-1,y)==0) ff.fill(width-1, y);
        }
        for (int x=0; x<width; x++){
            if (ip.getPixel(x,0)==0) ff.fill(x, 0);
            if (ip.getPixel(x,height-1)==0) ff.fill(x, height-1);
        }
        ImageInteger im = (ImageInteger)Image.createImageFrom2DPixelArray("", ip.getPixels(), width);
        int n = width*height;
        for (int xy = 0;xy<n; ++xy) {
            if (im.getPixelInt(xy, 0)==midValue) im.setPixel(xy, 0, 0);
            else im.setPixel(xy, 0, 1);
        }
    }
    private static class InterfaceSizeFilter implements RegionPopulation.Filter {
        RegionCluster clust;
        final RegionPopulation foregroundObjects;
        final double fusionSize, backgroundFactor;
        public InterfaceSizeFilter(RegionPopulation foregroundObjects, double fusionSize, double backgroundFactor) {
            this.foregroundObjects=foregroundObjects;
            this.fusionSize=fusionSize;
            this.backgroundFactor=backgroundFactor;
        }
        @Override
        public void init(RegionPopulation population) {
            if (!population.getImageProperties().sameDimensions(foregroundObjects.getImageProperties())) throw new IllegalArgumentException("Foreground objects population should have same bounds as current population");
            ClusterCollection.InterfaceFactory<Region, SimpleInterfaceVoxelSet> f = (Region e1, Region e2) -> new SimpleInterfaceVoxelSet(e1, e2);
            List<Region> allObjects = new ArrayList<>(population.getRegions().size()+foregroundObjects.getRegions().size());
            allObjects.addAll(population.getRegions());
            allObjects.addAll(foregroundObjects.getRegions());
            RegionPopulation mixedPop = new RegionPopulation(allObjects, population.getImageProperties());
            mixedPop.relabel();
            clust = new RegionCluster(mixedPop, true, false, f); // high connectivity -> more selective 
            //if (debug) new IJImageDisplayer().showImage(RegionCluster.drawInterfaces(clust));
        }
        private Map<Integer, Integer> getInterfaceSize(Region o) {
            Set<SimpleInterfaceVoxelSet> inter = clust.getInterfaces(o);
            Map<Integer, Integer> res = new HashMap<>(inter.size());
            for (SimpleInterfaceVoxelSet i : inter) {
                Region other = i.getOther(o);
                res.put(other.getLabel(), res.getOrDefault(other.getLabel(), 0) + i.getVoxels(other).size());
            }
            return res;
            
        }
        @Override
        public boolean keepObject(Region object) {
            Map<Integer, Integer> interfaces = getInterfaceSize(object);
            double bck = (interfaces.containsKey(0)) ? interfaces.remove(0) : 0;
            double fore = 0; for (Integer i : interfaces.values()) fore+=i;
            if (debug) logger.debug("fillHolesClosing object: {}, bck prop: {}, #inter: {}, bck:{}", object.getLabel(), bck/(bck+fore), interfaces.entrySet(), bck);
            if (bck>(bck+fore)*backgroundFactor) return false;
            if (interfaces.size()==1) return true;
            else { // link separated objects only if all but one are small enough
                Set<Region> interactants = clust.getInteractants(object);
                interactants.removeIf(o -> o.getLabel()==0 || o.getSize()<=fusionSize); 
                return interactants.size()<=1;
            }
        }
        
    }
}
