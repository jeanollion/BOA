/*
 * Copyright (C) 2015 nasique
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
import dataStructure.configuration.ExperimentDAO;
import dataStructure.objects.Measurements;
import dataStructure.objects.MorphiumObjectDAO;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.AnnotationAndReflectionHelper;
import de.caluga.morphium.DereferencingListener;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumAccessVetoException;
import de.caluga.morphium.MorphiumConfig;
import java.lang.reflect.Field;
import java.net.UnknownHostException;
import java.util.logging.Level;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class MorphiumUtils {
    public final static Logger logger = LoggerFactory.getLogger(MorphiumUtils.class);
    public static DereferencingListener addDereferencingListeners(Morphium m, final ExperimentDAO xpDao_) {
        DereferencingListener res =new DereferencingListener<Object, StructureObject, ObjectId>() {
            ExperimentDAO xpDAO = xpDao_;
            AnnotationAndReflectionHelper r = new AnnotationAndReflectionHelper(true);

            @Override
            public void wouldDereference(StructureObject entityIncludingReference, String fieldInEntity, ObjectId id, Class typeReferenced, boolean lazy) throws MorphiumAccessVetoException {
                //if (logger.isTraceEnabled()) logger.trace("would dereference: {} refrence: {} lazy: {} field: {}", entityIncludingReference.getFieldName(), typeReferenced.getSimpleName(), lazy, fieldInEntity);
                Object o = null;
                if (StructureObject.class.equals(typeReferenced)) o = ((MorphiumObjectDAO)entityIncludingReference.getDAO()).getById(id);
                else if (Experiment.class.equals(typeReferenced)) o = xpDAO.getExperiment();
                if (o != null) {
                    //logger.trace("would dereference: object found");
                    try {
                        Field f = r.getField(entityIncludingReference.getClass(), fieldInEntity);
                        f.set(entityIncludingReference, o);
                    } catch (IllegalArgumentException ex) {
                        logger.error("referencing error", ex);
                    } catch (IllegalAccessException ex) {
                        logger.error("referencing error", ex);
                    }
                    throw new MorphiumAccessVetoException();
                    //logger.trace("would dereference: {} previous field: {}", o, entityIncludingReference.previous);
                }
            }

            @Override
            public Object didDereference(StructureObject entitiyIncludingReference, String fieldInEntity, Object referencedObject, boolean lazy) {
                logger.error("did dereference: {} entityIncludingRef: {}, fieldInEntity: {}, lazy: {}", referencedObject, entitiyIncludingReference, fieldInEntity, lazy);
                return referencedObject;
            }
        };
        m.addDereferencingListener(res);
        return res;
    }

    public static void waitForWrites(Morphium m) {
        int count = 0;
        while (m.getWriteBufferCount() > 0) {
            count++;
            if (count % 200 == 0) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                }
            }
        }
    }
    public static Morphium createMorphium(String hostName, int portNumber, String dbName) {
        try {
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setGlobalLogLevel(3);
            cfg.setDatabase(dbName);
            cfg.addHost(hostName, portNumber);
            
            Morphium m=new Morphium(cfg);
            //m.readMaximums();
            //logger.debug("max write batch size {}" , m.getMaxWriteBatchSize());
            return m;
        } catch (UnknownHostException ex) {
            logger.error("Couldnot instanciate morphim", ex);
            return null;
        }
    }
    public static Morphium createMorphium(String dbName) {
        return createMorphium("localhost", 27017, dbName);
    }
}
