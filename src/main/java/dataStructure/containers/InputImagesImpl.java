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
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.ThreadRunner.ThreadAction2;

/**
 *
 * @author jollion
 */
public class InputImagesImpl implements InputImages {
    InputImage[][] imageTC;
    int defaultTimePoint;
    
    public InputImagesImpl(InputImage[][] imageTC, int defaultTimePoint) {
        this.imageTC = imageTC;
        this.defaultTimePoint= defaultTimePoint;
    }
    
    @Override public int getTimePointNumber() {return imageTC.length;}
    @Override public int getChannelNumber() {return imageTC[0].length;}
    @Override public int getDefaultTimePoint() {return defaultTimePoint;}
    @Override public int getSizeZ(int channelIdx) {return imageTC[0][channelIdx].imageSources.getSizeZ(channelIdx);}
    @Override public double getCalibratedTimePoint(int c, int t, int z) {
        return imageTC[t][c].imageSources.getCalibratedTimePoint(t, c, z);
    }
    public void addTransformation(int inputChannel, int[] channelIndicies, Transformation transfo) {
        if (channelIndicies!=null) for (int c : channelIndicies) addTransformation(c, transfo);
        else {
            if (transfo instanceof Transformation &&  ((Transformation)transfo).getOutputChannelSelectionMode()==Transformation.SelectionMode.SAME) addTransformation(inputChannel, transfo);
            else for (int c = 0; c<imageTC[0].length; ++c) addTransformation(c, transfo);
        }
    }
    
    public void addTransformation(int channelIdx, Transformation transfo) {
        for (int t = 0; t<this.imageTC.length; ++t) {
            imageTC[t][channelIdx].addTransformation(transfo);
        }
    }

    public Image getImage(int channelIdx, int timePoint) { 
        // TODO: gestion de la memoire: si besoin save & close d'une existante. Attention a signal & réouvrir depuis le DAO et non depuis l'original
        return imageTC[timePoint][channelIdx].getImage();
    }
    
    public void applyTranformationsSaveAndClose() {
        long tStart = System.currentTimeMillis();
        final int cCount = getChannelNumber();
        ThreadRunner.execute(imageTC, false, new ThreadAction2<InputImage[]>() {
            //MultipleImageContainer container;
            @Override public void setUp() {
                //container = imageTC[0][0].duplicateContainer();
                //logger.debug("Creating container for thread: {}, id: {}", Thread.currentThread(), container);
            }
            @Override public void tearDown() {
                //container.close();
            };
            @Override
            public void run(InputImage[] imageC, int idx, int threadIdx) {
                //long tStart = System.currentTimeMillis();
                for (int c = 0; c<cCount; ++c) {
                    imageC[c].getImage();
                    imageC[c].closeImage();
                }
                //long tEnd = System.currentTimeMillis();
                //logger.debug("apply transformation & save: {}", tEnd-tStart);
            }
        });
        
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
        logger.debug("apply transformation & save: total time: {}, for {} time points and {} channels", tEnd-tStart, getTimePointNumber(), cCount );
    }
    
    public void deleteFromDAO() {
        int tCount = getTimePointNumber();
        int cCount = getChannelNumber();
        for (int t = 0; t<tCount; ++t) {
            for (int c = 0; c<cCount; ++c) {
                imageTC[t][c].deleteFromDAO();
            }
        }
    }
    
    @Override public void flush() {
        int tCount = getTimePointNumber();
        int cCount = getChannelNumber();
        for (int t = 0; t<tCount; ++t) {
            for (int c = 0; c<cCount; ++c) {
                imageTC[t][c].flush();
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
        if (tEnd>=imageTC.length) tEnd = imageTC.length-1;
        InputImage[][] newImageTC = new InputImage[tEnd-tStart+1][imageTC[0].length];
        for (int t=tStart; t<=tEnd; ++t) {
            for (int c=0; c<imageTC[0].length; ++c) {
                newImageTC[t-tStart][c] = imageTC[t][c];
                imageTC[t][c].setTimePoint(t-tStart);
            }
        }
        if (this.defaultTimePoint<tStart) defaultTimePoint = 0;
        if (this.defaultTimePoint>tEnd-tStart) defaultTimePoint = tEnd-tStart;
        logger.debug("default time Point: {}", defaultTimePoint);
        this.imageTC=newImageTC;
    }

}
