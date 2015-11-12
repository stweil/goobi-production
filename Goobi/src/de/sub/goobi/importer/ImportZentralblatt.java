package de.sub.goobi.importer;

/**
 * This file is part of the Goobi Application - a Workflow tool for the support of mass digitization.
 * 
 * Visit the websites for more information. 
 *     		- http://www.goobi.org
 *     		- https://github.com/goobi/goobi-production
 * 		    - http://gdz.sub.uni-goettingen.de
 * 			- http://www.intranda.com
 * 			- http://digiverso.com 
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Linking this library statically or dynamically with other modules is making a combined work based on this library. Thus, the terms and conditions
 * of the GNU General Public License cover the whole combination. As a special exception, the copyright holders of this library give you permission to
 * link this library with independent modules to produce an executable, regardless of the license terms of these independent modules, and to copy and
 * distribute the resulting executable under terms of your choice, provided that you also meet, for each linked independent module, the terms and
 * conditions of the license of that module. An independent module is a module which is not derived from or based on this library. If you modify this
 * library, you may extend this exception to your version of the library, but you are not obliged to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.XStream;
import de.sub.goobi.beans.Prozess;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.WrongImportFileException;

/**
 * Die Klasse Schritt ist ein Bean für einen einzelnen Schritt mit dessen Eigenschaften und erlaubt die Bearbeitung der Schrittdetails
 * 
 * @author Steffen Hankiewicz
 * @version 1.00 - 10.01.2005
 */
public class ImportZentralblatt {
	private static final Logger myLogger = Logger.getLogger(ImportZentralblatt.class);
	String Trennzeichen;
	private final Helper help;
	private Prefs myPrefs;

	/**
	 * Allgemeiner Konstruktor ()
	 */
	public ImportZentralblatt() {
		this.help = new Helper();
	}

