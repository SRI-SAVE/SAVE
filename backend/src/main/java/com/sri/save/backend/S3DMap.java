/*
 * Copyright 2016 SRI International
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sri.save.backend;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sri.save.s3d.AssetGroupType;
import com.sri.save.s3d.AssetObjType;
import com.sri.save.s3d.AssetType;
import com.sri.save.s3d.FloraBaseType;
import com.sri.save.s3d.HierGroupType;
import com.sri.save.s3d.HierPartType;
import com.sri.save.s3d.S3DType;

/**
 * Keeps track of the mappings between ontology classes and model nodes.
 */
public class S3DMap {
    private static final Logger log = LoggerFactory.getLogger(S3DMap.class);

    private final Map<String, S3DAsset> assets;
    private final Map<String, S3DAsset> kbIds;
    private final Set<URL> floraUrls;

    public S3DMap() {
        assets = new HashMap<>();
        kbIds = new HashMap<>();
        floraUrls = new HashSet<>();
    }

    public void load(URL s3dUrl,
                     boolean isAuto)
            throws IOException {
        S3DType s3d;
        try {
            JAXBContext c = JAXBContext.newInstance(S3DType.class
                    .getPackage().getName());
            Unmarshaller um = c.createUnmarshaller();
            s3d = Backend.jaxbReader(s3dUrl,
                            S3DType.class.getResource("s3d.xsd"), um,
                            S3DType.class);
        } catch (Exception e) {
            throw new IOException("Reading S3D '" + s3dUrl + "'", e);
        }

        // Add this S3D's data to our map.
        Map<String, URL> floraBases = new HashMap<>();
        for (FloraBaseType fb : s3d.getFloraBase()) {
            String id = fb.getId();
            String uri = fb.getUri();
            URL fbUrl = new URL(s3dUrl, uri);
            floraBases.put(id, fbUrl);
            floraUrls.add(fbUrl);
        }
        Map<String, S3DHierGroup> groupings = new HashMap<>();
        for (HierGroupType grouping : s3d.getGrouping()) {
            S3DHierGroup s3dGroup = S3DHierGroup.fromXml(grouping);
            groupings.put(grouping.getName(), s3dGroup);
        }
        for (AssetType asset : s3d.getSemanticMapping().getAsset()) {
            String assetName = asset.getName();
            String assetUrl = asset.getUri();
            S3DHierGroup grouping = groupings.get(assetName);
            if (grouping == null) {
                throw new IOException("Asset " + assetName
                        + " doesn't contain a group in "
                        + groupings.keySet());
            }
            String floraClassName = asset.getFloraRef();
            String assetFloraSid = asset.getSid();
            URL assetFloraBase = floraBases.get(assetFloraSid);

            S3DAsset s3dasset = new S3DAsset(s3dUrl, isAuto, assetFloraBase,
                    floraClassName, assetUrl, assetName, grouping);
            add(s3dasset);
            for (AssetGroupType ag : asset.getGroup()) {
                String modelNode = ag.getNode();
                String floraSid = ag.getSid();
                URL floraBase = floraBases.get(floraSid);
                String grpFloraClass = ag.getFloraRef();
                String grpName = ag.getName();
                S3DGroup g = new S3DGroup(floraBase, grpFloraClass,
                        grpName, modelNode);
                s3dasset.add(g);
            }
            for (AssetObjType mo : asset.getObject()) {
                String modelNode = mo.getNode();
                String floraSid = mo.getSid();
                URL floraBase = floraBases.get(floraSid);
                String objFloraClass = mo.getFloraRef();
                S3DObject m = new S3DObject(floraBase, objFloraClass,
                        modelNode);
                s3dasset.add(m);
            }
        }
    }

    private void add(S3DAsset a) {
        log.debug("Adding asset {}", a);
        assets.put(a.getId(), a);
    }

    /**
     * Removes data associated with a given S3D file.
     */
    public void remove(URL url) {
        // TODO SAVE-288
    }
    /**
     * Retrieves all the <flora_base> URLs referenced in all the S3D files we
     * know about, whether or not the flora URL was used by any assets or
     * components.
     */
    public Set<URL> getFloraUrls() {
        return floraUrls;
    }

    public List<S3DAsset> getAssets() {
        return new ArrayList<>(assets.values());
    }

    public void addAssetByKbId(String kbId,
                               S3DAsset asset) {
        kbIds.put(kbId, asset);
    }

    public S3DAsset getAssetByKbId(String kbId) {
        return kbIds.get(kbId);
    }

    /**
     * The ID is not equal to the KB ID. It's uniquely assigned to each asset
     * in the tool shelf.
     */
    public S3DAsset getAssetById(String id) {
        return assets.get(id);
    }

    /**
     * Represents one asset from an S3D file. The asset potentially contains
     * many components, described by S3DObject objects.
     */
    public static class S3DAsset {
        private final URL s3dUrl;
        private final boolean isAuto;
        private final URL floraBase;
        private final String name;
        private final String id;
        private final String floraClass;
        private final String assetUrl;
        private final S3DHierGroup grouping;
        private final Map<String, S3DComponent> objects;

        private static final Random rand = new Random();

