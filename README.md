# OLE-admin

Code and example files for converting MARC21 bib, holdings and item data from GBV's union catalogue
into data suitable for importing into Kuali OLE using LOAD DATA INFILE.

There is `import.mrc` as an example in the example directory.  Convert it this way:

    java -jar Marc21ToOleBulk-1.0.1.jar import.mrc

This produces `import-bib.sql`, `import-holdings.sql` and `import-item.sql`.  See
https://github.com/gbv/OLE-admin/blob/master/src/main/resources/application.properties
or invoke

    java -jar Marc21ToOleBulk-1.0.1.jar

for the LOAD DATA INFILE sql code that imports the data into the database.
