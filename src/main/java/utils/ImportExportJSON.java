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
package utils;

import dataStructure.configuration.Experiment;
import dataStructure.containers.ImageDAO;
import dataStructure.objects.DBMapObjectDAO;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.Measurements;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import static dataStructure.objects.StructureObjectUtils.setAllChildren;
import image.Image;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.FileIO.ZipReader;
import utils.FileIO.ZipWriter;
import static utils.JSONUtils.parse;
import static utils.JSONUtils.serialize;

/**
 *
 * @author jollion
 */
public class ImportExportJSON {
    public static final Logger logger = LoggerFactory.getLogger(ImportExportJSON.class);
    public static void writeObjects(ZipWriter writer, ObjectDAO dao) {
        List<StructureObject> roots=dao.getRoots();
        if (roots.isEmpty()) return;
        List<StructureObject> allObjects = new ArrayList<>();
        allObjects.addAll(roots);
        for (int sIdx = 0; sIdx<dao.getExperiment().getStructureCount(); ++sIdx) {
            setAllChildren(roots, sIdx);
            for (StructureObject r : roots) allObjects.addAll(r.getChildren(sIdx));
        }
        writer.write(dao.getPositionName()+File.separator+"objects.txt", allObjects, o -> serialize(o));
        allObjects.removeIf(o -> !o.hasMeasurements());
        writer.write(dao.getPositionName()+File.separator+"measurements.txt", allObjects, o -> serialize(o.getMeasurements()));
    }
    public static void writeImages(ZipWriter writer, ObjectDAO dao) {
        int ch = dao.getExperiment().getChannelImageCount();
        int fr = dao.getExperiment().getPosition(dao.getPositionName()).getTimePointNumber(false);
        String dir = dao.getPositionName()+File.separator+"Images"+File.separator;
        ImageDAO iDao = dao.getExperiment().getImageDAO();
        for (int c = 0; c<ch; ++c) {
            for (int f = 0; f<fr; ++f) {
                InputStream is = iDao.openStream(c, f, dao.getPositionName());
                if (is!=null) {
                    writer.appendFile(dir+f+"_"+c, is);
                }
            }
        }
    }
    public static void readImages(ZipReader reader, ObjectDAO dao) {
        String dir = dao.getPositionName()+File.separator+"Images"+File.separator;
        ImageDAO iDao = dao.getExperiment().getImageDAO();
        String pos = dao.getPositionName();
        List<String> files = reader.listsubFiles(dir);
        logger.debug("pos: {}, images: {}", pos, Utils.toStringList(files));
        for (String f : files) {
            File file = new File(f);
            String[] fc = file.getName().split("_");
            int frame = Integer.parseInt(fc[0]);
            int channel = Integer.parseInt(fc[1]);
            InputStream is = reader.readFile(f);
            if (is!=null) {
                logger.debug("read images: f={}, c={} pos: {}", frame, channel, pos);
                iDao.writePreProcessedImage(is, channel, frame, pos);
            }
        }
    }
    public static void readObjects(ZipReader reader, ObjectDAO dao) {
        List<StructureObject> allObjects = reader.readObjects(dao.getPositionName()+File.separator+"objects.txt", o->parse(StructureObject.class, o));
        List<Measurements> allMeas = reader.readObjects(dao.getPositionName()+File.separator+"measurements.txt", o->parse(Measurements.class, o));
        Map<ObjectId, StructureObject> objectsById = new HashMap<>(allObjects.size());
        
        List<StructureObject> roots = new ArrayList<>();
        Iterator<StructureObject> it = allObjects.iterator();
        while(it.hasNext()) {
            StructureObject n = it.next();
            if (n.isRoot()) {
                roots.add(n);
                it.remove();
            }
        }
        
        for (StructureObject o : allObjects) objectsById.put(o.getId(), o);
        for (Measurements m : allMeas) {
            StructureObject o = objectsById.get(m.getId());
            if (o!=null) o.setMeasurements(m);
        }
        dao.store(roots, true);
        dao.store(allObjects, true);
        dao.upsertMeasurements(allObjects);
        if (dao instanceof DBMapObjectDAO) ((DBMapObjectDAO)dao).compactDBs(true);
    }
    
