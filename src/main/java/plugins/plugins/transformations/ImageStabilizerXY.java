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
package plugins.plugins.transformations;

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChannelImageParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.GroupParameter;
import configuration.parameters.ListParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.TimePointParameter;
import dataStructure.containers.InputImages;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import image.IJImageWrapper;
import image.Image;
import image.ImageFloat;
import image.TypeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import jj2000.j2k.util.ArrayUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.Plugin;
import plugins.Transformation;
import static plugins.plugins.transformations.ImageStabilizerCore.combine;
import static plugins.plugins.transformations.ImageStabilizerCore.copy;
import static plugins.plugins.transformations.ImageStabilizerCore.gradient;
import processing.ImageTransformation;
import utils.HashMapGetCreate;
import utils.Pair;
import utils.ReusableQueue;
import utils.ReusableQueueWithSourceObject;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.Utils;

/**
 *
 * @author nasique
 */
public class ImageStabilizerXY implements Transformation {
    public final static Logger logger = LoggerFactory.getLogger(ImageStabilizerXY.class);
    ChoiceParameter transformationType = new ChoiceParameter("Transformation", new String[]{"Translation"}, "Translation", false); //, "Affine"
    ChoiceParameter pyramidLevel = new ChoiceParameter("Pyramid Level", new String[]{"0", "1", "2", "3", "4"}, "1", false);
    BoundedNumberParameter alpha = new BoundedNumberParameter("Template Update Coefficient", 2, 1, 0, 1);
    BoundedNumberParameter maxIter = new BoundedNumberParameter("Maximum Iterations", 0, 1000, 1, null);
    BoundedNumberParameter segmentLength = new BoundedNumberParameter("Segment length", 0, 20, 2, null);
    NumberParameter tol = new BoundedNumberParameter("Error Tolerance", 12, 5e-8, 0, null);
    SimpleListParameter<GroupParameter> additionalTranslation = new SimpleListParameter<GroupParameter>("Additional Translation", new GroupParameter("Channel Translation", new ChannelImageParameter("Channel"), new NumberParameter("dX", 3, 0), new NumberParameter("dY", 3, 0), new NumberParameter("dZ", 3, 0)));
    Parameter[] parameters = new Parameter[]{maxIter, tol, pyramidLevel, segmentLength, additionalTranslation}; //alpha
    ArrayList<ArrayList<Double>> translationTXY = new ArrayList<ArrayList<Double>>();
    public static boolean debug=false;
    public ImageStabilizerXY(){}
    
    public ImageStabilizerXY(int pyramidLevel, int maxIterations, double tolerance, int segmentLength) {
        this.pyramidLevel.setSelectedIndex(pyramidLevel);
        this.tol.setValue(tolerance);
        this.maxIter.setValue(maxIterations);
        this.segmentLength.setValue(segmentLength);
    }
    
    public ImageStabilizerXY setAdditionalTranslation(int channelIdx, double... deltas) {
        if (getAdditionalTranslationParameter(channelIdx, false)!=null) throw new IllegalArgumentException("Translation already set for channel: "+channelIdx);
        GroupParameter g = additionalTranslation.createChildInstance();
        additionalTranslation.insert(g);
        ((ChannelImageParameter)g.getChildAt(0)).setSelectedStructureIdx(channelIdx);
        for (int i = 0; i<Math.min(3, deltas.length); ++i) ((NumberParameter)g.getChildAt(i+ 1)).setValue(deltas[i]);
        return this;
    }
    
    /*public void computeConfigurationData2(int channelIdx, InputImages inputImages) {
        Image imageRef = inputImages.getImage(channelIdx, 0);
        ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
        translationTXY = new ArrayList<Double[]>(inputImages.getMaxTimePoint());
        FloatProcessor ipRef = getFloatProcessor(imageRef, false);
        FloatProcessor currentIp;
        translationTXY.add(new Double[2]);
        for (int t = 1; t<inputImages.getMaxTimePoint(); ++t) {
            currentIp = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
            translationTXY[0][t] = performCorrection2(ipRef, currentIp, pyramids);
            ipRef = currentIp;
            logger.debug("ImageStabilizerXY: timepoint: {} dX: {} dY: {}", t, translationTXY[0][t][0], translationTXY[0][t][1]);
        }
        for (int t = 2; t<inputImages.getMaxTimePoint(); ++t) {
            translationTXY[0][t][0]+=translationTXY[0][t-1][0];
            translationTXY[0][t][1]+=translationTXY[0][t-1][1];
        }
    }*/
    