        public S3DAsset(URL s3dUrl,
                        boolean isAuto,
                        URL floraBase,
                        String floraClass,
                        String assetUrl,
                        String name,
                        S3DHierGroup grouping) {
            this.s3dUrl = s3dUrl;
            this.isAuto = isAuto;
            this.floraBase = floraBase;
            this.name = name;
            this.floraClass = floraClass;
            this.assetUrl = assetUrl;
            this.grouping = grouping;
            objects = new HashMap<>();

            /*
             * TODO This will eventually be used to give a different ID to each
             * EUI client, so all the EUIs can share the same backend and KB.
             * When an EUI asks to instantiate something, the associated ID will
             * tell us which EUI it is. But for now, everything's shared, so the
             * ID is assigned per asset, not per asset+EUI.
             */
            byte[] idbuf = new byte[5];
            rand.nextBytes(idbuf);
            StringBuffer idsb = new StringBuffer();
            for (byte byt : idbuf) {
                idsb.append(String.format("%02x", byt));
            }
            id = idsb.toString();
        }

        public boolean isAuto() {
            return isAuto;
        }

        public URL getFloraBase() {
            return floraBase;
        }

        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }

        public String getFloraClass() {
            return floraClass;
        }

        /*
         * TODO SAVE-152: This really should be a URL, resolved relative to the
         * S3D's location.
         */
        public String getAssetUrl() {
            return assetUrl;
        }

        public void add(S3DObject obj) {
            log.debug("Added S3D mapping to '{}': {}", id, obj);
            objects.put(obj.getNodeOrName(), obj);
        }

        public void add(S3DGroup grp) {
            log.debug("Added S3D group mapping to '{}': {}", id, grp);
            objects.put(grp.getNodeOrName(), grp);
        }

        public S3DComponent getObject(String nodeName) {
            return objects.get(nodeName);
        }

        public S3DHierGroup getGrouping() {
            return grouping;
        }

        @Override
        public String toString() {
            return "S3DAsset [floraBase=" + floraBase + ", name=" + name
                    + ", id=" + id + ", floraClass=" + floraClass
                    + ", assetUrl=" + assetUrl + "]";
        }

        public URL getS3dUrl() {
            return s3dUrl;
        }
    }

    /**
     * A hierarchy group, corresponding to an entry in the <grouping> element of
     * the S3D file.
     */
    public static class S3DHierGroup {
        private String name;
        private String node;
        private List<String> parts;
        private List<S3DHierGroup> groups;

        public static S3DHierGroup fromXml(HierGroupType hGroup) {
            S3DHierGroup group = new S3DHierGroup();
            group.name = hGroup.getName();
            group.node = hGroup.getNode();
            List<HierPartType> partsList = hGroup.getPart();
            if (!partsList.isEmpty()) {
                group.parts = new ArrayList<>();
                for (HierPartType hPart : partsList) {
                    group.parts.add(hPart.getNode());
                }
            }
            List<HierGroupType> groupsList = hGroup.getGroup();
            if (!groupsList.isEmpty()) {
                group.groups = new ArrayList<>();
                for (HierGroupType subGroup : groupsList) {
                    group.groups.add(fromXml(subGroup));
                }
            }

            return group;
        }

        public S3DHierGroup() {
        }

        public S3DHierGroup(String name,
                        List<String> parts,
                        List<S3DHierGroup> groups) {
            this.name = name;
            this.parts = parts;
            this.groups = groups;
        }

        public List<S3DHierGroup> getGroups() {
            return groups;
        }

        @Override
        public String toString() {
            return "S3DGroup [name=" + name + ", node=" + node + ", parts="
                    + parts + ", groups=" + groups + "]";
        }
    }

    public interface S3DComponent {
        String getNodeOrName();
        String getFloraClass();
    }

    /**
     * Represents one mapping between an ontology class and a 3D model node.
     * This is a component of an asset, not the entire asset.
     */
    public static class S3DObject
            implements S3DComponent {
        private final URL floraBase;
        private final String floraClass;
        private final String modelNode;

        public S3DObject(URL floraBase,
                         String floraClass,
                         String modelNode) {
            this.floraBase = floraBase;
            this.floraClass = floraClass;
            this.modelNode = modelNode;
        }

        public URL getFloraBase() {
            return floraBase;
        }

        @Override
        public String getFloraClass() {
            return floraClass;
        }

        public String getModelNode() {
            return modelNode;
        }

        @Override
        public String getNodeOrName() {
            return getModelNode();
        }

        @Override
        public String toString() {
            return "S3DMapping [floraBase=" + floraBase + ", floraClass="
                    + floraClass + ", modelNode=" + modelNode + "]";
        }
    }

    public static class S3DGroup
            implements S3DComponent {
        private final URL floraBase;
        private final String floraClass;
        private final String name;
        private final String modelNode;

        public S3DGroup(URL floraBase,
                        String floraClass,
                        String name,
                        String modelNode) {
            this.floraBase = floraBase;
            this.floraClass = floraClass;
            this.name = name;
            this.modelNode = modelNode;
        }

        @Override
        public String getFloraClass() {
            return floraClass;
        }

        @Override
        public String getNodeOrName() {
            if (modelNode != null && !modelNode.isEmpty()) {
                return modelNode;
            } else {
                return name;
            }
        }

        @Override
        public String toString() {
            return "S3DGroup [floraBase=" + floraBase + ", floraClass="
                    + floraClass + ", name=" + name + ", modelNode="
                    + modelNode + "]";
        }
    }
}
