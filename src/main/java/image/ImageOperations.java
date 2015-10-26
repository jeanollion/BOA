/*
 * Copyright (C) 2015 jollion
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
package image;

import dataStructure.objects.Voxel;
import static image.ImageOperations.Axis.*;

/**
 *
 * @author jollion
 */
public class ImageOperations {
    public static enum Axis {X, Y, Z;}
    
    public static ImageInteger threshold(Image image, double threshold, boolean overThreshold, boolean strict) {
        return threshold(image, threshold, overThreshold, strict, false, null);
    }
    
    public static ImageInteger threshold(Image image, double threshold, boolean overThreshold, boolean strict, boolean setBackground, ImageInteger dest) {
        if (dest==null) dest=new ImageByte("", image);
        if (setBackground) {
            if (overThreshold) {
                if (strict) {
                    for (int z = 0; z < image.sizeZ; z++) {
                        for (int xy = 0; xy < image.sizeXY; xy++) {
                            if (image.getPixel(xy, z)>threshold) {
                                dest.setPixel(xy, z, 1);
                            } dest.setPixel(xy, z, 0);
                        }
                    }
                } else {
                    for (int z = 0; z < image.sizeZ; z++) {
                        for (int xy = 0; xy < image.sizeXY; xy++) {
                            if (image.getPixel(xy, z)>=threshold) {
                                dest.setPixel(xy, z, 1);
                            } dest.setPixel(xy, z, 0);
                        }
                    }
                }
            } else {
                if (strict) {
                    for (int z = 0; z < image.sizeZ; z++) {
                        for (int xy = 0; xy < image.sizeXY; xy++) {
                            if (image.getPixel(xy, z)<threshold) {
                                dest.setPixel(xy, z, 1);
                            } dest.setPixel(xy, z, 0);
                        }
                    }
                } else {
                    for (int z = 0; z < image.sizeZ; z++) {
                        for (int xy = 0; xy < image.sizeXY; xy++) {
                            if (image.getPixel(xy, z)<=threshold) {
                                dest.setPixel(xy, z, 1);
                            } dest.setPixel(xy, z, 0);
                        }
                    }
                }
            }
        } else {
            if (overThreshold) {
                if (strict) {
                    for (int z = 0; z < image.sizeZ; z++) {
                        for (int xy = 0; xy < image.sizeXY; xy++) {
                            if (image.getPixel(xy, z)>threshold) {
                                dest.setPixel(xy, z, 1);
                            }
                        }
                    }
                } else {
                    for (int z = 0; z < image.sizeZ; z++) {
                        for (int xy = 0; xy < image.sizeXY; xy++) {
                            if (image.getPixel(xy, z)>=threshold) {
                                dest.setPixel(xy, z, 1);
                            }
                        }
                    }
                }
            } else {
                if (strict) {
                    for (int z = 0; z < image.sizeZ; z++) {
                        for (int xy = 0; xy < image.sizeXY; xy++) {
                            if (image.getPixel(xy, z)<threshold) {
                                dest.setPixel(xy, z, 1);
                            }
                        }
                    }
                } else {
                    for (int z = 0; z < image.sizeZ; z++) {
                        for (int xy = 0; xy < image.sizeXY; xy++) {
                            if (image.getPixel(xy, z)<=threshold) {
                                dest.setPixel(xy, z, 1);
                            }
                        }
                    }
                }
            }
        }
        
        return dest;
    }

