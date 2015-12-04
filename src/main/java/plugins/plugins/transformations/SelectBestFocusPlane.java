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

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import image.Image;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import plugins.Transformation;
import plugins.TransformationTimeIndependent;
import processing.ImageFeatures;
import utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class SelectBestFocusPlane implements Transformation {
    ArrayList<Integer> configurationData = new ArrayList<Integer>();
    NumberParameter gradientScale = new BoundedNumberParameter("Gradient Scale", 0, 3, 1, 10);
    Parameter[] parameters = new Parameter[]{gradientScale};
    
    public SelectBestFocusPlane() {}
    public SelectBestFocusPlane(double gradientScale) {
        this.gradientScale.setValue(gradientScale);
    }
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    public void computeConfigurationData(final int channelIdx, final InputImages inputImages) {
        final double scale = gradientScale.getValue().doubleValue();
        final Integer[] conf = new Integer[inputImages.getTimePointNumber()];
        if (inputImages.getSizeZ(channelIdx)>1) {
            final ThreadRunner tr = new ThreadRunner(0, conf.length, 0);
            for (int i = 0; i<tr.threads.length; i++) {
                tr.threads[i] = new Thread(
                    new Runnable() {
                        public void run() { 
                            for (int t = tr.ai.getAndIncrement(); t<tr.end; t = tr.ai.getAndIncrement()) {
                                Image image = inputImages.getImage(channelIdx, t);
                                if (image.getSizeZ()>1) {
                                    ArrayList<Image> planes = image.splitZPlanes();
                                    double maxValues = eval(planes.get(0), scale);
                                    int max=0;
                                    for (int z = 1; z<planes.size(); ++z) {
                                        double temp = eval(planes.get(z), scale);
                                        if (temp>maxValues) {
                                            maxValues = temp;
                                            max = z;
                                        }
                                    }
                                    conf[t] = max;
                                    logger.debug("select best focus plane: time:{}, plane: {}", t, max);
                                }
                            }
                        }
                    }
                );
            }
            tr.startAndJoin();
        }
        configurationData.addAll(Arrays.asList(conf));
    }
    
    
    private static double eval(Image plane, double scale) {
        Image gradient = ImageFeatures.getGradientMagnitude(plane, scale, false);
        return ImageOperations.getMeanAndSigma(gradient, null)[0];
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (image.getSizeZ()>1) return image.getZPlane(configurationData.get(timePoint));
        else return image;
    }

    public ArrayList getConfigurationData() {
        return configurationData;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
