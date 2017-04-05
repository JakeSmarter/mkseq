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

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteOrder;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingConstants;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffDirectory;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryType;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoRationals;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

/**
 * Main class of the {@code mkseq} tool. The tool prepares photo sequences for
 * upload to Mapillary.
 *
 * @author <a href="mailto:Jacob%20Wisor%20&lt;GITNE@noreply.users.github.com&gt;?subject=[com.mapillary.Sequencer]%20mkseq">Jacob Wisor</a>
 *
 * @see com.mapillary
 */
public final class Sequencer {
    /**
     * @see #exifDateTimeToDate(String)
     */
    private static final Pattern GPS_DATETIME_PATTERN =
        Pattern.compile(
            "\\d+:(0?\\d|1[0-2]):([0-2]?\\d|3[0-1])\\s+(0?\\d|1\\d|2[0-3])(:[0-5]?\\d){2}",
            Pattern.CANON_EQ | Pattern.UNICODE_CHARACTER_CLASS
        ),
                                 GPS_DATETIME_SPLIT_PATTERN =
        Pattern.compile(
            ":|\\s+",
            Pattern.CANON_EQ | Pattern.UNICODE_CHARACTER_CLASS
        ),
                                 GPS_DATE_SPLIT_PATTERN =
        Pattern.compile(":", Pattern.CANON_EQ),
                                 OPTION_PATTERN =
        Pattern.compile(
            "^(-{1,2}|/)\\p{Alpha}+",
            Pattern.CANON_EQ | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
        );

            public static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");

    private static ResourceBundle bundle;

    private static ExifRewriter exifRewriter;

    /**
     * Holds parameters for reading and writing image files. Its basic purpose
     * is to disable {@linkplain ImagingConstants#PARAM_KEY_READ_THUMBNAILS
     * reading contained thumbnails}.
     *
     * @see Imaging#getBufferedImage(org.apache.commons.imaging.common.bytesource.ByteSource, java.util.Map)
     * @see Imaging#getBufferedImage(File, java.util.Map)
     * @see Imaging#getBufferedImage(java.io.InputStream, java.util.Map)
     * @see Imaging#getBufferedImage(byte[], java.util.Map)
     * @see Imaging#getICCProfile(org.apache.commons.imaging.common.bytesource.ByteSource, java.util.Map)
     * @see Imaging#getICCProfile(File, java.util.Map)
     * @see Imaging#getICCProfile(byte[], java.util.Map)
     * @see Imaging#getICCProfile(java.io.InputStream, String, java.util.Map)
     * @see Imaging#getICCProfileBytes(org.apache.commons.imaging.common.bytesource.ByteSource, java.util.Map) 
     * @see Imaging#getICCProfileBytes(File, java.util.Map)
     * @see Imaging#getICCProfileBytes(byte[], java.util.Map)
     * @see Imaging#getImageInfo(org.apache.commons.imaging.common.bytesource.ByteSource, java.util.Map)
     * @see Imaging#getImageInfo(File, java.util.Map)
     * @see Imaging#getImageInfo(byte[], java.util.Map)
     * @see Imaging#getImageInfo(java.io.InputStream, String, java.util.Map)
     * @see Imaging#getImageInfo(String, byte[], java.util.Map)
     * @see ImagingConstants#PARAM_KEY_COMPRESSION
     * @see ImagingConstants#PARAM_KEY_EXIF
     * @see ImagingConstants#PARAM_KEY_FILENAME
     * @see ImagingConstants#PARAM_KEY_FORMAT
     * @see ImagingConstants#PARAM_KEY_PIXEL_DENSITY
     * @see ImagingConstants#PARAM_KEY_READ_THUMBNAILS
     * @see ImagingConstants#PARAM_KEY_STRICT
     * @see ImagingConstants#PARAM_KEY_VERBOSE
     * @see ImagingConstants#PARAM_KEY_XMP_XML
     */
    private static final HashMap<String, Object> IMAGING_PARAMS = new HashMap<String, Object>(2);

    static final Sequencer.Options OPTIONS = new Sequencer.Options();

    static {
        Sequencer.IMAGING_PARAMS.put(
            ImagingConstants.PARAM_KEY_READ_THUMBNAILS,
            Boolean.FALSE
        );
    }

    /**
     * Computes the linear latitude increment between two GPS points for the
     * given {@linkplain Sequencer.Options#inputFiles count of input files}. The
     * computed increment <b>does not</b> account for the distance on the
     * surface.<br>
     * This method is specific to the {@code -l} {@linkplain
     * Sequencer.Options#LINEAR_INTERPOLATE_OPTION linear interpolation option}.
     * @param gpsInfoStart the point at which to start linear interpolation
     * @param gpsInfoEnd the point at which to end linear interpolation
     * @return the latitude increment (distance) between two points (nodes) in a
     * linear interpolation in WGS 84 geographic coordinates
     * @throws ImageReadException
     * @see Sequencer.Options#LINEAR_INTERPOLATE_OPTION
     * @see Sequencer.Options#inputFiles
     * @see Sequencer#processFiles(File[])
     * @see Sequencer#computeLongitudeInc(TiffImageMetadata.GPSInfo,TiffImageMetadata.GPSInfo)
     */
    private static final double computeLatitudeInc(
        final TiffImageMetadata.GPSInfo gpsInfoStart,
        final TiffImageMetadata.GPSInfo gpsInfoEnd) throws ImageReadException {
        return (gpsInfoEnd.getLatitudeAsDegreesNorth() -
            gpsInfoStart.getLatitudeAsDegreesNorth()) /
            (Sequencer.OPTIONS.inputFiles.length - 1);
    }

    /**
     * Computes the linear longitude increment between two GPS points for the
     * given {@linkplain Sequencer.Options#inputFiles count of input files}. The
     * computed increment <b>does not</b> account for the distance on the
     * surface.<br>
     * This method is specific to the {@code -l} {@linkplain
     * Sequencer.Options#LINEAR_INTERPOLATE_OPTION linear interpolation option}.
     * @param gpsInfoStart the point at which to start linear interpolation
     * @param gpsInfoEnd the point at which to end linear interpolation
     * @return the longitude increment (distance) between two points (nodes) in
     * a linear interpolation in WGS 84 geographic coordinates
     * @throws ImageReadException
     * @see Sequencer.Options#LINEAR_INTERPOLATE_OPTION
     * @see Sequencer.Options#inputFiles
     * @see Sequencer#processFiles(File[])
     * @see Sequencer#computeLatitudeInc(TiffImageMetadata.GPSInfo,TiffImageMetadata.GPSInfo)
     */
    private static final double computeLongitudeInc(
        final TiffImageMetadata.GPSInfo gpsInfoStart,
        final TiffImageMetadata.GPSInfo gpsInfoEnd) throws ImageReadException {
        return (gpsInfoEnd.getLongitudeAsDegreesEast() -
            gpsInfoStart.getLongitudeAsDegreesEast()) /
            (Sequencer.OPTIONS.inputFiles.length - 1);
    }

    private static final boolean isOption(final String arg) {
        return Sequencer.OPTION_PATTERN.matcher(arg.trim()).find() &&
            !new File(arg).exists();
    }

    private Sequencer() {
        throw new UnsupportedOperationException();
    }

    private static final double getAverageIncline(
        final TiffImageMetadata.GPSInfo[] points) {
        double incline = 0.0;
        try {
            for (int i = points.length - 1; i >= 0; i--)
                for (int j = points.length - 1; j >= 0; j--)
                    if (i != j)
                        incline += (points[i].getLongitudeAsDegreesEast() - points[j].getLongitudeAsDegreesEast()) /
                                   (points[i].getLatitudeAsDegreesNorth() - points[j].getLatitudeAsDegreesNorth());
        } catch (ImageReadException e) {
            e.printStackTrace();
            return 0.0;
        }
        return incline / points.length;
    }

    private static final TiffImageMetadata.GPSInfo getAveragePoint(
        final TiffImageMetadata.GPSInfo[] points) {
        if (points.length <= 1)
            return points[0];
        double latitude = 0.0, longitude = 0.0;
        try {
            // TODO: Add handling on anti-meridian
            for (int i = points.length - 1; i >= 0; i--) {
                latitude += points[i].getLatitudeAsDegreesNorth();
                longitude += points[i].getLongitudeAsDegreesEast();
            }
        } catch (ImageReadException e) {
            e.printStackTrace();
            return new TiffImageMetadata.GPSInfo(
                GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF_VALUE_NORTH,
                GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF_VALUE_EAST,
                RationalNumber.valueOf(0.0),
                RationalNumber.valueOf(0.0),
                RationalNumber.valueOf(0.0),
                RationalNumber.valueOf(0.0),
                RationalNumber.valueOf(0.0),
                RationalNumber.valueOf(0.0)
            );
        }
        return new TiffImageMetadata.GPSInfo(
            latitude >= 0.0 ?
                GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF_VALUE_NORTH :
                GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF_VALUE_SOUTH,
            longitude >= 0.0 ?
                GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF_VALUE_EAST :
                GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF_VALUE_WEST,
            RationalNumber.valueOf(Math.floor(latitude = Math.abs(latitude / points.length))),
            RationalNumber.valueOf(Math.floor(latitude = (latitude - Math.floor(latitude)) * 60)),
            RationalNumber.valueOf((latitude - Math.floor(latitude)) * 60),
            RationalNumber.valueOf(Math.floor(longitude = Math.abs(longitude / points.length))),
            RationalNumber.valueOf(Math.floor(longitude = (longitude - Math.floor(longitude)) * 60)),
            RationalNumber.valueOf((longitude - Math.floor(longitude)) * 60)
        );
    }

