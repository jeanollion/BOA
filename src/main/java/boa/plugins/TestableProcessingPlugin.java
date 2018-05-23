/*
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins;

import static boa.configuration.parameters.Parameter.logger;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.gui.imageInteraction.TrackMask;
import boa.image.Image;
import boa.image.TypeConverter;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author Jean Ollion
 */
public interface TestableProcessingPlugin extends ImageProcessingPlugin {
    public void setTestDataStore(Map<StructureObject, TestDataStore> stores);

    public static Consumer<Image> getAddTestImageConsumer(Map<StructureObject, TestDataStore> stores, StructureObject parent) {
        if (stores==null) return null;
        return i -> {
            if (i.sameDimensions(parent.getBounds())) {
                stores.get(parent).addIntermediateImage(i.getName(), i);
            } else {
                stores.get(parent).addMisc(l -> {ImageWindowManagerFactory.showImage(i);});
            }
        };
    }
    
    public static class TestDataStore {
        final StructureObject parent;
        Map<String, Image> images = new HashMap<>();
        Map<String, Integer> nameOrder = new HashMap<>();
        List<Consumer<List<StructureObject>>> miscData = new ArrayList<>();
        public TestDataStore(StructureObject parent) {
            this.parent= parent;
        }

        public StructureObject getParent() {
            return parent;
        }
        
        public void addIntermediateImage(String imageName, Image image) {
            if (image==null) return;
            images.put(imageName, image);
            nameOrder.put(imageName, nameOrder.size());
        }
        /**
         * Adds misc data that will be displayed by running the run method of {@param misc}
         * @param misc data displayed though run method
         */
        public void addMisc(Consumer<List<StructureObject>> misc) {
            miscData.add(misc);
        }
        public void displayMiscData(List<StructureObject> selectedObjects) {
            miscData.forEach((r) -> r.accept(selectedObjects));
        }
    }
    
    public static Pair<ImageObjectInterface, List<Image>> buildIntermediateImages(Collection<TestDataStore> stores, int parentStructureIdx) {
        if (stores.isEmpty()) return null;
        int childStructure = stores.stream().findAny().get().parent.getStructureIdx();
        
        Set<String> allImageNames = stores.stream().map(s->s.images.keySet()).flatMap(Set::stream).collect(Collectors.toSet());
        List<StructureObject> parents = stores.stream().map(s->s.parent.getParent(parentStructureIdx)).distinct().sorted().collect(Collectors.toList());
        StructureObjectUtils.enshureContinuousTrack(parents);
        TrackMask ioi = TrackMask.generateTrackMask(parents, childStructure);
        List<Image> images = new ArrayList<>();
        allImageNames.forEach(name -> {
            int maxBitDepth = stores.stream().filter(s->s.images.containsKey(name)).mapToInt(s->s.images.get(name).getBitDepth()).max().getAsInt();
            Image image = ioi.generateEmptyImage(name, Image.createEmptyImage(maxBitDepth)).setName(name);
            stores.stream().filter(s->s.images.containsKey(name)).forEach(s-> Image.pasteImage(TypeConverter.cast(s.images.get(name), image), image, ioi.getObjectOffset(s.parent)));
            images.add(image);
        });
        // get order for each image (all images are not contained in all stores) & store
        Function<String, Double> getOrder = name -> stores.stream().filter(s -> s.nameOrder.containsKey(name)).mapToDouble(s->s.nameOrder.get(name)).max().orElse(Double.POSITIVE_INFINITY);
        Map<String, Double> orderMap = allImageNames.stream().collect(Collectors.toMap(n->n, n->getOrder.apply(n)));
        Collections.sort(images, (i1, i2)->Double.compare(orderMap.get(i1.getName()), orderMap.get(i2.getName())));
        return new Pair<>(ioi, images);
    }
    
}
