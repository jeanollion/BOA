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
package TestUtils;
import static TestUtils.TestUtils.logger;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import image.Image;
import image.ImageReader;
import image.ImgLib2ImageWrapper;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.imagej.ImageJ;
import net.imagej.ops.Op;
import static net.imagej.ops.OpEnvironment.run;
import net.imagej.ops.OpInfo;
import net.imagej.ops.OpRef;
import net.imagej.ops.filter.dog.DoGSingleSigmas;
import net.imagej.ops.filter.gauss.GaussRAISingleSigma;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.module.MethodCallException;
import org.scijava.module.Module;
import org.scijava.module.ModuleItem;
import plugins.ops.OpHelpers;
import utils.Utils;
/**
 *
 * @author jollion
 */
public class TestIJOps {
    public static void main(String[] args) {
        final ImageJ ij = new ImageJ();
        //ij.launch(args);
        
        logger.debug("ops: {}", ij.op().ops());
        Collection<OpInfo> thlds = ij.op().infos();
        thlds.removeIf(o->!o.isNamespace("threshold"));
        logger.debug("thld methods: {}", Utils.toStringList(thlds, o->o.getName()));
        Collection<OpInfo> filters = ij.op().infos();
        //filters.removeIf(ij.op().matcher().typesMatch(candidate) // see DefaultOpMatchingService to get all unary operators? see also inplace ? 
        if (true) return;
        logger.debug("dog: {}", ij.op().help("dog"));
        String opName = ij.op().ops().iterator().next();
        // TODO ne pas creer les operations comme ça -> mal initialisée. utiliser les noms
        //Op gauss = new GaussRAISingleSigma();
        Op dog=new DoGSingleSigmas();
        OpInfo info = ij.op().info(dog);
        logger.debug("dog: {}", info);
        logger.debug("inputs : {} ", Utils.toStringList(info.inputs(), o->o.getType()+ " Name:"+o.getName()+ " is double:"+(o.getGenericType()==double.class)));
        for (ModuleItem i : info.inputs()) {
            if (i.getType()==double.class) logger.debug("parameter: {} (style: {}", OpHelpers.mapParameter(i), i.getWidgetStyle());
            if (i.getType()==net.imglib2.outofbounds.OutOfBoundsFactory.class) {
                logger.debug("ofb: style: {}, def: {}", i.getWidgetStyle());
            }
        }
        Image image = ImageReader.openImage("/home/jollion/Pictures/t00000_c01.tif").setName("input");
        Img img = ImgLib2ImageWrapper.getImage(image);
        Img converted = ij.op().convert().float32(img);
        Img out = ij.op().create().img(converted);
        
        //Img<FloatType> dog = ij.op().create().img(converted);

        // Do the DoG filtering using ImageJ Ops
        //ij.op().filter().dog(out, converted, 1.0, 1.25);
        
        ij.op().run(dog, out, converted, 1.0, 1.25, null);
        Image res = ImgLib2ImageWrapper.wrap((RandomAccessibleInterval)out).setName("res");
        ImageWindowManagerFactory.showImage(res);
        /*Module mod = ij.op().module(gauss, out, img, 2d, null);
        try {
            mod.initialize();
        } catch (MethodCallException ex) {
           logger.debug("init error", ex);
        }
        Object blured = run(mod);
        Image bluredIm = ImgLib2ImageWrapper.wrap((RandomAccessibleInterval)blured).setName("blured");
        ImageWindowManagerFactory.showImage(image);
        ImageWindowManagerFactory.showImage(bluredIm);*/
    }
}
