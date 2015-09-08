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
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.AnnotationAndReflectionHelper;
import de.caluga.morphium.DereferencingListener;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumAccessVetoException;
import java.lang.reflect.Field;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class MorphiumUtils {
    public final static Logger logger = LoggerFactory.getLogger(MorphiumUtils.class);
    public static void addDereferencingListeners(Morphium m, final ObjectDAO objectDao_, final ExperimentDAO xpDao_) {
        m.addDereferencingListener(new DereferencingListener<Object, StructureObject, ObjectId>() {
            ObjectDAO objectDAO=objectDao_;
            ExperimentDAO xpDAO = xpDao_;
            AnnotationAndReflectionHelper r = new AnnotationAndReflectionHelper(true);

            @Override
            public void wouldDereference(StructureObject entityIncludingReference, String fieldInEntity, ObjectId id, Class typeReferenced, boolean lazy) throws MorphiumAccessVetoException {
                if (logger.isTraceEnabled()) logger.trace("would dereference: {} refrence: {} lazy: {} field: {}", entityIncludingReference.getFieldName(), typeReferenced.getSimpleName(), lazy, fieldInEntity);
                Object o = null;
                if (StructureObject.class.equals(typeReferenced)) o = objectDAO.getObject(id);
                else if (Experiment.class.equals(typeReferenced)) o = xpDAO.getExperiment();
                if (o != null) {
                    logger.trace("would dereference: object found");
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
                // todo solve problem with didDereference..
                logger.trace("did dereference: {} previous field: {}", referencedObject, entitiyIncludingReference.previous);
                
                if (referencedObject!=null && logger.isTraceEnabled()) logger.trace("did dereference: {} refrence: {} lazy: {} field: {}", entitiyIncludingReference.getFieldName(), referencedObject.getClass().getSimpleName(), lazy, fieldInEntity);
                //else if (logger.isTraceEnabled()) logger.trace("did dereference: {} null refrence lazy: {} field: {}", entitiyIncludingReference.getFieldName(), lazy, fieldInEntity);
                if (referencedObject!=null) {
                    if (referencedObject instanceof StructureObject) referencedObject=objectDAO.checkAgainstCache((StructureObject)referencedObject);
                    else if (referencedObject instanceof Experiment) referencedObject=xpDAO.checkAgainstCache((Experiment)referencedObject);
                }
                try {
                    Field f = r.getField(entitiyIncludingReference.getClass(), fieldInEntity);
                    f.set(entitiyIncludingReference, referencedObject);
                } catch (IllegalArgumentException ex) {
                    logger.error("referencing error", ex);
                } catch (IllegalAccessException ex) {
                    logger.error("referencing error", ex);
                }
                logger.trace("did dereference: after cache check: {} previous field: {}", referencedObject, entitiyIncludingReference.previous);
                return referencedObject;
                //return null;
            }
        });
        /*m.addDereferencingListener(new DereferencingListener<Experiment, StructureObject, ObjectId>() {
            AnnotationAndReflectionHelper r = new AnnotationAndReflectionHelper(true);

            @Override
            public void wouldDereference(StructureObject entityIncludingReference, String fieldInEntity, ObjectId id, Class typeReferenced, boolean lazy) throws MorphiumAccessVetoException {
            }

            @Override
            public Experiment didDereference(StructureObject entitiyIncludingReference, String fieldInEntity, Experiment referencedObject, boolean lazy) {
                //if (referencedObject!=null) System.out.println("did dereference: "+entitiyIncludingReference.value+ " refrence: "+referencedObject.value+ " lazy:"+lazy+ " field:"+fieldInEntity);
                if (lazy) {
                    try {
                        Field f = r.getField(entitiyIncludingReference.getClass(), fieldInEntity);
                        f.set(entitiyIncludingReference, referencedObject);
                    } catch (IllegalArgumentException ex) {
                        Logger.getLogger(MorphuimUtils.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IllegalAccessException ex) {
                        Logger.getLogger(MorphuimUtils.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                return referencedObject;
            }
        });*/
    }

    public static void waitForWrites(Morphium m) {
        int count = 0;
        while (m.getWriteBufferCount() > 0) {
            count++;
            if (count % 100 == 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                }
            }
        }
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }
    }
}
