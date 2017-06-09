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

import dataStructure.configuration.Experiment;
import dataStructure.objects.StructureObject;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageInteger;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public interface ImageDAO {
    public String getImageExtension();
    public InputStream openStream(int channelImageIdx, int timePoint, String microscopyFieldName);
    public Image openPreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName);
    public Image openPreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName, BoundingBox bounds);
    public BlankMask getPreProcessedImageProperties(String microscopyFieldName);
    public void writePreProcessedImage(Image image, int channelImageIdx, int timePoint, String microscopyFieldName);
    public void writePreProcessedImage(InputStream image, int channelImageIdx, int timePoint, String microscopyFieldName);
    public void deletePreProcessedImage(int channelImageIdx, int timePoint, String microscopyFieldName);
    
    // track images
    public void writeTrackImage(StructureObject trackHead, int channelImageIdx, Image image);
    public Image openTrackImage(StructureObject trackHead, int channelImageIdx);
    public void clearTrackImages(String position, int parentStructureIdx);
    
}