    public void computeConfigurationData(final int channelIdx, final InputImages inputImages) {
        long tStart = System.currentTimeMillis();
        final int tRef = inputImages.getDefaultTimePoint();
        //final int tRef=0;
        final int maxIterations = this.maxIter.getValue().intValue();
        final double tolerance = this.tol.getValue().doubleValue();
        
        //new IJImageDisplayer().showImage(imageRef.setName("ref image"));
        //if (true) return;
        final Double[][] translationTXYArray = new Double[inputImages.getFrameNumber()][];
      
        ccdSegments(channelIdx, inputImages, segmentLength.getValue().intValue(), tRef, translationTXYArray, maxIterations, tolerance);
        
        translationTXY = new ArrayList<ArrayList<Double>>(translationTXYArray.length);
        for (Double[] d : translationTXYArray) translationTXY.add(new ArrayList<Double>(Arrays.asList(d)));
        long tEnd = System.currentTimeMillis();
        logger.debug("ImageStabilizerXY: total estimation time: {}, reference timePoint: {}", tEnd-tStart, tRef);
    }
    
    protected void ccdSegments(final int channelIdx, final InputImages inputImages, int segmentLength, int tRef, final Double[][] translationTXYArray, final int maxIterations, final double tolerance) {
        if (segmentLength<2) segmentLength = 2;
        int nSegments = (int)(0.5 +(double)(inputImages.getFrameNumber()-1) / (double)segmentLength) ;
        if (nSegments<1) nSegments=1;
        int[][] segments = new int[nSegments][3]; // tStart, tEnd, tRef
        if (debug) logger.debug("n segment: {}, {}", segments.length);
        final Map<Integer, Integer> mapImageToRef = new HashMap<>(inputImages.getFrameNumber());
        for (int i = 0; i<nSegments; ++i) {
            segments[i][0] = i==0 ? 0 : segments[i-1][1]+1;
            segments[i][1] = i==segments.length-1 ? inputImages.getFrameNumber()-1 : segments[i][0]+segmentLength-1;
            segments[i][2] = i==0 ? Math.min(Math.max(0, tRef), segments[i][1]) : segments[i-1][1]; 
            for (int j = segments[i][0]; j<=segments[i][1]; ++j) mapImageToRef.put(j, segments[i][2]);
            if (debug) logger.debug("segment: {}, {}", i, segments[i]);
        }
        if (debug)logger.debug("im to ref map: {}", mapImageToRef);
        Image refImage = inputImages.getImage(channelIdx, tRef);
        // process each segment
        final HashMapGetCreate<Integer, FloatProcessor> processorMap = new HashMapGetCreate<>(i-> getFloatProcessor(inputImages.getImage(channelIdx, i), false));
        ReusableQueueWithSourceObject.Reset<Bucket, Integer> r = (bucket, imageRefIdx) -> {
            if (bucket.imageRefIdx!=imageRefIdx) { // only compute gradient if reference image is different
                gradient(bucket.pyramid[1][0], processorMap.getAndCreateIfNecessarySync(imageRefIdx));
                bucket.imageRefIdx=imageRefIdx;
            }
            return bucket;
        };
        ReusableQueueWithSourceObject.Factory<Bucket, Integer> f = (imageRefIdx) -> {
            Bucket res = new Bucket(refImage, pyramidLevel.getSelectedIndex());
            return r.reset(res, imageRefIdx);
        };
        ReusableQueueWithSourceObject<Bucket, Integer> pyramids = new ReusableQueueWithSourceObject(f, r, true);
        List<Entry<Integer, Integer>> l = new ArrayList<>(mapImageToRef.entrySet());
        Collections.shuffle(l); // shuffle so that pyramids with given gradient have more chance to be used several times
        //if (debug) logger.debug("will execute: {} processes", l.size());
        List<Pair<String, Exception>> ex = ThreadRunner.execute(l, false, (Entry<Integer, Integer> p, int idx) -> {
            double[] outParams = new double[2];
            if (p.getKey()==tRef) translationTXYArray[p.getKey()] = new Double[]{0d, 0d};
            else {
                Bucket b = pyramids.poll(p.getValue());
                translationTXYArray[p.getKey()] = performCorrection(processorMap, p.getKey(), b.pyramid, outParams);
                pyramids.push(b, p.getValue());
            }
            if (debug) logger.debug("t: {}, tRef: {}, dX: {}, dY: {}, rmse: {}, iterations: {}", p.getKey(), p.getValue(), translationTXYArray[p.getKey()][0], translationTXYArray[p.getKey()][1], outParams[0], outParams[1]);
        }, null, ThreadRunner.loggerProgressCallback(logger));
        if (debug) for (Pair<String, Exception> p : ex) logger.debug(p.key, p.value);
        // translate shifts
        for (int i = 1; i<segments.length; ++i) {
            Double[] ref = translationTXYArray[segments[i][2]];
            for (int t = segments[i][0]; t<=segments[i][1]; ++t) {
                translationTXYArray[t][0]+=ref[0];
                translationTXYArray[t][1]+=ref[1];
            }
            if (debug) logger.debug("ref: {}, tp: {}, trans: {}", i,segments[i][2], ref);
        }
    }
    