	/**
	 * @throws IOException
	 * @throws WrongImportFileException
	 * @throws TypeNotAllowedForParentException
	 * @throws TypeNotAllowedAsChildException
	 * @throws MetadataTypeNotAllowedException
	 * @throws WriteException
	 */
	protected void Parsen(BufferedReader reader, Prozess inProzess) throws IOException, WrongImportFileException, TypeNotAllowedForParentException,
			TypeNotAllowedAsChildException, MetadataTypeNotAllowedException, WriteException {
		myLogger.debug("ParsenZentralblatt() - Start");
		this.myPrefs = inProzess.getRegelsatz().getPreferences();
		String prozessID = String.valueOf(inProzess.getId().intValue());
		String line;
		this.Trennzeichen = ":";
		boolean istAbsatz = false;
		boolean istErsterTitel = true;
		LinkedList<DocStruct> listArtikel = new LinkedList<DocStruct>();

		/*
		 * -------------------------------- Vorbereitung der Dokumentenstruktur --------------------------------
		 */
		DigitalDocument dd = new DigitalDocument();
		DocStructType dst = this.myPrefs.getDocStrctTypeByName("Periodical");
		DocStruct dsPeriodical = dd.createDocStruct(dst);
		dst = this.myPrefs.getDocStrctTypeByName("PeriodicalVolume");
		DocStruct dsPeriodicalVolume = dd.createDocStruct(dst);
		dsPeriodical.addChild(dsPeriodicalVolume);

		/*
		 * -------------------------------- alle Zeilen durchlaufen --------------------------------
		 */
		while ((line = reader.readLine()) != null) {
			// myLogger.debug(line);

			/*
			 * -------------------------------- wenn die Zeile leer ist, ist es das Ende eines Absatzes --------------------------------
			 */
			if (line.length() == 0) {
				istAbsatz = false;
				/* wenn die Zeile nicht leer ist, den Inhalt prüfen */
			} else {

				/* prüfen ob der String korrekte xml-Zeichen enthält */
				String xmlTauglich = xmlTauglichkeitPruefen(line);
				if (xmlTauglich.length() > 0) {
					throw new WrongImportFileException("Parsingfehler (nicht druckbares Zeichen) der Importdatei in der Zeile <br/>" + xmlTauglich);
				}

				/* wenn es gerade ein neuer Absatz ist, diesen als neuen Artikel in die Liste übernehmen */
				if (!istAbsatz) {
					DocStructType dstLocal = this.myPrefs.getDocStrctTypeByName("Article");
					DocStruct ds = dd.createDocStruct(dstLocal);
					listArtikel.add(ds);
					// myLogger.debug("---------------          neuer Artikel          ----------------");
					istAbsatz = true;
					istErsterTitel = true;
				}

				/* Position des Trennzeichens ermitteln */
				int posTrennzeichen = line.indexOf(this.Trennzeichen);
				/* wenn kein Trennzeichen vorhanden, Parsingfehler */
				if (posTrennzeichen == -1) {
					myLogger.error("Import() - Parsingfehler (kein Doppelpunkt) der Importdatei in der Zeile <br/>" + HtmlTagsMaskieren(line));
					throw new WrongImportFileException("Parsingfehler (kein Doppelpunkt) der Importdatei in der Zeile <br/>"
							+ HtmlTagsMaskieren(line));
				} else {
					String myLeft = line.substring(0, posTrennzeichen).trim();
					String myRight = line.substring(posTrennzeichen + 1, line.length()).trim();
					ParsenArtikel(listArtikel.getLast(), myLeft, myRight, istErsterTitel);

					/* wenn es ein Titel war, ist der nächste nicht mehr der erste Titel */
					if (myLeft.equals("TI")) {
						istErsterTitel = false;
					}

					/* wenn es gerade der Zeitschriftenname ist, die Zeitschrift benennen */
					if (myLeft.equals("J")) {
						ParsenAllgemein(dsPeriodical, myLeft, myRight);
					}

					/* wenn es gerade eine Jahresangabe ist, dann für den aktuellen Band */
					if (myLeft.equals("Y")) {
						ParsenAllgemein(dsPeriodicalVolume, myLeft, myRight);
					}

					/* wenn es gerade eine Jahresangabe ist, dann für den aktuellen Band */
					if (myLeft.equals("V")) {
						ParsenAllgemein(dsPeriodicalVolume, myLeft, myRight);
					}

					/*
					 * wenn es gerade die Heftnummer ist, dann jetzt dem richtigen Heft zuordnen und dieses ggf. noch vorher anlegen
					 */
					if (myLeft.equals("I")) {
						DocStruct dsPeriodicalIssue = ParsenHeftzuordnung(dsPeriodicalVolume, myRight, dd);
						dsPeriodicalIssue.addChild(listArtikel.getLast());
					}
				}
			}
		}

		/*
		 * -------------------------------- physischer Baum (Seiten) --------------------------------
		 */
		dst = this.myPrefs.getDocStrctTypeByName("BoundBook");
		DocStruct dsBoundBook = dd.createDocStruct(dst);

		/*
		 * -------------------------------- jetzt die Gesamtstruktur bauen und in xml schreiben --------------------------------
		 */
		// DigitalDocument dd = new DigitalDocument();
		dd.setLogicalDocStruct(dsPeriodical);
		dd.setPhysicalDocStruct(dsBoundBook);
		try {
			Fileformat gdzfile = new XStream(this.myPrefs);
			gdzfile.setDigitalDocument(dd);

			/*
			 * -------------------------------- Datei am richtigen Ort speichern --------------------------------
			 */
			gdzfile.write(this.help.getGoobiDataDirectory() + prozessID + File.separator + "meta.xml");
		} catch (PreferencesException e) {
			Helper.setFehlerMeldung("Import aborted: ", e.getMessage());
			myLogger.error(e);
		}
		myLogger.debug("ParsenZentralblatt() - Ende");
	}

	private String xmlTauglichkeitPruefen(String text) {
		int laenge = text.length();
		String rueckgabe = "";
		for (int i = 0; i < laenge; i++) {
			char c = text.charAt(i);

			if (!isValidXMLChar(c)) {
				rueckgabe = HtmlTagsMaskieren(text.substring(0, i)) + "<span class=\"parsingfehler\">" + c + "</span>";
				if (laenge > i) {
					rueckgabe += HtmlTagsMaskieren(text.substring(i + 1, laenge));
				}
				break;
			}
		}
		return rueckgabe;
	}

	private static final boolean isValidXMLChar(char c) {
		switch (c) {
		case 0x9:
		case 0xa: // line feed, '\n'
		case 0xd: // carriage return, '\r'
			return true;
		default:
			return ((0x20 <= c && c <= 0xd7ff) || (0xe000 <= c && c <= 0xfffd));
		}
	}

	private String HtmlTagsMaskieren(String in) {
		return (in.replaceAll("<", "&lt;")).replaceAll(">", "&gt");
	}

