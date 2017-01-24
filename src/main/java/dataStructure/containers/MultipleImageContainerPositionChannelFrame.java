/*
 * Copyright (C) 2016 jollion
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

import static core.Processor.logger;
import de.caluga.morphium.annotations.Transient;
import image.BoundingBox;
import image.Image;
import image.ImageIOCoordinates;
import image.ImageReader;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public class MultipleImageContainerPositionChannelFrame extends MultipleImageContainer {
    
    final String inputDir, extension, positionKey, timeKeyword;
    final int frameNumber;
    final String[] channelKeywords;
    int[] sizeZC;
    @Transient List<List<String>> fileCT;

    public MultipleImageContainerPositionChannelFrame(String inputDir, String extension, String positionKey, String timeKeyword, String[] channelKeywords, int frameNumber, double scaleXY, double scaleZ) {
        super(scaleXY, scaleZ);
        this.inputDir = inputDir;
        this.extension = extension;
        this.positionKey = positionKey;
        this.channelKeywords = channelKeywords;
        this.timeKeyword = timeKeyword;
        this.frameNumber = frameNumber;
    }
    
    @Override public double getCalibratedTimePoint(int t, int c, int z) {
        if (fileCT==null) createFileMap();
        ImageReader reader = new ImageReader(fileCT.get(c).get(t));
        double res= reader.getTimePoint(0, 0, z);
        reader.closeReader();
        return res;
    }
    
    @Override
    public int getTimePointNumber() {
        return frameNumber;
    }

    @Override
    public int getChannelNumber() {
        return channelKeywords.length;
    }

    @Override
    public int getSizeZ(int channelNumber) {
        if (fileCT==null) createFileMap();
        if (sizeZC==null) sizeZC = new int[channelNumber]; 
        if (sizeZC[channelNumber]==0) {
            ImageReader reader = new ImageReader(fileCT.get(channelNumber).get(0));
            sizeZC[channelNumber] = reader.getSTCXYZNumbers()[0][4];
            reader.closeReader();
        } // temporary, for retrocompatibility
        return sizeZC[channelNumber];
    }

    @Override
    public Image getImage(int timePoint, int channel) {
        if (fileCT==null) createFileMap();
        if (timePoint==0) {
            logger.debug("fileMap: {} x {}", fileCT.size(), fileCT.get(0).size());
            logger.debug("file: {}", fileCT.get(channel).get(timePoint));
        }
        return ImageReader.openImage(fileCT.get(channel).get(timePoint));
    }

    @Override
    public Image getImage(int timePoint, int channel, BoundingBox bounds) {
        if (fileCT==null) createFileMap();
        return ImageReader.openImage(fileCT.get(channel).get(timePoint), new ImageIOCoordinates(0, 0, 0, bounds));
    }

    @Override
    public void close() {
        fileCT=null;
    }

    @Override
    public String getName() {
        return positionKey;
    }

    @Override
    public MultipleImageContainer duplicate() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    private void createFileMap() {
        File in = new File(inputDir);
        Pattern positionPattern = Pattern.compile(".*"+positionKey+".*"+extension);
        List<File> files = Arrays.stream(in.listFiles()).filter(f -> positionPattern.matcher(f.getName()).find()).collect(Collectors.toList());
        Pattern timePattern = Pattern.compile(".*"+timeKeyword+"(\\d+).*");
        Map<Integer, List<File>> filesByChannel = files.stream().collect(Collectors.groupingBy(f -> getKeywordIdx(f.getName(), channelKeywords)));
        fileCT = new ArrayList<>(filesByChannel.size());
        filesByChannel.entrySet().stream().sorted().forEach((channelFiles) -> {
            Map<Integer, String> filesByTimePoint = channelFiles.getValue().stream().collect(Collectors.toMap(f -> get(f.getName(), timePattern), f -> f.getAbsolutePath()));
            fileCT.add(new ArrayList<>(new TreeMap(filesByTimePoint).values()).subList(0, frameNumber));
        });
    }
    
    public static int getCommomStartIndex(List<String> names) {
        int startIndex = 0;
        String baseName = names.get(0);
        WL : while(startIndex<baseName.length()) {
            char currentChar = baseName.charAt(startIndex);
            for (String f : names) {
                if (f.charAt(startIndex)!=currentChar)  break WL;
            }
            ++startIndex;
        }
        return startIndex;
    }
    public static Integer get(String s, Pattern p) {
        Matcher m = p.matcher(s);
        if (m.find()) return Integer.parseInt(m.group(1));
        else return null;
    }
    public static String getAsString(String s, Pattern p) {
        Matcher m = p.matcher(s);
        if (m.find()) return m.group(1);
        else return null;
    }
    public static String getKeyword(String s, String[] keywords, String defaultValue) {
        for (String k : keywords) if (s.contains(k)) return k;
        return defaultValue;
    }
    public static int getKeywordIdx(String s, String[] keywords) {
        for (int i = 0; i<keywords.length; ++i) if (s.contains(keywords[i])) return i;
        return -1;
    }
}
