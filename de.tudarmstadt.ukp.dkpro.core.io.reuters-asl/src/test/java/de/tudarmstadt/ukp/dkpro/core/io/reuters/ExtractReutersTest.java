/*******************************************************************************
 * Copyright 2015
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.core.io.reuters;

import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExtractReutersTest
{
    private static final String REUTERS_DIR = "src/test/resources/reuters-sgml";

    @Test
    public void testExtract()
            throws Exception
    {
        int expectedDocs = 1000;
        String expectedTitleFirst = "BAHIA COCOA REVIEW";
        String expectedDateFirst = "26-FEB-1987 15:01:01.79";
        String expectedBodyFirst = "Showers";

        String expectedTitleLast = "NATIONAL AMUSEMENTS AGAIN UPS VIACOM <VIA> BID";
        String expectedDateLast = " 3-MAR-1987 09:17:32.30";
        String expectedBodyLast = "Viacom International Inc said ";

        List<Map<String, String>> docs = ExtractReuters.extract(new File(REUTERS_DIR).toPath());
        assertEquals(expectedDocs, docs.size());

        /* assert first doc */
        assertEquals(expectedTitleFirst, docs.get(0).get("TITLE"));
        assertEquals(expectedDateFirst, docs.get(0).get("DATE"));
        assertTrue(docs.get(0).get("BODY").startsWith(expectedBodyFirst));

        /* assert last doc */
        assertEquals(expectedTitleLast, docs.get(999).get("TITLE"));
        assertEquals(expectedDateLast, docs.get(999).get("DATE"));
        assertTrue(docs.get(999).get("BODY").startsWith(expectedBodyLast));
    }
}