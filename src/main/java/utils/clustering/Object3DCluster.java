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
package utils.clustering;

import boa.gui.imageInteraction.IJImageDisplayer;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.Voxel;
import image.Image;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageShort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import processing.neighborhood.EllipsoidalNeighborhood;

/**
 *
 * @author jollion
 */
public class Object3DCluster extends ClusterCollection<Object3D, Set<Voxel>> {
    ObjectPopulation population;
    static Comparator<Object3D> object3DComparator = new Comparator<Object3D>() {
    public int compare(Object3D o1, Object3D o2) {
                return Integer.compare(o1.getLabel(), o2.getLabel());
            };
    };
    static InterfaceDataFactory<Set<Voxel>> interfaceVoxSetFactory = new InterfaceDataFactory<Set<Voxel>>() {
            @Override public Set<Voxel> create() {
                return new HashSet<Voxel>();
            }
    };
    public Object3DCluster(ObjectPopulation population, boolean background) {
        super(population.getObjects(), object3DComparator, interfaceVoxSetFactory);
        this.population=population;
        setInterfaces(background);
    }
    
    protected void setInterfaces(boolean background) {
        Map<Integer, Object3D> objects = new HashMap<Integer, Object3D>();
        for (Object3D o : population.getObjects()) objects.put(o.getLabel(), o);
        if (background) objects.put(0, new Object3D(new ArrayList<Voxel>(), 0, population.getImageProperties().getBoundingBox(), population.getImageProperties().getScaleXY(), population.getImageProperties().getScaleZ()));
        ImageInteger inputLabels = population.getLabelMap();
        Voxel n;
        int otherLabel;
        int[][] neigh = inputLabels.getSizeZ()>1 ? ImageLabeller.neigh3DHalf : ImageLabeller.neigh2DHalf;
        for (Object3D o : population.getObjects()) {
            for (Voxel vox : o.getVoxels()) {
                vox = vox.duplicate(); // to avoid having the same instance of voxel as in the region. //TODO why?
                for (int i = 0; i<neigh.length; ++i) {
                    n = new Voxel(vox.x+neigh[i][0], vox.y+neigh[i][1], vox.z+neigh[i][2]); // en avant pour les interactions avec d'autre spots / 0
                    if (inputLabels.contains(n.x, n.y, n.z)) { 
                        otherLabel = inputLabels.getPixelInt(n.x, n.y, n.z);   
                        if (otherLabel!=o.getLabel()) {
                            if (background || otherLabel!=0) {
                                Interface<Object3D, Set<Voxel>> inter = getInterface(o, objects.get(otherLabel), true);
                                inter.data.add(n);
                                inter.data.add(vox);
                            }
                        }
                    }
                    n = new Voxel(vox.x-neigh[i][0], vox.y-neigh[i][1], vox.z-neigh[i][2]);// eventuellement aussi en arriere juste pour interaction avec 0 = background
                    if (inputLabels.contains(n.x, n.y, n.z)) {
                        otherLabel = inputLabels.getPixelInt(n.x, n.y, n.z);  
                        if (background && otherLabel==0) {
                            Interface<Object3D, Set<Voxel>> inter = getInterface(o, objects.get(otherLabel), true); 
                            inter.data.add(n);
                            inter.data.add(vox);
                        }
                    }
                }
            } 
        }
        if (verbose) logger.debug("Interface collection: nb of interfaces:"+interfaces.size());
    }
    