    /**
     * Gets the direction from {@code a} to {@code b} in radians.
     *
     * @param a the position looking from
     * @param b the position looking at
     * @return the direction from {@code a} to {@code b} in radians
     *
     * @see TiffImageMetadata.GPSInfo
     * @see <a href="http://www.movable-type.co.uk/scripts/latlong.html">
     * Calculate distance, bearing and more between Latitude/Longitude points
     * </a>
     */
    private static final double getDirection(
        final TiffImageMetadata.GPSInfo a,
        final TiffImageMetadata.GPSInfo b) {
        final double deltaLongitude, aLat, bLat, bCosLat;
        try {
            return (Math.atan2(
                Math.sin(
                        deltaLongitude = Math.toRadians(
                            b.getLongitudeAsDegreesEast()
                        ) - Math.toRadians(
                            a.getLongitudeAsDegreesEast()
                        )
                ) * (bCosLat = Math.cos(
                    bLat = Math.toRadians(
                        b.getLatitudeAsDegreesNorth()
                    )
                )),
                Math.cos(
                    aLat = Math.toRadians(
                        a.getLatitudeAsDegreesNorth())
                ) * Math.sin(bLat) -
                Math.sin(aLat) *
                bCosLat *
                Math.cos(
                        deltaLongitude
                )
            ) + 2 * Math.PI) % (2 * Math.PI);
        } catch (ImageReadException e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    /**
     * Gets the distance between two GPS points in coordinates.
     *
     * @return distance between point {@code a} and point {@code b} in coordinates
     */
    private static final double getDistance(
        final TiffImageMetadata.GPSInfo a,
        final TiffImageMetadata.GPSInfo b) {
        try {
            return Math.sqrt(
                        Math.pow(
                            Math.abs(
                                a.getLatitudeAsDegreesNorth() -
                                b.getLatitudeAsDegreesNorth()
                            ),
                            2.0
                        ) + Math.pow(
                            Math.abs(
                                a.getLongitudeAsDegreesEast() -
                                b.getLongitudeAsDegreesEast()
                            ),
                            2.0
                        ));
        } catch (ImageReadException e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    private static final double getGreatestDistance(
        final TiffImageMetadata.GPSInfo point,
        final TiffImageMetadata.GPSInfo[] points) {
        double greatestDistance = 0.0;
        for (int i = points.length - 1; i >= 0; i--) {
            final double distance;
            if ((distance = Sequencer.getDistance(
                point,
                points[i])) > greatestDistance)
                greatestDistance = distance;
        }
        return greatestDistance;
    }

    private static final File[] getInputFilesByDialog() {
        final FileDialog filedialog;
        (filedialog = new FileDialog(
            (Frame)null,
            Sequencer.m("Sequencer.open.dialog"),
            FileDialog.LOAD
        )).setFilenameFilter(new Sequencer.JPEGTIFFFilenameFilter());
        filedialog.setIconImage(Toolkit.getDefaultToolkit().getImage(
            "data/images/mapillary32.png"
        ));
        filedialog.setLocationByPlatform(true);
        filedialog.setDirectory(System.getProperty("user.dir"));
        filedialog.setMultipleMode(true);
        try {
            filedialog.setVisible(true);
            return filedialog.getFiles();
        } finally {
            filedialog.dispose();
        }
    }

    private static final File getOutputDirectoryByDialog() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException |
                 InstantiationException |
                 IllegalAccessException |
                 UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
        final JFileChooser fileChooser;
        (fileChooser = new JFileChooser(
            System.getProperty("user.dir"))).setAcceptAllFileFilterUsed(false);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setDialogTitle(Sequencer.m("Sequencer.save.dialog"));
        fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setVisible(true);
        return fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION ?
            fileChooser.getSelectedFile() : null;
    }

    private static final void center(
        final File[] files,
        final double degrees) throws IOException {
        // Load all GPS meta datas in sequence
        final JpegImageMetadata[] jpegImageMetadatas = new JpegImageMetadata[files.length];
        try {
            for (int i = files.length - 1; i >= 0; i--)
                jpegImageMetadatas[i] = (JpegImageMetadata)Imaging.getMetadata(files[i]);

            final TiffImageMetadata.GPSInfo gpsInfos[] = new TiffImageMetadata.GPSInfo[jpegImageMetadatas.length];
            for (int i = jpegImageMetadatas.length - 1; i >= 0; i--)
                gpsInfos[i] = jpegImageMetadatas[i].getExif().getGPS();
            final TiffImageMetadata.GPSInfo centerGPSInfo = Sequencer.getAveragePoint(gpsInfos);
            // Write center point and directions to files
            for (int i = jpegImageMetadatas.length - 1; i >= 0; i--) {
                final TiffOutputDirectory gpsDirectory;
                // Adjust latitude
                if ((gpsDirectory = jpegImageMetadatas[i].getExif().getOutputSet().getGPSDirectory()).findField(GpsTagConstants.GPS_TAG_GPS_LATITUDE) != null)
                    gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE);
                gpsDirectory.add(
                    GpsTagConstants.GPS_TAG_GPS_LATITUDE,
                    centerGPSInfo.latitudeDegrees,
                    centerGPSInfo.latitudeMinutes,
                    centerGPSInfo.latitudeSeconds
                );
                // Supply latitude reference
                if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF) != null)
                    gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF);
                gpsDirectory.add(
                    GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF,
                    centerGPSInfo.latitudeRef
                );
                // Adjust longitude
                if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE) != null)
                    gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE);
                gpsDirectory.add(
                    GpsTagConstants.GPS_TAG_GPS_LONGITUDE,
                    centerGPSInfo.longitudeDegrees,
                    centerGPSInfo.longitudeMinutes,
                    centerGPSInfo.longitudeSeconds
                );
                // Supply longitude reference
                if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF) != null)
                    gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF);
                gpsDirectory.add(
                    GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF,
                    centerGPSInfo.longitudeRef
                );
                // Set new outward direction
                if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION) != null)
                    gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION);
                {
                    final double direction;
                    gpsDirectory.add(
                        GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION,
                        RationalNumber.valueOf(
                            (direction = degrees + 360.0 / jpegImageMetadatas.length * i) > 360.0 ?
                                direction - 360.0 :
                                direction
                        )
                    );
                }
                if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF) != null)
                    gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF);
                gpsDirectory.add(
                    GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF,
                    Sequencer.OPTIONS.degreesRef
                );
                // Remove altitude field because it messes up Mapillary transitions
                gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_ALTITUDE);
                gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_ALTITUDE_REF);
                // Create new TiffOutputSet
                final TiffOutputSet tiffOutputSet;
                (tiffOutputSet = new TiffOutputSet()).addRootDirectory();
                tiffOutputSet.addDirectory(gpsDirectory);
                if (Sequencer.exifRewriter == null)
                    Sequencer.exifRewriter = new ExifRewriter();
                try (final FileOutputStream fis = new FileOutputStream(
                    new File(
                        Sequencer.OPTIONS.outputDir, files[i].getName()
                    ))) {
                    Sequencer.exifRewriter.updateExifMetadataLossy(
                        files[i],
                        fis,
                        tiffOutputSet
                    );
                }
            }
        } catch (ImageReadException | ImageWriteException e) {
            throw new IOException(e);
        }
    }

    private static final void processFiles(
        final File[] files) throws IOException {
        try {
            // Load all GPS meta datas in sequence
            final JpegImageMetadata[] jpegImageMetadatas = new JpegImageMetadata[files.length];
            for (int i = jpegImageMetadatas.length - 1; i >= 0; i--)
                jpegImageMetadatas[i] = (JpegImageMetadata)Imaging.getMetadata(
                    files[i],
                    IMAGING_PARAMS
                );
            // Add GPS directory and location if missing in file
            final TiffImageMetadata.GPSInfo[] gpsInfos = new TiffImageMetadata.GPSInfo[jpegImageMetadatas.length];
            for (int i = jpegImageMetadatas.length - 1; i >= 0; i--)
                if ((gpsInfos[i] = jpegImageMetadatas[i].getExif().getGPS()) == null) {
                    gpsInfos[i] = new TiffImageMetadata.GPSInfo(
                        GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF_VALUE_NORTH,
                        GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF_VALUE_EAST,
                        RationalNumber.valueOf(0.0),
                        RationalNumber.valueOf(0.0),
                        RationalNumber.valueOf(0.0),
                        RationalNumber.valueOf(0.0),
                        RationalNumber.valueOf(0.0),
                        RationalNumber.valueOf(0.0)
                    );
                }
            final double latitudeInc, longitudeInc;
            // If linear interpolation (-l) option is set, compute latitude and longitude increment
            if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.LINEAR_INTERPOLATE_OPTION)) {
                latitudeInc = Sequencer.computeLatitudeInc(
                    gpsInfos[0],
                    gpsInfos[gpsInfos.length - 1]
                );
                longitudeInc = Sequencer.computeLongitudeInc(
                    gpsInfos[0],
                    gpsInfos[gpsInfos.length - 1]
                );
            } else latitudeInc = longitudeInc = 0.0;
            // Go through every photo and process it
            TiffImageMetadata.GPSInfo previousGPSInfo = null;
            TiffOutputSet previousOutputSet = null;
            for (int i = 0; i < gpsInfos.length; i++) {
                // Print verbose
                if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.VERBOSE_OPTION)) {
                    Sequencer.IMAGING_PARAMS.put(
                        ImagingConstants.PARAM_KEY_VERBOSE,
                        Boolean.TRUE
                    );
                    Sequencer.printMetadata(files[i], jpegImageMetadatas[i]);
                }
                TiffImageMetadata.GPSInfo currentGPSInfo = gpsInfos[i];
                final TiffOutputDirectory gpsDirectory =
                    jpegImageMetadatas[i].getExif().getOutputSet().getGPSDirectory() != null ?
                        jpegImageMetadatas[i].getExif().getOutputSet().getGPSDirectory() :
                        new TiffOutputDirectory(
                            TiffDirectoryConstants.DIRECTORY_TYPE_GPS,
                            ByteOrder.nativeOrder()
                        );
                // Smooth sequence
                if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.SMOOTH_OPTION)) {
                    final int k, l;
                    currentGPSInfo =
                        Sequencer.getAveragePoint(
                            Arrays.copyOfRange(
                                gpsInfos,
                                (k = i - (Sequencer.OPTIONS.nodes >> 1)) < 0 ?
                                    0 :
                                    i + (Sequencer.OPTIONS.nodes >> 1) >= gpsInfos.length ?
                                        i - (gpsInfos.length - i) + 1 :
                                        k,
                                k < 0 ?
                                    2 * i + 1 :
                                    (l = k + Sequencer.OPTIONS.nodes) > gpsInfos.length ?
                                        gpsInfos.length :
                                        l
                            )
                        );
                    // TODO: Compute harmonic average
                    // Set new latitude location
                    if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_LATITUDE) != null)
                        gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE);
                    gpsDirectory.add(
                        GpsTagConstants.GPS_TAG_GPS_LATITUDE,
                        currentGPSInfo.latitudeDegrees,
                        currentGPSInfo.latitudeMinutes,
                        currentGPSInfo.latitudeSeconds
                    );
                    if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF) != null)
                        gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF);
                    gpsDirectory.add(
                        GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF,
                        currentGPSInfo.latitudeRef
                    );
                    // Set new longitude location
                    if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE) != null)
                        gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE);
                    gpsDirectory.add(
                        GpsTagConstants.GPS_TAG_GPS_LONGITUDE,
                        currentGPSInfo.longitudeDegrees,
                        currentGPSInfo.longitudeMinutes,
                        currentGPSInfo.longitudeSeconds
                    );
                    if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF) != null)
                        gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF);
                    gpsDirectory.add(
                        GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF,
                        currentGPSInfo.longitudeRef
                    );
                }
                // Linear interpolation
                if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.LINEAR_INTERPOLATE_OPTION)) {
                    double latitude, longitude;
                    currentGPSInfo = new TiffImageMetadata.GPSInfo(
                        GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF_VALUE_NORTH,
                        GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF_VALUE_EAST,
                        RationalNumber.valueOf(Math.floor(latitude = gpsInfos[0].getLatitudeAsDegreesNorth() + latitudeInc * i)),
                        RationalNumber.valueOf(Math.floor(latitude = (latitude - Math.floor(latitude)) * 60)),
                        RationalNumber.valueOf((latitude - Math.floor(latitude)) * 60),
                        RationalNumber.valueOf(Math.floor(longitude = gpsInfos[0].getLongitudeAsDegreesEast() + longitudeInc * i)),
                        RationalNumber.valueOf(Math.floor(longitude = (longitude - Math.floor(longitude)) * 60)),
                        RationalNumber.valueOf((longitude - Math.floor(longitude)) * 60)
                    );
                    gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE);
                    gpsDirectory.add(
                        GpsTagConstants.GPS_TAG_GPS_LATITUDE,
                        currentGPSInfo.latitudeDegrees,
                        currentGPSInfo.latitudeMinutes,
                        currentGPSInfo.latitudeSeconds
                    );
                    gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF);
                    gpsDirectory.add(
                        GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF,
                        GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF_VALUE_NORTH
                    );
                    gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE);
                    gpsDirectory.add(
                        GpsTagConstants.GPS_TAG_GPS_LONGITUDE,
                        currentGPSInfo.longitudeDegrees,
                        currentGPSInfo.longitudeMinutes,
                        currentGPSInfo.longitudeSeconds
                    );
                    gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF);
                    gpsDirectory.add(
                        GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF,
                        GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF_VALUE_EAST
                    );
                }
                // Compute normalized direction
                if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.NORMALIZE_OPTION)) {
                    // Remove any previous direction
                    gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION);
                    if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF) != null)
                        gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF);
                    // Set direction in previous photo
                    if (i > 0) {
                        final RationalNumber lastDirection;
                        previousOutputSet.getGPSDirectory().add(
                            GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION,
                            lastDirection = RationalNumber.valueOf(
                                Math.toDegrees(Sequencer.getDirection(
                                    previousGPSInfo, // Previous average GPS location
                                    currentGPSInfo
                                ))
                            )
                        );
                        // Set previous direction on last photo
                        if (i >= gpsInfos.length - 1)
                            gpsDirectory.add(
                                GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION,
                                lastDirection
                            );
                    }
                    gpsDirectory.add(
                        GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF,
                        GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF_VALUE_TRUE_NORTH
                    );
                }
                // Keep or add altitude
                if ((Sequencer.OPTIONS.options & Sequencer.Options.ALTITUDE_OPTION) != 0) {
                    if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_ALTITUDE) == null) {
                        gpsDirectory.add(
                            GpsTagConstants.GPS_TAG_GPS_ALTITUDE,
                            RationalNumber.valueOf(Math.abs(Sequencer.OPTIONS.altitude))
                        );
                        if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_ALTITUDE_REF) != null)
                            gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_ALTITUDE_REF);
                        gpsDirectory.add(
                            GpsTagConstants.GPS_TAG_GPS_ALTITUDE_REF,
                            (byte)(Sequencer.OPTIONS.altitude < 0.0 ?
                                GpsTagConstants.GPS_TAG_GPS_ALTITUDE_REF_VALUE_BELOW_SEA_LEVEL :
                                GpsTagConstants.GPS_TAG_GPS_ALTITUDE_REF_VALUE_ABOVE_SEA_LEVEL)
                        );
                    }
                } else { // Remove altitude data
                    if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_ALTITUDE) != null)
                        gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_ALTITUDE);
                    if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_ALTITUDE_REF) != null)
                        gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_ALTITUDE_REF);
                }

                // Add or overwrite GPS date and time stamps from file modification time stamp
                if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.TIME_STAMP_OPTION)) {
                    // If missing, add GPS date time stamp from EXIF time stamps
                    // or finally from file's last modification time stamp
                    if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_DATE_STAMP) == null)
                        gpsDirectory.add(
                            GpsTagConstants.GPS_TAG_GPS_DATE_STAMP,
                            Sequencer.millisToGPSDate(
                                ExifDateTimeComparator.getImageFileDate(
                                    files[i],
                                    true
                                ).getTime()
                            )
                        );
                    if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_TIME_STAMP) == null)
                        gpsDirectory.add(
                            GpsTagConstants.GPS_TAG_GPS_TIME_STAMP,
                            Sequencer.millisToGPSTime(
                                ExifDateTimeComparator.getImageFileDate(
                                    files[i],
                                    true
                                ).getTime()
                            )
                        );
                    if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.TIME_STAMP_OVERWRITE_OPTION)) {
                        final long lastModified;
                        if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_DATE_STAMP) != null)
                            gpsDirectory.removeField(
                                GpsTagConstants.GPS_TAG_GPS_DATE_STAMP
                            );
                        gpsDirectory.add(
                            GpsTagConstants.GPS_TAG_GPS_DATE_STAMP,
                            Sequencer.millisToGPSDate(
                                lastModified = files[i].lastModified()
                            )
                        );
                        if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_TIME_STAMP) != null)
                            gpsDirectory.removeField(
                                GpsTagConstants.GPS_TAG_GPS_TIME_STAMP
                            );
                        gpsDirectory.add(GpsTagConstants.GPS_TAG_GPS_TIME_STAMP,
                            Sequencer.millisToGPSTime(lastModified)
                        );
                    }
                }

                // Resize to Mapillary's thumb-2048 size while preserving the aspect ratio
                if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.RESIZE_OPTION)) {
                    final BufferedImage inputBufferedImage, outputBufferedImage;
                    inputBufferedImage = Imaging.getBufferedImage(
                        files[i],
                        Sequencer.IMAGING_PARAMS
                    );
                    final Graphics2D graphics2d;
                    (graphics2d = (outputBufferedImage =
                        new BufferedImage(
                            inputBufferedImage.getColorModel(),
                            inputBufferedImage.getColorModel().createCompatibleWritableRaster(
                                2048,
                                1536
                            ),
                            inputBufferedImage.isAlphaPremultiplied(),
                            null
                        )).createGraphics()
                    ).setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC
                    );
                    graphics2d.setRenderingHint(
                        RenderingHints.KEY_DITHERING,
                        RenderingHints.VALUE_DITHER_DISABLE
                    );
                    graphics2d.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_OFF
                    );
                    graphics2d.drawImage(
                        inputBufferedImage,
                        0,
                        0,
                        inputBufferedImage.getWidth() > 2048 ? 2048 : inputBufferedImage.getWidth(),
                        inputBufferedImage.getHeight() > 1536 ? 1536 : inputBufferedImage.getHeight(),
                        null
                    );
                    graphics2d.dispose();
                    try (final FileImageOutputStream fimos =
                        new FileImageOutputStream(
                            File.createTempFile("mkseq-", ".jpg")
                        )
                    ) {
                        final ImageWriter iw;
                        (iw = ImageIO.getImageWritersByFormatName("JPEG").next()).setOutput(fimos);
                        final ImageWriteParam imageWriteParam;
                        (imageWriteParam = iw.getDefaultWriteParam()).setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        imageWriteParam.setCompressionQuality(Sequencer.OPTIONS.quality);
                        imageWriteParam.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
                        iw.write(
                            null,
                            new IIOImage(outputBufferedImage, null, null),
                            imageWriteParam
                        );
                    }
                }

                // Add or overwrite GPS area information
                if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.GPS_AREA_INFO_OPTION)) {
                    if (gpsDirectory.findField(GpsTagConstants.GPS_TAG_GPS_AREA_INFORMATION) != null)
                        gpsDirectory.removeField(GpsTagConstants.GPS_TAG_GPS_AREA_INFORMATION);
                    gpsDirectory.add(
                        GpsTagConstants.GPS_TAG_GPS_AREA_INFORMATION,
                        Sequencer.OPTIONS.gpsAreaInformation
                    );
                }
                // Write modified GPS data to file
                if (Sequencer.exifRewriter == null)
                    Sequencer.exifRewriter = new ExifRewriter();
                File outputFile = null;
                if (i > 0) {// As long as not last photo write the previous file
                    previousOutputSet.getGPSDirectory().sortFields();
                    try (final FileOutputStream fis = new FileOutputStream(
                        outputFile = new File(
                            Sequencer.OPTIONS.outputDir,
                            files[i - 1].getName()
                        ))) {
                        Sequencer.exifRewriter.updateExifMetadataLossy(
                            files[i - 1],
                            fis,
                            previousOutputSet
                        );
                    }
                }
                if (i >= gpsInfos.length - 1) {// Write last photo
                    TiffOutputSet lastOutputSet;
                    (lastOutputSet = new TiffOutputSet()).addRootDirectory();
                    lastOutputSet.addDirectory(
                        gpsDirectory // Add GPS directory of current output set
                    );
                    lastOutputSet.getGPSDirectory().sortFields();
                    try (final FileOutputStream fis = new FileOutputStream(
                        outputFile = new File(
                            Sequencer.OPTIONS.outputDir,
                            files[i].getName())
                        )) {
                        Sequencer.exifRewriter.updateExifMetadataLossy(
                            files[i],
                            fis,
                            lastOutputSet
                        );
                    }
                } else {
                    // Save current GPS location to compute direction on next loop
                    previousGPSInfo = currentGPSInfo;
                    // Create new TiffOutputSet for next photo
                    (previousOutputSet = new TiffOutputSet()).addRootDirectory();
                    previousOutputSet.addDirectory(
                        gpsDirectory // Add GPS directory of current outputset
                    );
                }

                // If -k option is set adjust new output file's modification time stamp
                if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.PRESERVE_TIME_STAMP_OPTION) &&
                    outputFile != null)
                    outputFile.setLastModified(files[i].lastModified());
            }
        } catch (ImageReadException | ImageWriteException e) {
            throw new IOException(e);
        }
    }

    private static final void printMetadata(
        final File file,
        final JpegImageMetadata jpegImageMetadata) {
        try {
            final TiffImageMetadata tiffImageMetadata;
            final TiffImageMetadata.GPSInfo gpsInfo;
            final TiffDirectory gpsDirectory;
            RationalNumber[] rationalNumbers;
            final double meters, feet;
            System.out.println(
                new StringBuffer(2048).append(
                    Sequencer.m(
                        "Sequencer.verbose.file",
                        file.getName()
                    )
                ).append(
                    (gpsInfo = (tiffImageMetadata = jpegImageMetadata.getExif()).getGPS()) != null ?
                        "\n\t" +
                        Sequencer.m(
                            "Sequencer.verbose.latitude",
                            gpsInfo.getLatitudeAsDegreesNorth()
                        ) :
                        ""
                ).append(
                    gpsInfo != null ?
                        "\n\t" +
                        Sequencer.m(
                            "Sequencer.verbose.longitude",
                            gpsInfo.getLongitudeAsDegreesEast()
                        ) :
                        ""
                ).append(
                    (gpsDirectory = tiffImageMetadata.findDirectory(
                        TiffDirectoryConstants.DIRECTORY_TYPE_GPS
                    )) != null ?
                        (rationalNumbers = gpsDirectory.getFieldValue(
                            new TagInfoRationals(
                                GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION.name,
                                GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION.tag,
                                GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION.length,
                                GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION.directoryType
                            ),
                            false
                        )) != null ?
                            "\n\t" +
                            Sequencer.m(
                                "Sequencer.verbose.direction",
                                rationalNumbers[0].doubleValue()
                            ) :
                            "" :
                        ""
                ).append(
                    gpsDirectory != null && (rationalNumbers = gpsDirectory.getFieldValue(
                        new TagInfoRationals(
                            GpsTagConstants.GPS_TAG_GPS_ALTITUDE.name,
                            GpsTagConstants.GPS_TAG_GPS_ALTITUDE.tag,
                            GpsTagConstants.GPS_TAG_GPS_ALTITUDE.length,
                            GpsTagConstants.GPS_TAG_GPS_ALTITUDE.directoryType
                        ),
                        false
                    )) != null ?
                        "\n\t" +
                        Sequencer.m(
                            "Sequencer.verbose.altitude",
                            meters = rationalNumbers[0].doubleValue(), // meters
                            (long)Math.floor(feet = meters * 3.280839895), // feet
                            (feet - Math.floor(feet)) * 12.0 // inch
                        ) :
                        ""
                ).append(
                    gpsDirectory != null && (rationalNumbers = gpsDirectory.getFieldValue(
                        new TagInfoRationals(
                            GpsTagConstants.GPS_TAG_GPS_SPEED.name,
                            GpsTagConstants.GPS_TAG_GPS_SPEED.tag,
                            GpsTagConstants.GPS_TAG_GPS_SPEED.length,
                            GpsTagConstants.GPS_TAG_GPS_SPEED.directoryType
                        ),
                        false
                    )) != null ?
                        "\n\t" +
                        Sequencer.m(
                            "Sequencer.verbose.speed",
                            gpsDirectory.getFieldValue(
                                GpsTagConstants.GPS_TAG_GPS_SPEED_REF,
                                false
                            ).equals("K") ?
                                rationalNumbers[0].doubleValue() :
                                gpsDirectory.getFieldValue(
                                    GpsTagConstants.GPS_TAG_GPS_SPEED_REF,
                                    false
                                ).equals("M") ?
                                    rationalNumbers[0].doubleValue() * 1.609334 :
                                    rationalNumbers[0].doubleValue() * 1.852,
                            gpsDirectory.getFieldValue(
                                GpsTagConstants.GPS_TAG_GPS_SPEED_REF,
                                false
                            ).equals("K") ?
                                rationalNumbers[0].doubleValue() / 1.609334 :
                                gpsDirectory.getFieldValue(
                                    GpsTagConstants.GPS_TAG_GPS_SPEED_REF,
                                    false
                                ).equals("M") ?
                                    rationalNumbers[0].doubleValue() :
                                    rationalNumbers[0].doubleValue() *
                                    (1.852 / 1.609334)
                        ) :
                        ""
                ).append(
                    gpsDirectory != null && gpsDirectory.findField(
                        GpsTagConstants.GPS_TAG_GPS_DATE_STAMP
                    ) != null ?
                        "\n\t" +
                        Sequencer.m(
                            "Sequencer.verbose.datetime",
                            Sequencer.OPTIONS.dateTimeFormatter.format(
                                Sequencer.gpsDateTimeToDate(
                                    gpsDirectory.getFieldValue(
                                        GpsTagConstants.GPS_TAG_GPS_DATE_STAMP,
                                        false
                                    )[0],
                                    gpsDirectory.getFieldValue(
                                        GpsTagConstants.GPS_TAG_GPS_TIME_STAMP,
                                        false
                                    )
                                ).toInstant()
                            )
                        ) :
                        ""
                ).toString()
            );
        } catch (ImageReadException e) {
            System.err.println(
                Sequencer.m(
                    "Sequencer.verbose.error",
                    Sequencer.Options.getCanonicalPath(file),
                    e.getMessage()
                )
            );
        }
    }

    /**
     * Converts an EXIF date time string of the {@linkplain Pattern regex} form
     * {@code "\\d+:(0?\\d|1[0-2]):([0-2]?\\d|3[0-1])\\s+(0?\\d|1\\d|2[0-3])(:[0-5]?\\d){2}"}
     * into a {@link Date} object.
     *
     * @param exifDateTime the EXIF date time string to convert
     * @return a {@link Date} object representing the EXIF date time string
     * passed in by {@code exifDateTime}
     * @throws IllegalArgumentException if {@code exifDateTime} is {@code null},
     * empty, or an improperly formatted EXIF date time string
     *
     * @see #GPS_DATETIME_PATTERN
     */
    public static final Date exifDateTimeToDate(final String exifDateTime) {
        if (exifDateTime == null ||
            exifDateTime.isEmpty() ||
            !Sequencer.GPS_DATETIME_PATTERN.matcher(exifDateTime).matches())
            throw new IllegalArgumentException(
                Sequencer.m(
                    "Sequencer.error.invalid.exif.datetime",
                    exifDateTime
                )
            );
        final Calendar calendar;
        (calendar = Calendar.getInstance()).clear();
        calendar.setTimeZone(TimeZone.getDefault());
        // Tokenize EXIF date time string into numbers
        final String[] exifDateTimeTokens = Sequencer.GPS_DATETIME_SPLIT_PATTERN.split(exifDateTime);
        calendar.set(
            Integer.parseInt(exifDateTimeTokens[0]), // year
            Integer.parseInt(exifDateTimeTokens[1]) - 1, // month of year
            Integer.parseInt(exifDateTimeTokens[2]), // day of month
            Integer.parseInt(exifDateTimeTokens[3]), // hour of day
            Integer.parseInt(exifDateTimeTokens[4]), // minute of hour
            Integer.parseInt(exifDateTimeTokens[5]) // seconds of minute
        );
        return calendar.getTime();
    }

    public static final Date gpsDateTimeToDate(
        final String gpsDate,
        final RationalNumber[] gpsTime) {
        final Calendar c;
        // GPS date time is always in UTC
        (c = Calendar.getInstance(Sequencer.UTC_TIME_ZONE)).clear();
        if (gpsDate != null && !gpsDate.isEmpty()) {
            final String[] gpsDateSplit;
            c.set(Integer.parseInt((gpsDateSplit = Sequencer.GPS_DATE_SPLIT_PATTERN.split(
                        gpsDate
                    ))[0]
                ),
                Integer.parseInt(gpsDateSplit[1]) - 1,
                Integer.parseInt(gpsDateSplit[2])
            );
        }
        if (gpsTime != null) {
            if (gpsTime.length > 0 && gpsTime[0] != null) {
                c.set(Calendar.HOUR_OF_DAY, gpsTime[0].intValue());
                if (gpsTime.length > 1 && gpsTime[1] != null) {
                    c.set(Calendar.MINUTE, gpsTime[1].intValue());
                    if (gpsTime.length > 2 && gpsTime[2] != null) {
                        c.set(
                            Calendar.MILLISECOND,
                            (int)Math.round(gpsTime[2].doubleValue() * 1000)
                        );
                    }
                }
            }
        }
        return c.getTime();
    }

    /**
     * Converts milliseconds since the Unix epoch (1970-01-01 00:00:00 UTC) into a
     * GPS date field string of the {@link Pattern regex} form
     * {@code "[0-2][0-4](:[0-5]\\d){2}"}.
     *
     * @param millis the time stamp in milliseconds to convert
     * @return the GPS date string
     *
     * @see #millisToGPSTime(long)
     * @see System#currentTimeMillis()
     */
    public static final String millisToGPSDate(final long millis) {
        final Calendar c;
        (c = Calendar.getInstance(Sequencer.UTC_TIME_ZONE)).setTimeInMillis(millis);
        return String.format(
            "%02d:%02d:%02d",
            c.get(Calendar.YEAR),
            (c.get(Calendar.MONTH) + 1),
            c.get(Calendar.DAY_OF_MONTH)
        );
    }

    /**
     * Converts milliseconds since the Unix epoch (1970-01-01 00:00:00 UTC) into
     * three rational numbers of a GPS time field.
     *
     * @param millis the time stamp in milliseconds to convert
     * @return an array of three ratonal numbers where the first element denotes
     * the hour of day, the second the minute of the hour, and the third element
     * the second (including any fraction of the second) of the minute
     *
     * @see #millisToGPSDate(long)
     * @see System#currentTimeMillis()
     */
    public static final RationalNumber[] millisToGPSTime(final long millis) {
        final Calendar c;
        (c = Calendar.getInstance(Sequencer.UTC_TIME_ZONE)).setTimeInMillis(millis);
        return new RationalNumber[] {
            RationalNumber.valueOf(c.get(Calendar.HOUR_OF_DAY)),
            RationalNumber.valueOf(c.get(Calendar.MINUTE)),
            RationalNumber.valueOf(c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND) / 1000.0)
        };
    }

    private static final void processCommandLineArguments(final String[] args) {
        int smoothOptionIndex = 0;
        try {
            final Pattern OptionPrefixPattern = Pattern.compile(
                "^(-{1,2}|/)",
                Pattern.CANON_EQ
            );
            int i;
            // Process arguments as options as long as they are not an existing file
            for (i = 0; i < args.length && Sequencer.isOption(args[i]); i++) {
                // If control flow steps into the loop then the first argument
                // is an option, not a file thus reset default options
                if (i <= 0) Sequencer.OPTIONS.options = 0;
                int subOptionIndex;
                final String option;
                switch ((option = OptionPrefixPattern.matcher(args[i].trim().toLowerCase()).replaceAll("")).charAt(subOptionIndex = 0)) {
                    // Test for -a option
                    case 'a':
                        // If the -a option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.ALTITUDE_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the altitude option
                        Sequencer.OPTIONS.options |= Sequencer.Options.ALTITUDE_OPTION;
                        // If the -a option has a height sub-argument then parse and set it
                        // That is, if the next argument is neither a file nor an option then
                        // -a has a sub-argument specified
                        if (i + 1 < args.length &&
                            !Sequencer.isOption(args[i + 1])) {
                            try {
                                Sequencer.OPTIONS.altitude = NumberFormat.getInstance().parse(
                                    args[i + 1].trim()
                                ).doubleValue();
                            } catch (ParseException e) {
                                throw new IllegalArgumentException(
                                    Sequencer.m(
                                        "Sequencer.cmdline.error.altitude",
                                        args[i + 1],
                                        args[i]
                                    ),
                                    e
                                );
                            }
                            i++;
                        }
                        break;
                        // Test for -c option
                    case 'c':
                        // If the -c option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.CENTER_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // If the -l or -s option has been specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(
                                Sequencer.Options.LINEAR_INTERPOLATE_OPTION |
                                Sequencer.Options.SMOOTH_OPTION
                            ))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.exlusive",
                                    args[i],
                                    Sequencer.OPTIONS.isOptionSet(
                                        Sequencer.Options.LINEAR_INTERPOLATE_OPTION
                                    ) ?
                                        "-l" :
                                        "-s"
                                )
                            );
                        // Set the center option
                        Sequencer.OPTIONS.options |= Sequencer.Options.CENTER_OPTION;
                        // If the -c option has a degrees sub-argument then parse and set it
                        // That is, if the next argument is neither a file nor an option then
                        // -c has a sub-argument specified
                        {
                            final String subArg;
                            if (i + 1 < args.length &&
                                !Sequencer.isOption(subArg = args[i + 1])) {
                                final String upperCaseSubArg;
                                // Test if sub-argument ends with a T
                                if ((upperCaseSubArg = subArg.toUpperCase()).endsWith(GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF_VALUE_TRUE_NORTH))
                                    Sequencer.OPTIONS.degreesRef = GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF_VALUE_TRUE_NORTH;
                                // Test if sub-argument ends with an M
                                else if (upperCaseSubArg.endsWith(GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF_VALUE_MAGNETIC_NORTH))
                                    Sequencer.OPTIONS.degreesRef = GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF_VALUE_MAGNETIC_NORTH;
                                // TODO: Throw exception if any other suffix than T or M to the number
                                try {
                                    // Parse degrees number without reference suffix
                                    Sequencer.OPTIONS.degrees = NumberFormat.getNumberInstance().parse(
                                        Pattern.compile(
                                            "[TM]$",
                                            Sequencer.Options.PATTERN_FLAGS
                                        ).split(subArg)[0]
                                    ).doubleValue();
                                } catch (ParseException e) {
                                    throw new IllegalArgumentException(
                                        Sequencer.m(
                                            "Sequencer.cmdline.error.direction",
                                            args[i + 1],
                                            args[i]
                                        ),
                                        e
                                    );
                                }
                                i++;
                            }
                        }
                        break;
                        // Test for -d option
                    case 'd':
                        // If the -d option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.DROP_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the drop option
                        Sequencer.OPTIONS.options |= Sequencer.Options.DROP_OPTION;
                        break;
                        // Test for -g option
                    case 'g':
                        // If the -g option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.GPX_FILE_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the GPX file option
                        Sequencer.OPTIONS.options |= Sequencer.Options.GPX_FILE_OPTION;
                        break;
                        // Test for -i option
                    case 'i':
                        // If the -i option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.ISO_TIME_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the iso time stamp option
                        Sequencer.OPTIONS.options |= Sequencer.Options.ISO_TIME_OPTION;
                        // Set the local time zone ISO DateTimeFormatter
                        Sequencer.OPTIONS.dateTimeFormatter =
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(
                                ZoneId.systemDefault()
                            );
                        break;
                        // Test for -k option
                    case 'k':
                        // If the -k option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.PRESERVE_TIME_STAMP_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the preserve file modification time stamp option
                        Sequencer.OPTIONS.options |= Sequencer.Options.PRESERVE_TIME_STAMP_OPTION;
                        break;
                        // Test for -l option
                    case 'l':
                        // If the -l option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.LINEAR_INTERPOLATE_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // If the -c or -s option has been specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(
                                Sequencer.Options.CENTER_OPTION |
                                Sequencer.Options.SMOOTH_OPTION
                            ))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.exlusive",
                                    args[i],
                                    Sequencer.OPTIONS.isOptionSet(
                                        Sequencer.Options.CENTER_OPTION
                                    ) ?
                                        "-c" :
                                        "-s"
                                )
                            );
                        // Set the extrapolate option
                        Sequencer.OPTIONS.options |= Sequencer.Options.LINEAR_INTERPOLATE_OPTION;
                        break;
                        // Test for -n option
                    case 'n':
                        // If the -n option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.NORMALIZE_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the normalize option
                        Sequencer.OPTIONS.options |= Sequencer.Options.NORMALIZE_OPTION;
                        break;
                        // Test for -p option
                    case 'p':
                        // If the -p option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.SPEED_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the speed option
                        Sequencer.OPTIONS.options |= Sequencer.Options.SPEED_OPTION;
                        // Parse the speed sub-argument
                        if (i + 1 >= args.length)
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        {
                        final String subArg, upperCaseSubArg;
                        // Test if sub-argument ends with a K
                        if ((upperCaseSubArg = (subArg = args[i + 1].trim()).toUpperCase()).endsWith(GpsTagConstants.GPS_TAG_GPS_SPEED_REF_VALUE_KMPH))
                            Sequencer.OPTIONS.speedRef = GpsTagConstants.GPS_TAG_GPS_SPEED_REF_VALUE_KMPH;
                        // Test if sub-argument ends with an M
                        else if (upperCaseSubArg.endsWith(GpsTagConstants.GPS_TAG_GPS_SPEED_REF_VALUE_MPH))
                            Sequencer.OPTIONS.speedRef = GpsTagConstants.GPS_TAG_GPS_SPEED_REF_VALUE_MPH;
                        // Test if sub-argument ends with an N
                        else if (upperCaseSubArg.endsWith(GpsTagConstants.GPS_TAG_GPS_SPEED_REF_VALUE_KNOTS))
                            Sequencer.OPTIONS.speedRef = GpsTagConstants.GPS_TAG_GPS_SPEED_REF_VALUE_KNOTS;
                        // TODO: Throw exception if number ends with any other suffix than K, M, or N
                        try {
                            // Parse speed number without reference (unit of speed) suffix
                            Sequencer.OPTIONS.speed = NumberFormat.getNumberInstance().parse(
                                Pattern.compile(
                                    "[KMN]$",
                                    Sequencer.Options.PATTERN_FLAGS
                                ).split(subArg)[0]
                            ).doubleValue();
                        } catch (ParseException e) {
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.speed",
                                    args[i + 1],
                                    args[i]
                                ),
                                e
                            );
                        }
                        i++;
                        }
                        break;
                        // Test for -q option
                    case 'q':
                        // If the -q option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.QUALITY_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the quality level option
                        Sequencer.OPTIONS.options |= Sequencer.Options.QUALITY_OPTION;
                        // Parse the quality sub-argument
                        if (i + 1 >= args.length)
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.quality.level",
                                    args[i]
                                )
                            );
                        try {
                            // Parse quality level number
                            if ((Sequencer.OPTIONS.quality = NumberFormat.getIntegerInstance().parse(
                                args[i + 1].trim()
                            ).intValue()) < 0 || Sequencer.OPTIONS.quality > 100)
                                System.out.println(
                                    Sequencer.m(
                                        "Sequencer.cmdline.warning.quality.level",
                                        args[i],
                                        Sequencer.OPTIONS.quality = Sequencer.OPTIONS.quality < 0 ?
                                            0 :
                                            Sequencer.OPTIONS.quality > 100 ?
                                                100 :
                                                Sequencer.OPTIONS.quality
                                    )
                                );
                        } catch (ParseException e) {
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.quality.level.invalid.value",
                                    args[i + 1],
                                    args[i]
                                ),
                                e
                            );
                        }
                        i++;
                        break;
                        // Test for -r option
                    case 'r':
                        // If the -r option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.RESIZE_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the resize option
                        Sequencer.OPTIONS.options |= Sequencer.Options.RESIZE_OPTION;
                        break;
                        // Test for -s option
                    case 's':
                        // If the -s option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.SMOOTH_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // If the -c or -l option has been specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(
                                Sequencer.Options.CENTER_OPTION |
                                Sequencer.Options.LINEAR_INTERPOLATE_OPTION
                            ))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.exlusive",
                                    args[i],
                                    Sequencer.OPTIONS.isOptionSet(
                                        Sequencer.Options.CENTER_OPTION
                                    ) ?
                                        "-c" :
                                        "-l"
                                )
                            );
                        // Set the smooth option
                        Sequencer.OPTIONS.options |= Sequencer.Options.SMOOTH_OPTION;
                        smoothOptionIndex = i;
                        // Test for sub-options of -s
                        for (subOptionIndex++; subOptionIndex < option.length(); subOptionIndex++)
                            switch (option.charAt(subOptionIndex)) {
                                case 'a':
                                    if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.SMOOTH_ALTITUDE_OPTION))
                                        throw new IllegalArgumentException(
                                            Sequencer.m(
                                                "Sequencer.cmdline.error.suboption.once",
                                                option.charAt(subOptionIndex),
                                                option
                                            )
                                        );
                                    Sequencer.OPTIONS.options |= Sequencer.Options.SMOOTH_ALTITUDE_OPTION;
                                    break;
                                case 'h':
                                    if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.SMOOTH_HARMONIC_OPTION))
                                        throw new IllegalArgumentException(
                                            Sequencer.m(
                                                "Sequencer.cmdline.error.suboption.once",
                                                option.charAt(subOptionIndex),
                                                option
                                            )
                                        );
                                    Sequencer.OPTIONS.options |= Sequencer.Options.SMOOTH_HARMONIC_OPTION;
                                    break;
                                case 's':
                                    if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.SMOOTH_SPEED_OPTION))
                                        throw new IllegalArgumentException(
                                            Sequencer.m(
                                                "Sequencer.cmdline.error.suboption.once",
                                                option.charAt(subOptionIndex),
                                                option
                                            )
                                        );
                                    Sequencer.OPTIONS.options |= Sequencer.Options.SMOOTH_SPEED_OPTION;
                                    break;
                                case 't':
                                    if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.SMOOTH_TIME_OPTION))
                                        throw new IllegalArgumentException(
                                            Sequencer.m(
                                                "Sequencer.cmdline.error.suboption.once",
                                                option.charAt(subOptionIndex),
                                                option
                                            )
                                        );
                                    Sequencer.OPTIONS.options |= Sequencer.Options.SMOOTH_TIME_OPTION;
                                    break;
                                default:
                                    throw new IllegalArgumentException(
                                        Sequencer.m(
                                            "Sequencer.cmdline.error.suboption.invalid",
                                            option.charAt(subOptionIndex),
                                            option
                                        )
                                    );
                            }
                        // If the -s option has a nodes sub-argument then parse and set it
                        // That is, if the next argument is neither a file nor an option then
                        // -s has a sub-argument specified
                        if (i + 1 < args.length &&
                            !Sequencer.isOption(args[i + 1]))
