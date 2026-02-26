package com.eh.digitalpathology.dicomreceiver.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DicomDirDocumentTest {

    @Test
    @DisplayName("equals: should be reflexive, symmetric, and transitive for identical content")
    void testEqualsBasicContracts() {
        byte[] bytes = new byte[]{1, 2, 3};
        DicomDirDocument a = new DicomDirDocument("study-1", "series-1", 5, bytes);
        DicomDirDocument b = new DicomDirDocument("study-1", "series-1", 5, new byte[]{1, 2, 3});
        DicomDirDocument c = new DicomDirDocument("study-1", "series-1", 5, new byte[]{1, 2, 3});

        // Reflexive
        assertEquals(a, a);

        // Symmetric
        assertEquals(a, b);
        assertEquals(b, a);

        // Transitive
        assertEquals(a, b);
        assertEquals(b, c);
        assertEquals(a, c);

        // Consistent
        assertEquals(a, b);
        assertEquals(a, b);
    }

    @Test
    @DisplayName("equals: should return false for null and different class")
    void testEqualsAgainstNullAndDifferentType() {
        DicomDirDocument doc = new DicomDirDocument("s", "x", 1, new byte[]{1});

        assertNotEquals(doc, null);
        assertNotEquals(doc, "some string");
    }

    @Test
    @DisplayName("equals: should consider byte[] by content, not reference")
    void testEqualsArrayByContent() {
        byte[] arr1 = new byte[]{10, 20, 30};
        byte[] arr2 = new byte[]{10, 20, 30}; // different instance, same content

        DicomDirDocument d1 = new DicomDirDocument("S1", "R1", 3, arr1);
        DicomDirDocument d2 = new DicomDirDocument("S1", "R1", 3, arr2);

        assertNotSame(arr1, arr2);
        assertEquals(d1, d2, "Records should be equal if arrays have same content");
    }

    @Test
    @DisplayName("equals: should return false when any component differs")
    void testEqualsNegativeCases() {
        DicomDirDocument base = new DicomDirDocument("S", "R", 2, new byte[]{1, 2});

        assertNotEquals(base, new DicomDirDocument("S2", "R", 2, new byte[]{1, 2}));
        assertNotEquals(base, new DicomDirDocument("S", "R2", 2, new byte[]{1, 2}));
        assertNotEquals(base, new DicomDirDocument("S", "R", 3, new byte[]{1, 2}));
        assertNotEquals(base, new DicomDirDocument("S", "R", 2, new byte[]{1, 2, 3}));
    }

    @Test
    @DisplayName("equals: should handle null studyId/seriesId/dicomDirFile gracefully")
    void testEqualsWithNullComponents() {
        DicomDirDocument a = new DicomDirDocument(null, null, 0, null);
        DicomDirDocument b = new DicomDirDocument(null, null, 0, null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        // Differ only in one null vs non-null component
        assertNotEquals(a, new DicomDirDocument("S", null, 0, null));
        assertNotEquals(a, new DicomDirDocument(null, "R", 0, null));
        assertNotEquals(a, new DicomDirDocument(null, null, 1, null));
        assertNotEquals(a, new DicomDirDocument(null, null, 0, new byte[]{}));
    }

    @Test
    @DisplayName("hashCode: equal objects must have equal hash codes")
    void testHashCodeContract() {
        DicomDirDocument d1 = new DicomDirDocument("S", "R", 7, new byte[]{9, 8, 7});
        DicomDirDocument d2 = new DicomDirDocument("S", "R", 7, new byte[]{9, 8, 7});

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    @DisplayName("hashCode: should change when components differ")
    void testHashCodeDifference() {
        DicomDirDocument base = new DicomDirDocument("S", "R", 1, new byte[]{1});

        assertNotEquals(base.hashCode(), new DicomDirDocument("S2", "R", 1, new byte[]{1}).hashCode());
        assertNotEquals(base.hashCode(), new DicomDirDocument("S", "R2", 1, new byte[]{1}).hashCode());
        assertNotEquals(base.hashCode(), new DicomDirDocument("S", "R", 2, new byte[]{1}).hashCode());
        assertNotEquals(base.hashCode(), new DicomDirDocument("S", "R", 1, new byte[]{1, 2}).hashCode());
    }

    @Test
    @DisplayName("toString: should include field names and array content using Arrays.toString")
    void testToStringFormat() {
        byte[] content = new byte[]{5, 6};
        DicomDirDocument doc = new DicomDirDocument("study-42", "series-99", 12, content);

        String ts = doc.toString();

        // Basic structure and field labels
        assertTrue(ts.startsWith("DicomDirDocument{"));
        assertTrue(ts.contains("studyId='study-42'"));
        assertTrue(ts.contains("seriesId='series-99'"));
        assertTrue(ts.contains("imageCount=12"));

        // Arrays.toString representation => "[5, 6]"
        assertTrue(ts.contains("dicomDirFile=" + Arrays.toString(content)));
        assertTrue(ts.endsWith("}"));
    }

    @Test
    @DisplayName("toString: should handle null array and show 'null'")
    void testToStringWithNullArray() {
        DicomDirDocument doc = new DicomDirDocument("S", "R", 0, null);
        String ts = doc.toString();
        assertTrue(ts.contains("dicomDirFile=null"));
    }
}