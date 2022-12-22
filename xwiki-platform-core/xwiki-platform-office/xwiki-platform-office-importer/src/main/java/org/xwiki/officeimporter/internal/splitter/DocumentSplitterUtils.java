/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.officeimporter.internal.splitter;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.officeimporter.OfficeImporterException;
import org.xwiki.officeimporter.document.XDOMOfficeDocument;
import org.xwiki.officeimporter.splitter.OfficeDocumentSplitterParameters;
import org.xwiki.refactoring.WikiDocument;
import org.xwiki.refactoring.splitter.criterion.naming.HeadingNameNamingCriterion;
import org.xwiki.refactoring.splitter.criterion.naming.NamingCriterion;
import org.xwiki.refactoring.splitter.criterion.naming.PageIndexNamingCriterion;
import org.xwiki.rendering.block.Block.Axes;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.renderer.BlockRenderer;

/**
 * Utility class for the {@link org.xwiki.refactoring.splitter.DocumentSplitter} implementations.
 * 
 * @version $Id$
 * @since 4.1M1
 */
public final class DocumentSplitterUtils
{
    /** Private constructor preventing the instantiation of this utility class. */
    private DocumentSplitterUtils()
    {
        // Utility class, should not be instantiated
    }

    /**
     * Utility method for building a {@link NamingCriterion} based on the parameters provided.
     * 
     * @param parameters the split parameters
     * @param docBridge bridge needed by the actual {@link NamingCriterion} implementations
     * @param plainTextRenderer renderer needed by the actual {@link NamingCriterion} implementations
     * @return a {@link NamingCriterion} based on the parameters provided.
     * @throws OfficeImporterException if there is no naming criterion matching the given naming criterion id.
     */
    public static NamingCriterion getNamingCriterion(OfficeDocumentSplitterParameters parameters,
        DocumentAccessBridge docBridge, BlockRenderer plainTextRenderer) throws OfficeImporterException
    {
        // TODO: This code needs to be refactored along with the xwiki-refactoring module code.
        if (parameters.getNamingCriterionHint().equals("headingNames")) {
            return new HeadingNameNamingCriterion(parameters.getBaseDocumentReference().toString(), docBridge,
                plainTextRenderer, false);
        } else if (parameters.getNamingCriterionHint().equals("mainPageNameAndHeading")) {
            return new HeadingNameNamingCriterion(parameters.getBaseDocumentReference().toString(), docBridge,
                plainTextRenderer, true);
        } else if (parameters.getNamingCriterionHint().equals("mainPageNameAndNumbering")) {
            return new PageIndexNamingCriterion(parameters.getBaseDocumentReference().toString(), docBridge);
        } else {
            throw new OfficeImporterException("The specified naming criterion is not implemented yet.");
        }
    }

    /**
     * Move artifacts (i.e. embedded images) from the original office document to a specific wiki document corresponding
     * to a section. Only the artifacts from that section are moved.
     * 
     * @param sectionDoc the newly created wiki document corresponding to a section of the original office document
     * @param officeDocument the office document being splitted into wiki documents
     * @return the relocated artifacts
     */
    public static Set<File> relocateArtifacts(WikiDocument sectionDoc, XDOMOfficeDocument officeDocument)
    {
        Set<File> artifacts = officeDocument.getArtifactsFiles();
        Set<File> result = new HashSet<>();
        List<ImageBlock> imageBlocks =
            sectionDoc.getXdom().getBlocks(new ClassBlockMatcher(ImageBlock.class), Axes.DESCENDANT);
        if (!imageBlocks.isEmpty()) {
            Map<String, File> fileMap = new HashMap<>();
            artifacts.forEach(item -> fileMap.put(item.getName(), item));
            for (ImageBlock imageBlock : imageBlocks) {
                String imageReference = imageBlock.getReference().getReference();
                File file = fileMap.get(imageReference);
                result.add(file);
                artifacts.remove(file);
            }
        }
        return result;
    }
}