//                            !new File(args[i + 1]).exists() &&
//                            !OPTION_PATTERN.matcher(args[i + 1].trim()).find()) {
                            try {
                                // Parse nodes number
                                Sequencer.OPTIONS.nodes = NumberFormat.getIntegerInstance().parse(
                                    args[i + 1].trim()
                                ).intValue();
                            } catch (ParseException e) {
                                throw new IllegalArgumentException(
                                    Sequencer.m(
                                        "Sequencer.cmdline.error.natural.number",
                                        args[i + 1],
                                        args[i]
                                    ),
                                    e
                                );
                            }
                            i++;
//                        }
                        break;
                    case 't':
                        // If the -t option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.TIME_STAMP_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the time stamp from file option
                        Sequencer.OPTIONS.options |= Sequencer.Options.TIME_STAMP_OPTION;
                        // Test for sub-options of -t
                        for (subOptionIndex++; subOptionIndex < option.length(); subOptionIndex++)
                            switch (option.charAt(subOptionIndex)) {
                                case 'o':
                                    if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.TIME_STAMP_OVERWRITE_OPTION))
                                        throw new IllegalArgumentException(
                                            Sequencer.m(
                                                "Sequencer.cmdline.error.suboption.once",
                                                option.charAt(subOptionIndex),
                                                option
                                            )
                                        );
                                    Sequencer.OPTIONS.options |= Sequencer.Options.TIME_STAMP_OVERWRITE_OPTION;
                                    break;
                                default:
                                    throw new IllegalArgumentException(
                                        Sequencer.m(
                                            "Sequencer.cmdline.error.suboption.invalid",
                                            option.charAt(subOptionIndex),
                                            option
                                        )
                                    );
                            }
                        break;
                        // Test for -u option
                    case 'u':
                        // If the -u option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.UTC_TIME_ZONE_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the utc time zone option
                        Sequencer.OPTIONS.options |= Sequencer.Options.UTC_TIME_ZONE_OPTION;
                        // If ISO and UTC requested then use predefined DateTimeFormatter.ISO_INSTANT
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.ISO_TIME_OPTION))
                            Sequencer.OPTIONS.dateTimeFormatter = DateTimeFormatter.ISO_INSTANT;
                        else { // Format date time stamps in UTC time zone with time zone field since they are not local
                            Sequencer.OPTIONS.dateTimeFormatter =
                                DateTimeFormatter.ofLocalizedDateTime(
                                    FormatStyle.MEDIUM,
                                    FormatStyle.LONG
                                );
                            Sequencer.OPTIONS.dateTimeFormatter =
                                Sequencer.OPTIONS.dateTimeFormatter.withZone(
                                    ZoneId.of("UTC")
                                );
                        }
                        break;
                        // Test for -v option
                    case 'v':
                        // If the -v option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.VERBOSE_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        // Set the verbose option
                        Sequencer.OPTIONS.options |= Sequencer.Options.VERBOSE_OPTION;
                        break;
                        // Test for -x option
                    case 'x':
                        // If the -x option has been already specified then error out
                        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.GPS_AREA_INFO_OPTION))
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.once",
                                    args[i]
                                )
                            );
                        if (i + 1 >= args.length)
                            throw new IllegalArgumentException(
                                Sequencer.m(
                                    "Sequencer.cmdline.error.gps.area.info",
                                    args[i]
                                )
                            );
                        Sequencer.OPTIONS.gpsAreaInformation = args[i += 1];
                        // Set the GPS area information option
                        Sequencer.OPTIONS.options |= Sequencer.Options.GPS_AREA_INFO_OPTION;
                        break;
                    default:
                        throw new IllegalArgumentException(
                            Sequencer.m(
                                "Sequencer.cmdline.error.fatal.error",
                                args[i]
                            )
                        );
                }
            }
            // All options processed
            if (i < args.length) {
                // If args[i] is a file then process input files until no left or args[i] is a directory
                File inputFile;
                if ((inputFile = new File(args[i])).exists()) {
                    final List<File> fileList = new LinkedList<File>();
                    for (; i < args.length &&
                           (inputFile = new File(args[i])).exists() &&
                           !inputFile.isDirectory(); i++)
                        fileList.add(inputFile);
                    fileList.toArray(
                        Sequencer.OPTIONS.inputFiles = new File[fileList.size()]
                    );
                }
                // If args[i] is a directory then store output directory
                if (inputFile.isDirectory())
                    Sequencer.OPTIONS.outputDir = inputFile;
                // If args[i] is the last argument and a directory can be created
                else if (args.length - 1 == i && inputFile.mkdirs())
                    Sequencer.OPTIONS.outputDir = inputFile;
                else if (!inputFile.exists()) {
                    if (args.length - 1 == i && GraphicsEnvironment.isHeadless())
                    throw new IllegalArgumentException(
                        Sequencer.m(
                            "Sequencer.cmdline.error.directory",
                            Sequencer.Options.getCanonicalPath(inputFile)
                        )
                    );
                    else throw new IllegalArgumentException(
                        Sequencer.m(
                            "Sequencer.cmdline.error.no.input.file",
                            Sequencer.Options.getCanonicalPath(inputFile)
                        )
                    );
                }
            }
            // If still no input files have been specified or selected then error out
            if (Sequencer.OPTIONS.inputFiles == null ||
                Sequencer.OPTIONS.inputFiles.length <= 0)
                if (GraphicsEnvironment.isHeadless())
                    throw new IllegalArgumentException(Sequencer.m(
                        "Sequencer.cmdline.error.input.files")
                    );
                else if ((Sequencer.OPTIONS.inputFiles = Sequencer.getInputFilesByDialog()).length <= 0)
                    System.exit(0);
            // If still no output directory has been specified or selected then error out
            if (Sequencer.OPTIONS.outputDir == null)
                if (GraphicsEnvironment.isHeadless())
                    throw new IllegalArgumentException(Sequencer.m(
                        "Sequencer.cmdline.error.output.dir")
                    );
                else if ((Sequencer.OPTIONS.outputDir = Sequencer.getOutputDirectoryByDialog()) == null)
                    System.exit(0);
        } catch (IllegalArgumentException e) {
            System.out.println(Sequencer.m("Sequencer.usage"));
            throw e;
        }
        // If smooth option specified adjust nodes limits
        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.SMOOTH_OPTION)) {
            if (Sequencer.OPTIONS.nodes == 0) {
                Sequencer.OPTIONS.nodes = Sequencer.OPTIONS.inputFiles.length;
                return;
            }
            if (Sequencer.OPTIONS.nodes < 0)
                System.out.println(
                    Sequencer.m(
                        "Sequencer.cmdline.warning.nodes.too.low",
                        args[smoothOptionIndex],
                        Sequencer.OPTIONS.nodes = 0
                    )
                );
            // If nodes is smaller than 2 then effectively do not smooth
            if (Sequencer.OPTIONS.nodes < 2) {
                System.out.println(
                    Sequencer.m(
                        "Sequencer.cmdline.warning.nodes.off",
                        Sequencer.OPTIONS.nodes,
                        args[smoothOptionIndex]
                    )
                );
                Sequencer.OPTIONS.nodes = 0;
                Sequencer.OPTIONS.options ^= Sequencer.Options.SMOOTH_OPTION |
                    Sequencer.Options.SMOOTH_ALTITUDE_OPTION |
                    Sequencer.Options.SMOOTH_HARMONIC_OPTION |
                    Sequencer.Options.SMOOTH_SPEED_OPTION |
                    Sequencer.Options.SMOOTH_TIME_OPTION;
                return;
            }
            // Set the
            final int inputFileCount;
            if (Sequencer.OPTIONS.nodes > (inputFileCount = Sequencer.OPTIONS.inputFiles.length)) {
                System.out.println(
                    Sequencer.m(
                        "Sequencer.cmdline.warning.nodes.too.great",
                        args[smoothOptionIndex],
                        inputFileCount
                    )
                );
                Sequencer.OPTIONS.nodes = inputFileCount;
            }
        }
    }

    /**
     * Gets a formatted {@linkplain MessageFormat message} for the current
     * default {@link java.util.Locale}. Messages are stored in
     * <code>data/lang/Sequencer<i>_*</i>.properties</code> files.
     * @param messageKey the message key (property name) of the message to get
     * and format
     * @param args any arguments pertaining to the message
     * @return the formatted message
     * @see MessageFormat
     * @see <code><a href="../../../res/data/lang/Sequencer.properties">Sequencer.properties</a></code>
     */
    public static final String m(
        final String messageKey,
        final Object... args) {
        return MessageFormat.format(
            Sequencer.bundle == null ?
                (Sequencer.bundle = ResourceBundle.getBundle(
                    "data/lang/Sequencer"
                )).getString(messageKey) :
                Sequencer.bundle.getString(messageKey),
            args);
    }

    /**
     * @param args command line arguments
     * @throws java.io.IOException if any of the input files, the output
     * directory specified on the command line, or output file written to the
     * output directory cannot be accessed.
     */
    public static final void main(final String[] args) throws IOException {
        Sequencer.processCommandLineArguments(args);
        // Sort input files by GPS date time stamp or fallback to files' last
        // modification time (sort into sequence).
        System.out.println(Sequencer.OPTIONS.toString());
        // Sort files by GPS date and time stamp, optionally by an EXIF time
        // stamp, or finally the file's file system time stamp
        Arrays.<File>sort(
            Sequencer.OPTIONS.inputFiles,
            new ExifDateTimeComparator(true)
        );
        if (Sequencer.OPTIONS.isOptionSet(Sequencer.Options.CENTER_OPTION))
            Sequencer.center(Sequencer.OPTIONS.inputFiles, Sequencer.OPTIONS.degrees);
        else Sequencer.processFiles(Sequencer.OPTIONS.inputFiles);
    }

    private static final class JPEGTIFFFilenameFilter implements FilenameFilter {
        private JPEGTIFFFilenameFilter() {}

        @Override
        public final boolean accept(final File dir, final String name) {
            final String formatName;
            try {
                return (formatName = Imaging.guessFormat(
                    new File(dir, name)
                ).getName()).equals("JPEG") ||
                formatName.equals("TIFF");
            } catch (IOException | ImageReadException e) {
                return false;
            }
        }
    }

    /**
     * Representation of globally set options and any pertaining values.
     *
     * @see Sequencer#OPTIONS
     */
    public static final class Options {
        private Options() {}

        public static final int ALTITUDE_OPTION              = 0x00000001,
                                CENTER_OPTION                = 0x00000002,
                                DROP_OPTION                  = 0x00000004,
                                GPX_FILE_OPTION              = 0x00000008,
                                SMOOTH_OPTION                = 0x00000010,
                                SMOOTH_ALTITUDE_OPTION       = 0x00000020,
                                SMOOTH_HARMONIC_OPTION       = 0x00000040,
                                SMOOTH_TIME_OPTION           = 0x00000080,
                                SMOOTH_SPEED_OPTION          = 0x00000100,
                                ISO_TIME_OPTION              = 0x00000200,
                                PRESERVE_TIME_STAMP_OPTION   = 0x00000400,
                                LINEAR_INTERPOLATE_OPTION    = 0x00000800,
                                NORMALIZE_OPTION             = 0x00001000,
                                SPEED_OPTION                 = 0x00002000,
                                QUALITY_OPTION               = 0x00004000,
                                RESIZE_OPTION                = 0x00008000,
                                TIME_STAMP_OPTION            = 0x00010000,
                                TIME_STAMP_OVERWRITE_OPTION  = 0x00020000,
                                UTC_TIME_ZONE_OPTION         = 0x00040000,
                                VERBOSE_OPTION               = 0x00080000,
                                GPS_AREA_INFO_OPTION         = 0x00100000;
        private static final int PATTERN_FLAGS = Pattern.CANON_EQ |
                                                 Pattern.CASE_INSENSITIVE |
                                                 Pattern.UNICODE_CASE;
        private int options = Sequencer.Options.DROP_OPTION | 
                              Sequencer.Options.SMOOTH_OPTION |
                              Sequencer.Options.NORMALIZE_OPTION |
                              Sequencer.Options.RESIZE_OPTION;
        private double altitude;
        private double degrees;
        private String degreesRef =
            GpsTagConstants.GPS_TAG_GPS_IMG_DIRECTION_REF_VALUE_TRUE_NORTH;
        private String gpsAreaInformation;
        private File gpxFile;
        private int nodes;
        private double speed;
        private String speedRef =
            GpsTagConstants.GPS_TAG_GPS_SPEED_REF_VALUE_KMPH;
        private float quality = 0.9F;
        private DateTimeFormatter dateTimeFormatter =
            DateTimeFormatter.ofLocalizedDateTime(
                FormatStyle.MEDIUM,
                FormatStyle.MEDIUM
            ).withZone(ZoneId.systemDefault());
        private File[] inputFiles;
        private File outputDir;

        /**
         * Gets the current {@link DateTimeFormatter}.
         *
         * @return the current {@code DateTimeFormatter} object
         *
         * @see #dateTimeFormatter
         */
        public final DateTimeFormatter getDateFormatter() {
            return this.dateTimeFormatter;
        }

        /**
         * Gets the {@code String} to set for the GPS area information field
         * ({@code GPSAreaInformation 0x1C}).
         *
         * @return the GPS area information or {@code null}
         */
        public final String getGPSAreaInformation() {
            return this.gpsAreaInformation;
        }

        public final File getGPXFile() {
            try {
                return this.gpxFile == null ? this.gpxFile : this.gpxFile.getCanonicalFile();
            } catch (IOException e) {
                System.err.println(e.getLocalizedMessage());
                return null;
            }
        }

        /**
         * Gets the combination of currently set options. Use the
         * {@code Sequencer.Options.*_OPTION} constants to test for set options.
         *
         * @return a combination of {@code Sequencer.Options.*_OPTION} flags
         *
         * @see #ALTITUDE_OPTION
         * @see #CENTER_OPTION
         * @see #DROP_OPTION
         * @see #SMOOTH_OPTION
         * @see #SMOOTH_ALTITUDE_OPTION
         * @see #SMOOTH_HARMONIC_OPTION
         * @see #SMOOTH_TIME_OPTION
         * @see #SMOOTH_SPEED_OPTION
         * @see #ISO_TIME_OPTION
         * @see #NORMALIZE_OPTION
         * @see #SPEED_OPTION
         * @see #QUALITY_OPTION
         * @see #RESIZE_OPTION
         * @see #TIME_STAMP_OPTION
         * @see #TIME_STAMP_OVERWRITE_OPTION
         * @see #UTC_TIME_ZONE_OPTION
         * @see #VERBOSE_OPTION
         */
        public final int getOptions() {
            return this.options;
        }

        /**
         * Test if an option or a combination of options is set.
         *
         * @param option the option or combination of options to test for
         * @return {@code true} if the option or combination of options is set,
         * {@code false} otherwise
         *
         * @see #ALTITUDE_OPTION
         * @see #CENTER_OPTION
         * @see #DROP_OPTION
         * @see #SMOOTH_OPTION
         * @see #SMOOTH_ALTITUDE_OPTION
         * @see #SMOOTH_HARMONIC_OPTION
         * @see #SMOOTH_TIME_OPTION
         * @see #SMOOTH_SPEED_OPTION
         * @see #ISO_TIME_OPTION
         * @see #NORMALIZE_OPTION
         * @see #SPEED_OPTION
         * @see #QUALITY_OPTION
         * @see #RESIZE_OPTION
         * @see #TIME_STAMP_OPTION
         * @see #TIME_STAMP_OVERWRITE_OPTION
         * @see #UTC_TIME_ZONE_OPTION
         * @see #VERBOSE_OPTION
         */
        public final boolean isOptionSet(final int option) {
            return (this.options & option) != 0;
        }

        public final String toString() {
            return String.format(
                "options: 0x%08X\n" +
                "altitude: %,f\n" +
                "degrees: %,f\n" +
                "degreesRef: %s\n" +
                "gpsAreaInformation: %s\n" +
                "gpxFile: %s\n" +
                "nodes: %,d\n" +
                "speed: %,f\n" +
                "speedRef: %s\n" +
                "quality: %,f\n" +
                "outputDir: %s",
                this.options,
                this.altitude,
                this.degrees,
                this.degreesRef,
                this.gpsAreaInformation,
                Sequencer.Options.getCanonicalPath(this.gpxFile),
                this.nodes,
                this.speed,
                this.speedRef,
                this.quality,
                Sequencer.Options.getCanonicalPath(this.outputDir)
            );
        }

        /**
         * This method only exists because lambda expressions in Java have been
         * poorly designed hence they <strong>suck badly</strong>.
         *
         * @param file the file to get the canonical path of
         *
         * @return the canonical path of {@link #outputDir} or {@code null}
         */
        private static final String getCanonicalPath(final File file) {
            try {
                return file == null ? null : file.getCanonicalPath();
            } catch (IOException e) {
                System.err.println(e.getLocalizedMessage());
                return null;
            }
        }
    }
}