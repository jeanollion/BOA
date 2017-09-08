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
package core;

import static core.Processor.logger;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.containers.MultipleImageContainerChannelSerie;
import dataStructure.containers.MultipleImageContainerSingleFile;
import dataStructure.containers.MultipleImageContainerPositionChannelFrame;
import static dataStructure.containers.MultipleImageContainerPositionChannelFrame.get;
import static dataStructure.containers.MultipleImageContainerPositionChannelFrame.getAsString;
import static dataStructure.containers.MultipleImageContainerPositionChannelFrame.getKeyword;
import image.ImageReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import utils.Utils;

/**
 *
 * @author jollion
 */

public class ImageFieldFactory {
    private final static String seriesSeparator = "xy";
    private final static List<String> ignoredExtensions = Arrays.asList(new String[]{".log"});
    public static List<MultipleImageContainer> importImages(String[] path, Experiment xp) {
        ArrayList<MultipleImageContainer> res = new ArrayList<>();
        if (xp.getImportImageMethod().equals(Experiment.ImportImageMethod.SINGLE_FILE)) {
            for (String p : path) ImageFieldFactory.importImagesSingleFile(new File(p), xp, res);
        } else if (xp.getImportImageMethod().equals(Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD)) {
            // get keywords
            int nb = xp.getChannelImages().getChildCount();
            String[] keyWords = new String[nb];
            int idx = 0;
            for (ChannelImage i : xp.getChannelImages().getChildren()) keyWords[idx++] = i.getImportImageChannelKeyword();
            logger.debug("import image channel: keywords: {}", (Object)keyWords);
            for (String p : path) ImageFieldFactory.importImagesChannel(new File(p), xp, keyWords, res);
        } else if (xp.getImportImageMethod().equals(Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_TIME_POSITION)) {
            int nb = xp.getChannelImages().getChildCount();
            String[] keyWords = new String[nb];
            int idx = 0;
            int countBlank = 0;
            for (ChannelImage i : xp.getChannelImages().getChildren()) {
                keyWords[idx] = i.getImportImageChannelKeyword();
                if ("".equals(keyWords[idx])) ++countBlank;
                ++idx;
            }
            if (countBlank>1) {
                logger.error("When Experiement has several channels, one must specify channel keyword for this import method");
                return res;
            }
            for (String p : path) ImageFieldFactory.importImagesCTP(new File(p), xp, keyWords, res);
        }
        Collections.sort(res, (MultipleImageContainer arg0, MultipleImageContainer arg1) -> arg0.getName().compareToIgnoreCase(arg1.getName()));
        return res;
    }
    
    
    protected static void importImagesSingleFile(File f, Experiment xp, ArrayList<MultipleImageContainer> containersTC) {
        if (f.isDirectory()) {
            for (File ff : f.listFiles()) {
                ImageFieldFactory.importImagesSingleFile(ff, xp, containersTC);
            }
        } else if (!isIgnoredExtension(f.getName())) {
            addContainerSingleFile(f, xp, containersTC);
        }
    }
    
    protected static void addContainerSingleFile(File image, Experiment xp, ArrayList<MultipleImageContainer> containersTC) {
        ImageReader reader=null;
        long t0 = System.currentTimeMillis();
        try {
            reader = new ImageReader(image.getAbsolutePath());
        } catch(Exception e) {
            logger.warn("Image : {} could not be read", image.getAbsolutePath());
            return;
        }
        long t1 = System.currentTimeMillis();
        int[][] stc = reader.getSTCXYZNumbers();
        long t2 = System.currentTimeMillis();
        int s = 0;
        String end = "";
        int digits=(int)(Math.log10(stc.length)+0.5);
        for (int[] tc:stc) {
            if (stc.length>1) end = seriesSeparator+Utils.formatInteger(digits, s);
            if (tc[1]==xp.getChannelImageCount()) {
                double[] scaleXYZ = reader.getScaleXYZ(1);
                containersTC.add(new MultipleImageContainerSingleFile(end, image.getAbsolutePath(),s, tc[0], tc[1], tc[4], scaleXYZ[0], scaleXYZ[2])); //Utils.removeExtension(image.getName())+"_"+
                logger.info("image {} imported successfully", image.getAbsolutePath());
            } else {
                logger.warn("Invalid Image: {} has: {} channels instead of: {}", image.getAbsolutePath(), tc[1], xp.getChannelImageCount());
            }
            ++s;
        }
        long t3 = System.currentTimeMillis();
        logger.debug("import image: {}, open reader: {}, getSTC: {}, create image containers: {}", t1-t0, t2-t1, t3-t2);
    }
    
