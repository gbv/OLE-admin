package de.gbv.ole;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.marc4j.MarcStreamReader;
import org.marc4j.MarcStreamWriter;
import org.marc4j.MarcWriter;
import org.marc4j.marc.Record;

/**
 * Splits a MARC21 file into MARC21 files with 1000 records.
 */
public final class Marc21Split {
    /** Suffix .mrc der MARC21-Datei. */
    private static final String MRC_SUFFIX = ".mrc"; 
    
    /** Records per output file. */
    private static final int MAX_RECORDS_PER_FILE = 10000;

    /**
     * Private constructor to prevent instantiation.
     */
    private Marc21Split() {
    }

    /**
     * Print usage to Syste.err.
     */
    private static void usageExit() {
        System.err.println("Usage: Marc21Split filename.mrc");
        System.err.println("Reads MARC21 from filename.mrc, writes");
        System.err.println("filename-001.mrc, filename-002.mrc, ...");
        System.err.println("by splitting into files of "
                + MAX_RECORDS_PER_FILE + " records.");
        System.exit(1);
    }
    
    /**
     * Return i with at least thee digits by prepending 0s.
     * @param i         number to format
     * @return  formatted number
     */
    private static String format(final int i) {
        String s = Integer.toString(i);
        switch (s.length()) {
        case 0:
            return "000";
        case 1:
            return "00" + s;
        case 2:
            return "0" + s;
        default:
            return s;
        }
    }
    
    /**
     * Spaltet eine MARC21-Datei in mehrere MARC21-Dateien mit jeweils
     * 1000 Sätzen auf.
     * @param args      Pfad mit auf .mrc endendem Dateinamen der MARC21-Datei.
     * @throws IOException      Fehler beim Dateilesen oder -schreiben
     */
    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            usageExit();
        }

        String infile = args[0];

        if (!infile.endsWith(MRC_SUFFIX)) {
            System.err.println("Filename must end with .mrc");
            usageExit();
        }

        InputStream in = new FileInputStream(infile);
        MarcStreamReader reader = new MarcStreamReader(in);

        int filenumber = 0;
        int recordsWritten = 0;
        OutputStream out = null;
        MarcWriter writer = null;
        
        while (reader.hasNext()) {
            if (writer == null || recordsWritten >= MAX_RECORDS_PER_FILE) {
                if (writer != null) {
                    writer.close();
                }
                if (out != null) {
                    out.close();
                }
                
                filenumber++;
                String outfile = infile.substring(
                        0, infile.length() - MRC_SUFFIX.length())
                        + "-" + format(filenumber) + ".mrc";

                out = new FileOutputStream(outfile, false);
                writer = new MarcStreamWriter(out, "UTF8");
                
                recordsWritten = 0;
            }
            
            Record record = reader.next();
            writer.write(record);
            recordsWritten++;
        }

        out.close();
        in.close();
    }
}
