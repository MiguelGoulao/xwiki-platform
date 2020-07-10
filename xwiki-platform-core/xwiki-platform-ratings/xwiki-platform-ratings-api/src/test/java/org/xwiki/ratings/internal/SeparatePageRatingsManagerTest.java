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
package org.xwiki.ratings.internal;

import java.util.Arrays;
import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.ratings.Rating;
import org.xwiki.ratings.RatingsConfiguration;
import org.xwiki.ratings.RatingsManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.store.XWikiStoreInterface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.xwiki.ratings.RatingsManager.RATING_CLASS_FIELDNAME_PARENT;

/**
 * Tests for {@link SeparatePageRatingsManager}.
 *
 * @version $Id$
 * @since 12.6RC1
 */
@ComponentTest
public class SeparatePageRatingsManagerTest
{
    @InjectMockComponents
    private SeparatePageRatingsManager ratingsManager;

    @MockComponent
    @Named("compactwiki")
    protected EntityReferenceSerializer<String> entityReferenceSerializer;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private XWikiStoreInterface xWikiStoreInterface;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private RatingsConfiguration ratingsConfiguration;

    @Mock
    private XWikiContext context;

    @Mock
    private XWiki xWiki;

    @BeforeEach
    void setup()
    {
        when(this.xcontextProvider.get()).thenReturn(this.context);
        when(this.context.getWiki()).thenReturn(this.xWiki);
        when(this.xWiki.getStore()).thenReturn(this.xWikiStoreInterface);
    }

    @Test
    void getRatings() throws XWikiException
    {
        String sqlRequest = ", BaseObject as obj, StringProperty as parentprop where doc.fullName=obj.name"
            + " and obj.className=?1 and obj.id=parentprop.id.id and parentprop.id.name=?2 and parentprop.value=?3"
            + " and obj.name not in ("
            + "select obj2.name from BaseObject as obj2, StringProperty as statusprop where obj2.className=?4"
            + " and obj2.id=statusprop.id.id and statusprop.id.name=?5 and "
            + "(statusprop.value=?6 or statusprop.value= ?7) and obj.id=obj2.id"
            + ") order by doc.date asc";

        DocumentReference documentReference = new DocumentReference("mywiki", "Foo", "Bar");
        when(this.entityReferenceSerializer.serialize(documentReference)).thenReturn("mywiki:Foo.Bar");

        List<String> arguments = Arrays.asList(
            "XWiki.RatingsClass",
            RATING_CLASS_FIELDNAME_PARENT,
            "mywiki:Foo.Bar",
            "XWiki.RatingsClass",
            "status",
            "moderated",
            "refused"
        );

        DocumentReference result1 = new DocumentReference("mywiki", "Foo", "Rating1");
        DocumentReference result2 = new DocumentReference("mywiki", "Foo", "Rating2");
        DocumentReference result3 = new DocumentReference("mywiki", "Foo", "Rating3");

        XWikiDocument result1Doc = mock(XWikiDocument.class);
        when(this.xWiki.getDocument(result1, this.context)).thenReturn(result1Doc);

        XWikiDocument result2Doc = mock(XWikiDocument.class);
        when(this.xWiki.getDocument(result2, this.context)).thenReturn(result2Doc);

        XWikiDocument result3Doc = mock(XWikiDocument.class);
        when(this.xWiki.getDocument(result3, this.context)).thenReturn(result3Doc);

        when(this.xWikiStoreInterface.searchDocumentReferences(sqlRequest, 12, 2, arguments, this.context))
            .thenReturn(Arrays.asList(result1, result2, result3));

        List<Rating> expectedRatings = Arrays.asList(
            new SeparatePageRating(documentReference, result1Doc, this.context, this.ratingsManager),
            new SeparatePageRating(documentReference, result2Doc, this.context, this.ratingsManager),
            new SeparatePageRating(documentReference, result3Doc, this.context, this.ratingsManager)
        );

        assertEquals(expectedRatings, this.ratingsManager.getRatings(documentReference, 2, 12, true));
    }

