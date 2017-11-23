/*
 * Copyright (C) 2017 jollion
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
package plugins.plugins.postFilters;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageLabeller;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.List;
import plugins.PostFilter;
import processing.Filters;
import processing.ImageFeatures;
import processing.WatershedTransform;

/**
 *
 * @author jollion
 */
public class FitMicrochannelHeadToGradient implements PostFilter {
    NumberParameter gradientScale = new BoundedNumberParameter("Gradient Scale", 1, 2, 1, null);
    public static boolean debug = false;
    @Override
    public ObjectPopulation runPostFilter(StructureObject parent, int childStructureIdx, ObjectPopulation childPopulation) {
        fitHead(parent.getRawImage(childStructureIdx), gradientScale.getValue().doubleValue(), childPopulation);
        return childPopulation;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{gradientScale};
    }
    public static void fitHead(Image input, double gradientScale, ObjectPopulation inputPop) {
        Image grad = ImageFeatures.getGradientMagnitude(input, gradientScale, false).setName("grad");
        if (debug) ImageWindowManagerFactory.showImage(grad);
        for (Object3D o : inputPop.getObjects()) cutHead(grad, gradientScale, o);
        inputPop.redrawLabelMap(true);
        if (debug && !inputPop.getObjects().isEmpty()) logger.debug("object mask type: {}", inputPop.getObjects().get(0).getMask().getClass().getSimpleName());
    }
    
    private static void cutHead(Image grad, double gradientScale, Object3D object) {
        BoundingBox b = object.getBounds();
        BoundingBox head = new BoundingBox(b.getxMin(), b.getxMax(), b.getyMin(), b.getyMin()+b.getSizeX(), b.getzMin(), b.getzMax());
        Image gradLocal = grad.crop(head);
        List<Object3D> seeds = new ArrayList<>(3);
        int label = 0;
        double scaleXY = grad.getScaleXY();
        double scaleZ = grad.getScaleZ();
        Voxel corner1 = new Voxel(0, 0, 0);
        Voxel corner2 = new Voxel(gradLocal.getSizeX()-1, 0, 0);
        seeds.add(new Object3D(corner1, ++label, (float)scaleXY, (float)scaleZ));
        seeds.add(new Object3D(corner2, ++label, (float)scaleXY, (float)scaleZ));
        // add all local min within innerHead
        int margin =(int)Math.round(gradientScale+0.5)+1;
        if (margin*2>=b.getSizeX()-2) margin = Math.max(1, b.getSizeX()/4);
        BoundingBox innerHead = new BoundingBox(margin, head.getSizeX()-1-margin,margin, head.getSizeY()-1-margin, 0, head.getSizeZ()-1);
        ImageByte maxL = Filters.localExtrema(gradLocal, null, false, Filters.getNeighborhood(1.5, 1.5, gradLocal)).resetOffset();
        if (debug && object.getLabel()==1) ImageWindowManagerFactory.showImage(maxL.duplicate("inner seeds before and"));
        ImageOperations.andWithOffset(maxL, innerHead.getImageProperties(1, 1), maxL);
        if (debug && object.getLabel()==1) ImageWindowManagerFactory.showImage(maxL.duplicate("inner seeds after and"));
        seeds.addAll(ImageLabeller.labelImageList(maxL));
        //seeds.add(new Object3D(new Voxel((gradLocal.getSizeX()-1)/2, (gradLocal.getSizeY()-1)/2, 0), ++label, (float)scaleXY, (float)scaleZ));
        ObjectPopulation pop = WatershedTransform.watershed(gradLocal, null, seeds, false, null, null, false);
        pop.getObjects().removeIf(o->!o.getVoxels().contains(corner1)&&!o.getVoxels().contains(corner2));
        pop.translate(head, true);
        for (Object3D o : pop.getObjects()) {
            object.removeVoxels(o.getVoxels());
            if (debug)logger.debug("object: {} remove: {} voxels, left: {}", o, o.getVoxels().size(), object.getVoxels().size());
        }
    }
}
