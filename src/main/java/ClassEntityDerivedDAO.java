
import com.mongodb.MongoClient;
import java.util.List;
import morphiaTest2.ClassEntityDerived;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.dao.BasicDAO;
import org.mongodb.morphia.query.QueryResults;

/*
 * Copyright (C) 2015 ImageJ
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

/**
 *
 * @author jollion
 */
public class ClassEntityDerivedDAO extends BasicDAO<ClassEntityDerived, String>{

    public ClassEntityDerivedDAO(Class<ClassEntityDerived> entityClass, MongoClient mongoClient, Morphia morphia, String dbName) {
        super(entityClass, mongoClient, morphia, dbName);
    }
    @Override
    public QueryResults<ClassEntityDerived> find() {
        return super.find(this.createQuery().disableValidation().filter("className", ClassEntityDerived.class.getName()));
    }
}
