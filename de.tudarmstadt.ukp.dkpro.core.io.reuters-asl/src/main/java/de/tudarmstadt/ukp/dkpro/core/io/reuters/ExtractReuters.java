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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static int N_TAGS = 3;
    private static Pattern EXTRACTION_PATTERN = Pattern
            .compile("<(TITLE)>(.*?)</TITLE>|<(DATE)>(.*?)</DATE>|<(BODY)>(.*?)</BODY>");

    private static String[] META_CHARS = { "&", "<", ">", "\"", "'" };
    private static String[] META_CHARS_SERIALIZATIONS = { "&amp;", "&lt;",
            "&gt;", "&quot;", "&apos;" };

    /**
     * Reag all the SGML file in the given directory.
     *
     * @param reutersDir the directory that contains the Reuters SGML files.
     * @return a list of maps where each map represents the contents of one document.
     * @throws IOException if any of the files cannot be read.
     */
    public static List<Map<String, String>> extract(Path reutersDir)
            throws IOException
    {
        List<Map<String, String>> docs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(reutersDir, "*.sgm")) {
            for (Path sgmFile : stream) {
                docs.addAll(extractFile(sgmFile));
            }
        }
        if (docs.isEmpty()) {
            System.err.println("No .sgm files in " + reutersDir);
        }
        return docs;
    }

    /**
     * Read the documents out of a single file. Each file contains approx. 1000 documents.
     *
     * @param sgmFile a Reuters SGML file.
     * @return a list of maps where each map represents the contents of one document.
     */
    private static List<Map<String, String>> extractFile(Path sgmFile)
            throws IOException
    {
        BufferedReader reader = Files.newBufferedReader(sgmFile, StandardCharsets.ISO_8859_1);

        List<Map<String, String>> entries = new ArrayList<>();
        Map<String, String> doc = new HashMap<>();
        StringBuilder buffer = new StringBuilder(1024);

        String line;
        while ((line = reader.readLine()) != null) {
            // when we see a closing reuters tag, flush the file

            if (!line.contains("</REUTERS")) {
                // Replace the SGM escape sequences

                buffer.append(line).append(' ');// accumulate the strings for now,
                // then apply regular expression to
                // get the pieces,
            }
            else {
                // Extract the relevant pieces and write to a map representing the document
                Matcher matcher = EXTRACTION_PATTERN.matcher(buffer);
                while (matcher.find()) {
                    assert (matcher.groupCount() == N_TAGS * 2);
                    for (int i = 1; i <= matcher.groupCount(); i += 2) {
                        if (matcher.group(i) != null) {
                            String tag = matcher.group(i);
                            String content = matcher.group(i + 1);
                            for (int j = 0; j < META_CHARS_SERIALIZATIONS.length; j++) {
                                content = content.replaceAll(META_CHARS_SERIALIZATIONS[j],
                                        META_CHARS[j]);
                            }
                            doc.put(tag, content);
                        }
                    }
                }
                doc.put("PATH", sgmFile.toString());
                doc.put("URI", sgmFile.toUri().toString());
                doc.put("ID", sgmFile.getFileName().toString());
                entries.add(doc);
                doc = new HashMap<>();
            }
        }
        return entries;
    }
}
