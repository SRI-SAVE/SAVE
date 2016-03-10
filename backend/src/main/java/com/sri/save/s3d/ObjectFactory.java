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

//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2014.11.13 at 04:12:26 PM MST 
//


package com.sri.save.s3d;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.sri.save.s3d package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _S3D_QNAME = new QName("", "S3D");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.sri.save.s3d
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link S3DType }
     * 
     */
    public S3DType createS3DType() {
        return new S3DType();
    }

    /**
     * Create an instance of {@link AssetGroupType }
     * 
     */
    public AssetGroupType createAssetGroupType() {
        return new AssetGroupType();
    }

    /**
     * Create an instance of {@link AssetType }
     * 
     */
    public AssetType createAssetType() {
        return new AssetType();
    }

    /**
     * Create an instance of {@link HeadType }
     * 
     */
    public HeadType createHeadType() {
        return new HeadType();
    }

    /**
     * Create an instance of {@link FloraBaseType }
     * 
     */
    public FloraBaseType createFloraBaseType() {
        return new FloraBaseType();
    }

    /**
     * Create an instance of {@link AssetObjType }
     * 
     */
    public AssetObjType createAssetObjType() {
        return new AssetObjType();
    }

    /**
     * Create an instance of {@link SemanticMappingType }
     * 
     */
    public SemanticMappingType createSemanticMappingType() {
        return new SemanticMappingType();
    }

    /**
     * Create an instance of {@link HierGroupType }
     * 
     */
    public HierGroupType createHierGroupType() {
        return new HierGroupType();
    }

    /**
     * Create an instance of {@link HierPartType }
     * 
     */
    public HierPartType createHierPartType() {
        return new HierPartType();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link S3DType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "S3D")
    public JAXBElement<S3DType> createS3D(S3DType value) {
        return new JAXBElement<S3DType>(_S3D_QNAME, S3DType.class, null, value);
    }

}
