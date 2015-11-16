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

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.MetaDataStringField;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.JCasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Read a Reuters-21578 corpus in SGML format.
 * <p>
 * Set the directory that contains the SGML files with {@link #PARAM_SOURCE_LOCATION}.
 */
public class Reuters21578SgmlReader
        extends JCasCollectionReader_ImplBase
{
    /**
     * The directory that contains the Reuters-21578 SGML files.
     */
    public static final String PARAM_SOURCE_LOCATION = ComponentParameters.PARAM_SOURCE_LOCATION;
    private static final String LANGUAGE = "en";
    @ConfigurationParameter(name = PARAM_SOURCE_LOCATION, mandatory = true)
    private File sourceLocation;
    private Iterator<Map<String, Object>> docIter;

    @Override
    public void initialize(UimaContext context)
            throws ResourceInitializationException
    {
        //        super.initialize();
        try {
            getLogger().info("Extracting Reuters-21578 documents from " + sourceLocation);
            List<Map<String, Object>> docs = ExtractReuters.extract(sourceLocation.toPath());
            getLogger().info(docs.size() + " documents read.");
            docIter = docs.iterator();
        }
        catch (IOException e) {
            throw new ResourceInitializationException(e);
        }
    }

    @Override public void getNext(JCas jCas)
            throws IOException, CollectionException
    {
        try {
            Map<String, Object> doc = docIter.next();
            initCas(jCas.getCas(), doc);
            MetaDataStringField date = new MetaDataStringField(jCas);
            date.setKey("DATE");
            date.setValue((String) doc.get("DATE"));
            date.addToIndexes();
        }
        catch (CASException e) {
            throw new CollectionException(e);
        }
    }

    @Override public boolean hasNext()
            throws IOException, CollectionException
    {
        return docIter.hasNext();
    }

    @Override public Progress[] getProgress()
    {
        return new Progress[0];
    }

    private void initCas(CAS aCas, Map<String, Object> doc)
            throws IOException, CASException
    {
        DocumentMetaData docMetaData = DocumentMetaData.create(aCas);
        docMetaData.setDocumentTitle((String) doc.get("TITLE"));
        docMetaData.setDocumentUri((String) doc.get("URI"));
        docMetaData.setDocumentId((String) doc.get("ID"));   // TODO: extract ID in ExtractReuters
        docMetaData.setDocumentBaseUri(sourceLocation.toURI().toString());
        docMetaData.setCollectionId(sourceLocation.getPath());

        aCas.setDocumentLanguage(LANGUAGE);
        aCas.setDocumentText((String) doc.get("BODY"));
    }
}
