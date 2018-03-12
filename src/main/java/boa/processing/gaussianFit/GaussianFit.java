/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.processing.gaussianFit;

import boa.gui.imageInteraction.IJImageDisplayer;
import static boa.core.Processor.logger;
import boa.data_structure.Region;
import boa.data_structure.Voxel;
import ij.ImagePlus;
import ij.gui.EllipseRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.wrappers.ImgLib2ImageWrapper;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.algorithm.localization.FitFunction;
import net.imglib2.algorithm.localization.LevenbergMarquardtSolver;
import net.imglib2.algorithm.localization.LocalizationUtils;
import net.imglib2.algorithm.localization.Observation;
import net.imglib2.algorithm.localization.PeakFitter;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedByteType;

//import static plugins.Plugin.logger;

/**
 *
 * @author jollion
 */
public class GaussianFit {
    public static Map<Region, double[]> run(Image image, List<Region> peaks, double typicalSigma, double sigmaLow, double sigmaUp, double precision) {
        return run(image, peaks, typicalSigma, sigmaLow, sigmaUp, precision, 300, 1e-3d, 1e-1d);
    }
    
    public static Map<Region, double[]> run(Image image, List<Region> peaks, double typicalSigma, double sigmaLow, double sigmaUp, double precision, int maxIter, double lambda, double termEpsilon ) {
        double[] sigmas = new double[image.getSizeZ()>1 ? 3 :2];
        double[] sigmasLow = new double[image.getSizeZ()>1 ? 3 :2];
        double[] sigmasUp = new double[image.getSizeZ()>1 ? 3 :2];
        double[] precisions = new double[image.getSizeZ()>1 ? 3 :2];
        for (int i = 0; i<sigmas.length; ++i) {
            sigmas[i]=typicalSigma;
            sigmasUp[i] = sigmaUp;
            sigmasLow[i] = sigmaLow;
            precisions[i]=precision;
        }
        return run(image, peaks, new double[][]{sigmas, sigmasLow, sigmasUp, precisions}, maxIter, lambda, termEpsilon);
    }
    /**
     * 
     * @param image
     * @param peaks
     * @param typicalSigmas
     * @param maxIter
     * @param lambda
     * @param termEpsilon
     * @return for each peak array of fitted parameters: coordinates, intensity@peak, 1/sigma2 in each dimension, error
     */
    public static Map<Region, double[]> run(Image image, List<Region> peaks, double[][] typicalSigmas, int maxIter, double lambda, double termEpsilon ) {
        boolean is3D = image.getSizeZ()>1;
        Img img = ImgLib2ImageWrapper.getImage(image);
        //MLEllipticGaussianSimpleEstimator estimator = new MLEllipticGaussianSimpleEstimator(typicalSigmas[0], typicalSigmas[1], typicalSigmas[2]);
        //EllipticGaussianOrtho fitFunction = new EllipticGaussianOrtho();
        //MLGaussianSimpleEstimator estimator = new MLGaussianSimpleEstimator(typicalSigmas[0][0], typicalSigmas[1][0], typicalSigmas[2][0], is3D?3:2);
        
        //FitFunction fitFunction = new Gaussian();
        MLGaussianPlusConstantSimpleEstimator estimator = new MLGaussianPlusConstantSimpleEstimator(typicalSigmas[0][0], typicalSigmas[1][0], typicalSigmas[2][0], is3D?3:2);
        GaussianPlusConstant fitFunction = new GaussianPlusConstant();
        //logger.debug("span: {}", estimator.getDomainSpan());
        Map<Localizable, Region> locObj = new HashMap<Localizable, Region>(peaks.size());
        List<Localizable> peaksLoc = new ArrayList<Localizable>(peaks.size());
        for (Region o : peaks) {
            double[] center = o.getGeomCenter(false);
            if (o.isAbsoluteLandMark()) {
                center[0]-=image.getBoundingBox().getxMin();
                center[1]-=image.getBoundingBox().getyMin();
                center[2]-=image.getBoundingBox().getzMin();
            }
            Localizable l = getLocalizable(center, is3D);
            peaksLoc.add(l);
            locObj.put(l, o);
        }
        LevenbergMarquardtSolver solver = new LevenbergMarquardtSolver(maxIter, lambda, termEpsilon);
        //BruteForceSolver solver = new BruteForceSolver(is3D?new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN, typicalSigmas[3][0], typicalSigmas[3][1], typicalSigmas[3][2]} : new double[] {Double.NaN, Double.NaN, Double.NaN, typicalSigmas[3][0], typicalSigmas[3][1]});
        //BruteForceSolver solver = new BruteForceSolver(is3D?new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN, typicalSigmas[3][0], 2} : new double[] {Double.NaN, Double.NaN, Double.NaN, typicalSigmas[3][0], 2}); // toDo: estimate grayLevel precision
        PeakFitter fitter = new PeakFitter(img, peaksLoc, solver, fitFunction, estimator);
        fitter.setNumThreads(1);
        if ( !fitter.checkInput() || !fitter.process()) {
            //logger.error("Problem with peak fitting: {}", fitter.getErrorMessage());
            return null;
        }
        //logger.debug("Peak fitting of {} peaks, using {} threads, done in {} ms.", peaks.size(), fitter.getNumThreads(), fitter.getProcessingTime());
        
        Map<Localizable, double[]> results = fitter.getResult();
        Map<Region, double[]> results2 = new HashMap<Region, double[]>(results.size());
        for (Entry<Localizable, double[]> e : results.entrySet()) {
            Observation data = LocalizationUtils.gatherObservationData(img, e.getKey(), estimator.getDomainSpan());
            double[] params = new double[e.getValue().length+1];
            System.arraycopy(e.getValue(), 0, params, 0, e.getValue().length);
            params[params.length-1] = Math.sqrt(LevenbergMarquardtSolver.chiSquared(data.X, e.getValue(), data.I, fitFunction)); ///params[is3D?3:2]; // normalized error by intensity & number of pixels
            results2.put(locObj.get(e.getKey()), params);
        }
        return results2;
                
        //return null;
    }
    public static final double chiSquared(final double[][] x, final double[] a, final double[] y, final FitFunction f)  {
            int npts = y.length;
            double sum = 0.;

            for( int i = 0; i < npts; i++ ) {
                    double d = y[i] - f.val(x[i], a);
                    sum = sum + (d*d);
            }

            return sum;
    }
    