    public static void pasteImage(Image source, Image dest, BoundingBox offset) {
        if (source.getClass()!=dest.getClass()) throw new IllegalArgumentException("Paste Image: source and destination should be of the same type (source: "+source.getClass().getSimpleName()+ " destination: "+dest.getClass().getSimpleName()+")");
        if (source.getSizeX()+offset.xMin>dest.sizeX || source.getSizeY()+offset.yMin>dest.sizeY || source.getSizeZ()+offset.zMin>dest.sizeZ) throw new IllegalArgumentException("Paste Image: source does not fit in destination");
        Object[] sourceP = source.getPixelArray();
        Object[] destP = dest.getPixelArray();
        int off=dest.sizeX*offset.yMin+offset.xMin;
        int offSource = 0;
        for (int z = 0; z<source.sizeZ; ++z) {
            for (int y = 0 ; y<source.sizeY; ++y) {
                //logger.trace("paste imate: z source: {}, z dest: {}, y source: {} y dest: {} off source: {} off dest: {} size source: {} size dest: {}", z, z+offset.zMin, y, y+offset.yMin, offSource, off, ((byte[])sourceP[z]).length, ((byte[])destP[z+offset.zMin]).length);
                System.arraycopy(sourceP[z], offSource, destP[z+offset.zMin], off, source.sizeX);
                off+=dest.sizeX;
                offSource+=source.sizeX;
            }
            off=dest.sizeX*offset.yMin+offset.xMin;
            offSource=0;
        }
    }

    public static Image addImage(Image source1, Image source2, Image output, double coeff) {
        if (!source1.sameSize(source2)) throw new IllegalArgumentException("sources images have different sizes");
        if (output==null) output = Image.createEmptyImage(source1.getName()+" + "+coeff+" x "+source2.getName(), source1, source1);
        else if (!output.sameSize(source1)) output = Image.createEmptyImage(source1.getName()+" + "+coeff+" x "+source2.getName(), output, source1);
        if (coeff==1) {
            for (int z = 0; z<output.sizeZ; ++z) {
                for (int xy=0; xy<output.sizeXY; ++xy) {
                    output.setPixel(xy, z, source1.getPixel(xy, z)+source2.getPixel(xy, z)*coeff);
                }
            }
        } else if (coeff==-1) {
            for (int z = 0; z<output.sizeZ; ++z) {
                for (int xy=0; xy<output.sizeXY; ++xy) {
                    output.setPixel(xy, z, source1.getPixel(xy, z)-source2.getPixel(xy, z));
                }
            }
        } else {
            for (int z = 0; z<output.sizeZ; ++z) {
                for (int xy=0; xy<output.sizeXY; ++xy) {
                    output.setPixel(xy, z, source1.getPixel(xy, z)+source2.getPixel(xy, z)*coeff);
                }
            }
        }
        return output;
    }
    public static Image multiply(Image source1, Image output, double coeff) {
        if (output==null) output = Image.createEmptyImage(source1.getName()+" x "+coeff, source1, source1);
        else if (!output.sameSize(source1)) output = Image.createEmptyImage(source1.getName()+" x "+coeff, output, source1);
        double round = output instanceof ImageInteger?0.5:0;
        for (int z = 0; z<output.sizeZ; ++z) {
            for (int xy=0; xy<output.sizeXY; ++xy) {
                output.setPixel(xy, z, source1.getPixel(xy, z)*coeff+round);
            }
        } 
        return output;
    }
    
    public static ImageInteger or(ImageMask source1, ImageMask source2, ImageInteger output) {
        if (output==null) output = new ImageByte("or", source1);
        for (int z = 0; z<source1.getSizeZ(); ++z) {
            for (int xy=0; xy<source1.getSizeXY(); ++xy) {
                if (source1.insideMask(xy, z) || source2.insideMask(xy, z)) output.setPixel(xy, z, 1);
                else output.setPixel(xy, z, 0);
            }
        }
        return output;
    }
    
    public static ImageInteger xor(ImageMask source1, ImageMask source2, ImageInteger output) {
        if (output==null) output = new ImageByte("or", source1);
        for (int z = 0; z<source1.getSizeZ(); ++z) {
            for (int xy=0; xy<source1.getSizeXY(); ++xy) {
                if (source1.insideMask(xy, z)!=source2.insideMask(xy, z)) output.setPixel(xy, z, 1);
                else output.setPixel(xy, z, 0);
            }
        }
        return output;
    }
    
    public static ImageInteger and(ImageMask source1, ImageMask source2, ImageInteger output) {
        if (output==null) output = new ImageByte("and", source1);
        for (int z = 0; z<source1.getSizeZ(); ++z) {
            for (int xy=0; xy<source1.getSizeXY(); ++xy) {
                if (source1.insideMask(xy, z) && source2.insideMask(xy, z)) output.setPixel(xy, z, 1);
                else output.setPixel(xy, z, 0);
            }
        }
        return output;
    }
    
