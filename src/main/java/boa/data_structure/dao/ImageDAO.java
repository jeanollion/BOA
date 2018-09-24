/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.data_structure.dao;

import boa.configuration.experiment.Experiment;
import boa.data_structure.StructureObject;
import boa.image.BlankMask;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageInteger;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jean Ollion
 */
public interface ImageDAO {
    public String getImageExtension();
    public InputStream openPreProcessedImageAsStream(int channelImageIdx, int timePoint, String microscopyFieldName);
    public Image openPreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName);
    public Image openPreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName, MutableBoundingBox bounds);
    public BlankMask getPreProcessedImageProperties(String microscopyFieldName);
    public void writePreProcessedImage(Image image, int channelImageIdx, int timePoint, String microscopyFieldName);
    public void writePreProcessedImage(InputStream image, int channelImageIdx, int timePoint, String microscopyFieldName);
    public void deletePreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName);
    
    // track images
    public void writeTrackImage(StructureObject trackHead, int channelImageIdx, Image image);
    public Image openTrackImage(StructureObject trackHead, int channelImageIdx);
    public InputStream openTrackImageAsStream(StructureObject trackHead, int channelImageIdx);
    public void writeTrackImage(StructureObject trackHead, int channelImageIdx, InputStream image);
    public void deleteTrackImages(String position, int parentStructureIdx);
    
}
