package processing.mergeRegions;

import static core.Processor.logger;
import dataStructure.objects.Voxel;
import ij.IJ;
import image.Image;
import java.util.ArrayList;
import java.util.HashSet;

/**
 *
 **
 * /**
 * Copyright (C) 2012 Jean Ollion
 *
 *
 *
 * This file is part of tango
 *
 * tango is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Jean Ollion
 */

public class InterfaceVoxels extends Interface {
    HashSet<Voxel> r1Voxels;
    HashSet<Voxel> r2Voxels;
    // value field of voxels = intensity;
    public InterfaceVoxels(Region r1, Region r2, InterfaceCollection col) {
        super(r1, r2, col);
        r1Voxels = new HashSet<Voxel>();
        r2Voxels = new HashSet<Voxel>();
    }
    
    @Override 
    public void mergeInterface(Interface other) {
        if (this.r1.label==other.r1.label || this.r2.label==other.r2.label) {
            r1Voxels.addAll(((InterfaceVoxels)other).r1Voxels);
            r2Voxels.addAll(((InterfaceVoxels)other).r2Voxels);
        } else {
            r1Voxels.addAll(((InterfaceVoxels)other).r2Voxels);
            r2Voxels.addAll(((InterfaceVoxels)other).r1Voxels);
        }
        computeStrength();
    }
    
    @Override
    public void computeStrength() {
        strength=0;
        if (col.sortMethod==1) {
            strength= (double)(r1Voxels.size()+r2Voxels.size());
        } else  if (col.sortMethod ==2) {
            strength=getSum();
            double size =(double)(r1Voxels.size()+r2Voxels.size()); 
            if (size>0) strength/=size;
        } else if (col.sortMethod==0) {
            strength=getSum();
        }else if (col.sortMethod==3) {
            double sum=0;
            double sum2 = 0;
            double size2=0;
            for (Interface i : r2.interfaces) {
                double sumtemp = ((InterfaceVoxels)i).getSum();
                sum2+=sumtemp;
                size2+=((InterfaceVoxels)i).getSize();
                if (i.equals(this)) {
                    sum = sumtemp / (double)((InterfaceVoxels)i).getSize();
                }
            }
            if (size2>0) sum2/=size2;
            if (r1.label!=0) {
                double sum1 = 0;
                double size1=0;
                for (Interface i : r1.interfaces) {
                    sum1+=((InterfaceVoxels)i).getSum();
                    size1+=((InterfaceVoxels)i).getSize();
                }
                if (size1>0) sum1/=size1;
                if (sum1>=sum2 && sum1!=0) {
                    strength = sum/sum1;
                    return;
                }
            }
            if (sum2!=0) strength = sum/sum2;
        } else if (col.sortMethod==4) {  // ascending order
            strength=-getSum();
            double size =(double)(r1Voxels.size()+r2Voxels.size()); 
            if (size>0) strength/=size;
        }
    }
    
    protected double getSum() {
        double sum = 0;
        for (Voxel v : r1Voxels) sum+=v.value;
        for (Voxel v : r2Voxels) sum+=v.value;
        return sum;
    }
    
    protected int getSize() {
        return r1Voxels.size()+r2Voxels.size();
    }
    
    @Override
    public double[] checkFusionCriteria() {
        
        if (col.fusionMethod==0) {
            // compute rho of the fused regions....
            //double rho = Region.getRho(allVoxels, this.col.intensityMap, this.col.regions.nCPUs);
            double rho = 1; // TODO revoir le code!!!
            if (this.col.verbose) IJ.log("check fusion: "+r1.label+ " val="+r1.mergeCriterionValue+ " + "+r2.label+ " val="+r2.mergeCriterionValue+ " criterion:"+rho);
            // compare it to rho of each region
            if (rho>r1.mergeCriterionValue[0] && rho>r2.mergeCriterionValue[0]) return new double[]{rho};
            else return null;
        } else if (col.fusionMethod==1) {
            ArrayList<Voxel>[] allVoxels = new ArrayList[]{r1.voxels, r2.voxels};
            double hess = Region.getHessianMeanValue(allVoxels, col.hessian, col.erode, col.regions.nCPUs);
            if (this.col.verbose) IJ.log("check fusion: "+r1.label+ " val="+r1.mergeCriterionValue+ " + "+r2.label+ " val="+r2.mergeCriterionValue+ " criterion:"+hess);
            if (hess<r1.mergeCriterionValue[0] && hess<r2.mergeCriterionValue[0]) return new double[]{hess};
            else return null;
        } else if (col.fusionMethod==2) { // compare mean value to treshold
            double meanIntensity = getMean(col.regions.inputGray);
            double stat = -this.strength/meanIntensity;
            logger.trace("Interface: {}, stat: {} threshold: {}, fusion: {}", this, stat, col.fusionThreshold, stat<=col.fusionThreshold);
            if (stat<=col.fusionThreshold) return new double[0]; //strenght = -mean(hessian@interface)
            else return null;
            /*double[] musigma = getMeanAndSigma();
            double sum = r1.mergeCriterionValue[0]+r2.mergeCriterionValue[0];
            double sum2 = r1.mergeCriterionValue[1]+r2.mergeCriterionValue[1];
            double count = r1.mergeCriterionValue[2]+r2.mergeCriterionValue[2];
            double[] musigmaRegion = (count==0) ? new double[]{0, 0, 0} : new double[]{sum/count, Math.sqrt(sum2/count - Math.pow(sum/count, 2)), count};
            logger.trace("Interface: {}, mu & sigma interface: {}, mu & sigma regions: {}", this, musigma, musigmaRegion);
            if (musigma[0]<=musigmaRegion[0] || musigma[2]==0 || musigmaRegion[2]==0) {
                logger.trace("interface null or inferior to region value: fusion");
                return musigmaRegion;
            } // fusion
            double stat = (musigma[0] - musigmaRegion[0]) / (musigma[1]*musigma[1] + musigmaRegion[1]*musigmaRegion[1]);
            logger.debug("Interface: {}, stat: {} threshold: {}, fusion: {}", this, stat, col.fusionThreshold, stat<=col.fusionThreshold);
            if (stat>col.fusionThreshold) return null;
            else return musigmaRegion;*/
        } else return null;
    }
    
    public double[] getMeanAndSigma() {
        double mean = 0;
        double count = 0;
        double values2=0;
        for (Voxel v : r1Voxels) {
            if (v.value!=Float.NaN) {
                mean+=v.value;
                values2+=v.value*v.value;
                count++;
            }
        }
        for (Voxel v : r2Voxels) {
            if (v.value!=Float.NaN) {
                mean+=v.value;
                values2+=v.value*v.value;
                count++;
            }
        }
        if (count!=0) {
            mean/=count;
            values2/=count;
            return new double[]{mean, Math.sqrt(values2-mean*mean), count};
        } else return new double[]{0, 0, 0};
    }
    
    public double getMean(Image image) {
        double mean = 0;
        double count = 0;
        for (Voxel v : this.r1Voxels) {
            mean+=image.getPixel(v.x, v.y, v.z);
            count++;
        }
        if (count!=0) return mean/count;
        return 0;
    }

    
    public void addPair(Voxel v1, Voxel v2) {
        r1Voxels.add(v1);
        r2Voxels.add(v2);
    }
    
}
