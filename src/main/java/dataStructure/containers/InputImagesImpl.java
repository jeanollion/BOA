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
package dataStructure.containers;

import image.Image;
import static image.Image.logger;
import plugins.Transformation;
import plugins.TransformationTimeIndependent;
import utils.ArrayUtil;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.ThreadRunner.ThreadAction2;

/**
 *
 * @author jollion
 */
public class InputImagesImpl implements InputImages {
    InputImage[][] imageCT;
    int defaultTimePoint;
    int frameNumber;
    
    public InputImagesImpl(InputImage[][] imageTC, int defaultTimePoint) {
        this.imageCT = imageTC;
        this.defaultTimePoint= defaultTimePoint;
        for (int c = 0; c<imageTC.length; ++c) if (imageTC[c].length>frameNumber) frameNumber = imageTC[c].length;
    }
    
    public InputImagesImpl duplicate() {
        InputImage[][] imageCTDup = new InputImage[imageCT.length][];
        for (int i = 0; i<imageCT.length; ++i) {
            imageCTDup[i] = new InputImage[imageCT[i].length];
            for (int j = 0; j<imageCT[i].length; ++j) imageCTDup[i][j] = imageCT[i][j].duplicate();
        }
        return new InputImagesImpl(imageCTDup, defaultTimePoint);
    }
    
    @Override public int getFrameNumber() {return frameNumber;}
    @Override public int getChannelNumber() {return imageCT.length;}
    @Override public int getDefaultTimePoint() {return defaultTimePoint;}
    @Override public int getSizeZ(int channelIdx) {return imageCT[channelIdx][0].imageSources.getSizeZ(channelIdx);}
    @Override public double getCalibratedTimePoint(int c, int t, int z) {
        if (singleFrameChannel(c)) { // adjecent channel
            int c2=c;
            if (c>0) c2--;
            else {
                c2++;
                if (imageCT.length<=c2) return Double.NaN;
            }
            if (singleFrameChannel(c2)) {
                if (c>1) c2=c-2;
                else {
                    c2=c+2;
                    if (imageCT.length<=c2) return Double.NaN;
                }
                if (singleFrameChannel(c2)) return Double.NaN;
            }
            c=c2;
        }
        return imageCT[c][t].imageSources.getCalibratedTimePoint(t, c, z);
    }
    @Override public boolean singleFrameChannel(int channelIdx) {
        return imageCT[channelIdx].length==1;
    }
    public void addTransformation(int inputChannel, int[] channelIndicies, Transformation transfo) {
        if (channelIndicies!=null) for (int c : channelIndicies) addTransformation(c, transfo);
        else {
            if (transfo.getOutputChannelSelectionMode()==Transformation.SelectionMode.SAME) addTransformation(inputChannel, transfo);
            else for (int c = 0; c<getChannelNumber(); ++c) addTransformation(c, transfo);
        }
    }
    
    public void addTransformation(int channelIdx, Transformation transfo) {
        for (int t = 0; t<this.imageCT[channelIdx].length; ++t) {
            imageCT[channelIdx][t].addTransformation(transfo);
        }
    }

