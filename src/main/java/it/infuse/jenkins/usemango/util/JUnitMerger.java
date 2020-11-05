package it.infuse.jenkins.usemango.util;

import hudson.FilePath;
import it.infuse.jenkins.usemango.exception.UseMangoException;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.jws.soap.SOAPBinding;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

public class JUnitMerger {

    public static String merge(List<FilePath> resultFiles) throws UseMangoException {
        try {
            if (resultFiles.stream().anyMatch(f -> !f.getName().endsWith(".xml"))){
                throw new UseMangoException("Files list for merging must only contain XML files.");
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.newDocument();
            Element rootSuite = document.createElement("testsuite");
            document.appendChild(rootSuite);

            int failures = 0;
            float time = 0f;
            int testCount = 0;
            for (FilePath f : resultFiles) {
                if (f.getName().endsWith(".xml")) {
                    Document testCaseDoc = builder.parse(f.read());
                    Node suite = testCaseDoc.getFirstChild();
                    NodeList children = suite.getChildNodes();
                    NamedNodeMap attributes = suite.getAttributes();
                    failures += Integer.parseInt(attributes.getNamedItem("failures").getNodeValue());
                    time += Float.parseFloat(attributes.getNamedItem("time").getNodeValue());
                    testCount += children.getLength();

                    for (int i = 0; i < children.getLength(); i++) {
                        Node importedNode = document.importNode(children.item(i), true);
                        rootSuite.appendChild(importedNode);
                    }
                }
            }

            rootSuite.setAttribute("time", Float.toString(time));
            rootSuite.setAttribute("tests", String.valueOf(testCount));
            rootSuite.setAttribute("failures", Integer.toString(failures));

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            Writer writer = new StringWriter();
            Result output = new StreamResult(writer);
            Source input = new DOMSource(document);
            transformer.transform(input, output);
            return writer.toString();
        } catch (ParserConfigurationException
                | IOException
                | SAXException
                | InterruptedException
                | TransformerException e) {
            Log.severe("Error occurred while merge useMango result files: " + e.getMessage());
            Log.severe(Arrays.toString(e.getStackTrace()));
            throw new UseMangoException(e.getMessage(), e.getCause());
        }
    }
}