    @Test
    void hasRatingsSpaceForeachSpace()
    {
        DocumentReference documentReference = new DocumentReference("xwiki", "Foo", "Bar");

        when(this.xWiki.Param("xwiki.ratings.separatepagemanager.ratingsspaceforeachspace", "0"))
            .thenReturn("customVal");
        when(this.xWiki.getXWikiPreference("ratings_separatepagemanager_ratingsspaceforeachspace", "customVal",
            this.context)).thenReturn("otherValue");
        when(ratingsConfiguration.getConfigurationParameter(documentReference,
            RatingsManager.RATINGS_CONFIG_CLASS_FIELDNAME_STORAGE_SEPARATE_SPACES, "otherValue")).thenReturn("0");
        assertFalse(this.ratingsManager.hasRatingsSpaceForeachSpace(documentReference));
        verify(this.xWiki).Param("xwiki.ratings.separatepagemanager.ratingsspaceforeachspace", "0");
        verify(this.xWiki).getXWikiPreference("ratings_separatepagemanager_ratingsspaceforeachspace", "customVal",
            this.context);

        when(ratingsConfiguration.getConfigurationParameter(documentReference,
            RatingsManager.RATINGS_CONFIG_CLASS_FIELDNAME_STORAGE_SEPARATE_SPACES, "otherValue")).thenReturn("1");
        assertTrue(this.ratingsManager.hasRatingsSpaceForeachSpace(documentReference));
        verify(this.xWiki, times(2)).Param("xwiki.ratings.separatepagemanager.ratingsspaceforeachspace", "0");
        verify(this.xWiki, times(2)).getXWikiPreference("ratings_separatepagemanager_ratingsspaceforeachspace",
            "customVal", this.context);
    }

    @Test
    void getRatingsSpaceName()
    {
        DocumentReference documentReference = new DocumentReference("xwiki", "Foo", "Bar");

        when(this.xWiki.Param("xwiki.ratings.separatepagemanager.spacename", ""))
            .thenReturn("customVal");
        when(this.xWiki.getXWikiPreference("ratings_separatepagemanager_spacename", "customVal",
            this.context)).thenReturn("otherValue");
        when(ratingsConfiguration.getConfigurationParameter(documentReference,
            RatingsManager.RATINGS_CONFIG_CLASS_FIELDNAME_STORAGE_SPACE, "otherValue")).thenReturn("Ratings");
        assertEquals("Ratings", this.ratingsManager.getRatingsSpaceName(documentReference));
        verify(this.xWiki).Param("xwiki.ratings.separatepagemanager.spacename", "");
        verify(this.xWiki).getXWikiPreference("ratings_separatepagemanager_spacename", "customVal", this.context);
    }

