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

import static image.Image.logger;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static utils.ArrayFileWriter.separator;

/**
 *
 * @author jollion
 */
public class FileIO {
    public static final Logger logger = LoggerFactory.getLogger(FileIO.class);
    public static <T> void writeToFile(String outputFile, Collection<T> objects, Function<T, String> converter) {
        try {
            java.io.FileWriter fstream;
            BufferedWriter out;
            File output = new File(outputFile);
            output.delete();
            output.mkdirs();
            if (objects.isEmpty()) return;
            fstream = new java.io.FileWriter(output);
            out = new BufferedWriter(fstream);
            Iterator<T> it = objects.iterator();
            out.write(converter.apply(it.next()));

            while(it.hasNext()) {
                out.newLine();
                out.write(converter.apply(it.next()));
            }
            out.close();
        } catch (IOException ex) {
            logger.debug("Error while writing list to file", ex);
        }
    }
    
    public static <T> List<T> readFromFile(String path, Function<String, T> converter) {
        FileReader input = null;
        List<T> res = new ArrayList<>();
        try {
            input = new FileReader(path);
            BufferedReader bufRead = new BufferedReader(input);
            String myLine = null;
            while ( (myLine = bufRead.readLine()) != null) res.add(converter.apply(myLine));
        } catch (IOException ex) {
            logger.debug("an error occured trying read file: {}, {}", path, ex);
        } finally {
            try {
                if (input!=null) input.close();
            } catch (IOException ex) { }
        }
        return res;
    }
    public static <T> void writeToZip(ZipOutputStream outStream, String relativePath, Collection<T> objects, Function<T, String> converter) {
        try {
            ZipEntry e= new ZipEntry(relativePath);
            outStream.putNextEntry(e);
            for (T o : objects) {
                outStream.write(converter.apply(o).getBytes());
                outStream.write('\n');
            }
            outStream.closeEntry();
        } catch (IOException ex) {
            logger.debug("Error while writing list to file", ex);
        }
    }
    public static <T> List<T> readFromZip(ZipFile file, String relativePath, Function<String, T> converter) {
        List<T> res = new ArrayList<>();
        try {
            ZipEntry e = file.getEntry(relativePath);
            if (e!=null) {
                Reader r = new InputStreamReader(file.getInputStream(e));
                BufferedReader bufRead = new BufferedReader(r);
                String myLine = null;
                while ( (myLine = bufRead.readLine()) != null) res.add(converter.apply(myLine));
            }
        } catch (IOException ex) {
            logger.debug("an error occured trying read file: {}, {}", relativePath, ex);
        }
        return res;
    }
    public static void writeFile(InputStream in, String filePath) {
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(filePath));
            byte[] bytesIn = new byte[4096];
            int read = 0;
            while ((read = in.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        } catch (IOException ex) {
            logger.error("", ex);
        } finally {
            try {
                bos.close();
                in.close();
            } catch (IOException ex) {
                logger.error("", ex);
            }
        }
    }
    public static class ZipWriter {
        File f;
        ZipOutputStream out;
        public ZipWriter(String path) {
            f = new File(path);
            try {
                out  = new ZipOutputStream(new FileOutputStream(f));
                out.setLevel(9);
            } catch (FileNotFoundException ex) {
                logger.debug("error while trying to write to zip file", ex);
            }
        }
        public boolean isValid() {return out!=null;}
        public <T> void write(String relativePath, List<T> objects, Function<T, String> converter) {
            writeToZip(out, relativePath, objects, converter);
        }
        public void appendFile(String relativePath, InputStream in) {
            try {
                ZipEntry e= new ZipEntry(relativePath);
                out.putNextEntry(e);
                byte[] buffer = new byte[4096];
                int length;
                while((length = in.read(buffer)) > 0) out.write(buffer, 0, length);
                out.closeEntry();
            } catch (IOException ex) {
                logger.error("", ex);
            } finally {
                try {
                    in.close();
                } catch (IOException ex) {
                    logger.error("", ex);
                }
            }
        }
        public void close() {
            if (out==null) return;
            try {
                out.close();
            } catch (IOException ex) {
                logger.error("error while closing zip file", ex);
            }
        }
    }
    public static class ZipReader {
        ZipFile in;
        public ZipReader(String path) {
            try {
                in = new ZipFile(path);
            } catch (IOException ex) {
                logger.error("error while reading zip", ex);
            } 
        }
        public boolean valid() {return in!=null;}
        public <T> List<T> readObjects(String relativePath, Function<String, T> converter) {
            return readFromZip(in, relativePath, converter);
        }
        public InputStream readFile(String relativePath) {
            try {
                ZipEntry e = in.getEntry(relativePath);
                if (e!=null) {
                    InputStream is = in.getInputStream(e);
                    return is;
                }
            } catch (IOException ex) {
                logger.debug("an error occured trying read file: {}, {}", relativePath, ex);
            }
            return null;
        }
        public void readFiles(String relativePathDir, String localDir) {
            List<String> files = listsubFiles(relativePathDir);
            for (String f : files) {
                try {
                    ZipEntry e = in.getEntry(f);
                    if (e!=null) {
                        InputStream is = in.getInputStream(e);
                        String fileName = new File(f).getName();
                        String outPath = localDir+File.separator+fileName;
                        writeFile(is, outPath);
                    }
                } catch (IOException ex) {
                    logger.debug("an error occured trying read file: {}, {}", f, ex);
                }
            }
        }
        public void close() {
            if (in==null) return;
            try {
                in.close();
            } catch (IOException ex) {
                logger.error("error while closing zip", ex);
            }
        }
        public List<String> listsubFiles(String relativeDir) {
            List<String> res = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = in.entries();
            int l = relativeDir.length();
            while(entries.hasMoreElements()){
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(relativeDir) && name.length()>l) res.add(name);
            }
            return res;
        }
        public List<String> listDirectories(String... excludeKeyWords) {
            List<String> res = listDirectories();
            if (res.isEmpty()) return res;
            for (String k : excludeKeyWords) res.removeIf(s->s.contains(k));
            return res;
        }
        public List<String> listDirectories() {
            List<String> res = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = in.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.contains(File.separator)) {
                    res.add(new File(entry.getName()).getParent());
                }
            }
            return res;
        }
    }
}
