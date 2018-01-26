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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package boa.misc;

import static boa.test_utils.TestUtils.logger;
import boa.ui.ConsoleUserInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.PreFilterSequence;
import boa.core.ProgressCallback;
import boa.core.Task;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.Measurements;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import ij.ImageJ;
import ij.gui.Plot;
import ij.process.AutoThresholder;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.io.ImageFormat;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.image.io.ImageReader;
import boa.image.io.ImageWriter;
import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import static boa.measurement.MeasurementExtractor.numberFormater;
import org.apache.commons.lang.ArrayUtils;
import boa.plugins.PluginFactory;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.utils.HashMapGetCreate;
import boa.utils.ThreadRunner;
import boa.utils.ThreadRunner.ThreadAction;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class MOPEmptyMicrochannel {
    static int margin = 4;
    static class ImageFeats {
        private Image cropped;
        private Image normalized;
        private Image normedLaplacian;
        private Image laplacian;
        final Image image;
        public ImageFeats(Image image) {
            this.image = image;
        }
        public Image laplacian() {
            if (laplacian==null) laplacian = ImageFeatures.getLaplacian(cropped(), 2, false, false);
            return laplacian;
        }
        public Image normedLaplacian() {
            if (normedLaplacian==null) normedLaplacian = ImageFeatures.getLaplacian(normed(), 2, false, false);
            return normedLaplacian;
        }
        public Image cropped() {
            if (cropped==null) cropped = image.crop(new BoundingBox(margin, image.getSizeX()-margin-1, margin, image.getSizeY()-margin-1, 0, image.getSizeZ()-1)).setName(image.getName()+"cropped");
            return cropped;
        }
        public Image normed() {
            if (normalized ==null) normalized = ImageOperations.normalize(cropped(), null, null, 0.01, 0.99, true);
            return normalized;
        }
        public void show() {
            ImageWindowManagerFactory.showImage(image);
            ImageWindowManagerFactory.showImage(cropped());
            ImageWindowManagerFactory.showImage(normed());
            ImageWindowManagerFactory.showImage(laplacian());
        }
    }
    static HashMapGetCreate<Image, ImageFeats> imageFeatures = new HashMapGetCreate<>(i -> new ImageFeats(i));
    interface Feature {
        public double compute(Image i);
        public String name();
    }
    static Feature contrast = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            double[] mm = ifea.cropped().getMinAndMax(null);
            return (mm[1]-mm[0]) / (mm[1]+mm[0]);
        }
        @Override public String name() {return "Contrast";}
    }; 
    static Feature contrastPercentile = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            double[] mm = ImageOperations.getQuantiles(ifea.cropped(), null, null, 0.01, 0.99);
            return (mm[1]-mm[0]) / (mm[1]+mm[0]);
        }
        @Override public String name() {return "ContrastPercentile";}
    }; 
    static Feature entropy = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            int[] histo = ifea.cropped().getHisto256(null).data;
            double count = 0;
            for (int p : histo) count+=p;
            double entropy = 0;
            for (int v : histo) {
                if (v==0) continue;
                double p = (double)v/count;
                entropy+=p * Math.log(p);
            }
            return -entropy;
        }
        @Override public String name() {return "Entropy";}
    }; 
    static Feature sigma = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            return ImageOperations.getMeanAndSigma(ifea.cropped(), null)[1]; 
        }
        @Override public String name() {return "Sigma";}
    };
    static Feature mean = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            return ImageOperations.getMeanAndSigma(ifea.cropped(), null)[0]; 
        }
        @Override public String name() {return "Mean";}
    };

    static Feature normMean = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            return ImageOperations.getMeanAndSigma(ifea.normed(), null)[0];
        }
        @Override public String name() {return "NormedMean";}
    };
    static Feature normSigma = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            return ImageOperations.getMeanAndSigma(ifea.normed(), null)[1];
        }
        @Override public String name() {return "NormedSigma";}
    };
    static Feature normOtsu = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            return IJAutoThresholder.runThresholder(ifea.normed(), null, AutoThresholder.Method.Otsu);
        }
        @Override public String name() {return "NormedOtsu";}
    };
    static Feature ostuVolume = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            double thld = IJAutoThresholder.runThresholder(ifea.cropped(), null, AutoThresholder.Method.Otsu);
            //logger.debug("{} ostu: {}, prop: {}", i.getName(), thld, ImageOperations.getMeanAndSigma(ifea.cropped(), null, v->v>thld)[2]/ifea.cropped().getSizeXYZ());
            return ImageOperations.getMeanAndSigma(ifea.cropped(), null, v->v>thld)[2]/ifea.cropped().getSizeXYZ(); 
        }
        @Override public String name() {return "OtsuProportion";}
    };
    static Feature LCVolume = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            double thld = IJAutoThresholder.runThresholder(ifea.laplacian(), null, AutoThresholder.Method.MaxEntropy);
            //logger.debug("{} ostu LC: {} prop: {}", i.getName(), thld, ImageOperations.getMeanAndSigma(ifea.laplacian(), null, v->v>thld)[2]/ifea.cropped().getSizeXYZ());
            return ImageOperations.getMeanAndSigma(ifea.laplacian(), null, v->v>thld)[2]/ifea.cropped().getSizeXYZ(); 
        }
        @Override public String name() {return "LCProportion";}
    };
    static Feature ostuDistance = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            double thld = IJAutoThresholder.runThresholder(ifea.normed(), null, AutoThresholder.Method.Otsu);
            double[] msOver = ImageOperations.getMeanAndSigma(ifea.normed(), null, v->v>thld); 
            double[] msUnder = ImageOperations.getMeanAndSigma(ifea.normed(), null, v->v<thld); 
            return msOver[0]-msUnder[0];
        }
        @Override public String name() {return "OtsuDistance";}
    };
    static Feature normedLCSigma = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            return ImageOperations.getMeanAndSigma(ifea.normedLaplacian(), null)[1]; 
        }
        @Override public String name() {return "NormedLCSigma";}
    };
    static Feature LCSigma = new Feature() {
        @Override
        public double compute(Image i) {
            ImageFeats ifea = imageFeatures.getAndCreateIfNecessary(i);
            return ImageOperations.getMeanAndSigma(ifea.laplacian(), null)[0]; 
        }
        @Override public String name() {return "LCSigma";}
    };
    
    static Feature[] features = new Feature[]{sigma, mean, LCSigma, contrast, contrastPercentile, entropy, ostuVolume, LCVolume, ostuDistance};
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        //createImageDataset(false);
        //createImageDataset(true);
        //if (true) return;
        logger.debug("get ds");
        Image[][] ds = getImageDataset(false);
        
        
        // test normalization
        /*new ImageJ();
        imageFeatures.getAndCreateIfNecessary(ds[0][0]).show();
        imageFeatures.getAndCreateIfNecessary(ds[1][0]).show();
        logger.debug("entropy: {}", entropy.compute(ds[0][0]));
        logger.debug("entropy: {}", entropy.compute(ds[1][0]));
        logger.debug("OD: {}", ostuDistance.compute(ds[0][0]));
        logger.debug("OD: {}", ostuDistance.compute(ds[1][0]));
        if (true) return ;
        */
        
        logger.debug("get features for crtl...");
        List<Measurements> cMeas = getMeasurements(ds[0], null, "", features);
        logger.debug("get features for empty...");
        List<Measurements> eMeas = getMeasurements(ds[1], null, "", features);
        logger.debug("get ds filtered");
        ds = getImageDataset(true);
        logger.debug("get features for crtl...");
        getMeasurements(ds[0], cMeas, "F", features);
        logger.debug("get features for empty...");
        getMeasurements(ds[1], eMeas, "F", features);
                
        for (Measurements m  : cMeas) m.setValue("Empty", false);
        for (Measurements m  : eMeas) m.setValue("Empty", true);
        List<Measurements> allMeas = new ArrayList<>(cMeas);
        allMeas.addAll(eMeas);
        // extract measurements
        extractData("/data/Images/MOP/EmptyMCFeatures.xls", allMeas);
    }
    
    public static void show(String title, int nb, double[] feature, Image[] images, boolean lower) {
        int c = 0;
        SortedMap<Double, Image> map = lower ? mapFeature(feature, images) : mapFeature(feature, images).descendingMap();
        Iterator<Entry<Double, Image>> it = map.entrySet().iterator();
        while(c<nb) {
            Entry<Double, Image> eC = it.next();
            ImageWindowManagerFactory.showImage(eC.getValue().setName(title+eC.getValue().getName()));
            ++c;
        }
    }
    public static TreeMap<Double, Image> mapFeature(double[] f, Image[] images) {
        Map<Double, Image> map = new HashMap<>();
        for (int i = 0; i<f.length; ++i) {
            map.put(f[i], images[i]);
        }
        return new TreeMap(map);
    }
    private static List<Measurements> getMeasurements(Image[] images, List<Measurements> mes, String prefix, Feature... features) {
        if (mes==null) mes = Utils.transform( Arrays.asList(images), i->getMeasurementsFromImageName(i.getName()) );
        else if (mes.size()!=images.length) throw new IllegalArgumentException("Wrong measurements size");
        if (prefix==null) prefix = "";
        for (Feature f : features) {
            for (int i = 0; i<images.length; ++i) mes.get(i).setValue(prefix+f.name(), f.compute(images[i]));
        }
        return mes;
    }
    public static List<double[]> getFeatures(Image[] images, Function<Image, Double>... features) {
        List<double[]> res = new ArrayList<>();
        for (Function<Image, Double> f : features) res.add(ArrayUtils.toPrimitive(Utils.transform(images, new Double[images.length], f)));
        return res;
    }
    public static Image[][] getImageDataset(boolean filter) {
        String dir = !filter ? "/data/Images/MOP/EmptyPhaseMCNoSub/" : "/data/Images/MOP/EmptyPhaseMC/";
        File[] ctrl = new File(dir+"Control").listFiles(f->f.getName().endsWith(".tif"));
        File[] empty = new File(dir+"Empty").listFiles(f->f.getName().endsWith(".tif"));
        Image[][] res = new Image[2][];
        res[0] = new Image[ctrl.length];
        res[1] = new Image[empty.length];
        int count = 0;
        logger.debug("open : {} ctrl images", ctrl.length);
        for (File f : ctrl) res[0][count++] = ImageReader.openIJTif(f.getAbsolutePath());
        count = 0; 
        logger.debug("open : {} empty images", empty.length);
        for (File f : empty) res[1][count++] = ImageReader.openIJTif(f.getAbsolutePath());
        return res;
    }
    public static void createImageDataset(boolean filter) {
        String[] dbs = new String[]{"MutH_150324", "WT_150616", "MutD5_141209", "MutD5_141202"};
        String sel = "MCWithCells";
        String selEmpty = "MCWithoutCells";
        String dir = !filter ? "/data/Images/MOP/EmptyPhaseMCNoSub/" : "/data/Images/MOP/EmptyPhaseMC/";
        createImageDataset(dbs, sel, dir+"Control", filter);
        createImageDataset(dbs, selEmpty, dir+"Empty", filter);
    }
    public static void createImageDataset(String[] dbs, String selName, String dir, boolean filter) {
        logger.debug("selection: {}", selName);
        Utils.deleteDirectory(new File(dir));
        new File(dir).mkdirs();
        for (String dbS : dbs) {
            MasterDAO db = new Task(dbS).getDB();
            db.setReadOnly(true);
            Selection sel = db.getSelectionDAO().getSelections().stream().collect(Collectors.toMap(s->s.getName(), s->s)).get(selName);
            Set<String> allPos = sel.getAllPositions();
            logger.debug("DB: {} sel: {} positions: {} elements: {}", dbS, allPos.size(),selName, sel.getAllElementStrings().size());
            final ProgressCallback pcb = ProgressCallback.get(new ConsoleUserInterface(), allPos.size());
            ThreadRunner.execute(allPos, false, new ThreadAction<String>() {
                @Override
                public void run(String pos, int idx) {
                    //for (String pos : allPos) {
                        PreFilterSequence f = db.getExperiment().getStructure(1).getProcessingScheme().getPreFilters();
                        Set<StructureObject> mcs = sel.getElements(pos);
                        pcb.log("position: "+pos+ " elements: "+mcs.size());
                        for (StructureObject mc : mcs) {
                            Image im = mc.getRawImage(0);
                            if (filter) im = f.filter(im, mc.getMask());
                            ImageWriter.writeToFile(im, dir, dbS+"."+pos+"."+Selection.indicesString(mc), ImageFormat.TIF);
                        }
                        db.clearCache(pos);
                    //}
                }
            }, pcb);
            
            db.clearCache();
        }
    }
    public static Measurements getMeasurementsFromImageName(String name) {
        String[] split = name.split("\\.");
        int[] idx = Selection.parseIndices(split[2]);
        Measurements res = new Measurements(split[1], idx[0], 0, idx).initValues();
        res.setValue("XP", split[0]);
        return res;
    }
    final static String separator =";";
    private static String getHeader(List<String> keys) { //TODO split Indicies column ...
        StringBuilder sb = new StringBuilder(50);
        sb.append("Position");
        sb.append(separator);
        sb.append("Indices");
        sb.append(separator);
        sb.append("Frame");
        sb.append(separator);
        Utils.appendArray(keys, s->s, separator, sb);
        return sb.toString();
    }
    private static String getLine(Measurements m, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getPosition());
        sb.append(separator);
        Utils.appendArray(m.getIndices(), Selection.indexSeparator, sb);
        sb.append(separator);
        sb.append(m.getFrame());
        sb.append(separator);
        Utils.appendArray(keys, s->m.getValueAsString(s, numberFormater), separator, sb);
        return sb.toString();
    }
    public static void extractData(String outputFile, List<Measurements> mes) {
        FileWriter fstream;
        BufferedWriter out;
        List<String> keys = new ArrayList<>(mes.get(0).getValues().keySet());
        Collections.sort(keys);
        Collections.sort(mes);
        int count = 0;
        long t0 = System.currentTimeMillis();
        try {
            File output = new File(outputFile);
            output.delete();
            fstream = new FileWriter(output);
            out = new BufferedWriter(fstream);
            out.write(getHeader(keys)); 
            
            for (Measurements m : mes) {
                out.newLine();
                out.write(getLine(m, keys));
                ++count;
            }
            out.close();
            long t1 = System.currentTimeMillis();
            logger.debug("data extractions: {} line in: {} ms", count, t1-t0);
        } catch (IOException ex) {
            logger.debug("init extract data error: {}", ex);
        }
    }
}
