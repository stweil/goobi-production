/*
 * This file is part of the Goobi Application - a Workflow tool for the support of
 * mass digitization.
 *
 * Visit the websites for more information.
 *     - http://gdz.sub.uni-goettingen.de
 *     - http://www.goobi.org
 *     - http://launchpad.net/goobi-production
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.sub.goobi.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import de.sub.goobi.beans.Prozess;

public class DigitalCollections {

	@SuppressWarnings("unchecked")
	public static List<String> possibleDigitalCollectionsForProcess(
			Prozess process) throws JDOMException, IOException {
		
		List<String> result = new ArrayList<String>();
		String filename = ConfigMain.getParameter("KonfigurationVerzeichnis") + "goobi_digitalCollections.xml";
		if (!(new File(filename).exists())) {
			throw new FileNotFoundException("File not found: " + filename);
		}
		
		/* Datei einlesen und Root ermitteln */
		SAXBuilder builder = new SAXBuilder();
		Document doc = builder.build(new File(filename));
		Element root = doc.getRootElement();
		/* alle Projekte durchlaufen */
		List<Element> projekte = root.getChildren();
		for (Iterator<Element> iter = projekte.iterator(); iter.hasNext();) {
			Element projekt = iter.next();
			List<Element> projektnamen = projekt.getChildren("name");
			for (Iterator<Element> iterator = projektnamen.iterator(); iterator.hasNext();) {
				Element projektname = iterator.next();

				/*
				 * wenn der Projektname aufgeführt wird, dann alle Digitalen Collectionen in die Liste
				 */
				if (projektname.getText().equalsIgnoreCase(process.getProjekt().getTitel())) {
					List<Element> myCols = projekt.getChildren("DigitalCollection");
					for (Iterator<Element> it2 = myCols.iterator(); it2.hasNext();) {
						Element col = it2.next();
						result.add(col.getText());
					}
				}
			}
		}
		// If result is empty, get „default“
		if (result.size() == 0) {
			List<Element> primaryChildrenIterator = root.getChildren();
			for (Iterator<Element> iter = primaryChildrenIterator.iterator(); iter.hasNext();) {
				Element child = iter.next();
				if (child.getName().equals("default")) {
					List<Element> myCols = child.getChildren("DigitalCollection");
					for (Iterator<Element> it2 = myCols.iterator(); it2.hasNext();) {
						Element col = it2.next();
						result.add(col.getText());
					}
				}
			}
		}
		return result;
	}
}
