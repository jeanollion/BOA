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

import boa.gui.GUI;
import core.ProgressCallback;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        allObjects.removeIf(o -> o.getMeasurements().getValues().isEmpty());
        writer.write(dao.getPositionName()+File.separator+"measurements.txt", allObjects, o -> serialize(o.getMeasurements()));
    }
    public static void writePreProcessedImages(ZipWriter writer, ObjectDAO dao) {
        int ch = dao.getExperiment().getChannelImageCount();
        int fr = dao.getExperiment().getPosition(dao.getPositionName()).getTimePointNumber(false);
        String dir = dao.getPositionName()+File.separator+"Images"+File.separator;
        ImageDAO iDao = dao.getExperiment().getImageDAO();
        for (int c = 0; c<ch; ++c) {
            for (int f = 0; f<fr; ++f) {
                InputStream is = iDao.openStream(c, f, dao.getPositionName());
                if (is!=null) writer.appendFile(dir+f+"_"+c, is); //closes is
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
                //logger.debug("read images: f={}, c={} pos: {}", frame, channel, pos);
                iDao.writePreProcessedImage(is, channel, frame, pos);
            }
        }
    }
    public static void readObjects(ZipReader reader, ObjectDAO dao) {
        List<StructureObject> allObjects = reader.readObjects(dao.getPositionName()+File.separator+"objects.txt", o->parse(StructureObject.class, o));
        List<Measurements> allMeas = reader.readObjects(dao.getPositionName()+File.separator+"measurements.txt", o->parse(Measurements.class, o));
        Map<String, StructureObject> objectsById = new HashMap<>(allObjects.size());
        
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
        for (StructureObject o : roots) objectsById.put(o.getId(), o);
        StructureObjectUtils.setRelatives(objectsById, true, false); // avoiding calls to dao getById when storing measurements: set parents
        
        for (Measurements m : allMeas) {
            StructureObject o = objectsById.get(m.getId());
            if (o!=null) o.setMeasurements(m);
        }
        logger.debug("storing roots");
        dao.store(roots);
        logger.debug("storing other objects");
        dao.store(allObjects);
        logger.debug("storing measurements");
        dao.upsertMeasurements(allObjects);
        if (dao instanceof DBMapObjectDAO) ((DBMapObjectDAO)dao).compactDBs(true);
    }
    
    public static <T> List<T> readObjects(String path, Class<T> clazz) {
        return FileIO.readFromFile(path, s-> parse(clazz, s));
    }
    
    public static void exportPositions(ZipWriter w, MasterDAO dao, boolean images, ProgressCallback pcb) {exportPositions(w, dao, images, null, pcb);}
    public static void exportPositions(ZipWriter w, MasterDAO dao, boolean images, List<String> positions, ProgressCallback pcb) {
        if (!w.isValid()) return;
        if (positions==null) positions = Arrays.asList(dao.getExperiment().getPositionsAsString());
        int count = 0;
        if (pcb!=null) pcb.incrementTaskNumber(positions.size());
        for (String p : positions) {
            count++;
            logger.info("Exporting: {}/{}", count, positions.size());
            if (pcb!=null) pcb.log("Exporting position: "+p+ " ("+count+"/"+positions.size()+")");
            ObjectDAO oDAO = dao.getDao(p);
            writeObjects(w, oDAO);
            if (images) writePreProcessedImages(w, oDAO);
            if (pcb!=null) pcb.incrementProgress();
            if (pcb!=null) pcb.log("Position: "+p+" exported!");
        }
        logger.info("Exporting position done!");
    }
    public static void exportConfig(ZipWriter w, MasterDAO dao) {
        if (!w.isValid()) return;
        w.write("config.txt", new ArrayList<Experiment>(1){{add(dao.getExperiment());}}, o->JSONUtils.serialize(o));
    }
    
    public static void exportSelections(ZipWriter w, MasterDAO dao) {
        if (!w.isValid()) return;
        if (dao.getSelectionDAO()!=null) w.write("selections.txt", dao.getSelectionDAO().getSelections(), o -> JSONUtils.serialize(o));
    }
    public static Experiment readConfig(File f) {
        if (f.getName().endsWith(".txt")) {
            List<Experiment> xp = FileIO.readFromFile(f.getAbsolutePath(), o->JSONUtils.parse(Experiment.class, o));
            if (xp.size()==1) return xp.get(0);
        } else if (f.getName().endsWith(".zip")) {
            ZipReader r = new ZipReader(f.getAbsolutePath());
            if (r.valid()) {
                List<Experiment> xp = r.readObjects("config.txt", o->JSONUtils.parse(Experiment.class, o));
                if (xp.size()==1) return xp.get(0);
            }
        }
        return null;
    }
    public static void importConfigurationFromFile(String path, MasterDAO dao, boolean structures, boolean preProcessingTemplate) {
        File f = new File(path);
        if (f.getName().endsWith(".txt")) {
            List<Experiment> xp = FileIO.readFromFile(path, o->JSONUtils.parse(Experiment.class, o));
            if (xp.size()==1) {
                Experiment source = xp.get(0);
                if (source.getStructureCount()!=dao.getExperiment().getStructureCount()) {
                    GUI.log("Source has: "+source.getStructureCount()+" instead of "+dao.getExperiment().getStructureCount());
                    return;
                }
                // set structures
                dao.getExperiment().getStructures().setContentFrom(source.getStructures());
                // set preprocessing template
                dao.getExperiment().getPreProcessingTemplate().setContentFrom(source.getPreProcessingTemplate());
                // set measurements
                dao.getExperiment().getMeasurements().setContentFrom(source.getMeasurements());
                dao.updateExperiment();
                logger.debug("XP: {} from file: {} set to db: {}", dao.getExperiment().getName(), path, dao.getDBName());
            }
            
        }
    }
    public static void importFromFile(String path, MasterDAO dao, boolean config, boolean selections, boolean objects, ProgressCallback pcb) {
        File f = new File(path);
        if (f.getName().endsWith(".txt")) {
            if (config) {
                List<Experiment> xp = FileIO.readFromFile(path, o->JSONUtils.parse(Experiment.class, o));
                if (xp.size()==1) {
                    xp.get(0).setOutputDirectory(dao.getDir()+File.separator+"Output");
                    xp.get(0).setOutputImageDirectory(xp.get(0).getOutputDirectory());
                    dao.setExperiment(xp.get(0));
                    logger.debug("XP: {} from file: {} set to db: {}", dao.getExperiment().getName(), path, dao.getDBName());
                }
            }
        } else if (f.getName().endsWith(".zip")) importFromZip(path, dao, config, selections, objects, pcb);
    }
    
    public static void importFromZip(String path, MasterDAO dao, boolean config, boolean selections, boolean objects, ProgressCallback pcb) {
        ZipReader r = new ZipReader(path);
        if (r.valid()) {
            if (config) {
                List<Experiment> xp = r.readObjects("config.txt", o->JSONUtils.parse(Experiment.class, o));
                if (xp.size()==1) {
                    xp.get(0).setOutputDirectory(dao.getDir()+File.separator+"Output");
                    xp.get(0).setOutputImageDirectory(xp.get(0).getOutputDirectory());
                    dao.setExperiment(xp.get(0));
                    logger.debug("XP: {} from file: {} set to db: {}", dao.getExperiment().getName(), path, dao.getDBName());
                }
            }
            if (selections) {
                logger.debug("importing selections....");
                List<Selection> sels = r.readObjects("selections.txt", o->JSONUtils.parse(Selection.class, o));
                logger.debug("selections: {}", sels.size());
                if (sels.size()>0 && dao.getSelectionDAO()!=null) {
                    for (Selection s: sels )dao.getSelectionDAO().store(s);
                    logger.debug("Stored: #{} selections from file: {} set to db: {}", sels.size(), path, dao.getDBName());
                }
            }
            if (objects) {
                Set<String> dirs = objects ? r.listDirectories("/Images") : Collections.EMPTY_SET;
                if (pcb!=null) pcb.incrementTaskNumber(dirs.size());
                int count = 0;
                for (String position : dirs) {
                    count++;
                    if (pcb!=null) pcb.log("Importing: Position: "+position + " ("+ count+"/"+dirs.size()+")");
                    ObjectDAO oDAO = dao.getDao(position);
                    oDAO.deleteAllObjects();
                    readObjects(r, oDAO);
                    readImages(r, oDAO);
                    if (pcb!=null) pcb.incrementProgress();
                    if (pcb!=null) pcb.log("Position: "+position+" imported!");
                }
            }
            dao.clearCache(); // avoid lock issues
            r.close();
        }
    }
    public static Map<String, File> listExperiments(String dir) {
        File fDir = new File(dir);
        Map<String, File> res= new HashMap<>();
        listExperiments(fDir, res);
        if (fDir.isDirectory()) {
            for (File subF : fDir.listFiles(f ->f.isDirectory())) listExperiments(subF, res);
        }
        return res;
    }
    private static void listExperiments(File dir, Map<String, File> res) {
        if (dir.isDirectory()) {
            for (File subF : dir.listFiles()) addXP(subF, res);
        } else addXP(dir, res);
    }
    private static void addXP(File file , Map<String, File> res) {
        Experiment xp = readConfig(file);
        if (xp==null) return;
        if (file.getName().endsWith(".zip")) {
            res.put(Utils.removeExtension(file.getName()), file);
        } else if (file.getName().endsWith(".txt")) {
            String name = file.getName();
            name = Utils.removeExtension(name);
            if (name.endsWith("config") || name.endsWith("Config")) name = name.substring(name.length()-6, name.length());
            if (name.length()==0) name = "xp";
            res.put(name, file);
        }
    }
    
    /*
        // MONGODB IMPORT LEGACY
        // whole xp are located in sub directories
        FileFilter filter = new FileFilter() {
            @Override public boolean accept(File file) {
                if (file.isDirectory()) { // contains at least experiment collection
                    for (String fName : file.list()) {
                        if (fName.equals("Experiment.bson") || fName.equals("Experiment.json")) return true;
                    }
                } 
                return false;
            }
        };
        List<File> subDirs = new ArrayList<File>(Arrays.asList(directory.listFiles(filter)));
        if (filter.accept(directory)) subDirs.add(directory);
        
        boolean someDBAlreadyExist = false;
        for (File f : subDirs) {
            if (dbNames.contains(DBUtil.addPrefix(f.getName(), DBprefix))) {
                someDBAlreadyExist=true;
                break;
            }
        }
        
        boolean ignoreExisting=false;
        if (someDBAlreadyExist) {
            Object[] options = {"Override existig experiments (data loss)", "Ignore existing experiments"};
            int n = JOptionPane.showOptionDialog(this, "Some experiments found in the directory are already present", "Import Whole Experiment", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            ignoreExisting = n==1;
        }
        if (ignoreExisting) {
            Iterator<File> it = subDirs.iterator();
            while (it.hasNext()) { if (dbNames.contains(it.next().getName())) it.remove();}
        }
        String hostname = getCurrentHostNameOrDir();
        int count = 0;
        for (File f : subDirs) {
            logger.info("Importing XP: {}/{}", ++count, subDirs.size());
            CommandExecuter.restoreDB(hostname, DBUtil.addPrefix(f.getName(), DBprefix), f.getAbsolutePath());
        }
        populateExperimentList();
        PropertyUtils.set(PropertyUtils.LAST_IO_DATA_DIR, dir);
        */
}
