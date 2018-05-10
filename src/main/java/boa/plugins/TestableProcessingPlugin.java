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
import boa.gui.GUI;
import static boa.gui.PluginConfigurationUtils.lastTest;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.gui.imageInteraction.TrackMask;
import boa.image.Image;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author Jean Ollion
 */
public interface TestableProcessingPlugin {
    public void setTestDataStore(Map<StructureObject, TestDataStore> stores);

    public static class TestDataStore {
        final StructureObject parent;
        Map<String, Image> images = new HashMap<>();
        List<Runnable> miscData = new ArrayList<>();
        public TestDataStore(StructureObject parent) {
            this.parent= parent;
        }

        public StructureObject getParent() {
            return parent;
        }
        
        public void addIntermediateImage(String imageName, Image image) {
            images.put(imageName, image);
        }
        /**
         * Adds misc data that will be displayed by running the run method of {@param misc}
         * @param misc data displayed though run method
         */
        public void addMisc(Runnable misc) {
            miscData.add(misc);
        }
        public void displayMiscData() {
            miscData.forEach((r) -> r.run());
        }
    }
    
    public static Pair<ImageObjectInterface, List<Image>> buildIntermediateImages(List<TestDataStore> stores, int parentStructureIdx) {
        if (stores.isEmpty()) return null;
        int childStructure = stores.get(0).parent.getStructureIdx();
        stores.forEach(s->s.addIntermediateImage("input", s.parent.getParent(parentStructureIdx).getRawImage(childStructure))); // add input image
        Set<String> allImageNames = stores.stream().map(s->s.images.keySet()).flatMap(Set::stream).collect(Collectors.toSet());
        List<StructureObject> parents = stores.stream().map(s->s.parent.getParent(parentStructureIdx)).distinct().sorted().collect(Collectors.toList());
        TrackMask ioi = TrackMask.generateTrackMask(parents, childStructure);
        List<Image> images = new ArrayList<>();
        allImageNames.forEach(name -> {
            Image image = ioi.generateEmptyImage(name, stores.stream().map(s->s.images.get(name)).filter(i->i!=null).findAny().get()).setName(name);
            stores.stream().filter(s->s.images.containsKey(name)).forEach(s-> Image.pasteImage(s.images.get(name), image, ioi.getObjectOffset(s.parent)));
            images.add(image);
        });
        Collections.sort(images, (i1, i2)->i1.getName().compareToIgnoreCase(i2.getName()));
        return new Pair<>(ioi, images);
    }
    public static void displayIntermediateImages(List<TestDataStore> stores, int parentStructureIdx) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (lastTest!=null) { // only one interactive image at the same time -> if 2 test on same track -> collapse in windows manager ! 
            for (Image i : lastTest.value) iwm.removeImage(i);
            iwm.removeImageObjectInterface(lastTest.key.getKey()); 
            GUI.getInstance().populateSelections(); 
            iwm.getDisplayer().close(lastTest.key);
            lastTest=null;
        }
        
        Pair<ImageObjectInterface, List<Image>> res = buildIntermediateImages(stores, parentStructureIdx);
        int childStructure = stores.get(0).parent.getStructureIdx();
        int segParentStrutureIdx = stores.get(0).parent.getExperiment().getStructure(childStructure).getSegmentationParentStructure();
        res.value.forEach((image) -> {
            iwm.addImage(image, res.key, childStructure, true);
            iwm.addWindowClosedListener(image, e->{
                iwm.removeImage(image);
                lastTest.value.remove(image);
                if (lastTest.value.isEmpty()) {
                    iwm.removeImageObjectInterface(res.key.getKey()); 
                    GUI.getInstance().populateSelections(); 
                    lastTest=null; 
                    logger.debug("cloose image"); 
                }
                return null;
            });
        });
        if (parentStructureIdx!=segParentStrutureIdx) { // add a selection to diplay the segmentation parent on the intermediate image
            List<StructureObject> parentTrack = stores.stream().map(s->s.parent.getParent(parentStructureIdx)).distinct().sorted().collect(Collectors.toList());
            Collection<StructureObject> bact = Utils.flattenMap(StructureObjectUtils.getChildrenByFrame(parentTrack, segParentStrutureIdx));
            Selection bactS = new Selection("testTrackerSelection", parentTrack.get(0).getDAO().getMasterDAO());
            bactS.setColor("Grey");
            bactS.addElements(bact);
            bactS.setIsDisplayingObjects(true);
            GUI.getInstance().addSelection(bactS);
            res.value.forEach((image) -> GUI.updateRoiDisplayForSelections(image, res.key));
        }
        GUI.getInstance().setInteractiveStructureIdx(childStructure);
        res.value.forEach((image) -> {
            iwm.displayAllObjects(image);
            iwm.displayAllTracks(image);
        });
        
    }
}
