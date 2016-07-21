/**
 * This file is part of the Goobi Application - a Workflow tool for the support
 * of mass digitization.
 * 
 * (c) 2014 Goobi. Digitalisieren im Verein e. V. <contact@goobi.org>
 * 
 * Visit the websites for more information.
 *     		- http://www.kitodo.org/en/
 *     		- https://github.com/goobi
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU General Public License cover the whole combination. As a special
 * exception, the copyright holders of this library give you permission to link
 * this library with independent modules to produce an executable, regardless of
 * the license terms of these independent modules, and to copy and distribute
 * the resulting executable under terms of your choice, provided that you also
 * meet, for each linked independent module, the terms and conditions of the
 * license of that module. An independent module is a module which is not
 * derived from or based on this library. If you modify this library, you may
 * extend this exception to your version of the library, but you are not obliged
 * to do so. If you do not wish to do so, delete this exception statement from
 * your version.
 */
package de.sub.goobi.metadaten.copier;

import java.sql.SQLException;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import de.sub.goobi.beans.Prozess;
import de.sub.goobi.persistence.apache.MySQLHelper;
import de.sub.goobi.persistence.apache.ProcessObject;

/**
 * A CopierData object contains all the data the data copier has access to. It
 * has been implemented as an own bean class to allow to easily add variables
 * later without needing to extend many interfaces.
 * 
 * @author Matthias Ronge &lt;matthias.ronge@zeutschel.de&gt;
 */
public class CopierData {

	/**
	 * A metadata selector relative to which the data shall be read during
	 * copying.
	 */
	private final MetadataSelector destination;

	/**
	 * The workspace file to modify
	 */
	private final Fileformat fileformat;

	/**
	 * The Goobi process corresponding to the workspace file
	 */
	private final Object process;

	/**
	 * Creates a new CopierData bean with an additional destination metadata
	 * selector.
	 * 
	 * @param data
	 *            data bean without or with destination metadata selector
	 * @param destination
	 *            destination metadata selector to use
	 */
	public CopierData(CopierData data, MetadataSelector destination) {
		this.fileformat = data.fileformat;
		this.process = data.process;
		this.destination = destination;
	}

	/**
	 * Creates a new CopierData bean.
	 * 
	 * @param fileformat
	 *            the document to modify
	 * @param process
	 *            the related goobi process
	 */
	public CopierData(Fileformat fileformat, Object process) {
		this.fileformat = fileformat;
		this.process = process;
		this.destination = null;
	}

	/**
	 * Returns the destination metadata selector relative to which the data
	 * shall be read during copying.
	 * 
	 * @return the destination metadata selector
	 */
	public MetadataSelector getDestination() {
		return destination;
	}

	/**
	 * Returns the digital document contained in the fileformat passed-in in the
	 * constructor.
	 * 
	 * @return the digital document
	 */
	DigitalDocument getDigitalDocument() {
		try {
			return fileformat.getDigitalDocument();
		} catch (PreferencesException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * Returns the top-level element of the logical document structure tree.
	 * 
	 * @return the logical document structure
	 */
	public DocStruct getLogicalDocStruct() {
		return getDigitalDocument().getLogicalDocStruct();
	}

	/**
	 * Returns the ruleset to be used with the fileformat.
	 * 
	 * @return the required ruleset.
	 */
	public Prefs getPreferences() throws SQLException {
		if (process instanceof ProcessObject) {
			return MySQLHelper.getRulesetForId(((ProcessObject) process).getRulesetId()).getPreferences();
		} else {
			return ((Prozess) process).getRegelsatz().getPreferences();
		}
	}

	/**
	 * Returns the process title.
	 * 
	 * @return the process title
	 */
	public String getProcessTitle() {
		if (process instanceof Prozess) {
			return ((Prozess) process).getTitel();
		} else if (process instanceof ProcessObject) {
			return ((ProcessObject) process).getTitle();
		} else {
			return String.valueOf(process);
		}
	}
	
	/**
	 * Returns a string that textually represents this bean.
	 * 
	 * @return a string representation of this object
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "{fileformat: " + fileformat.toString() + ", process: " + getProcessTitle() + '}';
	}
}
