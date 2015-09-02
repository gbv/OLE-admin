package de.gbv.ole;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.marc4j.Constants;
import org.marc4j.MarcException;
import org.marc4j.MarcStreamReader;
import org.marc4j.MarcWriter;
import org.marc4j.converter.CharConverter;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Liest eine MARC21-Datei ein, erzeugt drei Dateien für
 * den Import per LOAD DATA INFILE in die OLE-Tabellen
 * ole_ds_bib_t, ole_ds_bib_t und ole_ds_item_t.

 * This file contains parts of  
 * org.marc4j/MarcXmlWriter.java which is
 * Copyright (C) 2004 Bas Peters
 * 
 * This file is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public 
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public 
 * License along with MARC4J; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
public class Marc21ToOleBulk implements MarcWriter {
    /** Factory to create MARC data structures */
    protected static final MarcFactory marcFactory = MarcFactory.newInstance();
    /** Suffix .mrc der MARC21-Datei. */
    private static final String MRCSUFFIX = ".mrc"; 
    /** XML-Tagname. */ 
    protected static final String COLLECTION = "collection";
    /** XML-Tagname. */ 
    protected static final String RECORD = "record";
    /** XML-Tagname. */ 
    protected static final String LEADER = "leader";
    /** XML-Tagname. */ 
    protected static final String CONTROL_FIELD = "controlfield";
    /** XML-Tagname. */ 
    protected static final String DATA_FIELD = "datafield";
    /** XML-Tagname. */ 
    protected static final String SUBFIELD = "subfield";
    /** Ist die Location der Key-Wert, ist sie in den Value-Wert zu ändern. */
    protected static final Map<String,String> convertLocation
        = new HashMap<String,String>();
    static {
        convertLocation.put("ALT",                          "UB/ALT");
        convertLocation.put("Alt",                          "UB/ALT");
        convertLocation.put("AMI",                          "UB/AMI");
        convertLocation.put("Arbeitsbibliothek Holthusen",  "UB/AH");
        convertLocation.put("AZP",                          "UB/AZP");
        convertLocation.put("HA",                           "UB/HA");
        convertLocation.put("HB",                           "UB/HA");
        convertLocation.put("HI/UB/LS",                     "UB/LS");
        convertLocation.put("LEIHSTELLE",                   "UB/LS");
        convertLocation.put("Leihstelle",                   "UB/LS");
        convertLocation.put("Leselounge",                   "UB/LS");
        convertLocation.put("LS-NUTZUNG",                   "UB/LS");
        convertLocation.put("!MAGAZIN",                     "UB/MAG");
        convertLocation.put("HI/UB/MG",                     "UB/MAG");
        convertLocation.put("MAGAZIN",                      "UB/MAG");
        convertLocation.put("Magazin",                      "UB/MAG");
        convertLocation.put("Verlust",                      "UB/MAG");
        convertLocation.put("ZSS-Dublettenlager",           "UB/MAG");
        convertLocation.put("ZSS-MAGAZIN",                  "UB/MAG");
        convertLocation.put("ZSS-Magazin",                  "UB/MAG");
        convertLocation.put("MEDIENARCHIV",                 "UB/MED");
        convertLocation.put("Medienarchiv",                 "UB/MED");
        convertLocation.put("MEDIOTHEK",                    "UB/MED");
        convertLocation.put("Mediothek",                    "UB/MED");
        convertLocation.put("MIKROFORM",                    "UB/MIK");
        convertLocation.put("MiKROFORM",                    "UB/MIK");
        convertLocation.put("ATLANTENSCHRANK",              "UB/UBHIL");
        convertLocation.put("Ausgeschieden",                "UB/UBHIL");
        convertLocation.put("HI",                           "UB/UBHIL");
        convertLocation.put("Makuliert",                    "UB/UBHIL");
        convertLocation.put("PRF",                          "UB/UBHIL");
        convertLocation.put("PRZ",                          "UB/UBHIL");
        convertLocation.put("RARA",                         "UB/UBHIL");
        convertLocation.put("Rara",                         "UB/UBHIL");
        convertLocation.put("STA",                          "UB/UBHIL");
        convertLocation.put("",                             "UB/UBHIL");
    }
    /** True to output only PPNs with 5 before check digit */
    private boolean ppn5 = false;
    /** XML transformer handler. */
    private TransformerHandler handler = null;
    /** bib output stream. */
    private Writer bibWriter = null;
    /** holdings output stream. */
    private Writer holdingsWriter = null;
    /** item output stream. */
    private Writer itemWriter = null;
    