    protected static void importImagesChannel(File input, Experiment xp, String[] channelKeywords, ArrayList<MultipleImageContainer> containersTC) {
        if (channelKeywords.length==0) return;
        if (!input.isDirectory()) return;
        File[] subDirs = input.listFiles(getDirectoryFilter()); // recursivity
        for (File dir : subDirs) importImagesChannel(dir, xp, channelKeywords, containersTC);// recursivity
        
        File[] file0 = input.listFiles((File dir, String name) -> name.contains(channelKeywords[0]) && !isIgnoredExtension(name));
        logger.debug("import images in dir: {} number of candidates: {}", input.getAbsolutePath(), file0.length);
        for (File f : file0) {
            String[] allChannels = new String[channelKeywords.length];
            allChannels[0] = f.getAbsolutePath();
            boolean allFiles = true;
            for (int c = 1; c < channelKeywords.length; ++c) {
                String name = input + File.separator + f.getName().replace(channelKeywords[0], channelKeywords[c]);
                File channel = new File(name);
                if (!channel.exists()) {
                    logger.warn("missing file: {}", name);
                    allFiles=false;
                    break;
                } else allChannels[c] = name;
            }
            if (allFiles) {
                String name = Utils.removeExtension(f.getName().replace(channelKeywords[0], ""));
                addContainerChannel(allChannels, name, xp, containersTC);
            }
            
        }
    }
    
