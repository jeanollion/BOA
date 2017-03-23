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
package dataStructure.objects;

/**
 *
 * @author jollion
 */
public class MasterDAOFactory {
    public enum DAOType {Morphium, DBMap, Basic};
    private static DAOType currentType = DAOType.Morphium;

    public static DAOType getCurrentType() {
        return currentType;
    }

    public static void setCurrentType(DAOType currentType) {
        MasterDAOFactory.currentType = currentType;
    }
    
    public static MasterDAO createDAO(String dbName, String dir, DAOType daoType) {
        if (daoType.equals(DAOType.Morphium)) return new MorphiumMasterDAO(dbName, dir);
        else if (daoType.equals(DAOType.DBMap)) return new DBMapMasterDAO(dir, dbName);
        else if (daoType.equals(DAOType.Basic)) return new BasicMasterDAO();
        else return null;
    }
    public static MasterDAO createDAO(String dbName, String dir) {
        return createDAO(dbName, dir, currentType);
    }
}
