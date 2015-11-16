/*
 * Copyright 2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.dkpro.core.io.reuters;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extract all the documents from a Reuters-21587 corpus in SGML format. The SGML files are expected
 * to reside in a single directory.
 * <p>
 * This is an adaption of the {@code ExtractReuters} class in the {@code lucene-benchmarks} package.
 *
 * @see <a href="http://lucene.apache.org/core/5_3_1/benchmark/org/apache/lucene/benchmark/utils/ExtractReuters.html">ExtractReuters</a>
 */
public class ExtractReuters
{
    private static Set<String> NESTED_TAGS = new HashSet<>(
            Arrays.asList(new String[] { "TOPICS" }));
    private static Pattern EXTRACTION_PATTERN = Pattern
            .compile(
                    "<(TITLE)>(.*?)</TITLE>|<(DATE)>(.*?)</DATE>|<(BODY)>(.*?)</BODY>|<(TOPICS)>(.*?)</TOPICS>");
    private static Pattern NESTED_EXTRACTION_PATTERN = Pattern.compile("<D>(.*?)</D>");

    private static String[] META_CHARS = { "&", "<", ">", "\"", "'" };
    private static String[] META_CHARS_SERIALIZATIONS = { "&amp;", "&lt;", "&gt;", "&quot;",
            "&apos;" };

    /**
     * Reag all the SGML file in the given directory.
     *
     * @param reutersDir the directory that contains the Reuters SGML files.
     * @return a list of maps where each map represents the contents of one document.
     * @throws IOException if any of the files cannot be read.
     */
    public static List<Map<String, Object>> extract(Path reutersDir)
            throws IOException
    {
        List<Map<String, Object>> docs = new ArrayList<>();
        DirectoryStream<Path> stream = Files.newDirectoryStream(reutersDir, "*.sgm");
        for (Path sgmFile : stream) {
            docs.addAll(extractFile(sgmFile));
        }
        return docs;
    }

    /**
     * Read the documents out of a single file. Each file contains approximately 1000 documents.
     *
     * @param sgmFile a Reuters SGML file.
     * @return a list of maps where each map represents the contents of one document.
     */
    private static List<Map<String, Object>> extractFile(Path sgmFile)
            throws IOException
    {
        BufferedReader reader = Files.newBufferedReader(sgmFile, StandardCharsets.ISO_8859_1);

        List<Map<String, Object>> entries = new ArrayList<>();  // collection of all documents in file
        StringBuilder docBuffer = new StringBuilder(1024);  // text of current document

        String line;
        while ((line = reader.readLine()) != null) {
            // when we see a closing reuters tag, flush the file

            if (!line.contains("</REUTERS")) {
                /* document continuing */

                docBuffer.append(line).append(' ');// accumulate the strings for now,
                // then apply regular expression to
                // get the pieces,
            }
            else {
                /* document end reached in input file, parse content */
                Map<String, Object> doc = new HashMap<>();

                // Extract the relevant pieces and write to a map representing the document
                Matcher matcher = EXTRACTION_PATTERN.matcher(docBuffer);
                while (matcher.find()) {
                    /* iterate over outer tags */
                    for (int i = 1; i <= matcher.groupCount(); i += 2) {
                        if (matcher.group(i) != null) {
                            String tag = matcher.group(i);
                            String value = matcher.group(i + 1);

                            /* replace SGML characters */
                            for (int j = 0; j < META_CHARS_SERIALIZATIONS.length; j++) {
                                value = value
                                        .replaceAll(META_CHARS_SERIALIZATIONS[j], META_CHARS[j]);
                            }

                            /* extract value(s) */
                            if (NESTED_TAGS.contains(tag)) {
                                Set<String> all_ds = extractNested(doc, tag, value);
                                doc.put(tag, all_ds);
                            }
                            else {
                                doc.put(tag, value);
                            }
                        }
                    }
                }
                /* add metadata information for current doc */
                doc.put("PATH", sgmFile.toString());
                doc.put("URI", sgmFile.toUri().toString());
                doc.put("ID", sgmFile.getFileName().toString());
                entries.add(doc);
                /* reset document buffer */
                docBuffer.setLength(0);
            }
        }
        return entries;
    }

    /**
     * Find the {@code <D>} tags that are nested within another tag.
     *
     * @param doc  the current document represented as a {@code Map<String, Object>}
     * @param tag  the outer tag, e.g. {@code <TOPICS>}
     * @param text the value of the outer tag from which nested tags are extracted
     * @return a set of all detected values of the nested {@code <D>} tags.
     */
    private static Set<String> extractNested(Map<String, Object> doc, String tag, String text)
    {
        Matcher nestedMatcher = NESTED_EXTRACTION_PATTERN.matcher(text);
        @SuppressWarnings("unchecked")
        Set<String> all_ds = (Set<String>) doc.getOrDefault(tag, new HashSet<String>());
        while (nestedMatcher.find()) {
            /* iterate over <D> tags */
            for (int j = 1; j <= nestedMatcher.groupCount(); j++) {
                String d = nestedMatcher.group(j);
                all_ds.add(d);
            }
        }
        return all_ds;
    }
}
