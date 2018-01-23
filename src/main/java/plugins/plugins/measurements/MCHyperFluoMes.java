/*
 * Copyright (C) 2018 jollion
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
package plugins.plugins.measurements;

import configuration.parameters.Parameter;
import dataStructure.objects.StructureObject;
import ij.process.AutoThresholder;
import image.Image;
import image.ImageOperations;
import image.ThresholdMask;
import java.util.ArrayList;
import java.util.List;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.thresholders.IJAutoThresholder;

/**
 *
 * @author jollion
 */
public class MCHyperFluoMes implements Measurement {

    @Override
    public int getCallStructure() {
        return -1;
    }

    @Override
    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<>(3);
        res.add(new MeasurementKeyObject("Sigma", 0));
        res.add(new MeasurementKeyObject("Mu", 0));
        res.add(new MeasurementKeyObject("SigmaOverOtsu", 0));
        res.add(new MeasurementKeyObject("MuOverOtsu", 0));
        res.add(new MeasurementKeyObject("SigmaOverBck", 0));
        res.add(new MeasurementKeyObject("MuOverBck", 0));
        res.add(new MeasurementKeyObject("MaxPer", 0));
        res.add(new MeasurementKeyObject("MaxPerOverOtsu", 0));
        res.add(new MeasurementKeyObject("MaxPerOverBck", 0));
        return res;
    }

    @Override
    public void performMeasurement(StructureObject root) {
        Image rootImage = root.getRawImage(0);
        double bck = BackgroundThresholder.runThresholder(rootImage, root.getMask(), 3, 3, 2, Double.POSITIVE_INFINITY);
        for (StructureObject object : root.getChildren(0)) {
            Image image = object.getRawImage(0);
            double otsu = IJAutoThresholder.runThresholder(image, object.getMask(), AutoThresholder.Method.Otsu);
            //double bck = BackgroundThresholder.runThresholder(image, object.getMask(), 3, 3, 2, otsu);
            double[] musig = ImageOperations.getMeanAndSigma(image, object.getMask());
            double[] musigO = ImageOperations.getMeanAndSigma(image, object.getMask(), v->v>=otsu);
            double[] musigB = ImageOperations.getMeanAndSigma(image, object.getMask(), v->v>=bck);
            double dec = ImageOperations.getPercentile(image, object.getMask(), null, 0.99)[0];
            double decOverBck = ImageOperations.getPercentile(image, new ThresholdMask(image, bck, true, false), null, 0.99)[0];
            double decOverOtsu = ImageOperations.getPercentile(image, new ThresholdMask(image, bck, true, false), null, 0.99)[0];
            object.getMeasurements().setValue("Mu", musig[0]);
            object.getMeasurements().setValue("Sigma", musig[1]);
            object.getMeasurements().setValue("MuOverOtsu", musigO[0]);
            object.getMeasurements().setValue("SigmaOverOtsu", musigO[1]);
            object.getMeasurements().setValue("MuOverBck", musigB[0]);
            object.getMeasurements().setValue("SigmaOverBck", musigB[1]);
            object.getMeasurements().setValue("MaxPer", dec);
            object.getMeasurements().setValue("MaxPerOverBck", decOverBck);
            object.getMeasurements().setValue("MaxPerOverOtsu", decOverOtsu);
        }
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[0];
    }
    
}