	/**
	 * Funktion für das Ermitteln des richtigen Heftes für einen Artikel Liegt das Heft noch nicht in dem Volume vor, wird es angelegt. Als Rückgabe
	 * kommt das Heft als DocStruct
	 * 
	 * @param dsPeriodicalVolume
	 * @param myRight
	 * @return
	 * @throws TypeNotAllowedForParentException
	 * @throws MetadataTypeNotAllowedException
	 * @throws TypeNotAllowedAsChildException
	 */
	private DocStruct ParsenHeftzuordnung(DocStruct dsPeriodicalVolume, String myRight, DigitalDocument inDigitalDocument)
			throws TypeNotAllowedForParentException, MetadataTypeNotAllowedException, TypeNotAllowedAsChildException {
		DocStructType dst;
		MetadataType mdt = this.myPrefs.getMetadataTypeByName("CurrentNo");
		DocStruct dsPeriodicalIssue = null;
		/* erstmal prüfen, ob das Heft schon existiert */
		List<DocStruct> myList = dsPeriodicalVolume.getAllChildrenByTypeAndMetadataType("PeriodicalIssue", "CurrentNo");
		if (myList != null && myList.size() != 0) {
			for (DocStruct dsIntern : myList) {
				// myLogger.debug(dsIntern.getAllMetadataByType(mdt).getFirst());
				Metadata myMD1 = dsIntern.getAllMetadataByType(mdt).get(0);
				// myLogger.debug("und der Wert ist: " + myMD1.getValue());
				if (myMD1.getValue().equals(myRight)) {
					dsPeriodicalIssue = dsIntern;
				}
			}
		}
		/* wenn das Heft nicht gefunden werden konnte, jetzt anlegen */
		if (dsPeriodicalIssue == null) {
			dst = this.myPrefs.getDocStrctTypeByName("PeriodicalIssue");
			dsPeriodicalIssue = inDigitalDocument.createDocStruct(dst);
			Metadata myMD = new Metadata(mdt);
			// myMD.setType(mdt);
			myMD.setValue(myRight);
			dsPeriodicalIssue.addMetadata(myMD);
			dsPeriodicalVolume.addChild(dsPeriodicalIssue);
		}
		return dsPeriodicalIssue;
	}

	/**
	 * @throws WrongImportFileException
	 * @throws IOException
	 * @throws WrongImportFileException
	 * @throws TypeNotAllowedForParentException
	 * @throws TypeNotAllowedForParentException
	 * @throws MetadataTypeNotAllowedException
	 */
	private void ParsenAllgemein(DocStruct inStruct, String myLeft, String myRight) throws WrongImportFileException,
			TypeNotAllowedForParentException, MetadataTypeNotAllowedException {

		// myLogger.debug(myLeft);
		// myLogger.debug(myRight);
		// myLogger.debug("---");
		Metadata md;
		MetadataType mdt;

		// J: Zeitschrift
		// V: Band
		// I: Heft
		// Y: Jahrgang

		/*
		 * -------------------------------- Zeitschriftenname --------------------------------
		 */
		if (myLeft.equals("J")) {
			mdt = this.myPrefs.getMetadataTypeByName("TitleDocMain");
			List<? extends ugh.dl.Metadata> myList = inStruct.getAllMetadataByType(mdt);
			/* wenn noch kein Zeitschrifenname vergeben wurde, dann jetzt */
			if (myList.size() == 0) {
				md = new Metadata(mdt);
				// md.setType(mdt);
				md.setValue(myRight);
				inStruct.addMetadata(md);
			} else {
				/* wurde schon ein Zeitschriftenname vergeben, prüfen, ob dieser genauso lautet */
				md = myList.get(0);
				if (!myRight.equals(md.getValue())) {
					throw new WrongImportFileException("Parsingfehler: verschiedene Zeitschriftennamen in der Datei ('" + md.getValue() + "' & '"
							+ myRight + "')");
				}
			}
			return;
		}

		/*
		 * -------------------------------- Jahrgang --------------------------------
		 */
		if (myLeft.equals("Y")) {
			mdt = this.myPrefs.getMetadataTypeByName("PublicationYear");
			List<? extends ugh.dl.Metadata> myList = inStruct.getAllMetadataByType(mdt);

			/* wenn noch kein Zeitschrifenname vergeben wurde, dann jetzt */
			if (myList.size() == 0) {
				md = new Metadata(mdt);
				// md.setType(mdt);
				md.setValue(myRight);
				inStruct.addMetadata(md);
			} else {

				/* wurde schon ein Zeitschriftenname vergeben, prüfen, ob dieser genauso lautet */
				md = myList.get(0);

				/*
				 * -------------------------------- da Frau Jansch ständig Importprobleme mit jahrübergreifenden Bänden hat, jetzt mal auskommentiert
				 * --------------------------------
				 */
				// if (!myRight.equals(md.getValue()))
				// throw new WrongImportFileException("Parsingfehler: verschiedene Jahresangaben in der Datei ('"
				// + md.getValue() + "' & '" + myRight + "')");
			}
			return;
		}

		/*
		 * -------------------------------- Bandnummer --------------------------------
		 */
		if (myLeft.equals("V")) {
			mdt = this.myPrefs.getMetadataTypeByName("CurrentNo");
			List<? extends ugh.dl.Metadata> myList = inStruct.getAllMetadataByType(mdt);

			/* wenn noch keine Bandnummer vergeben wurde, dann jetzt */
			if (myList.size() == 0) {
				md = new Metadata(mdt);
				md.setValue(myRight);
				inStruct.addMetadata(md);
			} else {

				/* wurde schon eine Bandnummer vergeben, prüfen, ob dieser genauso lautet */
				md = myList.get(0);
				if (!myRight.equals(md.getValue())) {
					throw new WrongImportFileException("Parsingfehler: verschiedene Bandangaben in der Datei ('" + md.getValue() + "' & '" + myRight
							+ "')");
				}
			}
			return;
		}
	}

