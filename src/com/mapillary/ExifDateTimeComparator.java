/*
 * Copyright 2016 Mapillary AB, Sweden
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mapillary;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;

/**
 * A JPEG file {@link Comparator} for comparing GPS date time stamps. This
 * class is useful for sorting JPEG photo files by GPS date time stamp.
 *
 * @author <a href="mailto:Jacob%20Wisor%20&lt;GITNE@noreply.users.github.com&gt;?subject=[com.mapillary.GPSDateTimeComparator]%20mkseq">Jacob Wisor</a>
 */
public final class ExifDateTimeComparator implements Comparator<File> {
    
    private final boolean fallback;

    /**
     * Constructs a default {@link Comparator} for comparing <b>GPS date time
     * stamps</b>. If a GPS date time stamp is not available this object does
     * not fallback to a file's last modification time stamp for comparison.
     * Calling this constructor is equivalent to calling
     * {@link #ExifDateTimeComparator(boolean) ExifDateTimeComparator(false)}.
     *
     * @see #ExifDateTimeComparator(boolean)
     */
    public ExifDateTimeComparator() {
        this(false);
    }

    /**
     * Constructs a {@link Comparator} for comparing <b>GPS date time
     * stamps</b>. Optionally, this comparator can fall back to comparing
     * against the {@code DateTimeOriginal}, {@code DateTimeDigitized},
     * {@code DateTime} EXIF fields, and finally the file's last modification
     * time stamp if a GPS date time stamp is not available.
     *
     * @param fallback if {@code true}, fall back to comparing against
     * {@code DateTimeOriginal}, {@code DateTimeDigitized}, {@code DateTime},
     * and finally a file's last modification time stamp. If {@code false}, do
     * not fall back.
     *
     * @see #ExifDateTimeComparator()
     */
    public ExifDateTimeComparator(final boolean fallback) {
        this.fallback = fallback;
    }

    /**
     * Compares the GPS date time stamp of file {@code a} to file {@code b}.
     *
     * @param a the file compared
     * @param b the comparand file
     *
     * @return {@code < 0}, if the GPS date time stamp of file {@code a} is
     * before that of file {@code b},<br>
     * {@code 0}, if the GPS date time stamp of file a is equal to that of file
     * b,<br>
     * {@code > 0} if the GPS date time stamp of file {@code a} is after that
     * of file {@code b}
     *
     * @see Date#compareTo(Date)
     */
    @Override
    public final int compare(final File a, final File b) {
            return ExifDateTimeComparator.getImageFileDate(
                a,
                this.fallback
            ).compareTo(
                ExifDateTimeComparator.getImageFileDate(
                    b,
                    this.fallback
                )
            );
    }

    public static final Date getImageFileDate(
        final File f,
        final boolean fallback) {
        try {
            final TiffImageMetadata tiffImageMetadata;
            return (tiffImageMetadata = ((JpegImageMetadata)Imaging.getMetadata(f)).getExif()).getFieldValue(
                GpsTagConstants.GPS_TAG_GPS_DATE_STAMP) == null &&
                fallback ?
                    tiffImageMetadata.getFieldValue(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL) == null &&
                    fallback ?
                        tiffImageMetadata.getFieldValue(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED) == null &&
                        fallback ?
                            tiffImageMetadata.getFieldValue(TiffTagConstants.TIFF_TAG_DATE_TIME) == null &&
                            fallback ?
                                new Date(f.lastModified()) :
                                Sequencer.exifDateTimeToDate(
                                    tiffImageMetadata.getFieldValue(
                                        TiffTagConstants.TIFF_TAG_DATE_TIME
                                    )[0]
                                ) :
                            Sequencer.exifDateTimeToDate(
                                tiffImageMetadata.getFieldValue(
                                    ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED
                                )[0]
                            ) :
                        Sequencer.exifDateTimeToDate(
                            tiffImageMetadata.getFieldValue(
                                ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL
                            )[0]
                        ) :
                    Sequencer.gpsDateTimeToDate(
                        tiffImageMetadata.getFieldValue(
                            GpsTagConstants.GPS_TAG_GPS_DATE_STAMP
                        )[0],
                        tiffImageMetadata.getFieldValue(
                            GpsTagConstants.GPS_TAG_GPS_TIME_STAMP
                        ));
        } catch (IOException | ImageReadException e) {
            e.printStackTrace();
            return new Date(0L);
        }
    }
    /**
     * Gives a human readable textural represention of this comparator's state.
     *
     * @return a human readable string
     */
    public final String toString() {
        return super.getClass().getCanonicalName() +
            ".fallback: " +
            String.valueOf(this.fallback);
    }
}