    public static void trim(Image image, float value, boolean zeroUnderValue, boolean strict) {
        if (strict) {
            if (zeroUnderValue) {
                for (int z = 0; z<image.sizeZ; ++z) {
                    for (int xy=0; xy<image.sizeXY; ++xy) {
                        if (image.getPixel(xy, z)<value) image.setPixel(xy, z, 0);
                    }
                }
            } else {
                for (int z = 0; z<image.sizeZ; ++z) {
                    for (int xy=0; xy<image.sizeXY; ++xy) {
                        if (image.getPixel(xy, z)>value) image.setPixel(xy, z, 0);
                    }
                }
            }
        } else {
            if (zeroUnderValue) {
                for (int z = 0; z<image.sizeZ; ++z) {
                    for (int xy=0; xy<image.sizeXY; ++xy) {
                        if (image.getPixel(xy, z)<=value) image.setPixel(xy, z, 0);
                    }
                }
            } else {
                for (int z = 0; z<image.sizeZ; ++z) {
                    for (int xy=0; xy<image.sizeXY; ++xy) {
                        if (image.getPixel(xy, z)>=value) image.setPixel(xy, z, 0);
                    }
                }
            }
        }
    }
    /**
     * 
     * @param image
     * @param axis along which project values
     * @param limit projection within the boundingbox
     * @return 
     */
    public static float[] meanProjection(Image image, Axis axis, BoundingBox limit) {
        float[] res;
        if (limit==null) limit = new BoundingBox(image, false);
        if (axis.equals(X)) {
            res = new float[limit.getSizeX()];
            for (int x = limit.getxMin(); x<=limit.getxMax(); ++x) {
                double sum=0;
                for (int z=limit.getzMin(); z<=limit.getzMax(); ++z) for (int y=limit.getyMin(); y<=limit.getyMax(); ++y) sum+=image.getPixel(x, y, z);
                res[x-limit.getxMin()]=(float) (sum/(limit.getSizeY()*limit.getSizeZ()));
            }
        } else if (axis.equals(Y)) {
            res = new float[limit.getSizeY()];
            for (int y = limit.getyMin(); y<=limit.getyMax(); ++y) {
                double sum=0;
                for (int z=limit.getzMin(); z<=limit.getzMax(); ++z) for (int x=limit.getxMin(); x<=limit.getxMax(); ++x) sum+=image.getPixel(x, y, z);
                res[y-limit.getyMin()]=(float) (sum/(limit.getSizeX()*limit.getSizeZ()));
            }
        } else {
            res = new float[limit.getSizeZ()];
            for (int z = limit.getzMin(); z<=limit.getzMax(); ++z) {
                double sum=0;
                for (int x=limit.getxMin(); x<=limit.getxMax(); ++x) for (int y=limit.getyMin(); y<=limit.getyMax(); ++y) sum+=image.getPixel(x, y, z);
                res[z-limit.getzMin()]=(float) (sum/(limit.getSizeY()*limit.getSizeX()));
            }
        }
        return res;
    }
    
    /**
     * 
     * @param image
     * @param axis along which project values
     * @param limit projection within the boundingbox
     * @return 
     */
    public static float[] maxProjection(Image image, Axis axis, BoundingBox limit) {
        float[] res;
        float value;
        if (limit==null) limit = new BoundingBox(image, false);
        if (axis.equals(X)) {
            res = new float[limit.getSizeX()];
            for (int x = limit.getxMin(); x<=limit.getxMax(); ++x) {
                float max=image.getPixel(x, limit.getyMin(), limit.getzMin());
                for (int z=limit.getzMin(); z<=limit.getzMax(); ++z) for (int y=limit.getyMin(); y<=limit.getyMax(); ++y) {value=image.getPixel(x, y, z); if (value>max) max=value;}
                res[x-limit.getxMin()]=max;
            }
        } else if (axis.equals(Y)) {
            res = new float[limit.getSizeY()];
            for (int y = limit.getyMin(); y<=limit.getyMax(); ++y) {
                float max=image.getPixel(limit.getxMin(), y, limit.getzMin());
                for (int z=limit.getzMin(); z<=limit.getzMax(); ++z) for (int x=limit.getxMin(); x<=limit.getxMax(); ++x) {value=image.getPixel(x, y, z); if (value>max) max=value;}
                res[y-limit.getyMin()]=max;
            }
        } else {
            res = new float[limit.getSizeZ()];
            for (int z = limit.getzMin(); z<=limit.getzMax(); ++z) {
                float max=image.getPixel(limit.getxMin(), limit.getyMin(), z);
                for (int x=limit.getxMin(); x<=limit.getxMax(); ++x) for (int y=limit.getyMin(); y<=limit.getyMax(); ++y) {value=image.getPixel(x, y, z); if (value>max) max=value;}
                res[z-limit.getzMin()]=max;
            }
        }
        return res;
    }
    