    protected void ccdSegment(final int channelIdx, final InputImages inputImages, final int tStart, final int tEnd, final int tRef, final int nThreads, final Double[][] translationTXYArray, final int maxIterations, final double tolerance) {
        //logger.debug("start: {}, stop: {}, ref: {}, nThreads: {}", tStart, tEnd, tRef, nThreads);
        final Image imageRef = inputImages.getImage(channelIdx, tRef);
        final FloatProcessor ipFloatRef = getFloatProcessor(imageRef, false);
        final ThreadRunner tr = new ThreadRunner(tStart, tEnd+1, nThreads);
        final ImageProcessor[][][] pyramids = new ImageProcessor[tr.threads.length][][];
        pyramids[0] = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
        gradient(pyramids[0][1][0], ipFloatRef);
        for (int i = 1; i<tr.threads.length; ++i) {
            pyramids[i] = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
            copy(pyramids[i][1][0], pyramids[0][1][0]);
        }
        for (int i = 0; i<tr.threads.length; i++) {
            final int trIdx = i;
            tr.threads[i] = new Thread(
                new Runnable() {
                    public void run() {
                        for (int t = tr.ai.getAndIncrement(); t<tr.end; t = tr.ai.getAndIncrement()) {
                            double[] outParams = new double[2];
                            if (t==tRef) translationTXYArray[t] = new Double[]{0d, 0d};
                            else translationTXYArray[t] = performCorrection(channelIdx, inputImages, t, pyramids[trIdx], outParams);
                            if (debug) logger.debug("t: {}, tRef: {}, dX: {}, dY: {}, rmse: {}, iterations: {}", t, tRef, translationTXYArray[t][0], translationTXYArray[t][1], outParams[0], outParams[1]);
                        }
                    }
                }
            );
        }
        tr.startAndJoin();
        
    }
    