    /**
     * Print usage to System.err and exit the program.
     */
    private static void usageExit() {
        ResourceBundle messages = ResourceBundle.getBundle("application");
        System.err.println(messages.getString("usage"));
        System.exit(1);
    }
    
    /**
     * Setup of output.
     * @param bib       bib destination
     * @param holdings  holdings destination
     * @param item      item destination
     * @param ppn5      true to output only PPNs with 5 before check digit
     */
    public Marc21ToOleBulk(final Writer bibWriter,
            final Writer holdingsWriter, final Writer itemWriter, final boolean ppn5) {
        this.bibWriter      = bibWriter;
        this.holdingsWriter = holdingsWriter;
        this.itemWriter     = itemWriter;
        setHandler(new StreamResult(bibWriter));
        this.ppn5 = ppn5;
    }
    
    /**
     * Set the <code>Result</code> associated with this
     * <code>TransformerHandler</code> to be used for the transformation.
     *
     * @param result    the Result to be used for the transformation
     */
    protected final void setHandler(final Result result) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            if (!factory.getFeature(SAXTransformerFactory.FEATURE)) {
                throw new UnsupportedOperationException(
                        "SAXTransformerFactory is not supported");
            }
            
            SAXTransformerFactory saxFactory = (SAXTransformerFactory) factory;
            handler = saxFactory.newTransformerHandler();
            handler.getTransformer()
                    .setOutputProperty(OutputKeys.METHOD, "xml");
            handler.setResult(result);

        } catch (Exception e) {
            throw new MarcException(e.getMessage(), e);
        }
    }
    
    /**
     * Return the input without the tailing check digit.
     * 
     * Example:
     * cut("123456789X") = "123456789"
     * cut("987654321")  = "98765432"
     * 
     * @param numberWithCheckdigit      -       number including check digit
     * @return number without check digit
     */
    static final String withoutCheckdigit(final String numberWithCheckdigit) {
        return StringUtils.left(numberWithCheckdigit, numberWithCheckdigit.length() - 1);
    }
    
    /**
     * Return the ISBN10 check digit for number.
     * 
     * isbn10checkdigit(        0) = "0"
     * isbn10checkdigit(        1) = "9"
     * isbn10checkdigit(212121212) = "4"
     * isbn10checkdigit(123456789) = "X"
     * isbn10checkdigit(999999999) = "9"
     * 
     * @param number from the range 0 .. 999999999
     * @throws IllegalArgumentException if number is off range
     * @return calculated check digit (0 .. 9 or X)
     */
    static final String isbn10checkdigit(final int number) {
        if (number < 0 || number > 999999999) {
            throw new IllegalArgumentException(
                    "number from range 0 .. 999999999 expected, "
                    + "but found " + number);
        }
        
        int n = number;
        int sum = 0;
        int factor = 9;
        while (n != 0) {
            sum += (n % 10) * factor;
            factor--;
            n /= 10;
        }
        
        int checkdigit = sum % 11;
        if (checkdigit == 10) {
            return "X";
        }
        return Integer.toString(checkdigit);
    }
    
    /**
     * Throws a MarcException if the parameter is not a valid isbn10 number
     * with valid check digit.
     * 
     * Valid: "00", "19", "27",
     * "2121212124", 
     * "9999999980",
     * "9999999999"
     * 
     * @param isbn10 the number to validate
     */
    static void validateIsbn10(final String isbn10) {
        if (StringUtils.isEmpty(isbn10)) {
            throw new MarcException("null or empty number");
        }
        if (StringUtils.isWhitespace(isbn10)) {
            throw new MarcException(
                    "number expected, found only whitespace");
        }
        if (StringUtils.containsWhitespace(isbn10)) {
            throw new MarcException(
                    "number plus check digit expected, but contains "
                    + "whitespace: '" + isbn10 + "'");
        } 
        if (isbn10.length() < 2) {
            throw new MarcException("number plus check digit expected, "
                    + "but found: " + isbn10);
        }
        if (isbn10.length() > 10) {
            throw new MarcException("maximum length of number plus "
                    + "check digit is 10 (9 + 1 check digit),\n"
                    + "input is " + isbn10.length() + " characters long: " + isbn10);
        }
        String checkdigit = StringUtils.right(isbn10, 1);
        if (!StringUtils.containsOnly(checkdigit, "0123456789xX")) {
            throw new MarcException("check digit must be 0-9, x or X, "
                    + "but is " + checkdigit);
        }
        String number = withoutCheckdigit(isbn10);
        if (!StringUtils.containsOnly(number, "0123456789")) {
            throw new MarcException("number before check digit must "
                    + "contain 0-9 only, but is " + checkdigit);
        }
        String calculatedCheckdigit = isbn10checkdigit(Integer.parseInt(number));
        if (! StringUtils.equalsIgnoreCase(checkdigit, calculatedCheckdigit)) {
            throw new MarcException("wrong checkdigit " + checkdigit 
                    + ", correct check digit is " + calculatedCheckdigit 
                    + ": " + isbn10);
        }
    }

    /**
     * Write the Record object to the output files.
     *
     * @param record - the <code>Record</code> object
     */
    public final void write(final Record record) {
        /* get control field 001 */
        String ppn = record.getControlNumber();
        
        if (ppn5) {
            // Nur Daten mit 5 vor der PPN-Prüfziffer ausgeben;
            // das reduziert den Datenumfang auf ca. 10 %
            if (! StringUtils.substring(ppn,  -2,  -1).equals("5")) {
                return;
            }
        }
        
        try {
            validateIsbn10(ppn);
            bibWriter.write(withoutCheckdigit(ppn));
            bibWriter.write("\t");
            toXml(ppn, record);
            bibWriter.write("\n");
        } catch (Exception e) {
            throw new MarcException("Control field 001 (PPN): " + ppn, e);
        }
    }

    /**
     * Start the element using <code>handler</code>. 
     * 
     * @param name      used for localName and qName
     * @param atts      the attributes to attach to the element
     * @throws SAXException     bei XML-Fehler
     */
    private void startElement(final String name, final Attributes atts)
            throws SAXException {
        handler.startElement(Constants.MARCXML_NS_URI, name, name, atts);
    }
    
    /**
     * Start the element without attributes using <code>handler</code>. 
     * 
     * @param name      used for localName and qName
     * @throws SAXException     bei XML-Fehler
     */
    private void startElement(final String name) throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        startElement(name, atts);
    }
    
    /**
     * End the element using <code>handler</code>. 
     * 
     * @param name      used for localName and qName
     * @throws SAXException     bei XML-Fehler
     */
    private void endElement(final String name) throws SAXException {
        handler.endElement(Constants.MARCXML_NS_URI, name, name);
    }

    /**
     * Add an CDATA attribute.
     * 
     * @param atts      where to add
     * @param name      used for localName and qName
     * @param value     attribute value
     */
    private void add(final AttributesImpl atts,
            final String name, final String value) {
        atts.addAttribute("", name, name, "CDATA", value);
    }
    
    /**
     * Add an CDATA attribute.
     * 
     * @param atts      where to add
     * @param name      used for localName and qName
     * @param value     attribute value
     */
    private void add(final AttributesImpl atts,
            final String name, final char value) {
        atts.addAttribute("", name, name, "CDATA", String.valueOf(value));
    }
    
    /**
     * Write data to bib output.
     * 
     * Does Unicode NFC normalization.
     * 
     * Masks these four characters: \n \r \t \\
     * This masking is needed for LOAD DATA INFILE.
     * 
     * @param data      data to write
     * @throws SAXException     on write error
     */
    private void write(final String data) throws SAXException {
        String normalized = Normalizer.normalize(data, Normalizer.Form.NFC);
        char [] charArray = StringUtils.replaceEach(normalized,
                new String[]{  "\n",   "\r",   "\t",   "\\", },
                new String[]{"\\\n", "\\\r", "\\\t", "\\\\", }).toCharArray();
        handler.characters(charArray, 0, charArray.length);
    }
    
    /**
     * Print the content of field as a multiline String.
     * @param field     content to print
     * @return multiline String
     */
    final static String print(final DataField field) {
        StringBuffer s = new StringBuffer();
        s.append(field.getTag());
        s.append(' ').append(field.getIndicator1());
        s.append(' ').append(field.getIndicator2());
        s.append('\n');
        for (Subfield subfield : field.getSubfields()) {
            s.append(subfield.getCode()).append(' ').
                append(subfield.getData()).append('\n');
        }
        
        return s.toString();
    }
    
    /**
     * Schreibt die Exemplare sortiert nach Exemplarnummer.
     * @param ppn       PPN der Exemplare.
     * @param record    Record, zu dem das Exemplar gehört
     * @param exemplare Zu konvertierende und schreibende Exemplare.
     * @throws IOException      bei Ausgabefehler
     */
    void write(String ppn, Record record, Map<String, Exemplar> exemplare)
            throws IOException {
        SortedMap<String, Exemplar> sort =
                new TreeMap<String, Exemplar>(exemplare);
        for (Map.Entry<String, Exemplar> e : sort.entrySet()) {
            String exemplarnummer = e.getKey();
            Exemplar exemplar = e.getValue();
            fetchItemType(record, exemplar);
            write(ppn, exemplarnummer, exemplar);
        }
    }
    
    void write(String ppn, String exemplarnummer, Exemplar exemplar)
            throws IOException {
        String ppnCut = withoutCheckdigit(ppn);
        String epnCut = withoutCheckdigit(exemplar.epn);
        
        // holdings_id=epnCut, bib_id=ppnCut
        holdingsWriter.write(
                  epnCut + "\t" // holdings_id
                + ppnCut + "\t" // bib_id
                + exemplar.location + "\t"
                + exemplar.callnumber + "\t"
                + exemplar.shelvingOrder + "\n");
        
        // item_id=epnCut, holdings_id=epnCut
        itemWriter.write(
                  epnCut + "\t"  // item_id 
                + epnCut + "\t"  // holdings_id
                + exemplar.barcode + "\t"
                + exemplar.itemType + "\n");  // item_type_id (ole_cat_itm_typ_t)
    }
    
    /**
     * Writes a Record object to the result.
     *
     * @param ppn    PPN of the record
     * @param record -
     *               the <code>Record</code> object
     * @throws SAXException     
     */
    protected final void toXml(final String ppn, final Record record)
            throws SAXException, IOException {
        if (!marcFactory.validateRecord(record)) {
            throw new MarcException("Marc record didn't validate");
        }
        
        startElement(COLLECTION);
        startElement(RECORD);
        startElement(LEADER);
        write(record.getLeader().toString());
        endElement(LEADER);

        for (ControlField field : record.getControlFields()) {
            AttributesImpl atts = new AttributesImpl();
            add(atts, "tag", field.getTag());
            startElement(CONTROL_FIELD, atts);
            write(field.getData());
            endElement(CONTROL_FIELD);
        }

        Map<String, Exemplar> exemplare = new HashMap<String, Exemplar>();
        
        for (DataField field : record.getDataFields()) {
            if (field == null) {
                throw new MarcException("DataField is null");
            }

            try {
                {                             
                    AttributesImpl atts = new AttributesImpl();
                    add(atts, "tag", field.getTag());
                    add(atts, "ind1", field.getIndicator1());
                    add(atts, "ind2", field.getIndicator2());
                    startElement(DATA_FIELD, atts);
                }
                
                for (Subfield subfield : field.getSubfields()) {
                    AttributesImpl atts = new AttributesImpl();
                    add(atts, "code", subfield.getCode());
                    startElement(SUBFIELD, atts);
                    write(subfield.getData());
                    endElement(SUBFIELD);
                }

                endElement(DATA_FIELD);
                
                exemplar(field, exemplare);
            }
            catch (Exception e) {
                throw new MarcException(
                        "Marc data field: " + print(field), e);
            }
        }

        endElement(RECORD);
        endElement(COLLECTION);
        
        write(ppn, record, exemplare);
    }

    /**
     * Returns true iff c is a digit from 0 .. 9.
     * @param c         char to test
     * @return true if digit, false otherwise
     */
    final static boolean isDigit(final char c) {
        return '0' <= c && c <= '9';
    }
    
    /**
     * Liest Exemplarinformationen aus dem DataField,
     * schreibt sie nach exemplare, getrennt nach
     * Exemplarnummer.
     * @param field     Datenquelle
     * @param exemplare Datenziel
     */
    static void exemplar(final DataField field,
            Map<String, Exemplar> exemplare) {
        
        String tag = field.getTag();
        if (!StringUtils.equals(tag, "980") &&
            !StringUtils.equals(tag, "984")   ) {
            return;
        }
        
        Subfield ilnSubfield = field.getSubfield('2');
        if (ilnSubfield == null) {
            throw new MarcException(
                    "ILN expected, but subfield 2 is missing");
        }
        String iln = ilnSubfield.getData();
        if (StringUtils.isBlank(iln)) {
            throw new MarcException(
                    "ILN expected, but subfield 2 is empty");
        }

        // wir wollen nur Hildesheimer Daten, ILN 90
        if (!"90".equals(iln.trim())) {
            return;
        }
        
        Subfield exemplarmummerSubfield = field.getSubfield('1');
        if (exemplarmummerSubfield == null) {
            throw new MarcException(
                    "Exemplarnummer expected, but subfield 1 is missing");
        }
        String exemplarnummer = exemplarmummerSubfield.getData();
        if (StringUtils.isBlank(exemplarnummer)) {
            throw new MarcException(
                    "Exemplarnummer expected, but subfield 1 is empty");
        }

        exemplarnummer = exemplarnummer.trim();
        
        if (exemplarnummer.length() != 2) {
            throw new MarcException(
                    "Exemplarnummer with 2 digits expected, but found "
                    + exemplarnummer);
        }
        
        if (! isDigit(exemplarnummer.charAt(0)) ||
            ! isDigit(exemplarnummer.charAt(1))   ) {
            throw new MarcException(
                    "Exemplarnummer with 2 digits expected, but found "
                    + "a non-digit character: " + exemplarnummer);
        }

        Exemplar exemplar = exemplare.get(exemplarnummer);
        if (exemplar == null) {
            exemplar = new Exemplar();
            exemplare.put(exemplarnummer, exemplar);
        }
        
        if ("980".equals(tag)) {
            fetchEPN(field, exemplar);
            fetchCallnumber(field, exemplar);
            fetchLocation(field, exemplar);
            fetchAusleihindikator(field, exemplar);
        } else if ("984".equals(tag)) {
            fetchBarcode(field, exemplar);
        }
    }

    /**
     * Holt den Wert aus dem angegebenen Subfeld, macht trim(). 
     * 
     * @param field     MARC-Feld, in dem nach dem Subfeld gesucht wird
     * @param subfieldName      Name des gesuchten Subfeldes
     * @param subfieldDescription Feldbezeichnung für Fehlermeldungen
     * @return  gesuchter Wert
     * @throws MarcException    wenn Subfeld fehlt oder (bis auf Whitespace)
     *                          leer ist.
     */
    final static String requiredSubfield(
            final DataField field,
            char subfieldName,
            String subfieldDescription) {
        
        Subfield subfield = field.getSubfield(subfieldName);
        if (subfield == null) {
            throw new MarcException(
                    subfieldDescription + " expected, subfield "
                    + subfieldName + " is missing");
        }
        String value = subfield.getData();
        if (StringUtils.isBlank(value)) {
            throw new MarcException(
                    subfieldDescription + " expected, subfield "
                            + subfieldName + " is empty");
        }
        
        return value.trim();
    }
    
    /**
     * Holt den Wert aus dem angegebenen Subfeld, macht trim(), 
     * liefert "" statt null.
     * @param field     MARC-Feld, in dem nach dem Subfeld gesucht wird
     * @param subfieldName      Name des gesuchten Subfeldes
     * @return  gesuchter Wert
     */
    final static String optionalSubfield(final DataField field, char subfieldName) {
        Subfield subfield = field.getSubfield(subfieldName);
        if (subfield == null) {
            return "";
        }
        
        return StringUtils.defaultString(subfield.getData()).trim();
    }
    
    /**
     * Liest aus Subfeld b des MARC-Felds die EPN, schreibt sie in
     * das Exemplar und validiert sie.
     * @param field     MARC-Feld
     * @param exemplar das Exemplar
     * @throws MarcException    wenn die EPN ungültig ist
     */
    final static void fetchEPN(final DataField field, Exemplar exemplar) {
        exemplar.epn = requiredSubfield(field, 'b', "EPN");
        validateIsbn10(exemplar.epn);
    }
    
    
    /**
     * Liest aus Subfeld a des MARC-Felds den optionalen Barcode und
     * schreibt ihn das Exemplar.
     * @param field     MARC-Feld, das den Barcode enthalten kann
     * @param exemplar das Exemplar
     */
    final static void fetchBarcode(final DataField field, Exemplar exemplar) {
        Subfield barcodeField = field.getSubfield('a');
        if (barcodeField == null) {
            return;
        }
        String barcode = barcodeField.getData();
        if (StringUtils.isBlank(barcode)) {
            throw new MarcException(
                    "Barcode expected, subfield a is empty");
        }
        exemplar.barcode = barcode.trim();
    }
    
    /**
     * Liest aus Subfeld d des MARC-Felds die optionale Callnumber und
     * schreibt sie und die dazugehörige shelvingOrder in das Exemplar.
     * @param field     MARC-Feld, das die Callnumber enthalten kann
     * @param exemplar das Exemplar
     */
    final static void fetchCallnumber(final DataField field, Exemplar exemplar) {
        exemplar.callnumber = optionalSubfield(field, 'd');
        exemplar.shelvingOrder =
                CallNumber.getShelvingOrder(exemplar.callnumber);
    }
    
    /**
     * Liest aus Subfeld f des MARC-Felds die optionale Location und
     * schreibt sie in das Exemplar.
     * @param field     MARC-Feld, das die Location enthalten kann
     * @param exemplar das Exemplar
     */
    final static void fetchLocation(final DataField field, Exemplar exemplar) {
        String location = optionalSubfield(field, 'f');
        location = StringUtils.defaultString(
                convertLocation.get(location), location);
        exemplar.location = location;
    }

    /**
     * Return s if not empty, a single space otherwise.
     * @param s         zu bearbeitender String, darf null sein
     * @return  String, nicht null und nicht leer.
     */
    final static String empty2space(String s) {
        if (s == null | "".equals(s)) {
            return " ";
        }
        
        return s;
    }
    
    /**
     * Liest aus Subfeld e des MARC-Felds den optionale Ausleihindikator
     * und schreibt ihn in das Exemplar.
     * @param field     MARC-Feld, das den Ausleihindikator enthalten kann
     * @param exemplar das Exemplar
     */
    final static void fetchAusleihindikator(final DataField field, Exemplar exemplar) {
        exemplar.ausleihindikator = empty2space(
                optionalSubfield(field, 'e'));
    }

    /**
     * Return the data of the control field with number tag with whitespace
     * trimmed.
     * @param record    Where to search for the control field.
     * @param tag       The number of the control field.
     * @return  The data of the control field.
     * @throws MarcException    if the control field does not exist,
     *          is empty or contains only whitespace
     */
    final static String getRequiredControlField(Record record, String tag) {
        for (ControlField controlField : record.getControlFields()) {
            if (! tag.equals(controlField.getTag())) {
                continue;
            }
            
            String data = StringUtils.trimToEmpty(controlField.getData());
            
            if ("".equals(data)) {
                throw new MarcException("Control field " + tag + " is empty.");
            }
            
            return data;
        }
        
        throw new MarcException("Control field " + tag + " expected "
                + "but not found.");
    }
    
    /**
     * Return the data of the control field with number tag with
     * whitespace trimmed.
     * @param record    Where to search for the control field.
     * @param tag       The number of the control field.
     * @return  The data of the control field, or an empty String if
     *          the field does not exist or contains only whitespace
     */
    final static String getOptionalControlField(Record record, String tag) {
        for (ControlField controlField : record.getControlFields()) {
            if (! tag.equals(controlField.getTag())) {
                continue;
            }
            
            return StringUtils.trimToEmpty(controlField.getData());
        }
        
        return "";
    }
    
    /**
     * Liest aus Leader und Control Fields sowie dem Ausleihindikator
     * den item type und schreibt ihn in exemplar.
     * @param record    Record, der Leader und Control Fields enthält
     * @param exemplar  Exemplar mit Ausleihindikator und Ziel für Item Type.
     */
    final static void fetchItemType(Record record, Exemplar exemplar) {
        int itemType = getItemType(record, exemplar);
        exemplar.itemType = Integer.toString(itemType);
    }        

    /**
     * Liest aus Leader und Control Fields sowie dem Ausleihindikator
     * den item type und liefert seine id zurück.
     * @param record    Record, der Leader und Control Fields enthält
     * @param exemplar  Exemplar mit Ausleihindikator und Ziel für Item Type.
     * @return  item type id
     */
    final static int getItemType(Record record, Exemplar exemplar) {
        // Spec siehe http://www.loc.gov/marc/bibliographic/
        String leader = record.getLeader().toString();
        String typeOfRecord       = leader.substring(6, 7);
        String bibliographicLevel = leader.substring(7, 8);
        String field007 = getOptionalControlField(record, "007");
        String categoryOfMaterial  = empty2space(
                StringUtils.substring(field007, 0, 1));
        String materialDesignation = empty2space(
                StringUtils.substring(field007, 1, 2));
        
        if (typeOfRecord.equals("a") &&
                "mb".contains(bibliographicLevel)) {
            if ("ubc".contains(exemplar.ausleihindikator)) {
                return 53;      // BUA Buch ausleihbar
            }
            if ("sd".contains(exemplar.ausleihindikator)) {
                return 55;      // BUZ Buch nach Zustimmung ausleihbar
            }
            if ("ifgaoz".contains(exemplar.ausleihindikator)) {
                return 54;      // BUP Buch präsenz
            }
        }
        
        // eBook
        if (typeOfRecord.equals("m") &&
                bibliographicLevel.equals("m") &&
                categoryOfMaterial.equals("c") &&
                materialDesignation.equals("r")  ) {
            return 53;      // BUA Buch ausleihbar
        }
             
        // Zeitschrift
        if (typeOfRecord.equals("a") &&
                bibliographicLevel.equals("s")) {
            return 59;      // ZSP Zeitschrift präsenz
        }
             
        // eZeitschrift
        if (typeOfRecord.equals("m") &&
                bibliographicLevel.equals("s")) {
            return 53;      // BUA Buch ausleihbar
        }
             
        // Aufsatz
        if (typeOfRecord.equals("a") &&
                bibliographicLevel.equals("a")) {
            return 59;      // ZSP Zeitschrift präsenz
        }
        
        // eAufsatz
        if (bibliographicLevel.equals("a")) {
            return 53;      // BUA Buch ausleihbar
        }
        
        // Handschrift
        if ("dft".contains(typeOfRecord)) {
            return 54;      // BUP Buch präsenz
        }
        
        // Mikroform
        if (categoryOfMaterial.equals("h")) {
            return 60;      // MKP Mikroform präsenz
        }
        
        // Datenträger, Karte, Film, Tonträger, Spiel/Objekt
        
        if ("ubc".contains(exemplar.ausleihindikator)) {
            return 56;      // AVA AV ausleihbar
        }
        if ("sd".contains(exemplar.ausleihindikator)) {
            return 58;      // AVZ AV nach Zustimmung ausleihbar
        }

        // ifgaoz
        return 57;          // AVP AV präsenz
    }
    
    public final void setConverter(final CharConverter aConverter) {
        throw new RuntimeException(
                "setConverter(CharConverter) is not implemented");
    }

    public final CharConverter getConverter() {
        throw new RuntimeException("getConverter() is not implemented");
    }

    public void close() {
        // nothing to do
    }

    private static Writer utf8Writer(String filename) throws IOException {
        FileOutputStream stream = new FileOutputStream(filename);
        return new OutputStreamWriter(stream, "UTF8");
    }
    
    /**
     * Convertiert eine MARC21-Datei in eine MarcXML-Bulk-Import-SQL-Datei.
     * @param args      Pfad mit auf .mrc endendem Dateinamen der MARC21-Datei.
     * @throws IOException      Fehler beim Dateilesen oder -schreiben
     */
    public static void main(final String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            usageExit();
        }

        boolean ppn5 = false;
        if (args.length == 2) {
            if ("-5".equals(args[0])) {
                ppn5 = true;
            } else {
                usageExit();
            }
        }
        String infile = args[args.length-1];

        if (!infile.endsWith(MRCSUFFIX)) {
            System.err.println("Filename ending in .mrc expected, "
                    + "but found filename: " + infile);
            usageExit();
        }

        InputStream in = new FileInputStream(infile);
        MarcStreamReader reader = new MarcStreamReader(in);

        String filename = infile.substring(
                0, infile.length() - MRCSUFFIX.length()); 
        Writer bib      = utf8Writer(filename + "-bib.sql");
        Writer holdings = utf8Writer(filename + "-holdings.sql");
        Writer item     = utf8Writer(filename + "-item.sql");

        while (reader.hasNext()) {
            MarcWriter writer = new Marc21ToOleBulk(bib, holdings, item, ppn5);
            writer.write(reader.next());
            writer.close();
        }

        in.close();
        item.close();
        holdings.close();
        bib.close();
    }
}
