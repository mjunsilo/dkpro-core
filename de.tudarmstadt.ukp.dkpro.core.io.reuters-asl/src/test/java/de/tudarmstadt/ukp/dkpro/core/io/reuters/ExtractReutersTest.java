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
import java.util.*;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExtractReutersTest
{
    private static final String REUTERS_DIR = "src/test/resources/reuters-sgml";

    @Test
    @SuppressWarnings("unchecked")
    public void testExtract()
            throws Exception
    {
        int expectedDocs = 1000;
        String expectedTitleFirst = "BAHIA COCOA REVIEW";
        String expectedDateFirst = "26-FEB-1987 15:01:01.79";
        String expectedBodyFirst = "Showers";
        String expectedTopicFirst = "cocoa";

        String expectedTitle4 = "NATIONAL AVERAGE PRICES FOR FARMER-OWNED RESERVE";
        String expectedDate4 = "26-FEB-1987 15:10:44.60";
        String expectedBody4 = "The U.S. Agriculture Department";
        Set<String> expectedTopic4 = new HashSet(
                Arrays.asList(
                        new String[] { "grain", "wheat", "corn", "barley", "oat", "sorghum" }));

        String expectedTitleLast = "NATIONAL AMUSEMENTS AGAIN UPS VIACOM <VIA> BID";
        String expectedDateLast = " 3-MAR-1987 09:17:32.30";
        String expectedBodyLast = "Viacom International Inc said ";
        String expectedTopicLast = "acq";

        List<Map<String, Object>> docs = ExtractReuters.extract(new File(REUTERS_DIR).toPath());
        assertEquals(expectedDocs, docs.size());

        /* assert first doc */
        Map<String, Object> doc0 = docs.get(0);
        assertEquals(expectedTitleFirst, doc0.get("TITLE"));
        assertEquals(expectedDateFirst, doc0.get("DATE"));
        assertTrue(((Set<String>) doc0.get("TOPICS")).contains(expectedTopicFirst));
        assertTrue(doc0.get("BODY").toString().startsWith(expectedBodyFirst));

        Map<String, Object> doc4 = docs.get(4);
        assertEquals(expectedTitle4, doc4.get("TITLE"));
        assertEquals(expectedDate4, doc4.get("DATE"));
        assertEquals(expectedTopic4, doc4.get("TOPICS"));
        assertTrue(doc0.get("BODY").toString().startsWith(expectedBodyFirst));

        /* assert last doc */
        Map<String, Object> doc999 = docs.get(999);
        assertEquals(expectedTitleLast, doc999.get("TITLE"));
        assertEquals(expectedDateLast, doc999.get("DATE"));
        assertTrue(((Set<String>) doc999.get("TOPICS")).contains(expectedTopicLast));
        assertTrue(doc999.get("BODY").toString().startsWith(expectedBodyLast));
    }
}