    protected void ccdSegmentTemplateUpdate(final int channelIdx, final InputImages inputImages, final int tStart, final int tEnd, final int tRef, final Double[][] translationTXYArray, final int maxIterations, final double tolerance) {
        
        final Image imageRef = inputImages.getImage(channelIdx, tRef);
        FloatProcessor ipFloatRef = getFloatProcessor(imageRef, true);
        ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
        FloatProcessor trans=null;
        double a = alpha.getValue().doubleValue();
        translationTXYArray[tRef] = new Double[]{0d, 0d};
        if (a<1) trans  = new FloatProcessor(imageRef.getSizeX(), imageRef.getSizeY());
        for (int t = tRef-1; t>=0; --t) translationTXYArray[t] = performCorrectionWithTemplateUpdate(channelIdx, inputImages, t, ipFloatRef, pyramids, trans, maxIterations, tolerance, a, translationTXYArray[t+1]);
        if (a<1 && tRef>0) ipFloatRef = getFloatProcessor(imageRef, true); // reset template
        for (int t = tRef+1; t<inputImages.getFrameNumber(); ++t) translationTXYArray[t] = performCorrectionWithTemplateUpdate(channelIdx, inputImages, t, ipFloatRef, pyramids, trans, maxIterations, tolerance, a, translationTXYArray[t-1]);
        
    }
    /*
    protected void ccdTimeToTime(final int channelIdx, final InputImages inputImages, final int tRef, final Double[][] translationTXYArray, final int maxIterations, final double tolerance) {
        
        
        // multithreaded version: 
        // cut in time segments
        int nThreads = ThreadRunner.getMaxCPUs();
        int length = (int)((double)inputImages.getFrameNumber() / (double)nThreads) ;
        int[][] segments = new int[nThreads][2];
        for (int i = 0; i<nThreads; ++i) {
            segments[i][0] = i==0 ? 0 : segments[i-1][1]+1;
            segments[i][1] = i==segments.length-1 ? inputImages.getFrameNumber()-1 : segments[i][0]+length;
            //logger.debug("segment: {}, {}", i, segments[i]);
        }
        ThreadRunner.execute(segments, false, new ThreadAction<int[]>() {
            public void run(int[] tp, int idx, int threadIdx) {
                Image imageRef = inputImages.getImage(channelIdx, tp[0]);
                FloatProcessor ipFloatRef = getFloatProcessor(imageRef, false);
                ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
                translationTXYArray[tp[0]] = new Double[]{0d, 0d};
                ImageProcessor[] pyrRef = pyramids[1];
                ImageProcessor[] pyrCurrent = pyramids[0];
                ImageProcessor[] temp;
                gradient(pyrRef[0], ipFloatRef);
                double[] outParam = new double[2];
                for (int t = tp[0]+1; t<=tp[1]; ++t) {
                    FloatProcessor ipFloat = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
                    double[][] wp = ImageStabilizerCore.estimateTranslation(ipFloat, null, pyrCurrent, pyrRef, false, maxIterations, tolerance, null, outParam);
                    logger.debug("Stab: t1: {}, t2: {}, dX: {} dY: {}", t-1, t, wp[0][0], wp[1][0]);
                    translationTXYArray[t] = new Double[]{wp[0][0], wp[1][0]};
                    // exchange pyr -> in next timepoint, the ref will be the current
                    temp = pyrRef;
                    pyrRef = pyrCurrent;
                    pyrCurrent = temp;
                }
            }
        });
        // cumulative shift
        for (int t = 1; t<inputImages.getFrameNumber(); ++t) {
            translationTXYArray[t][0]+=translationTXYArray[t-1][0];
            translationTXYArray[t][1]+=translationTXYArray[t-1][1];
        }
        double[] shiftRef = new double[]{translationTXYArray[tRef][0], translationTXYArray[tRef][1]};
        for (int t = 0; t<inputImages.getFrameNumber(); ++t) {
            translationTXYArray[t][0]-=shiftRef[0];
            translationTXYArray[t][1]-=shiftRef[1];
        }
        
    }
    */
    
