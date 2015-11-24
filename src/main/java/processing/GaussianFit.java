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
package processing;

import boa.gui.imageInteraction.IJImageDisplayer;
import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel;
import ij.ImagePlus;
import ij.gui.EllipseRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import image.Image;
import image.ImgLib2ImageWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.localization.BruteForceSolver;
import net.imglib2.algorithm.localization.EllipticGaussianOrtho;
import net.imglib2.algorithm.localization.FunctionFitter;
import net.imglib2.algorithm.localization.Gaussian;
import net.imglib2.algorithm.localization.LevenbergMarquardtSolver;
import net.imglib2.algorithm.localization.LocalizationUtils;
import net.imglib2.algorithm.localization.MLEllipticGaussianEstimator;
import net.imglib2.algorithm.localization.MLEllipticGaussianSimpleEstimator;
import net.imglib2.algorithm.localization.MLGaussianSimpleEstimator;
import net.imglib2.algorithm.localization.Observation;
import net.imglib2.algorithm.localization.PeakFitter;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import static plugins.Plugin.logger;

/**
 *
 * @author jollion
 */
public class GaussianFit {
    public static Map<Object3D, double[]> run(Image image, List<Object3D> peaks, double typicalSigma, double sigmaLow, double sigmaUp, double precision) {
        return run(image, peaks, typicalSigma, sigmaLow, sigmaUp, precision, 300, 1e-3d, 1e-1d);
    }
    
    public static Map<Object3D, double[]> run(Image image, List<Object3D> peaks, double typicalSigma, double sigmaLow, double sigmaUp, double precision, int maxIter, double lambda, double termEpsilon ) {
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
    public static Map<Object3D, double[]> run(Image image, List<Object3D> peaks, double[][] typicalSigmas, int maxIter, double lambda, double termEpsilon ) {
        boolean is3D = image.getSizeZ()>1;
        Img img = ImgLib2ImageWrapper.getImage(image);
        //MLEllipticGaussianSimpleEstimator estimator = new MLEllipticGaussianSimpleEstimator(typicalSigmas[0], typicalSigmas[1], typicalSigmas[2]);
        MLGaussianSimpleEstimator estimator = new MLGaussianSimpleEstimator(typicalSigmas[0][0], typicalSigmas[1][0], typicalSigmas[2][0], is3D?3:2);
        //EllipticGaussianOrtho fitFunction = new EllipticGaussianOrtho();
        Gaussian fitFunction = new Gaussian();
        Map<Localizable, Object3D> locObj = new HashMap<Localizable, Object3D>(peaks.size());
        List<Localizable> peaksLoc = new ArrayList<Localizable>(peaks.size());
        for (Object3D o : peaks) {
            Localizable l = getLocalizable(o.getCenter(), is3D);
            peaksLoc.add(l);
            locObj.put(l, o);
        }
        //LevenbergMarquardtSolver solver = new LevenbergMarquardtSolver(maxIter, lambda, termEpsilon);
        //BruteForceSolver solver = new BruteForceSolver(is3D?new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN, typicalSigmas[3][0], typicalSigmas[3][1], typicalSigmas[3][2]} : new double[] {Double.NaN, Double.NaN, Double.NaN, typicalSigmas[3][0], typicalSigmas[3][1]});
        BruteForceSolver solver = new BruteForceSolver(is3D?new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN, typicalSigmas[3][0]} : new double[] {Double.NaN, Double.NaN, Double.NaN, typicalSigmas[3][0]});
        PeakFitter<UnsignedByteType> fitter = new PeakFitter<UnsignedByteType>(img, peaksLoc, solver, fitFunction, estimator);
        fitter.setNumThreads(1);
        if ( !fitter.checkInput() || !fitter.process()) {
            logger.error("Problem with peak fitting: {}", fitter.getErrorMessage());
            return null;
        }
        logger.debug("Peak fitting of {} peaks, using {} threads, done in {} ms.", peaks.size(), fitter.getNumThreads(), fitter.getProcessingTime());
        
        Map<Localizable, double[]> results = fitter.getResult();
        Map<Object3D, double[]> results2 = new HashMap<Object3D, double[]>(results.size());
        for (Entry<Localizable, double[]> e : results.entrySet()) {
            Observation data = LocalizationUtils.gatherObservationData(img, e.getKey(), estimator.getDomainSpan());
            double[] params = new double[e.getValue().length+1];
            System.arraycopy(e.getValue(), 0, params, 0, e.getValue().length);
            params[params.length-1] = Math.sqrt(LevenbergMarquardtSolver.chiSquared(data.X, e.getValue(), data.I, fitFunction))/params[is3D?3:2]/data.I.length; // normalized error by intensity & number of pixels
            results2.put(locObj.get(e.getKey()), params);
        }
        return results2;
    }
    private static Localizable getLocalizable(double[] v, boolean is3D) {
        if (is3D) return new Point((long)(v[0]+0.5d), (long)(v[1]+0.5d), (long)(v[2]+0.5d));
        else return new Point((long)(v[0]+0.5d), (long)(v[1]+0.5d));
    }
    public static void display2DImageAndRois(Image image, Map<Object3D, double[]> params) {
        ImagePlus ip = new IJImageDisplayer().showImage(image);
        final Overlay overlay = new Overlay();
        ip.setOverlay(overlay);
        TreeMap<Object3D, double[]> paramsSort = new TreeMap<Object3D, double[]>(new Comparator<Object3D>() {

            public int compare(Object3D arg0, Object3D arg1) {
                return Integer.compare(arg0.getLabel(), arg1.getLabel());
            }
        });
        paramsSort.putAll(params);
        for (Entry<Object3D, double[]> e : paramsSort.entrySet()) overlay.add(get2DEllipse(e.getKey(), e.getValue()));
    }
    public static Roi get2DEllipse(Object3D o, double[] p) {
        double Ar = p[2];
        double x = p[0];
        double y = p[1];
        double sx = p[3];
        double sy = p[3];
        double error = p[4];
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
        logger.debug("gaussian fit on seed: {}; center: {}, sigmaX: {}, error: {}", o.getLabel(), o.getCenter(), sx, error);
        return new EllipseRoi(x1, y1, x2, y2, ar);
    }
}
