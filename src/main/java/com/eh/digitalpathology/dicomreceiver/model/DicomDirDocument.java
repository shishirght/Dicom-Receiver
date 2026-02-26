package com.eh.digitalpathology.dicomreceiver.model;

import java.util.Arrays;
import java.util.Objects;

public record DicomDirDocument(String studyId, String seriesId, int imageCount, byte[] dicomDirFile) {
    @Override
    public boolean equals ( Object o ) {
        if ( o == null || getClass( ) != o.getClass( ) ) return false;
        DicomDirDocument that = (DicomDirDocument) o;
        return imageCount == that.imageCount && Objects.equals( studyId, that.studyId ) && Objects.equals( seriesId, that.seriesId ) && Objects.deepEquals( dicomDirFile, that.dicomDirFile );
    }

    @Override
    public int hashCode ( ) {
        return Objects.hash( studyId, seriesId, imageCount, Arrays.hashCode( dicomDirFile ) );
    }

    @Override
    public String toString ( ) {
        return "DicomDirDocument{" +
                "studyId='" + studyId + '\'' +
                ", seriesId='" + seriesId + '\'' +
                ", imageCount=" + imageCount +
                ", dicomDirFile=" + Arrays.toString( dicomDirFile ) +
                '}';
    }
}
