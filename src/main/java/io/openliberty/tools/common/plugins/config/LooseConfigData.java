/**
 * (C) Copyright IBM Corporation 2017.
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
package io.openliberty.tools.common.plugins.config;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

public class LooseConfigData extends XmlDocument {
    
    public LooseConfigData() throws ParserConfigurationException {
        createDocument("archive");
    }
    
    public void addDir(File src, String target) throws DOMException, IOException {
        if (src != null && src.exists() && src.isDirectory()) {
            addDir(doc.getDocumentElement(), src, target);
        }
    }
    
    public void addDir(Element parent, File src, String target) throws DOMException, IOException {
        if (src != null && src.exists() && src.isDirectory()) {
            Element child = doc.createElement("dir");
            addElement(parent, child, src, target);
        }
    }
    
    public void addFile(File src, String target) throws DOMException, IOException {
        if (src != null && src.exists() && src.isFile()) {
            addFile(doc.getDocumentElement(), src, target);
        }
    }
    
    public void addFile(Element parent, File src, String target) throws DOMException, IOException {
        if (src != null && src.exists() && src.isFile()) {
            Element child = doc.createElement("file");
            addElement(parent, child, src, target);
        }
    }
    
    public Element addArchive(String target) {
        return addArchive(doc.getDocumentElement(), target);
    }
    
    public Element addArchive(Element parent, String target) {
        Element child = doc.createElement("archive");
        addElement(parent, child, target);
        return child;
    }
    
    public void addArchive(File src, String target) throws DOMException, IOException {
        Element child = addArchive(target);
        addElement(child, doc.createElement("dir"), src, "/");
    }
    
    public void toXmlFile(File xmlFile) throws Exception {        
        writeXMLDocument(xmlFile);
    }
    
    public Element getDocumentRoot() {
        return doc.getDocumentElement();
    }
    
    private void addElement(Element parent, Element child, File src, String target) throws DOMException, IOException {
        child.setAttribute("sourceOnDisk", src.getCanonicalPath());
        addElement(parent, child, target);
    }
    
    private void addElement(Element parent, Element child, String target) {
        child.setAttribute("targetInArchive", target);
        parent.appendChild(child);
    }
}