    private static Localizable getLocalizable(double[] v, boolean is3D) {
        if (is3D) return new Point((long)(v[0]+0.5d), (long)(v[1]+0.5d), (long)(v[2]+0.5d));
        else return new Point((long)(v[0]+0.5d), (long)(v[1]+0.5d));
    }
    public static void display2DImageAndRois(Image image, Map<Region, double[]> params) {
        ImagePlus ip = new IJImageDisplayer().showImage(image);
        final Overlay overlay = new Overlay();
        ip.setOverlay(overlay);
        TreeMap<Region, double[]> paramsSort = new TreeMap<Region, double[]>(new Comparator<Region>() {

            public int compare(Region arg0, Region arg1) {
                return Integer.compare(arg0.getLabel(), arg1.getLabel());
            }
        });
        paramsSort.putAll(params);
        for (Entry<Region, double[]> e : paramsSort.entrySet()) overlay.add(get2DEllipse(e.getKey(), e.getValue(), null));
    }
    public static void appendRois(Overlay overlay, BoundingBox offset, Map<Region, double[]> params) {
        TreeMap<Region, double[]> paramsSort = new TreeMap<Region, double[]>(new Comparator<Region>() {

            public int compare(Region arg0, Region arg1) {
                return Integer.compare(arg0.getLabel(), arg1.getLabel());
            }
        });
        paramsSort.putAll(params);
        for (Entry<Region, double[]> e : paramsSort.entrySet()) overlay.add(get2DEllipse(e.getKey(), e.getValue(), offset));
    }
    
    public static Roi get2DEllipse(Region o, double[] p, BoundingBox offset) {
        double Ar = p[2];
        double x = p[0];
        double y = p[1];
        double sx = p[3];
        double sy = p[3];
        double C = p[4];
        double error = p[p.length-1];
        if (offset!=null) {
            x+=offset.getxMin();
            y+=offset.getyMin();
        }
        //double sy = p[4];
        //double sx = 1/Math.sqrt(p[3]);
        //double sy = 1/Math.sqrt(p[4]);

        // Draw ellipse on the target image
        double x1, x2, y1, y2, ar;
        if (sy < sx) {
                x1 = x - 2.3548 * sx / 2 + 0.5;
                x2 = x + 2.3548 * sx / 2 + 0.5;
                y1 = y + 0.5;
                y2 = y + 0.5;
                ar = sy / sx; 
        } else {
                x1 = x + 0.5;
                x2 = x + 0.5;
                y1 = y - 2.3548 * sy / 2 + 0.5;
                y2 = y + 2.3548 * sy / 2 + 0.5; 
                ar = sx / sy; 
        }
        //logger.debug("gaussian fit on seed: {}; center: {}, x: {}, y: {}, I: {}, sigmaX: {}, sigmaY: {}, error: {}", o.getLabel(), o.getCenter(),x, y, Ar, sx, sy, error);
        logger.debug("gaussian fit on seed: {}; center: {}, sigmaX: {}, A: {}, C:{}, error: {}", o.getLabel(), o.getGeomCenter(false), sx, Ar, C, error);
        return new EllipseRoi(x1, y1, x2, y2, ar);
    }
}