    private static String[] imageExtensions = new String[]{"tif", "tiff", "nd2", "png"};
    public final static String[] timeKeywords = new String[]{"t"};
    public final static String[] positionKeywords = new String[]{"xy"};
    protected static void importImagesCTP(File input, Experiment xp, String[] channelKeywords, ArrayList<MultipleImageContainer> containersTC) {
        if (channelKeywords.length==0) return;
        if (!input.isDirectory()) return;
        File[] subDirs = input.listFiles(getDirectoryFilter()); // recursivity
        for (File dir : subDirs) importImagesCTP(dir, xp, channelKeywords, containersTC);// recursivity
        // 1 : filter by extension
        Pattern allchanPattern = getAllChannelPattern(channelKeywords);
        Map<String, List<File>> filesByExtension = Arrays.stream(input.listFiles((File dir, String name) -> allchanPattern.matcher(name).find() && !isIgnoredExtension(name))).collect(Collectors.groupingBy(f -> Utils.getExtension(f.getName())));
        List<File> files=null;
        String extension = null;
        if (filesByExtension.size()>1) { // keep most common extension
            int maxLength = Collections.max(filesByExtension.entrySet(), (e1, e2) -> e1.getValue().size() - e2.getValue().size()).getValue().size();
            filesByExtension.entrySet().removeIf(e -> e.getValue().size()<maxLength);
            if (filesByExtension.size()>1) { // keep extension in list
                Set<String> contained = new HashSet<>(Arrays.asList(imageExtensions));
                filesByExtension.entrySet().removeIf(e -> !contained.contains(e.getKey()));
                if (filesByExtension.size()>1) {
                    logger.warn("Folder: {} contains several image extension: {}", input.getAbsolutePath(), filesByExtension.keySet());
                    return;
                } else if (filesByExtension.isEmpty()) return;
            }
            //List<Entry<String, List<File>>> l = filesByExtension.entrySet().stream().filter(e -> e.getValue().size()==maxLength).collect(Collectors.toList());
        } 
        if (filesByExtension.size()==1) {
            files = filesByExtension.entrySet().iterator().next().getValue();
            extension = filesByExtension.keySet().iterator().next();
        } else return;
        logger.debug("extension: {}, #files: {}", extension, files.size());
        // get other channels
        
        // 2/ get maximum common part at start
        
        /*List<String> fileNames = new ArrayList<String>(files.size());
        for (File f : files) fileNames.add(Utils.removeExtension(f.getName()));
        int startIndex = MultipleImageContainerPositionChannelFrame.getCommomStartIndex(fileNames);
        String startName = fileNames.get(0).substring(0, startIndex+1);
        logger.debug("common image name: {}", startName);
        Map<String, File> filesMap= new HashMap<>(fileNames.size());
        for (File f : files) filesMap.put(f.getName().substring(startIndex+1, f.getName().length()), f);*/
        
        // 3 split by position / channel (check number) / frames (check same number between channels & continity)
        Pattern posPattern = Pattern.compile(".*("+positionKeywords[0]+"\\d+).*");
        Pattern timePattern = Pattern.compile(".*"+timeKeywords[0]+"(\\d+).*");
        Map<String, List<File>> filesByPosition = files.stream().collect(Collectors.groupingBy(f -> getAsString(f.getName(), posPattern)));
        logger.debug("Dir: {} # positions: {}", input.getAbsolutePath(), filesByPosition.size());
        PosLoop : for (Entry<String, List<File>> positionFiles : filesByPosition.entrySet()) {
            Map<String, List<File>> filesByChannel = positionFiles.getValue().stream().collect(Collectors.groupingBy(f -> getKeyword(f.getName(), channelKeywords, "")));
            logger.debug("Pos: {}, channel found: {}", positionFiles.getKey(),filesByChannel.keySet() );
            if (filesByChannel.size()==channelKeywords.length) {
                Integer frameNumber = null;
                boolean ok = true;
                for (Entry<String, List<File>> channelFiles : filesByChannel.entrySet()) {
                    Map<Integer, File> filesByTimePoint = channelFiles.getValue().stream().collect(Collectors.toMap(f -> get(f.getName(), timePattern), Function.identity()));
                    List<Integer> tpList = new ArrayList<>(new TreeMap<>(filesByTimePoint).keySet());
                    int minTimePoint = tpList.get(0);
                    int maxFrameNumberSuccessive=1;
                    while(maxFrameNumberSuccessive<tpList.size() && tpList.get(maxFrameNumberSuccessive-1)+1==tpList.get(maxFrameNumberSuccessive)) {++maxFrameNumberSuccessive;}
                    int maxTimePoint = tpList.get(tpList.size()-1);
                    //int maxTimePoint = Collections.max(filesByTimePoint.entrySet(), (e1, e2) -> e1.getKey() - e2.getKey()).getKey();
                    //int minTimePoint = Collections.min(filesByTimePoint.entrySet(), (e1, e2) -> e1.getKey() - e2.getKey()).getKey();
                    int theoframeNumberCurrentChannel = maxTimePoint-minTimePoint+1;
                    
                    if (theoframeNumberCurrentChannel != maxFrameNumberSuccessive) {
                        logger.warn("Dir: {} Position: {}, missing time points for channel: {}, 1st: {}, last: {}, count: {}, max successive: {}", input.getAbsolutePath(), positionFiles.getKey(), channelFiles.getKey(), minTimePoint, maxTimePoint, filesByTimePoint.size(), maxFrameNumberSuccessive);
                        //ok = false;
                        //break;
                    } 
                    // check if all channels have same number of Frames
                    if (frameNumber == null) frameNumber = maxFrameNumberSuccessive;
                    else {
                        if (frameNumber!=maxFrameNumberSuccessive) {
                            logger.warn("Dir: {} Position: {}, Channel: {}, {} tp found instead of {}", input.getAbsolutePath(), positionFiles.getKey(), channelFiles.getKey(), maxFrameNumberSuccessive, frameNumber);
                            ok = false;
                            break;
                        }
                    }
                }
                if (ok) {
                    ImageReader r = new ImageReader(positionFiles.getValue().get(0).getAbsolutePath());
                    double[] scaleXYZ = r.getScaleXYZ(1);
                    containersTC.add(
                            new MultipleImageContainerPositionChannelFrame(
                                    input.getAbsolutePath(), 
                                    extension, 
                                    positionFiles.getKey(), 
                                    timeKeywords[0], 
                                    channelKeywords, 
                                    frameNumber, 
                                    scaleXYZ[0], 
                                    scaleXYZ[2]
                            ));
                }
                
            } else logger.warn("Dir: {} Position: {}, {} channels instead of {}", input.getAbsolutePath(), positionFiles.getKey(), filesByChannel.size(), channelKeywords.length);
        }
    }
    