    public static ImageFloat normalize(Image input, ImageMask mask, ImageFloat output) {
        float[] mm = input.getMinAndMax(mask);
        if (output==null || !output.sameSize(input)) output = new ImageFloat(input.getName()+" normalized", input);
        if (mm[0]==mm[1]) return output;
        double scale = 1 / (mm[1] - mm[0]);
        double offset = -mm[0] * scale;
        float[][] pixels = output.getPixelArray();
        for (int z = 0; z < input.sizeZ; z++) {
            for (int xy = 0; xy < input.sizeXY; xy++) {
                pixels[z][xy] = (float) (input.getPixel(xy, z) * scale + offset);
            }
        }
        return output;
    }
    public static double getPercentile(Image image, double percent, ImageMask mask) {
        float[] mm = image.getMinAndMax(mask);
        int[] histo = image.getHisto256(mm[0], mm[1], mask);
        double binSize = (image instanceof ImageByte) ? 1: (mm[1]-mm[0]) / 256d;
        int count = 0;
        for (int i : histo) count += i;
        double limit = count * percent;
        if (limit >= count) return mm[0];
        count = histo[255];
        int idx = 255;
        while (count < limit && idx > 0) {
            idx--;
            count += histo[idx];
        }
        double idxInc = (histo[idx] != 0) ? (count - limit) / (histo[idx]) : 0; //lin approx
        //ij.IJ.log("percentile: bin:"+idx+ " inc:"+ idxInc+ " min:"+min+ " max:"+max);
        return (double) (idx + idxInc) * binSize + mm[0];
    }
    
    public static Voxel getGlobalExtremum(Image image, BoundingBox area, boolean max) {
        float extrema = image.getPixel(area.getxMin(), area.getyMin(), area.getzMin());
        int xEx=area.getxMin(), yEx=area.getyMin(), zEx=area.getzMin();
        if (max) {
            for (int z= area.getzMin();z<=area.getzMax();++z) {
                for (int y = area.getyMin(); y<=area.getyMax(); y++) {
                    for (int x=area.getxMin(); x<=area.getxMax(); ++x) {
                        if (image.getPixel(x, y, z)>extrema) {
                            extrema = image.getPixel(x, y, z); 
                            yEx=y; xEx=x; zEx=z;
                        }
                    }
                }
            }
        } else {
            for (int z= area.getzMin();z<=area.getzMax();++z) {
                for (int y = area.getyMin(); y<=area.getyMax(); y++) {
                    for (int x=area.getxMin(); x<=area.getxMax(); ++x) {
                        if (image.getPixel(x, y, z)<extrema) {
                            extrema = image.getPixel(x, y, z); 
                            yEx=y; xEx=x; zEx=z;
                        }
                    }
                }
            }
        }
        return new Voxel(xEx, yEx, zEx, extrema);  
            
    }
    
    public static void fill(Image image, double value, BoundingBox area) {
        if (area==null) area=image.getBoundingBox().translateToOrigin();
        for (int z= area.getzMin();z<=area.getzMax();++z) {
            for (int y = area.getyMin(); y<=area.getyMax(); y++) {
                for (int x=area.getxMin(); x<=area.getxMax(); ++x) {
                    image.setPixel(x, y, z, value);
                }
            }
        }
    }
}
