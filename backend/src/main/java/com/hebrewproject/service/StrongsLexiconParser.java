package com.hebrewproject.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses HebrewStrong.xml into a Map of Strong's ID -&gt; StrongsLexiconEntry.
 * Direct port of root_finder_v2.py's regex-based entry extraction, but via DOM
 * (same XXE-hardened approach as GenesisIngestionRunner) rather than regex -
 * more robust against incidental formatting in the source file, and
 * equivalent in behavior: getTextContent() concatenates text nodes across
 * nested tags exactly like the Python script's raw-string substring checks did.
 */
@Component
public class StrongsLexiconParser {

    public Map<String, StrongsLexiconEntry> parseClasspathResource(String path) throws Exception {
        try (var is = new ClassPathResource(path).getInputStream()) {
            return parse(is.readAllBytes());
        }
    }

    public Map<String, StrongsLexiconEntry> parse(byte[] xmlBytes) throws Exception {
        Document doc = parseDocument(xmlBytes);
        Map<String, StrongsLexiconEntry> entries = new HashMap<>();

        NodeList entryNodes = doc.getElementsByTagName("entry");
        for (int i = 0; i < entryNodes.getLength(); i++) {
            Element entryEl = (Element) entryNodes.item(i);
            String id = stripHPrefix(entryEl.getAttribute("id"));

            Element sourceEl = firstChildElement(entryEl, "source");
            String sourceText = sourceEl != null ? sourceEl.getTextContent() : "";
            boolean primitiveRoot = sourceText.contains("primitive root") || sourceText.contains("a primitive");

            List<String> refs = new ArrayList<>();
            if (sourceEl != null) {
                NodeList refNodes = sourceEl.getElementsByTagName("w");
                for (int r = 0; r < refNodes.getLength(); r++) {
                    Element refEl = (Element) refNodes.item(r);
                    String src = refEl.getAttribute("src");
                    if (!src.isEmpty()) {
                        refs.add(stripHPrefix(src));
                    }
                }
            }

            // The entry's own headword is a direct <w> child (with pos=...);
            // nested <w src="..."> reference tags inside <source> are never
            // direct children of <entry> itself, so this can't pick those up.
            Element headwordEl = firstChildElement(entryEl, "w");
            String hebrewPointed = headwordEl != null ? headwordEl.getTextContent() : null;

            entries.put(id, new StrongsLexiconEntry(id, primitiveRoot, refs, hebrewPointed));
        }
        return entries;
    }

    private String stripHPrefix(String raw) {
        return raw.startsWith("H") ? raw.substring(1) : raw;
    }

    private Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && tagName.equals(((Element) n).getTagName())) {
                return (Element) n;
            }
        }
        return null;
    }

    private Document parseDocument(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (var is = new ByteArrayInputStream(xmlBytes)) {
            return builder.parse(is);
        }
    }
}