    public static Image testTranslate(Image imageRef, Image imageToTranslate, int maxIterations, double maxTolerance, int pyramidLevel) {
        FloatProcessor ipFloat1 = getFloatProcessor(imageRef, true);
        FloatProcessor ipFloat2 = getFloatProcessor(imageToTranslate, true);
        
        ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel);
        double[] outParam = new double[2];
        double[][] wp = ImageStabilizerCore.estimateTranslation(ipFloat2, ipFloat1, pyramids[0], pyramids[1], true, maxIterations, maxTolerance, null, outParam);
        logger.debug("dX: {}, dY: {}, rmse: {}, iterations: {}", wp[0][0], wp[1][0], outParam[0], outParam[1]);
        return ImageTransformation.translate(imageToTranslate, -wp[0][0], -wp[1][0], 0, ImageTransformation.InterpolationScheme.BSPLINE5);
    }
    
    private Double[] performCorrection(HashMapGetCreate<Integer, FloatProcessor> processorMap, int t, ImageProcessor[][] pyramids, double[] outParameters) {
        long t0 = System.currentTimeMillis();
        FloatProcessor currentTime = processorMap.getAndCreateIfNecessarySync(t); 
        long tStart = System.currentTimeMillis();
        double[][] wp = ImageStabilizerCore.estimateTranslation(currentTime, null, pyramids[0], pyramids[1], false, maxIter.getValue().intValue(), tol.getValue().doubleValue(), null, outParameters);
        long tEnd = System.currentTimeMillis();
        Double[] res =  new Double[]{wp[0][0], wp[1][0]};
        logger.debug("ImageStabilizerXY: timepoint: {} dX: {} dY: {}, open & preProcess time: {}, estimate translation time: {}", t, res[0], res[1], tStart-t0, tEnd-tStart);
        return res;
    }
    
    private Double[] performCorrection(int channelIdx, InputImages inputImages, int t, ImageProcessor[][] pyramids, double[] outParameters) {
        long t0 = System.currentTimeMillis();
        FloatProcessor currentTime = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
        long tStart = System.currentTimeMillis();
        double[][] wp = ImageStabilizerCore.estimateTranslation(currentTime, null, pyramids[0], pyramids[1], false, maxIter.getValue().intValue(), tol.getValue().doubleValue(), null, outParameters);
        long tEnd = System.currentTimeMillis();
        Double[] res =  new Double[]{wp[0][0], wp[1][0]};
        //logger.debug("ImageStabilizerXY: timepoint: {} dX: {} dY: {}, open & preProcess time: {}, estimate translation time: {}", t, res[0], res[1], tStart-t0, tEnd-tStart);
        return res;
    }
    
    private Double[] performCorrectionWithTemplateUpdate(int channelIdx, InputImages inputImages, int t, FloatProcessor ipFloatRef, ImageProcessor[][] pyramids,  FloatProcessor trans, int maxIterations, double tolerance, double alpha, Double[] estimateShift) {
        long t0 = System.currentTimeMillis();
        FloatProcessor currentTime = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
        long tStart = System.currentTimeMillis();
        double[] outParam = new double[2];
        double[][] wp = ImageStabilizerCore.estimateTranslation(currentTime, ipFloatRef, pyramids[0], pyramids[1], true, maxIterations, tolerance, estimateShift, outParam);
        long tEnd = System.currentTimeMillis();
        Double[] res =  new Double[]{wp[0][0], wp[1][0]};
        logger.debug("ImageStabilizerXY: timepoint: {} dX: {} dY: {}, open & preProcess time: {}, estimate translation time: {}", t, res[0], res[1], tStart-t0, tEnd-tStart);
        //update template 
        if (alpha<1) {
            ImageStabilizerCore.warpTranslation(trans, currentTime, wp);
            ImageStabilizerCore.combine(ipFloatRef, trans, alpha);
        }
        return res;
    }
    
    private static FloatProcessor getFloatProcessor(Image image, boolean duplicate) {
        if (image.getSizeZ()>1) image = image.getZPlane((int)(image.getSizeZ()/2.0+0.5)); //select middle slice only
        if (!(image instanceof ImageFloat)) image = TypeConverter.toFloat(image, null);
        else if (duplicate) image = image.duplicate("");
        ImagePlus impRef = IJImageWrapper.getImagePlus(image);
        return (FloatProcessor)impRef.getProcessor();
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        ArrayList<Double> trans = translationTXY.get(timePoint);
        //logger.debug("stabilization time: {}, channel: {}, X:{}, Y:{}", timePoint, channelIdx, trans.get(0), trans.get(1));
        if (trans.get(0)==0 && trans.get(1)==0) return image;
        if (!(image instanceof ImageFloat)) image = TypeConverter.toFloat(image, null);
        double[] additionalTranslationValue = getAdditionalTranslation(channelIdx);
        //if (timePoint<=0) logger.debug("add trans for channel: {} = {}", channelIdx, additionalTranslationValue);
        return ImageTransformation.translate(image, -trans.get(0)+additionalTranslationValue[0], -trans.get(1)+additionalTranslationValue[1], +additionalTranslationValue[2], ImageTransformation.InterpolationScheme.BSPLINE5);
    }
    private GroupParameter getAdditionalTranslationParameter(int channelIdx, boolean onlyActivated) {
        List<GroupParameter> list = onlyActivated ? additionalTranslation.getActivatedChildren() : additionalTranslation.getChildren();
        for (GroupParameter g : list) {
            if (((ChannelImageParameter)g.getChildAt(0)).getSelectedIndex()==channelIdx) return g;
        }
        return null;
    }
    private double[] getAdditionalTranslation(int channelIdx) {
        double[] res = new double[3];
        GroupParameter g = getAdditionalTranslationParameter(channelIdx, true);
        if (g!=null) {
            for (int i = 0; i<3; ++i) res[i] = ((NumberParameter)g.getChildAt(i+1)).getValue().doubleValue();
        }
        return res;
    }

    public ArrayList getConfigurationData() {
        return this.translationTXY;
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return translationTXY!=null && translationTXY.size()==totalTimePointNumber;
    } 

    public boolean isTimeDependent() {
        return true;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }
    private static class Bucket {
        ImageProcessor[][] pyramid;
        int imageRefIdx;
        public Bucket(Image imageRef, int pyramidLevel) {
            pyramid = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel);
            this.imageRefIdx=-1;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 29 * hash + this.imageRefIdx;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Bucket other = (Bucket) obj;
            if (this.imageRefIdx != other.imageRefIdx) {
                return false;
            }
            return true;
        }
        
    }
    
}
