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
 * You should have received a copyDataFrom of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package boa.gui.imageInteraction;

import boa.gui.ManualCorrection;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.Offset;
import boa.image.SimpleImageProperties;
import boa.image.SimpleOffset;
import boa.image.processing.ImageOperations;
import boa.image.processing.RegionFactory;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.image.processing.FillHoles2D;
import boa.image.processing.Filters;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.Set;

/**
 *
 * @author jollion
 */
public class ManualObjectStrecher {
    public static final Logger logger = LoggerFactory.getLogger(ManualObjectStrecher.class);
    public static void strechObjects(List<Pair<StructureObject, BoundingBox>> parents, int structureIdx, int[] xPoints, int[] yPoints, double thresholdQuantile, boolean brightObject) {
        logger.debug("will strech {} objects, of structure: {}, x: {}, y: {}", parents.size(), structureIdx, xPoints, yPoints);
        List<Pair<StructureObject, Region>> objectsToUpdate = new ArrayList<>(parents.size());
        for (Pair<StructureObject, BoundingBox> p : parents) {
            List<StructureObject> children = p.key.getChildren(structureIdx);
            if (children.isEmpty()) continue;
            // get uppermost children : 
            StructureObject child = children.stream().min((s1, s2)->Integer.compare(s1.getBounds().yMin(), s2.getBounds().yMin())).orElse(children.get(0));
            Region childObject = child.getRegion().duplicate();
            Offset offset = new SimpleOffset(p.key.getBounds()).reverseOffset().translate(p.value);
            childObject.translate(offset); // translate object in ROI referencial
            Set<Voxel> contour = childObject.getContour();
            if (contour.isEmpty()) continue;
            Voxel left = contour.iterator().next(), right = left;
            for (Voxel v : contour) { // get upper leftmost & upper rightmost voxels
                if (v.x<left.x) left=v;
                else if (v.x==left.x && v.y<left.y) left = v;
                if (v.x>right.x) right=v;
                else if (v.x==right.x && v.y<right.y) right = v;
            }
            ImageByte strechMap = new ImageByte("strech map", new SimpleImageProperties(p.value, 1, 1));
            logger.debug("strechMap Bounds: {}", strechMap.getBoundingBox());
            Voxel leftUp=null, rightUp = null;
            for (int i = 0; i<xPoints.length; ++i) { // draw upper part && look
                if (strechMap.containsWithOffset(xPoints[i], yPoints[i], 0)) {
                    if (leftUp==null) {
                        leftUp = new Voxel(xPoints[i], yPoints[i], 0);
                        leftUp.value = (float)leftUp.getDistanceSquare(left);
                    } else {
                        float d = (float)left.getDistanceSquare(xPoints[i], yPoints[i], 0);
                        if (d<leftUp.value) {
                            leftUp = new Voxel(xPoints[i], yPoints[i], 0, d);
                        }
                    }
                    if (rightUp==null) {
                        rightUp = new Voxel(xPoints[i], yPoints[i], 0);
                        rightUp.value = (float)rightUp.getDistanceSquare(right);
                    } else {
                        float d = (float)right.getDistanceSquare(xPoints[i], yPoints[i], 0);
                        if (d<rightUp.value) {
                            rightUp = new Voxel(xPoints[i], yPoints[i], 0, d);
                        }
                    }
                }
            }
            // draw contour of new object
            drawLine(leftUp.x, leftUp.y, rightUp.x, rightUp.y, 0, 1, strechMap);
            drawLine(leftUp.x, leftUp.y, left.x, left.y, 0, 1, strechMap);
            drawLine(right.x, right.y, rightUp.x, rightUp.y, 0, 1, strechMap);
            childObject.draw(strechMap, 1, null);
            //ImageWindowManagerFactory.showImage(strechMap.duplicate("contours"));
            FillHoles2D.fillHoles(strechMap, 2);
            //childObject.draw(strechMap, 0, strechMap.getBoundingBox().reverseOffset());
            //ImageWindowManagerFactory.showImage(strechMap.duplicate("filledObject"));
            
            // Adjust filled object according to contours of existing object
            double meanIntensityContour=0;
            Image intensityMap = p.key.getRawImage(structureIdx);
            intensityMap.translate(offset);
            for (Voxel v : contour) meanIntensityContour += intensityMap.getPixelWithOffset(v.x, v.y, v.z);
            meanIntensityContour/=contour.size();
            
            ImageInteger outsideChildrenMask = p.key.getObjectPopulation(structureIdx).getLabelMap();
            ImageOperations.not(outsideChildrenMask, outsideChildrenMask);
            double meanIntensityOutsideObject = ImageOperations.getMeanAndSigma(intensityMap, outsideChildrenMask)[0];
            double thld = meanIntensityContour * thresholdQuantile + meanIntensityOutsideObject *(1-thresholdQuantile);
            logger.debug("mean int thld: {}, contour: {}, meanOutside : {}", thld,  meanIntensityContour, meanIntensityOutsideObject);
            
            // TODO adjust object according to intensities -> 2 scenarios bright object vs dark object, and compare using compaction of resulting objects
            ImageByte thlded = ImageOperations.threshold(intensityMap, thld, brightObject, false);
            //ImageWindowManagerFactory.showImage(thlded.duplicate("thld"));
            ImageOperations.and(thlded, strechMap, thlded);
            //ImageWindowManagerFactory.showImage(thlded.duplicate("and with thld"));
            //check that after thesholding, object reaches line -> if not , do not apply thresholding
            int yMin = RegionFactory.getObjectsImage(strechMap, false)[0].getBounds().yMin()+p.value.yMin();
            logger.debug("y Min: {}, line y to reach: {}", yMin, Math.max(leftUp.y, rightUp.y)+1);
            if (yMin<=Math.max(leftUp.y, rightUp.y)+1) {
                strechMap = thlded;
                // Regularisation of object
                Filters.close(strechMap, strechMap, Filters.getNeighborhood(2, 1, strechMap));
                Filters.open(strechMap, strechMap, Filters.getNeighborhood(2, 1, strechMap));
                //ImageWindowManagerFactory.showImage(strechMap.duplicate("after close"));
            }
            offset.reverseOffset();
            strechMap.translate(offset);
            Region[] allO = RegionFactory.getObjectsImage(strechMap, false);
            if (allO.length>0) {
                Region newObject = allO[0].translate(strechMap.getBoundingBox());
                objectsToUpdate.add(new Pair(child, newObject));
                logger.debug("resulting object bounds: {} (old: {})", newObject, child.getRegion().getBounds(), newObject);
            }
            intensityMap.translate(offset);
            childObject.translate(offset);
        }
        logger.debug("objects to update: {}", Utils.toStringList(objectsToUpdate, p->p.key.getRegion().getVoxels().size()+">"+p.value.getVoxels().size()));
        ManualCorrection.updateObjects(objectsToUpdate, true);
    }
    public static void drawLine(int x,int y,int x2, int y2, int z, int value, Image image) {
        int w = x2 - x ;
        int h = y2 - y ;
        int dx1 = 0, dy1 = 0, dx2 = 0, dy2 = 0 ;
        if (w<0) dx1 = -1 ; else if (w>0) dx1 = 1 ;
        if (h<0) dy1 = -1 ; else if (h>0) dy1 = 1 ;
        if (w<0) dx2 = -1 ; else if (w>0) dx2 = 1 ;
        int longest = Math.abs(w) ;
        int shortest = Math.abs(h) ;
        if (!(longest>shortest)) {
            longest = Math.abs(h) ;
            shortest = Math.abs(w) ;
            if (h<0) dy2 = -1 ; else if (h>0) dy2 = 1 ;
            dx2 = 0 ;            
        }
        int numerator = longest >> 1 ;
        for (int i=0;i<=longest;i++) {
            image.setPixelWithOffset(x, y, z, value);
            numerator += shortest ;
            if (!(numerator<longest)) {
                numerator -= longest ;
                x += dx1 ;
                y += dy1 ;
            } else {
                x += dx2 ;
                y += dy2 ;
            }
        }
    }
}
