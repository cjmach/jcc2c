/*
 *  Copyright 2024 Carlos Machado
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package pt.cjmach.jcc2c;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author cmachado
 */
public class ReportConverter {
    
    public void convert(File inputFile, File outputFile, File[] sourceDirs) 
            throws ParserConfigurationException, SAXException, IOException, TransformerException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(true);
        factory.setFeature("http://xml.org/sax/features/namespaces", false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document tree;
        if ("-".equals(inputFile.getName())) {
            System.err.println("[INFO] Parsing input from stdin...");
            tree = builder.parse(System.in);
        } else {
            System.err.println("[INFO] Parsing input from file " + inputFile.getAbsolutePath());
            tree = builder.parse(inputFile);
        }
        Element target = tree.createElement("coverage");
        convertRoot(tree, target, sourceDirs);
        
        if ("-".equals(outputFile.getName())) {
            System.err.println("[INFO] Writing output to stdout...");
            nodeToStream(target, System.out);
        } else {
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                System.err.println("[INFO] Writing output to file " + outputFile.getAbsolutePath());
                nodeToStream(target, fos);
            }
        }
    }
    
    private void convertRoot(Document tree, Element target, File[] sourceDirs) throws IOException {
        Element source = tree.getDocumentElement();
        NodeList list = source.getElementsByTagName("sessioninfo");
        if (list.getLength() <= 0) {
            throw new IOException("[ERROR] Could not find the 'sessioninfo' XML element.");
        }
        
        Element sessionInfo = (Element) list.item(0);
        String start = sessionInfo.getAttribute("start");
        long startValue = Long.parseLong(start) / 1000L;
        target.setAttribute("timestamp", Long.toString(startValue));
        
        Element sources = tree.createElement("sources");
        for (File sourceDir : sourceDirs) {
            Element sourceElem = tree.createElement("source");
            sourceElem.setTextContent(sourceDir.getPath());
            sources.appendChild(sourceElem);
        }
        target.appendChild(sources);
        
        Element packages = tree.createElement("packages");
        NodeList sourcePackages = source.getElementsByTagName("package");
        for (int i = 0; i < sourcePackages.getLength(); i++) {
            Element pkg = (Element) sourcePackages.item(i);
            Element pkgElem = convertPackage(tree, pkg);
            packages.appendChild(pkgElem);
        }
        target.appendChild(packages);
        
        addCounters(source, target);
    }
    
    private Element convertPackage(Document tree, Element pkg) {
        Element cPackage = tree.createElement("package");
        String nameAttr = pkg.getAttribute("name");
        cPackage.setAttribute("name", nameAttr.replaceAll("/", "."));
        
        Element classes = tree.createElement("classes");
        NodeList sourceClasses = pkg.getElementsByTagName("class");
        for (int i = 0; i < sourceClasses.getLength(); i++) {
            Element clazz = (Element) sourceClasses.item(i);
            Element classElem = convertClass(tree, clazz, pkg);
            classes.appendChild(classElem);
        }
        cPackage.appendChild(classes);
        
        addCounters(pkg, cPackage);
        
        return cPackage;
    }
    
    private Element convertClass(Document tree, Element clazz, Element pkg) {
        Element cClass = tree.createElement("class");
        String nameAttr = clazz.getAttribute("name");
        cClass.setAttribute("name", nameAttr.replaceAll("/", "."));
        cClass.setAttribute("filename", guessFilename(nameAttr));
        
        List<Element> allLines = findLines(pkg, cClass.getAttribute("filename"));
        
        Element cMethods = tree.createElement("methods");
        NodeList sourceMethods = clazz.getElementsByTagName("method");
        List<Element> allMethods = new ArrayList<>();
        for (int i = 0; i < sourceMethods.getLength(); i++) {
            Element sourceMethod = (Element) sourceMethods.item(i);
            allMethods.add(sourceMethod);
        }
        for (Element method : allMethods) {
            List<Element> methodLines = getMethodLines(method, allMethods, allLines);
            cMethods.appendChild(convertMethod(tree, method, methodLines));
        }
        cClass.appendChild(cMethods);
        
        addCounters(clazz, cClass);
        convertLines(tree, allLines, cClass);
        return cClass;
    }
    
    private Element convertMethod(Document tree, Element method, List<Element> allLines) {
        Element cMethod = tree.createElement("method");
        cMethod.setAttribute("name", method.getAttribute("name"));
        cMethod.setAttribute("signature", method.getAttribute("desc"));
        
        addCounters(method, cMethod);
        convertLines(tree, allLines, cMethod);
        
        return cMethod;
    }
    
    private void nodeToStream(Element node, OutputStream outputStream) throws TransformerException {
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(node), new StreamResult(outputStream));
    }
    
    private static final Pattern FILENAME_PATTERN = Pattern.compile("([^\\$]*)");
    
    private static String guessFilename(String nameAttr) {
        Matcher m = FILENAME_PATTERN.matcher(nameAttr);
        if (m.matches()) {
            return m.group(1) + ".java";
        }
        return nameAttr + ".java";
    }
    
    private static void addCounters(Element source, Element target) {
        target.setAttribute("line-rate", counter(source, "LINE"));
        target.setAttribute("branch-rate", counter(source, "BRANCH"));
        target.setAttribute("complexity", counter(source, "COMPLEXITY", ReportConverter::sum));
    }
    
    private static String counter(Element source, String type) {
        return counter(source, type, ReportConverter::fraction);
    }
    
    private static String counter(Element source, String type, BiFunction<Integer, Integer, String> operation) {
        NodeList cs = source.getElementsByTagName("counter");
        Element c = null;
        for (int i = 0; i < cs.getLength(); i++) {
            Element ct = (Element) cs.item(i);
            if (type.equals(ct.getAttribute("type"))) {
                c = ct;
                break;
            }
        }
        if (c != null) {
            int covered = Integer.parseInt(c.getAttribute("covered"));
            int missed = Integer.parseInt(c.getAttribute("missed"));
            
            return operation.apply(covered, missed);
        }
        return "0.0";
    }
    
    private static String fraction(int covered, int missed) {
        double result = ((double) covered) / (covered + missed);
        return Double.toString(result);
    }
    
    private static String sum(int covered, int missed) {
        double result = covered + missed;
        return Double.toString(result);
    }
    
    private static void convertLines(Document tree, List<Element> allLines, Element into) {
        Element lines = tree.createElement("lines");
        for (Element line : allLines) {
            int mb = Integer.parseInt(line.getAttribute("mb"));
            int cb = Integer.parseInt(line.getAttribute("cb"));
            int ci = Integer.parseInt(line.getAttribute("ci"));
            
            
            Element cline = tree.createElement("line");
            cline.setAttribute("number", line.getAttribute("nr"));
            cline.setAttribute("hits", ci > 0 ? "1" : "0");
            
            if (mb + cb > 0) {
                double percentage = 100.0 * (cb / (cb + mb));
                cline.setAttribute("branch", "true");
                cline.setAttribute("condition-coverage", percentage + "% (" + cb + "/" + (mb + cb) + ")");
                
                Element conditions = tree.createElement("conditions");
                Element condition = tree.createElement("condition");
                condition.setAttribute("number", "0");
                condition.setAttribute("type", "jump");
                condition.setAttribute("coverage", percentage + "%");
                conditions.appendChild(condition);
                cline.appendChild(conditions);
            } else {
                cline.setAttribute("branch", "false");
            }
            lines.appendChild(cline);
        }
        into.appendChild(lines);
    }
    
    private static List<Element> findLines(Element pkg, String filename) {
        String basename = new File(filename).getName();
        List<Element> lines = new ArrayList<>();
        NodeList sourceFiles = pkg.getElementsByTagName("sourceFile");
        for (int i = 0; i < sourceFiles.getLength(); i++) {
            Element sourceFile = (Element) sourceFiles.item(i);
            if (basename.equals(sourceFile.getAttribute("name"))) {
                NodeList sourceFileLines = sourceFile.getElementsByTagName("line");
                for (int j = 0; j < sourceFileLines.getLength(); i++) {
                    Element line = (Element) sourceFileLines.item(i);
                    lines.add(line);
                }
            }
        }
        return lines;
    }
    
    private static List<Element> getMethodLines(Element method, List<Element> allMethods, List<Element> allLines) {
        String tempLine = method.getAttribute("line");
        int startLine = 0;
        if (tempLine != null) {
            startLine = Integer.parseInt(tempLine);
        }
        List<Integer> larger = new ArrayList<>();
        for (Element jm : allMethods) {
            tempLine = jm.getAttribute("line");
            int jmLine = 0;
            if (tempLine != null) {
                jmLine = Integer.parseInt(tempLine);
            }
            if (jmLine > startLine) {
                larger.add(jmLine);
            }
        }
        int endLine = Integer.MAX_VALUE;
        if (!larger.isEmpty()) {
            endLine = larger.stream().mapToInt(v -> v).min().getAsInt();
        }
        
        List<Element> result = new ArrayList<>();
        for (Element line : allLines) {
            int nr = Integer.parseInt(line.getAttribute("nr"));
            if (startLine <= nr && nr < endLine) {
                result.add(line);
            }
        }
        return result;
    }
}