    protected static void addContainerChannel(String[] imageC, String fieldName, Experiment xp, ArrayList<MultipleImageContainer> containersTC) {
        //checks timepoint number is equal for all channels
        int timePointNumber=0;
        int[] sizeZC = new int[imageC.length];
        boolean[] singleFile = new boolean[imageC.length];
        double[] scaleXYZ=null;
        for (int c = 0; c< imageC.length; ++c) {
            ImageReader reader = null;
            try {
                reader = new ImageReader(imageC[c]);
            } catch (Exception e) {
                logger.warn("Image {} could not be read: ", imageC[c], e);
            }
            if (reader != null) {
                int[][] stc = reader.getSTCXYZNumbers();
                if (stc.length>1) {
                    logger.warn("Import method selected = one file per channel and per microscopy field, but file: {} contains {} series", imageC[c], stc.length);
                    return;
                }
                if (stc.length==0) return;
                if (stc[0][1]>1) {
                    logger.warn("Import method selected = one file per channel and per microscopy field, but file: {} contains {} channels", imageC[c], stc[0][1]);
                }
                if (c==0) timePointNumber=stc[0][0];
                else {
                    if (timePointNumber==1 && stc[0][0]>1) timePointNumber = stc[0][0];
                    if (stc[0][0]!=timePointNumber && stc[0][0]!=1) {
                        logger.warn("Warning: invalid file: {}. Contains {} time points whereas file: {} contains: {} time points", imageC[c], stc[0][0], imageC[0], timePointNumber);
                        return;
                    }
                }
                singleFile[c] = stc[0][0] == 1;
                sizeZC[c] = stc[0][4];
                if (c==0) scaleXYZ = reader.getScaleXYZ(1);
            }
        }
        if (timePointNumber>0) {
            MultipleImageContainerChannelSerie c = new MultipleImageContainerChannelSerie(fieldName, imageC, timePointNumber, singleFile, sizeZC, scaleXYZ[0], scaleXYZ[2]);
            containersTC.add(c);
        }
        
        
    }
    
    
    
    private static boolean isIgnoredExtension(String s) {
        for (int i =s.length()-1; i>=0; --i) {
            if (s.charAt(i)=='.') {
                String ext = s.substring(i, s.length());
                return ignoredExtensions.contains(ext);
            }
        }
        return false;
    }
    
    private static FileFilter getDirectoryFilter() {
        return new FileFilter() {
            public boolean accept(File file) {
                return file.isDirectory();
            }
        };
    }
    
    private static FileFilter getFileFilter() {
        return (File file) -> !file.isDirectory();
    }
    
    private static Pattern getAllChannelPattern(String[] channelKeywords) {
        String pat = ".*("+channelKeywords[0]+")";
        for (int i = 1; i<channelKeywords.length; ++i) pat+="|("+channelKeywords[i]+")";
        pat+=".*";
        return Pattern.compile(pat);
    }
    
}
