/**
 * This file is part of the Goobi Application - a Workflow tool for the support
 * of mass digitization.
 * 
 * (c) 2014 Goobi. Digitalisieren im Verein e.V. <contact@goobi.org>
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
package org.goobi.production.constants;

/**
 * These constants define configuration parameters usable in the configuration
 * file.
 * 
 * TODO: Make all string literals throughout the code constants here.
 * 
 * @author Matthias Ronge &lt;matthias.ronge@zeutschel.de&gt;
 */
public class Parameters {
	/**
	 * Content to put in the URI field when adding a new metadata element of
	 * type person. This should usually be your preferred norm data file’s URI
	 * prefix as to the user doesn’t have to enter it over and over again.
	 * 
	 * Example: authority.default=http\://d-nb.info/gnd/
	 */
	public static final String AUTHORITY_DEFAULT = "authority.default";

	/**
	 * Which authority identifier to use for a given URI prefix.
	 * 
	 * Example: authority.http\://d-nb.info/gnd/.id=gnd
	 */
	public static final String AUTHORITY_ID_FROM_URI = "authority.{0}.id";

	/**
	 * Integer, limits the number of batches showing on the page “Batches”.
	 * Defaults to -1 which disables this functionality. If set, only the
	 * limited number of batches will be shown, the other batches will be
	 * present but hidden and thus cannot be modified and not even be deleted.
	 */
	public static final String BATCH_DISPLAY_LIMIT = "batchMaxSize";

	/**
	 * Milliseconds. Indicates the maximum duration an interaction with a
	 * library catalogue may take. Defaults to 30 minutes.
	 */
	public static final String CATALOGUE_TIMEOUT = "catalogue.timeout";

	/**
	 * Points to a folder on the file system that contains Production
	 * configuration files.
	 */
	public static final String CONFIG_DIR = "KonfigurationVerzeichnis";

	/**
	 * Whether during an export to the DMS the images will be copied. Defaults
	 * to true.
	 */
	public static final String EXPORT_WITH_IMAGES = "automaticExportWithImages";

	/**
	 * Integer. Number of hits to show per page on the hitlist when multiple
	 * hits were found on a catalogue search.
	 */
	public static final String HITLIST_PAGE_SIZE = "catalogue.hitlist.pageSize";

	/**
	 * Long. Number of pages per process below which the features in the
	 * granualarity dialog shall be locked.
	 */
	public static final String MINIMAL_NUMBER_OF_PAGES = "numberOfPages.minimum";

	/**
	 * Comma-separated list of Strings which may be enclosed in double quotes.
	 * Separators available for double page pagination modes.
	 */
	public static final String PAGE_SEPARATORS = "pageSeparators";

	/**
	 * Points to a folder on the file system that contains Production plug-in
	 * jars. In the folder, there must be subfolders named as defined in enum
	 * PluginType (currently: “import”, “step”, “validation”, “command” and
	 * “opac”) in which the plug-in jars must be stored.
	 * 
	 * <p>
	 * Must be terminated by the file separator.
	 * </p>
	 * 
	 * @see org.goobi.production.enums.PluginType
	 */
	// TODO: Some of the old code doesn’t yet use
	// org.apache.commons.io.FilenameUtils for path management which causes
	// paths not ending in the file separator not to work. Use the library
	// for any path handling. It does it less error prone.
	public static final String PLUGIN_FOLDER = "pluginFolder";

	/**
	 * Points to a folder on the file system that plug-ins may use to write
	 * temporary files.
	 */
	public static final String PLUGIN_TEMP_DIR = "debugFolder";

	/**
	 * Boolean. Set to true to enable the feature of automatic meta data
	 * inheritance and enrichment. If this is enabled, all meta data elements
	 * from a higher level of the logical document structure are automatically
	 * inherited and lower levels are enriched with them upon process creation,
	 * given they have the same meta data type addable. Defaults to false.
	 */
	public static final String USE_METADATA_ENRICHMENT = "useMetadataEnrichment";
}