    public static Interface<Object3D, Set<Voxel>> getInteface(Object3D o1, Object3D o2, ImageInteger labelImage) {
        EllipsoidalNeighborhood neigh = labelImage.getSizeZ()>1 ? new EllipsoidalNeighborhood(1, 1, true) : new EllipsoidalNeighborhood(1, true);
        Object3D min;
        int otherLabel;
        if (o1.getVoxels().size()<=o2.getVoxels().size()) {
            min=o1;
            otherLabel = o2.getLabel();
        } else {
            min = o2;
            otherLabel = o1.getLabel();
        }
        int xx, yy, zz;
        Set<Voxel> inter = new HashSet<Voxel>();
        for (Voxel v : min.getVoxels()) {
            for (int i = 0; i<neigh.dx.length; ++i) {
                xx=v.x+neigh.dx[i];
                yy=v.y+neigh.dy[i];
                zz=v.z+neigh.dz[i];
                if (labelImage.contains(xx, yy, zz) && labelImage.getPixelInt(xx, yy, zz)==otherLabel) inter.add(v);
            }
        }
        return new Interface<Object3D, Set<Voxel>>(o1, o2, inter, object3DComparator);
    }
    
    public void setVoxelIntensities(Image image, boolean objects, boolean interfaces) {
        if (objects) for (Object3D o : this.allElements) for (Voxel v : o.getVoxels()) v.value=image.getPixel(v.x, v.y, v.z);
        if (interfaces) for (Interface<Object3D, Set<Voxel>> i : this.interfaces) for (Voxel v : i.data) v.value=image.getPixel(v.x, v.y, v.z);
    }  
    
    public void mergeSort(FusionCriterion criterion, InterfaceSortMethod<Object3D, Set<Voxel>> interfaceSortMethod) {
        int nInit = population.getObjects().size();
        super.mergeSort(new InterfaceVoxelFusion(criterion), interfaceSortMethod);
        if (nInit > population.getObjects().size()) population.relabel(true);
    }
    
    public ImageShort drawInterfaces() {
        ImageShort im = new ImageShort("Iterfaces", population.getImageProperties());
        for (Interface<Object3D, Set<Voxel>> i : interfaces) {
            logger.debug("Interface: {}+{}, size: {}", i.e1.getLabel(), i.e2.getLabel(), i.data.size());
            for (Voxel v : i.data) {
                if (population.getLabelMap().getPixelInt(v.x, v.y, v.z)==i.e1.getLabel()) im.setPixel(v.x, v.y, v.z, i.e2.getLabel());
                else im.setPixel(v.x, v.y, v.z, i.e1.getLabel());
            }
        }
        return im;
    }
    
    public ImageFloat drawInterfacesSortValue(InterfaceSortMethod<Object3D, Set<Voxel>> sm) {
        ImageFloat im = new ImageFloat("Iterface Strength", population.getImageProperties());
        for (Interface<Object3D, Set<Voxel>> i : interfaces) {
            i.updateSortValue(sm);
            if (i.e1.getLabel()==0) continue;
            for (Voxel v : i.data) {
                im.setPixel(v.x, v.y, v.z, i.sortValue);
            }
        }
        return im;
    }
    
    private static class InterfaceVoxelFusion extends FusionImpl<Object3D, Set<Voxel>> {
        final FusionCriterion criterion;
        public InterfaceVoxelFusion(FusionCriterion criterion) {
            super(new InterfaceDataFusionCollection<Set<Voxel>, Voxel>(), Object3DCluster.object3DComparator);
            if (criterion==null) {
                this.criterion = new FusionCriterion() {
                    public boolean checkCriterion(Interface<Object3D, Set<Voxel>> i, double[] criterionValues) {
                        return true;
                    }
                };
            } else this.criterion=criterion;
            if (interfaceDataFusion==null) throw new IllegalArgumentException("interfaceDataFusion null!");
        }
        @Override public void performFusion(Interface<Object3D, Set<Voxel>> i) {
            i.e1.addVoxels(i.e2.getVoxels());
        }
        @Override public boolean checkFusion(Interface<Object3D, Set<Voxel>> i) {
            return criterion.checkCriterion(i, null);
        }
    }
    
    public interface FusionCriterion {
        public boolean checkCriterion(Interface<Object3D, Set<Voxel>> i, double[] criterionValues);
    }
}