	/**
	 * @throws MetadataTypeNotAllowedException
	 * @throws WrongImportFileException
	 * @throws IOException
	 * @throws WrongImportFileException
	 * @throws TypeNotAllowedForParentException
	 */
	private void ParsenArtikel(DocStruct inStruct, String myLeft, String myRight, boolean istErsterTitel) throws MetadataTypeNotAllowedException,
			WrongImportFileException {
		// myLogger.debug(myLeft);
		// myLogger.debug(myRight);
		// myLogger.debug("---");
		Metadata md;
		MetadataType mdt;

		// J: Zeitschrift
		// V: Band
		// I: Heft
		// Y: Jahrgang
		// SO: Quelle (fuer uns intern)
		// AR: Author (Referenz)
		// BR: Biographische Referenz
		// AB: Abstract-Review
		// DE: Vorlaeufige AN-Nummer (eher fuer uns intern)
		// SI: Quellenangabe für Rezension im Zentralblatt
		//		

		/*
		 * -------------------------------- erledigt
		 * 
		 * TI: Titel AU: Autor LA: Sprache NH: Namensvariationen CC: MSC 2000 KW: Keywords AN: Zbl und/oder JFM Nummer P: Seiten
		 * 
		 * --------------------------------
		 */

		/*
		 * -------------------------------- Titel --------------------------------
		 */
		if (myLeft.equals("TI")) {
			if (istErsterTitel) {
				mdt = this.myPrefs.getMetadataTypeByName("TitleDocMain");
			} else {
				mdt = this.myPrefs.getMetadataTypeByName("MainTitleTranslated");
			}
			md = new Metadata(mdt);
			md.setValue(myRight);
			inStruct.addMetadata(md);
			return;
		}

		/*
		 * -------------------------------- Sprache --------------------------------
		 */
		if (myLeft.equals("LA")) {
			mdt = this.myPrefs.getMetadataTypeByName("DocLanguage");
			md = new Metadata(mdt);
			md.setValue(myRight.toLowerCase());
			inStruct.addMetadata(md);
			return;
		}

		/*
		 * -------------------------------- ZBLIdentifier --------------------------------
		 */
		if (myLeft.equals("AN")) {
			mdt = this.myPrefs.getMetadataTypeByName("ZBLIdentifier");
			md = new Metadata(mdt);
			md.setValue(myRight);
			inStruct.addMetadata(md);
			return;
		}

		/*
		 * -------------------------------- ZBLPageNumber --------------------------------
		 */
		if (myLeft.equals("P")) {
			mdt = this.myPrefs.getMetadataTypeByName("ZBLPageNumber");
			md = new Metadata(mdt);
			md.setValue(myRight);
			inStruct.addMetadata(md);
			return;
		}

		/*
		 * -------------------------------- ZBLSource --------------------------------
		 */
		if (myLeft.equals("SO")) {
			mdt = this.myPrefs.getMetadataTypeByName("ZBLSource");
			md = new Metadata(mdt);
			md.setValue(myRight);
			inStruct.addMetadata(md);
			return;
		}

		/*
		 * -------------------------------- ZBLAbstract --------------------------------
		 */
		if (myLeft.equals("AB")) {
			mdt = this.myPrefs.getMetadataTypeByName("ZBLAbstract");
			md = new Metadata(mdt);
			md.setValue(myRight);
			inStruct.addMetadata(md);
			return;
		}

		/*
		 * -------------------------------- ZBLReviewAuthor --------------------------------
		 */
		if (myLeft.equals("RV")) {
			mdt = this.myPrefs.getMetadataTypeByName("ZBLReviewAuthor");
			md = new Metadata(mdt);
			md.setValue(myRight);
			inStruct.addMetadata(md);
			return;
		}

		/*
		 * -------------------------------- ZBLCita --------------------------------
		 */
		if (myLeft.equals("CI")) {
			mdt = this.myPrefs.getMetadataTypeByName("ZBLCita");
			md = new Metadata(mdt);
			md.setValue(myRight);
			inStruct.addMetadata(md);
			return;
		}

		/*
		 * -------------------------------- ZBLTempID --------------------------------
		 */
		if (myLeft.equals("DE")) {
			mdt = this.myPrefs.getMetadataTypeByName("ZBLTempID");
			md = new Metadata(mdt);
			md.setValue(myRight);
			inStruct.addMetadata(md);
			return;
		}

		/*
		 * -------------------------------- ZBLReviewLink --------------------------------
		 */
		if (myLeft.equals("SI")) {
			mdt = this.myPrefs.getMetadataTypeByName("ZBLReviewLink");
			md = new Metadata(mdt);
			md.setValue(myRight);
			inStruct.addMetadata(md);
			return;
		}

		/*
		 * -------------------------------- ZBLIntern --------------------------------
		 */
		if (myLeft.equals("XX")) {
			mdt = this.myPrefs.getMetadataTypeByName("ZBLIntern");
			md = new Metadata(mdt);
			md.setValue(myRight);
			inStruct.addMetadata(md);
			return;
		}

		/*
		 * -------------------------------- Keywords --------------------------------
		 */
		if (myLeft.equals("KW")) {
			StringTokenizer tokenizer = new StringTokenizer(myRight, ";");
			while (tokenizer.hasMoreTokens()) {
				md = new Metadata(this.myPrefs.getMetadataTypeByName("Keyword"));
				String myTok = tokenizer.nextToken();
				md.setValue(myTok.trim());
				inStruct.addMetadata(md);
			}
			return;
		}

		/*
		 * -------------------------------- Autoren als Personen --------------------------------
		 */
		if (myLeft.equals("AU")) {
			StringTokenizer tokenizer = new StringTokenizer(myRight, ";");
			while (tokenizer.hasMoreTokens()) {
				Person p = new Person(this.myPrefs.getMetadataTypeByName("ZBLAuthor"));
				String myTok = tokenizer.nextToken();

				if (myTok.indexOf(",") == -1) {
					throw new WrongImportFileException("Parsingfehler: Vorname nicht mit Komma vom Nachnamen getrennt ('" + myTok + "')");
				}

				p.setLastname(myTok.substring(0, myTok.indexOf(",")).trim());
				p.setFirstname(myTok.substring(myTok.indexOf(",") + 1, myTok.length()).trim());
				p.setRole("ZBLAuthor");
				inStruct.addPerson(p);
			}
			return;
		}

		/*
		 * -------------------------------- AutorVariationen als Personen --------------------------------
		 */
		if (myLeft.equals("NH")) {
			StringTokenizer tokenizer = new StringTokenizer(myRight, ";");
			while (tokenizer.hasMoreTokens()) {
				Person p = new Person(this.myPrefs.getMetadataTypeByName("AuthorVariation"));
				String myTok = tokenizer.nextToken();

				if (myTok.indexOf(",") == -1) {
					throw new WrongImportFileException("Parsingfehler: Vorname nicht mit Komma vom Nachnamen getrennt ('" + myTok + "')");
				}

				p.setLastname(myTok.substring(0, myTok.indexOf(",")).trim());
				p.setFirstname(myTok.substring(myTok.indexOf(",") + 1, myTok.length()).trim());
				p.setRole("AuthorVariation");
				inStruct.addPerson(p);
			}
			return;
		}

		/*
		 * -------------------------------- MSC 2000 - ClassificationMSC --------------------------------
		 */
		if (myLeft.equals("CC")) {
			StringTokenizer tokenizer = new StringTokenizer(myRight);
			while (tokenizer.hasMoreTokens()) {
				md = new Metadata(this.myPrefs.getMetadataTypeByName("ClassificationMSC"));
				String myTok = tokenizer.nextToken();
				md.setValue(myTok.trim());
				inStruct.addMetadata(md);
			}
			return;
		}
	}

}
