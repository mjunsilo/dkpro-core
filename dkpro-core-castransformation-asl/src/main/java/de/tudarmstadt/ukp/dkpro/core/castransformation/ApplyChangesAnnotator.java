/*
 * Copyright 2010
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.dkpro.core.castransformation;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultimap;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.transform.alignment.AlignedString;
import de.tudarmstadt.ukp.dkpro.core.api.transform.type.SofaChangeAnnotation;
import de.tudarmstadt.ukp.dkpro.core.castransformation.internal.AlignmentStorage;

/**
 * Applies changes annotated using a {@link SofaChangeAnnotation}.
 *
 * @since 1.1.0
 * @see Backmapper
 */
@TypeCapability(
        inputs={
            "de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData",
            "de.tudarmstadt.ukp.dkpro.core.api.transform.type.SofaChangeAnnotation"},
        outputs={
            "de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData",
            "de.tudarmstadt.ukp.dkpro.core.api.transform.type.SofaChangeAnnotation"})

public class ApplyChangesAnnotator
	extends JCasAnnotator_ImplBase
{
    public static final String VIEW_SOURCE = "source";
    public static final String VIEW_TARGET = "target";
    
	public static final String OP_INSERT = "insert";
	public static final String OP_REPLACE = "replace";
	public static final String OP_DELETE = "delete";
	public static final String OP_CUT = "cut";

	@Override
	public void process(JCas aJCas)
		throws AnalysisEngineProcessException
	{
		try {
			JCas sourceView = aJCas.getView(VIEW_SOURCE);
			JCas targetView = aJCas.createView(VIEW_TARGET);
            new Cut(sourceView).process();
			DocumentMetaData.copy(sourceView, targetView);
			applyChanges(sourceView, targetView);
		}
		catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	protected void applyChanges(JCas aSourceView, JCas aTargetView)
	{
		FSIndex<Annotation> idx = aSourceView.getAnnotationIndex(SofaChangeAnnotation.type);

		getLogger().info("Found " + idx.size() + " changes");

		// Apply all the changes
		AlignedString as = new AlignedString(aSourceView.getDocumentText());

		// Collect all those edits that are going to be executed.
		//
		// | A | C1 C2 R
		// BBBBBB + - -
		// BBBBBBBBBB + + +
		// BBBBBBBBBBBBBBBBB + + +
		// BBBBBBB - + -
		// BBBBBBBBBBBBB - + -
		// BBBBBBBB - + -
		//
		if (idx.size() > 0) {
			List<SofaChangeAnnotation> edits = new ArrayList<SofaChangeAnnotation>();
			{
				// Get an iterator over all the change annotations. Per UIMA default
				// this iterator is sorted first by begin and then by end offsets.
				// We will make use of this fact here to skip change annotations that
				// are covered by others. The earliest longest change wins - this means
				// the one with the smallest begin offset and the largest end offset.
				FSIterator<Annotation> it = idx.iterator();

				SofaChangeAnnotation top = (SofaChangeAnnotation) it.get();
				edits.add(top);
				it.moveToNext();
				while (it.isValid()) {
					SofaChangeAnnotation b = (SofaChangeAnnotation) it.get();
					if (((top.getBegin() <= b.getBegin()) && // C1
							(top.getEnd() > b.getBegin()) // C2
							)
							|| ((top.getBegin() == b.getBegin()) && (top.getEnd() == b.getEnd()))) {
						// Found annotation covering current annotation. Skipping
						// current annotation.
					}
					else {
						top = b;
						edits.add(top);
					}
					it.moveToNext();
				}
			}

            // If we remove or add stuff all offsets right of the change location
            // will change and thus the offsets in the change annotation are no
            // longer valid. If we move from right to left it works better because
            // the left offsets remain stable.
			Collections.reverse(edits);
			for (SofaChangeAnnotation a : edits) {
				if (OP_INSERT.equals(a.getOperation())) {
//                    getLogger().debug("Performing insert[" + a.getBegin() + "-" + a.getEnd() + "]: ["
//                                    + a.getCoveredText() + "]");
					as.insert(a.getBegin(), a.getValue());
				}
				if (OP_DELETE.equals(a.getOperation())) {
//                    getLogger().debug("Performing delete[" + a.getBegin() + "-" + a.getEnd() + "]: ["
//                            + a.getCoveredText() + "]");
					as.delete(a.getBegin(), a.getEnd());
				}
				if (OP_REPLACE.equals(a.getOperation())) {
//                    getLogger().debug("Performing replace[" + a.getBegin() + "-" + a.getEnd() + "]: ["
//                            + a.getCoveredText() + "]");
					as.replace(a.getBegin(), a.getEnd(), a.getValue());
				}
			}
		}

		// Set the text of the new Sofa
		aTargetView.setDocumentText(as.get());

		// Set document language
		aTargetView.setDocumentLanguage(aSourceView.getDocumentLanguage());

		// Optionally we may want to remember the AlignedString for the backmapper.
		AlignmentStorage.getInstance().put(aSourceView.getCasImpl().getBaseCAS(),
				aSourceView.getViewName(), aTargetView.getViewName(), as);
	}

    /**
     * Pre-processing annotator that converts {@link SofaChangeAnnotation sofa change annotations} with cut operation to
     * delete sofa change annotations. Note that insert and replace operations, which are not completely inside the cut
     * area, will be removed because the behaviour is unpredictable when backmapping after holes have been punched in the
     * insertion or replacement strings. Instead any masking of such operations needs to be added explicitly using
     * additional cut annotations.
     *
     * TODO: Add unit tests
     */
    protected class Cut {

        private final JCas sourceView;

        private final TreeMultimap<Integer, SofaChangeAnnotation> endings = TreeMultimap.create(
                Integer::compare, Comparator.comparing(SofaChangeAnnotation::getEnd)
        );

        private Set<SofaChangeAnnotation> cuts = new HashSet<>();

        int cutFrom = 0;

        public Cut(JCas sourceView) {
            this.sourceView = sourceView;
        }

        public void process() {

            JCasUtil.select(sourceView, SofaChangeAnnotation.class)
                    .stream()
                    .collect(java.util.stream.Collectors.toList()).forEach(change -> {
                if (isCut(change)) {
                    if (change.getBegin() <= cutFrom) {
                        // Extend the current cut out area if the overlapping cut ends after the previous
                        cutFrom = change.getEnd() > cutFrom ? change.getEnd() : cutFrom;
                    } else if (change.getBegin() > cutFrom) {
                        // A new cut has been identified. Complete the previous cut now.
                        cut(cutFrom, change.getBegin());
                        cutFrom = change.getEnd();
                    }
                    cuts.add(change);
                } else {
                    endings.put(change.getEnd(), change);
                }
            });

            if (cutFrom > 0) {
                // Completing any final cuts
                int end = sourceView.getDocumentText().length();
                if (end > cutFrom) cut(cutFrom, end);
            }

            // Remove all cuts when done
            cuts.forEach(c -> {
                c.removeFromIndexes();
            });

            // Cleaning up and optimizing. Removing operations inside deletes.
            // All potential deletes must be collected first to avoid concurrent modification exceptions from CAS.
            // It is important that only one delete is removed when there are multiple exactly overlapping deletes.
            Set<SofaChangeAnnotation> keep = new HashSet<>();
            Set<SofaChangeAnnotation> remove = new HashSet<>();
            JCasUtil.select(sourceView, SofaChangeAnnotation.class).stream()
                    .filter(c -> "delete".equals(c.getOperation()))
                    .forEach(d -> {
                        if (!remove.contains(d)) {
                            keep.add(d);
                            JCasUtil.selectCovered(SofaChangeAnnotation.class, d).stream()
                                    .forEach(c -> remove.add(c));
                        }
                    });
            remove.forEach(Annotation::removeFromIndexes);
        }

        public boolean isCut(SofaChangeAnnotation change) {
            return OP_CUT.equals(change.getOperation());
        }

        public void cut(int begin, int end) {

            if (end > begin) {
                SofaChangeAnnotation delete = new SofaChangeAnnotation(sourceView);
                delete.setOperation(ApplyChangesAnnotator.OP_DELETE);
                delete.setBegin(begin);
                delete.setEnd(end);
                delete.addToIndexes();
            }

            // Exclude first those changes that are outside the delete area
            endings.asMap().headMap(begin).values().stream()
                    .flatMap(c -> c.stream())
                    .collect(toMultiset())
                    .forEach(c -> {
                        endings.remove(c.getEnd(), c);
                    });
            // For the remaining truncate any overlapping delete changes and remove the rest
            endings.asMap().values().stream()
                    .flatMap(c -> c.stream())
                    .collect(toMultiset())
                    .forEach(c -> {
                        if (ApplyChangesAnnotator.OP_DELETE.equals(c.getOperation())) {
                            c.removeFromIndexes();
                            if (c.getEnd() > end) {
                                c.setBegin(end);
                                c.addToIndexes();
                            }
                        } else {
                            endings.remove(c.getEnd(), c);
                            c.removeFromIndexes();
                        }
                    });

        }

    }

    public static <T> Collector<T, Multiset<T>, Multiset<T>> toMultiset() {
        return Collector.of(() -> HashMultiset.create(), Multiset::add, (left, right) -> {
            left.addAll(right);
            return left;
        });
    }

}