    public static <T> List<T> readObjects(String path, Class<T> clazz) {
        return FileIO.readFromFile(path, s-> parse(clazz, s));
    }
    
    public static void exportPositions(ZipWriter w, MasterDAO dao, boolean images) {exportPositions(w, dao, images, null);}
    public static void exportPositions(ZipWriter w, MasterDAO dao, boolean images, List<String> positions) {
        if (!w.isValid()) return;
        if (positions==null) positions = Arrays.asList(dao.getExperiment().getPositionsAsString());
        int count = 0;
        for (String p : positions) {
            logger.info("Exporting: {}/{}", ++count, positions.size());
            ObjectDAO oDAO = dao.getDao(p);
            writeObjects(w, oDAO);
            if (images) writeImages(w, oDAO);
        }
    }
    public static void exportConfig(ZipWriter w, MasterDAO dao) {
        if (!w.isValid()) return;
        w.write("config.txt", new ArrayList<Experiment>(1){{add(dao.getExperiment());}}, o->JSONUtils.serialize(o));
        if (dao.getSelectionDAO()!=null) w.write("selections.txt", dao.getSelectionDAO().getSelections(), o -> JSONUtils.serialize(o));
    }

    public static void importFromZip(String path, MasterDAO dao, boolean config, boolean selections, boolean objects) {
        ZipReader r = new ZipReader(path);
        if (r.valid()) {
            if (config) {
                List<Experiment> xp = r.readObjects("config.txt", o->JSONUtils.parse(Experiment.class, o));
                if (xp.size()==1) {
                    xp.get(0).setOutputDirectory(dao.getDir()+File.separator+"Output");
                    dao.setExperiment(xp.get(0));
                    logger.debug("XP: {} from file: {} set to db: {}", dao.getExperiment().getName(), path, dao.getDBName());
                }
            }
            if (selections) {
                List<Selection> sels = r.readObjects("selections.txt", o->JSONUtils.parse(Selection.class, o));
                if (sels.size()>0 && dao.getSelectionDAO()!=null) {
                    for (Selection s: sels )dao.getSelectionDAO().store(s);
                    logger.debug("Stored: #{} selections from file: {} set to db: {}", sels.size(), path, dao.getDBName());
                }
            }
            if (objects) {
                for (String position : r.listDirectories("/Images")) {
                    ObjectDAO oDAO = dao.getDao(position);
                    oDAO.deleteAllObjects();
                    readObjects(r, oDAO);
                    readImages(r, oDAO);
                }
            }
            r.close();
        }
    }
    public static Map<String, File> listExperiments(String dir) {
        File fDir = new File(dir);
        Map<String, File> res= new HashMap<>();
        listExperiments(fDir, res);
        for (File subF : fDir.listFiles(f ->f.isDirectory())) listExperiments(subF, res);
        return res;
    }
    private static void listExperiments(File dir, Map<String, File> res) {
        if (dir.isDirectory()) {
            for (File subF : dir.listFiles((f, n) -> n.endsWith(".zip"))) {
                ZipReader r = new ZipReader(subF.getAbsolutePath());
                if (r.valid()) {
                    List<Experiment> xpList = r.readObjects("config.txt", o->JSONUtils.parse(Experiment.class, o));
                    if (xpList.size()==1) res.put(Utils.removeExtension(subF.getName()), subF); //xpList.get(0).getName()
                }
            }
        } else if (dir.getName().endsWith(".zip")) {
            ZipReader r = new ZipReader(dir.getAbsolutePath());
            if (r.valid()) {
                List<Experiment> xpList = r.readObjects("config.txt", o->JSONUtils.parse(Experiment.class, o));
                if (xpList.size()==1) res.put(Utils.removeExtension(dir.getName()), dir); //xpList.get(0).getName()
            }
        }
    }
}