    @Test
    void getRatingDocumentReference() throws XWikiException
    {
        when(context.getWikiReference()).thenReturn(new WikiReference("xwiki"));
        when(context.getWikiId()).thenReturn("xwiki");
        DocumentReference ratedDocumentReference = new DocumentReference("xwiki",
            Arrays.asList("ParentSpace", "MySpace"),
            "WebHome");

        when(this.xWiki.clearName(any(), eq(this.context))).then(invocation -> invocation.getArgument(0));
        when(ratingsConfiguration.getConfigurationParameter(any(),
            eq(RatingsManager.RATINGS_CONFIG_CLASS_FIELDNAME_STORAGE_SPACE), any())).thenReturn("RatingSpace");

        // Test with separate space = false
        when(ratingsConfiguration.getConfigurationParameter(ratedDocumentReference,
            RatingsManager.RATINGS_CONFIG_CLASS_FIELDNAME_STORAGE_SEPARATE_SPACES, null)).thenReturn("0");

        DocumentReference expectedReference = new DocumentReference("xwiki", "RatingSpace", "MySpace_WebHome");
        when(this.xWiki.exists(expectedReference, this.context)).thenReturn(false);
        DocumentReference obtainedReference = this.ratingsManager.getRatingDocumentReference(ratedDocumentReference);
        assertEquals(expectedReference, obtainedReference);

        // Test with separate space = false and already existing ratings
        expectedReference = new DocumentReference("xwiki", "RatingSpace", "MySpace_WebHomeR2");
        when(this.xWiki.exists(expectedReference, this.context)).thenReturn(false);

        when(this.xWiki.exists(new DocumentReference("xwiki", "RatingSpace", "MySpace_WebHome"), this.context))
            .thenReturn(true);
        when(this.xWiki.exists(new DocumentReference("xwiki", "RatingSpace", "MySpace_WebHomeR1"), this.context))
            .thenReturn(true);
        obtainedReference = this.ratingsManager.getRatingDocumentReference(ratedDocumentReference);
        assertEquals(expectedReference, obtainedReference);

        // Test with separate space = true
        when(ratingsConfiguration.getConfigurationParameter(ratedDocumentReference,
            RatingsManager.RATINGS_CONFIG_CLASS_FIELDNAME_STORAGE_SEPARATE_SPACES, null)).thenReturn("1");

        expectedReference = new DocumentReference("xwiki",
            Arrays.asList("ParentSpace", "MySpaceRatingSpace"), "WebHome");
        when(this.xWiki.exists(expectedReference, this.context)).thenReturn(false);
        obtainedReference = this.ratingsManager.getRatingDocumentReference(ratedDocumentReference);
        assertEquals(expectedReference, obtainedReference);

        // Test with separate space = true and already existing ratings
        when(ratingsConfiguration.getConfigurationParameter(ratedDocumentReference,
            RatingsManager.RATINGS_CONFIG_CLASS_FIELDNAME_STORAGE_SEPARATE_SPACES, null)).thenReturn("1");

        expectedReference = new DocumentReference("xwiki",
            Arrays.asList("ParentSpace", "MySpaceRatingSpace"), "WebHomeR2");
        when(this.xWiki.exists(new DocumentReference("xwiki",
            Arrays.asList("ParentSpace", "MySpaceRatingSpace"), "WebHome"), this.context))
            .thenReturn(true);
        when(this.xWiki.exists(new DocumentReference("xwiki",
            Arrays.asList("ParentSpace", "MySpaceRatingSpace"), "WebHomeR1"), this.context))
            .thenReturn(true);
        obtainedReference = this.ratingsManager.getRatingDocumentReference(ratedDocumentReference);
        assertEquals(expectedReference, obtainedReference);

        // Tests when rating space is null
        when(ratingsConfiguration.getConfigurationParameter(any(),
            eq(RatingsManager.RATINGS_CONFIG_CLASS_FIELDNAME_STORAGE_SPACE), any())).thenReturn(null);

        // Test with separate space = false and already existing ratings
        when(ratingsConfiguration.getConfigurationParameter(ratedDocumentReference,
            RatingsManager.RATINGS_CONFIG_CLASS_FIELDNAME_STORAGE_SEPARATE_SPACES, null)).thenReturn("0");
        expectedReference = new DocumentReference("xwiki",
            Arrays.asList("ParentSpace", "MySpace"), "WebHomeR2");
        when(this.xWiki.exists(expectedReference, this.context)).thenReturn(false);

        when(this.xWiki.exists(new DocumentReference("xwiki",
            Arrays.asList("ParentSpace", "MySpace"), "WebHomeR"), this.context))
            .thenReturn(true);
        when(this.xWiki.exists(new DocumentReference("xwiki",
            Arrays.asList("ParentSpace", "MySpace"), "WebHomeR1"), this.context))
            .thenReturn(true);
        obtainedReference = this.ratingsManager.getRatingDocumentReference(ratedDocumentReference);
        assertEquals(expectedReference, obtainedReference);

        // Test with separate space = true and already existing ratings
        when(ratingsConfiguration.getConfigurationParameter(ratedDocumentReference,
            RatingsManager.RATINGS_CONFIG_CLASS_FIELDNAME_STORAGE_SEPARATE_SPACES, null)).thenReturn("1");

        expectedReference = new DocumentReference("xwiki",
            Arrays.asList("ParentSpace", "MySpacenull"), "WebHomeR2");
        when(this.xWiki.exists(new DocumentReference("xwiki",
            Arrays.asList("ParentSpace", "MySpacenull"), "WebHome"), this.context))
            .thenReturn(true);
        when(this.xWiki.exists(new DocumentReference("xwiki",
            Arrays.asList("ParentSpace", "MySpacenull"), "WebHomeR1"), this.context))
            .thenReturn(true);
        obtainedReference = this.ratingsManager.getRatingDocumentReference(ratedDocumentReference);
        assertEquals(expectedReference, obtainedReference);
    }
}