    @Override public Image getImage(int channelIdx, int timePoint) { 
        // TODO: gestion de la memoire: si besoin save & close d'une existante. Attention a signal & réouvrir depuis le DAO et non depuis l'original
        if (imageCT[channelIdx].length==1) timePoint = 0;
        return imageCT[channelIdx][timePoint].getImage();
    }
    public Image[][] getImagesTC() {
        return getImagesTC(0, this.getFrameNumber());
    }
    public Image[][] getImagesTC(int frameMin, int frameMaxExcluded, int... channels) {
        if (channels==null || channels.length==0) channels = ArrayUtil.generateIntegerArray(this.getChannelNumber());
        if (frameMin>=this.getFrameNumber()) {
            frameMin=this.getFrameNumber()-1;
            frameMaxExcluded = this.getFrameNumber();
        }
        if (frameMaxExcluded<frameMin) {
            frameMin = 0;
            frameMaxExcluded = getFrameNumber();
        } else if (frameMaxExcluded==frameMin) frameMaxExcluded+=1;
        if (frameMaxExcluded>=this.getFrameNumber()) frameMaxExcluded = this.getFrameNumber();
        Image[][] imagesTC = new Image[frameMaxExcluded-frameMin][channels.length];
        int cIdx = 0;
        for (int channelIdx : channels) {
            for (int f = frameMin; f<frameMaxExcluded; ++f) {
                imagesTC[f-frameMin][cIdx] = getImage(channelIdx, f).setName("Channel: "+channelIdx+" Frame: "+f);
                if (imagesTC[f-frameMin][cIdx]==null) {
                    logger.debug("could not open image: channel:{} frame:{}", channelIdx, f);
                    return null;
                }
            }
            ++cIdx;
        }
        return imagesTC;
    }
    public void applyTranformationsSaveAndClose() {
        long tStart = System.currentTimeMillis();
        final int cCount = getChannelNumber();
        /*ThreadAction<InputImage> ta = (InputImage image, int idx, int threadIdx) -> {
            image.getImage();
            image.closeImage();
            logger.debug("apply transfo: frame {}", idx);
        };*/
        ThreadAction<InputImage> ta = new ThreadAction<InputImage>() {
            @Override
            public void run(InputImage image, int idx, int threadIdx) {
                image.getImage();
                image.closeImage();
                //logger.debug("apply transfo: frame {}", idx);
            }
        };
        for (int c = 0; c<getChannelNumber(); ++c) {
            //logger.debug("apply transfo: channel {}", c);
            ThreadRunner.execute(imageCT[c], false, ta);
        }
        
        /*
        int tCount = getTimePointNumber();
        for (int t = 0; t<tCount; ++t) {
            for (int c = 0; c<cCount; ++c) {
                imageTC[t][c].getImage();
                imageTC[t][c].saveToDAO=true;
                imageTC[t][c].closeImage();
            }
        }
        */
        long tEnd = System.currentTimeMillis();
        logger.debug("apply transformation & save: total time: {}, for {} time points and {} channels", tEnd-tStart, getFrameNumber(), cCount );
    }
    
    public void deleteFromDAO() {
        for (int c = 0; c<getChannelNumber(); ++c) {
            for (int t = 0; t<imageCT[c].length; ++t) {
                imageCT[c][t].deleteFromDAO();
            }
        }
    }
    
    @Override public void flush() {
        for (int c = 0; c<getChannelNumber(); ++c) {
            for (int t = 0; t<imageCT[c].length; ++t) {
                imageCT[c][t].flush();
            }
        }
    }
    
    /**
     * Remove all time points excluding time points between {@param tStart} and {@param tEnd}, for testing purposes only
     * @param tStart
     * @param tEnd 
     */
    public void subSetTimePoints(int tStart, int tEnd) {
        if (tStart<0) tStart=0; 
        if (tEnd>=getFrameNumber()) tEnd = getFrameNumber()-1;
        InputImage[][] newImageCT = new InputImage[this.getChannelNumber()][];
        
        for (int c=0; c<getChannelNumber(); ++c) {
            if (imageCT[c].length==1) {
                newImageCT[c] = new InputImage[1];
                newImageCT[c][0] = imageCT[c][0];
            } else {
                newImageCT[c] = new InputImage[tEnd-tStart+1];
                for (int t=tStart; t<=tEnd; ++t) {
                    newImageCT[c][t-tStart] = imageCT[c][t];
                    imageCT[c][t].setTimePoint(t-tStart);
                }
            }
        }
        if (this.defaultTimePoint<tStart) defaultTimePoint = 0;
        if (this.defaultTimePoint>tEnd-tStart) defaultTimePoint = tEnd-tStart;
        logger.debug("default time Point: {}", defaultTimePoint);
        this.imageCT=newImageCT;
    }

}
