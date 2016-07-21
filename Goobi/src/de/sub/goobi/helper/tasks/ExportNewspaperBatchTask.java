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
package de.sub.goobi.helper.tasks;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.goobi.production.constants.Parameters;
import org.hibernate.HibernateException;
import org.joda.time.LocalDate;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.fileformats.mets.MetsMods;
import ugh.fileformats.mets.MetsModsImportExport;
import de.sub.goobi.beans.Batch;
import de.sub.goobi.beans.Projekt;
import de.sub.goobi.beans.Prozess;
import de.sub.goobi.config.ConfigMain;
import de.sub.goobi.export.dms.ExportDms;
import de.sub.goobi.forms.LoginForm;
import de.sub.goobi.helper.ArrayListMap;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.BatchDAO;

public class ExportNewspaperBatchTask extends EmptyTask implements INameableTask {
	private static final Logger logger = Logger.getLogger(ExportNewspaperBatchTask.class);

	private static final double GAUGE_INCREMENT_PER_ACTION = 100 / 3d;

	/**
	 * Name of the structural element used to represent the day of month in the
	 * anchor structure of the newspaper.
	 */
	private final String dayLevelName;

	/**
	 * Name of the structural element used to represent the issue in the anchor
	 * structure of the newspaper.
	 */
	private final String issueLevelName;

	/**
	 * Name of the structural element used to represent the month in the anchor
	 * structure of the newspaper.
	 */
	private final String monthLevelName;

	/**
	 * Name of the structural element used to represent the year in the anchor
	 * structure of the newspaper.
	 */
	private final String yearLevelName;

	/**
	 * The field batch holds the batch whose processes are to export.
	 */
	private Batch batch;

	/**
	 * The field aggregation holds a 4-dimensional list (hierarchy: year, month,
	 * day, issue) into which the issues are aggregated.
	 */
	private final ArrayListMap<org.joda.time.LocalDate, String> aggregation;

	/**
	 * The field action holds the number of the action the task currently is in.
	 * Valid values range from 1 to 2. This is to start up at the right position
	 * after an interruption again.
	 */
	private int action;

	/**
	 * The field processesIterator holds an iterator object to walk though the
	 * processes of the batch. The processes in a batch are a Set
	 * implementation, so we use an iterator to walk through and do not use an
	 * index.
	 */
	private Iterator<Prozess> processesIterator;

	/**
	 * The field dividend holds the number of processes that have been processed
	 * in this action. The fields dividend and divisor are used to display a
	 * progress bar.
	 */
	private int dividend;

	/**
	 * The field dividend holds the number of processes to process in each
	 * action. The fields dividend and divisor are used to display a progress
	 * bar.
	 */
	private final double divisor;

	private final HashMap<Integer, String> collectedYears;
	
	/**
	 * The field batchId holds the ID number of the batch whose processes are to
	 * export.
	 */
	private Integer batchId;

	/**
	 * Constructor to create an ExportNewspaperBatchTask.
	 * 
	 * @param batch
	 *            batch to export
	 * @throws HibernateException
	 *             if the batch isn’t attached to a Hibernate session and cannot
	 *             be reattached either
	 * @throws PreferencesException
	 *             if the no node corresponding to the file format is available
	 *             in the rule set configured
	 * @throws ReadException
	 *             if the meta data file cannot be read
	 * @throws SwapException
	 *             if an error occurs while the process is swapped back in
	 * @throws DAOException
	 *             if an error occurs while saving the fact that the process has
	 *             been swapped back in to the database
	 * @throws IOException
	 *             if creating the process directory or reading the meta data
	 *             file fails
	 * @throws InterruptedException
	 *             if the current thread is interrupted by another thread while
	 *             it is waiting for the shell script to create the directory to
	 *             finish
	 */
	public ExportNewspaperBatchTask(Batch batch) throws HibernateException, PreferencesException, ReadException, SwapException,
			DAOException, IOException, InterruptedException {
		super(batch.getLabel());
		batchId = batch.getId();
		action = 1;
		aggregation = new ArrayListMap<LocalDate, String>();
		collectedYears = new HashMap<Integer, String>();
		dividend = 0;
		divisor = batch.getProcesses().size() / GAUGE_INCREMENT_PER_ACTION;
		DocStruct dsNewspaper = batch.getProcesses().iterator().next().getDigitalDocument().getLogicalDocStruct();
		DocStruct dsYear = dsNewspaper.getAllChildren().get(0);
		yearLevelName = dsYear.getType().getName();
		DocStruct dsMonth = dsYear.getAllChildren().get(0);
		monthLevelName = dsMonth.getType().getName();
		DocStruct dsDay = dsMonth.getAllChildren().get(0);
		dayLevelName = dsDay.getType().getName();
		DocStruct dsIssue = dsDay.getAllChildren().get(0);
		issueLevelName = dsIssue.getType().getName();
	}

	/**
	 * The copy constructor creates a new thread from a given one. This is
	 * required to call the copy constructor of the parent.
	 * 
	 * @param master
	 *            copy master
	 */
	public ExportNewspaperBatchTask(ExportNewspaperBatchTask master) {
		super(master);
		batch = master.batch;
		action = master.action;
		aggregation = master.aggregation;
		collectedYears = master.collectedYears;
		processesIterator = master.processesIterator;
		dividend = master.dividend;
		divisor = master.divisor;
		dayLevelName = master.dayLevelName;
		issueLevelName = master.issueLevelName;
		monthLevelName = master.monthLevelName;
		yearLevelName = master.yearLevelName;
	}

	/**
	 * The function run() is the main function of this task (which is a thread).
	 * It will aggregate the data from all processes and then export all
	 * processes with the recombined data. The statusProgress variable is being
	 * updated to show the operator how far the task has proceeded.
	 * 
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		Prozess process = null;
		try {
			if(processesIterator == null){
				batch = BatchDAO.read(batchId);
				processesIterator = batch.getProcesses().iterator();
			}
			if (action == 1) {
				while (processesIterator.hasNext()) {
					if (isInterrupted()) {
						return;
					}
					process = processesIterator.next();
					Integer processesYear = Integer.valueOf(getYear(process.getDigitalDocument()));
					if (!collectedYears.containsKey(processesYear)) {
						collectedYears.put(processesYear, getMetsYearAnchorPointerURL(process));
					}
					aggregation.addAll(getIssueDates(process.getDigitalDocument()), getMetsPointerURL(process));
					setProgress(++dividend / divisor);
				}
				action = 2;
				processesIterator = batch.getProcesses().iterator();
				dividend = 0;
			}

			if (action == 2) {
				while (processesIterator.hasNext()) {
					if (isInterrupted()) {
						return;
					}
					MetsMods extendedData = buildExportableMetsMods(process = processesIterator.next(),
							collectedYears, aggregation);
					setProgress(GAUGE_INCREMENT_PER_ACTION + (++dividend / divisor));

					new ExportDms(ConfigMain.getBooleanParameter(Parameters.EXPORT_WITH_IMAGES, true)).startExport(
							process, LoginForm.getCurrentUserHomeDir(), extendedData.getDigitalDocument());
					setProgress(GAUGE_INCREMENT_PER_ACTION + (++dividend / divisor));
				}
			}
		} catch (Exception e) { // PreferencesException, ReadException, SwapException, DAOException, IOException, InterruptedException and some runtime exceptions
			String message = e.getClass().getSimpleName() + " while " + (action == 1 ? "examining " : "exporting ")
					+ (process != null ? process.getTitel() : "") + ": " + e.getMessage();
			setException(new RuntimeException(message, e));
			return;
		}
	}

	/**
	 * The function getYear() returns the year that of issues are contained in
	 * this act. The function relies on the assumption that the first level of
	 * the logical structure tree of the act is of type METADATA_ELEMENT_YEAR,
	 * is present exactly once and has a METADATA_FIELD_LABEL whose value
	 * represents the year and can be parsed to integer.
	 * 
	 * @param act
	 *            act to examine
	 * @return the year
	 * @throws ReadException
	 *             if one of the preconditions fails
	 */
	private int getYear(DigitalDocument act) throws ReadException {
		List<DocStruct> children = act.getLogicalDocStruct().getAllChildren();
		if (children == null) {
			throw new ReadException(
					"Could not get date year: Logical structure tree doesn’t have elements. Exactly one element of type "
							+ yearLevelName + " is required.");
		}
		if (children.size() > 1) {
			throw new ReadException(
					"Could not get date year: Logical structure has several elements. Exactly one element (of type "
							+ yearLevelName + ") is required.");
		}
		try {
			return getMetadataIntValueByName(children.get(0), MetsModsImportExport.CREATE_LABEL_ATTRIBUTE_TYPE);
		} catch (NoSuchElementException nose) {
			throw new ReadException("Could not get date year: " + yearLevelName + " has no meta data field "
					+ MetsModsImportExport.CREATE_LABEL_ATTRIBUTE_TYPE + '.');
		} catch (NumberFormatException uber) {
			throw new ReadException("Could not get date year: " + MetsModsImportExport.CREATE_LABEL_ATTRIBUTE_TYPE
					+ " value from " + yearLevelName + " cannot be interpeted as whole number.");
		}
	}

	/**
	 * The function getMetadataIntValueByName() returns the value of a named
	 * meta data entry associated with a structure entity as int.
	 * 
	 * @param structureTypeName
	 *            structureEntity to get the meta data value from
	 * @param metaDataTypeName
	 *            name of the meta data element whose value is to obtain
	 * @return value of a meta data element with the given name
	 * @throws NoSuchElementException
	 *             if there is no such element
	 * @throws NumberFormatException
	 *             if the value cannot be parsed to int
	 */
	private static int getMetadataIntValueByName(DocStruct structureTypeName, String metaDataTypeName)
			throws NoSuchElementException, NumberFormatException {
		List<MetadataType> metadataTypes = structureTypeName.getType().getAllMetadataTypes();
		for (MetadataType metadataType : metadataTypes) {
			if (metaDataTypeName.equals(metadataType.getName())) {
				return Integer.parseInt(new HashSet<Metadata>(structureTypeName.getAllMetadataByType(metadataType))
						.iterator().next().getValue());
			}
		}
		throw new NoSuchElementException();
	}

	/**
	 * The function getMetsYearAnchorPointerURL() returns the URL for a METS
	 * pointer to retrieve the course of appearance data for the year the given
	 * process is in.
	 * 
	 * @param process
	 *            process whese year anchor pointer shall be returend
	 * @return URL to retrieve the year data
	 * @throws PreferencesException
	 *             if the no node corresponding to the file format is available
	 *             in the rule set configured
	 * @throws ReadException
	 *             if the no node corresponding to the file format is available
	 *             in the rule set configured
	 * @throws SwapException
	 *             if an error occurs while the process is swapped back in
	 * @throws DAOException
	 *             if an error occurs while saving the fact that the process has
	 *             been swapped back in to the database
	 * @throws IOException
	 *             if creating the process directory or reading the meta data
	 *             file fails
	 * @throws InterruptedException
	 *             if the current thread is interrupted by another thread while
	 *             it is waiting for the shell script to create the directory to
	 *             finish
	 */
	private static String getMetsYearAnchorPointerURL(Prozess process) throws PreferencesException, ReadException,
			SwapException, DAOException, IOException, InterruptedException {

		VariableReplacer replacer = new VariableReplacer(process.getDigitalDocument(), process.getRegelsatz()
				.getPreferences(), process, null);
		String metsPointerPathAnchor = process.getProjekt().getMetsPointerPath();
		if (metsPointerPathAnchor.contains(Projekt.ANCHOR_SEPARATOR)) {
			metsPointerPathAnchor = metsPointerPathAnchor.split(Projekt.ANCHOR_SEPARATOR)[1];
		}
		return replacer.replace(metsPointerPathAnchor);
	}

	/**
	 * The function getIssueDates() returns a list with all the dates of the
	 * issues contained in this process. The function relies on the assumption
	 * that the child elements descending from the topmost logical structure
	 * entity of the process represent year, month and day of appearance and
	 * that the immediate children of the day level represent issues. The levels
	 * year, month and day must have meta data elements named "PublicationYear",
	 * "PublicationMonth" and "PublicationDay" associated whose value can be
	 * interpreted as an integer.
	 * 
	 * @return a list with the dates of all issues in this process
	 * @throws PreferencesException
	 *             if the no node corresponding to the file format is available
	 *             in the rule set used
	 * @throws ReadException
	 *             if the meta data file cannot be read
	 * @throws SwapException
	 *             if an error occurs while the process is swapped back in
	 * @throws DAOException
	 *             if an error occurs while saving the fact that the process has
	 *             been swapped back in to the database
	 * @throws IOException
	 *             if creating the process directory or reading the meta data
	 *             file fails
	 * @throws InterruptedException
	 *             if the current thread is interrupted by another thread while
	 *             it is waiting for the shell script to create the directory to
	 *             finish
	 */
	private static List<LocalDate> getIssueDates(DigitalDocument act) throws PreferencesException, ReadException,
			SwapException, DAOException, IOException, InterruptedException {
		List<LocalDate> result = new LinkedList<LocalDate>();
		DocStruct logicalDocStruct = act.getLogicalDocStruct();
		for (DocStruct annualNode : skipIfNull(logicalDocStruct.getAllChildren())) {
			int year = getMetadataIntValueByName(annualNode, MetsModsImportExport.CREATE_LABEL_ATTRIBUTE_TYPE);
			for (DocStruct monthNode : skipIfNull(annualNode.getAllChildren())) {
				int monthOfYear = getMetadataIntValueByName(monthNode,
						MetsModsImportExport.CREATE_ORDERLABEL_ATTRIBUTE_TYPE);
				for (DocStruct dayNode : skipIfNull(monthNode.getAllChildren())) {
					LocalDate appeared = new LocalDate(year, monthOfYear, getMetadataIntValueByName(dayNode,
							MetsModsImportExport.CREATE_ORDERLABEL_ATTRIBUTE_TYPE));
					for (@SuppressWarnings("unused")
					DocStruct entry : skipIfNull(dayNode.getAllChildren())) {
						result.add(appeared);
					}
				}
			}
		}
		return result;
	}

	/**
	 * The function skipIfNull() returns the list passed in, or
	 * Collections.emptyList() if the list is null.
	 * {@link DocStruct#getAllChildren()} does return null if no children are
	 * contained. This would throw a NullPointerException if passed into a loop.
	 * Replacing null by Collections.emptyList() results in the loop to be
	 * silently skipped, so that the outer code continues normally.
	 * 
	 * @param list
	 *            list to check for being null
	 * @return the list, Collections.emptyList() if the list is null
	 */
	private static <T> List<T> skipIfNull(List<T> list) {
		if (list == null) {
			list = Collections.emptyList();
		}
		return list;
	}

	/**
	 * Returns the display name of the task to show to the user.
	 * 
	 * @see de.sub.goobi.helper.tasks.INameableTask#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return Helper.getTranslation("ExportNewspaperBatchTask");
	}

	/**
	 * The function getMetsPointerURL() investigates the METS pointer URL of the
	 * process.
	 * 
	 * @param process
	 *            process to take values for path variables from
	 * @return the METS pointer URL of the process
	 * @throws PreferencesException
	 *             if the no node corresponding to the file format is available
	 *             in the rule set used
	 * @throws ReadException
	 *             if the meta data file cannot be read
	 * @throws SwapException
	 *             if an error occurs while the process is swapped back in
	 * @throws DAOException
	 *             if an error occurs while saving the fact that the process has
	 *             been swapped back in to the database
	 * @throws IOException
	 *             if creating the process directory or reading the meta data
	 *             file fails
	 * @throws InterruptedException
	 *             if the current thread is interrupted by another thread while
	 *             it is waiting for the shell script to create the directory to
	 *             finish
	 */
	static String getMetsPointerURL(Prozess process) throws PreferencesException, ReadException, SwapException,
			DAOException, IOException, InterruptedException {
		VariableReplacer replacer = new VariableReplacer(process.getDigitalDocument(), process.getRegelsatz()
				.getPreferences(), process, null);
		return replacer.replace(process.getProjekt().getMetsPointerPathAnchor());
	}

	/**
	 * The function buildExportableMetsMods() returns a MetsModsImportExport
	 * object whose logical document structure tree has been enriched with all
	 * nodes that have to be exported along with the data to make
	 * cross-newspaper referencing work. References to both year data files and
	 * other issues within the same year have been attached.
	 * 
	 * @param process
	 *            process to get the METS/MODS data from
	 * @param years
	 *            a map with all years and their pointer URLs
	 * @param issues
	 *            a map with all issues and their pointer URLs
	 * @return an enriched MetsModsImportExport object
	 * @throws PreferencesException
	 *             if the no node corresponding to the file format is available
	 *             in the rule set used
	 * @throws ReadException
	 *             if the meta data file cannot be read
	 * @throws SwapException
	 *             if an error occurs while the process is swapped back in
	 * @throws DAOException
	 *             if an error occurs while saving the fact that the process has
	 *             been swapped back in to the database
	 * @throws IOException
	 *             if creating the process directory or reading the meta data
	 *             file fails
	 * @throws InterruptedException
	 *             if the current thread is interrupted by another thread while
	 *             it is waiting for the shell script to create the directory to
	 *             finish
	 * @throws TypeNotAllowedForParentException
	 *             is thrown, if this DocStruct is not allowed for a parent
	 * @throws MetadataTypeNotAllowedException
	 *             if the DocStructType of this DocStruct instance does not
	 *             allow the MetadataType or if the maximum number of Metadata
	 *             (of this type) is already available
	 * @throws TypeNotAllowedAsChildException
	 *             if a child should be added, but it's DocStruct type isn't
	 *             member of this instance's DocStruct type
	 */
	private MetsMods buildExportableMetsMods(Prozess process, HashMap<Integer, String> years,
			ArrayListMap<LocalDate, String> issues) throws PreferencesException, ReadException, SwapException,
			DAOException, IOException, InterruptedException, TypeNotAllowedForParentException,
			MetadataTypeNotAllowedException, TypeNotAllowedAsChildException {

		Prefs ruleSet = process.getRegelsatz().getPreferences();
		MetsMods result = new MetsMods(ruleSet);
		result.read(process.getMetadataFilePath());

		DigitalDocument caudexDigitalis = result.getDigitalDocument();
		int ownYear = getMetadataIntValueByName(caudexDigitalis.getLogicalDocStruct().getAllChildren().iterator()
				.next(), MetsModsImportExport.CREATE_LABEL_ATTRIBUTE_TYPE);
		String ownMetsPointerURL = getMetsPointerURL(process);

		insertReferencesToYears(years, ownYear, caudexDigitalis, ruleSet);
		insertReferencesToOtherIssuesInThisYear(issues, ownYear, ownMetsPointerURL, caudexDigitalis, ruleSet);
		return result;
	}

	/**
	 * The function insertReferencesToYears() inserts METS pointer references to
	 * all years into the logical hierarchy of the document. For all but the
	 * current year, an additional child node will be created.
	 * 
	 * @param act
	 *            level of the logical document structure tree that holds years
	 *            (that is the top level)
	 * @param years
	 *            a map with all years and their pointer URLs
	 * @param ownYear
	 * @param ruleSet
	 *            the rule set this process is based on
	 * @throws TypeNotAllowedForParentException
	 *             is thrown, if this DocStruct is not allowed for a parent
	 * @throws MetadataTypeNotAllowedException
	 *             if the DocStructType of this DocStruct instance does not
	 *             allow the MetadataType or if the maximum number of Metadata
	 *             (of this type) is already available
	 * @throws TypeNotAllowedAsChildException
	 *             if a child should be added, but it's DocStruct type isn't
	 *             member of this instance's DocStruct type
	 */
	private void insertReferencesToYears(HashMap<Integer, String> years, int ownYear, DigitalDocument act,
			Prefs ruleSet) throws TypeNotAllowedForParentException, MetadataTypeNotAllowedException,
			TypeNotAllowedAsChildException {
		for (Integer year : years.keySet()) {
			if (year.intValue() != ownYear) {
				DocStruct child = getOrCreateChild(act.getLogicalDocStruct(), yearLevelName,
						MetsModsImportExport.CREATE_LABEL_ATTRIBUTE_TYPE, year.toString(),
						MetsModsImportExport.CREATE_ORDERLABEL_ATTRIBUTE_TYPE, act, ruleSet);
				child.addMetadata(MetsModsImportExport.CREATE_MPTR_ELEMENT_TYPE, years.get(year));
			}
		}
	}

	/**
	 * The function getOrCreateChild() returns a child of a DocStruct of the
	 * given type and identified by an identifier in a meta data field of
	 * choice. If no such child exists, it will be created.
	 * 
	 * @param parent
	 *            DocStruct to get the child from or create it in.
	 * @param type
	 *            type of the DocStruct to return
	 * @param identifierField
	 *            field whose value identifies the DocStruct to return
	 * @param identifier
	 *            value that identifies the DocStruct to return
	 * @param optionalField
	 *            adds another meta data field with this name and the value used
	 *            as identifier if the metadata type is allowed
	 * @param act
	 *            act to create the child in
	 * @param ruleset
	 *            rule set the act is based on
	 * @return the first child matching the given conditions, if any, or a newly
	 *         created child with these properties otherwise
	 * @throws TypeNotAllowedForParentException
	 *             is thrown, if this DocStruct is not allowed for a parent
	 * @throws MetadataTypeNotAllowedException
	 *             if the DocStructType of this DocStruct instance does not
	 *             allow the MetadataType or if the maximum number of Metadata
	 *             (of this type) is already available
	 * @throws TypeNotAllowedAsChildException
	 *             if a child should be added, but it's DocStruct type isn't
	 *             member of this instance's DocStruct type
	 */
	private static DocStruct getOrCreateChild(DocStruct parent, String type, String identifierField, String identifier,
			String optionalField, DigitalDocument act, Prefs ruleset) throws TypeNotAllowedForParentException,
			MetadataTypeNotAllowedException, TypeNotAllowedAsChildException {
		try {
			return parent.getChild(type, identifierField, identifier);
		} catch (NoSuchElementException nose) {
			DocStruct child = act.createDocStruct(ruleset.getDocStrctTypeByName(type));
			child.addMetadata(identifierField, identifier);
			try {
				child.addMetadata(optionalField, identifier);
			} catch (MetadataTypeNotAllowedException e) {
				if(logger.isInfoEnabled()){
					logger.info(e.getMessage().replaceFirst("^Couldn’t add ([^:]+):", "Couldn’t add optional field $1."));
				}
			}
			
			Integer rank = null;
			try {
				rank = Integer.valueOf(identifier);
			} catch (NumberFormatException e) {
				if (logger.isEnabledFor(Priority.WARN)) {
					logger.warn("Cannot place " + type + " \"" + identifier
							+ "\" correctly because its sorting criterion is not numeric.");
				}
			}
			parent.addChild(positionByRank(parent.getAllChildren(), identifierField, rank), child);
			
			return child;
		}
	}

	/**
	 * Returns the index of the child to insert between its siblings depending
	 * on its rank. A return value of {@code null} will indicate that no
	 * position could be determined which will cause
	 * {@link DocStruct#addChild(Integer, DocStruct)} to simply append the new
	 * child at the end.
	 * 
	 * @param siblings
	 *            brothers and sisters of the child to add
	 * @param metadataType
	 *            field indicating the rank value
	 * @param rank
	 *            rank of the child to insert
	 * @return the index position to insert the child
	 */
	private static Integer positionByRank(List<DocStruct> siblings, String metadataType, Integer rank) {
		int result = 0;

		if (siblings == null || rank == null) {
			return null;
		}

		SIBLINGS: for (DocStruct aforeborn : siblings) {
			List<Metadata> allMetadata = aforeborn.getAllMetadata();
			if (allMetadata != null) {
				for (Metadata metadataElement : allMetadata) {
					if (metadataElement.getType().getName().equals(metadataType)) {
						try {
							if (Integer.parseInt(metadataElement.getValue()) < rank) {
								result++;
								continue SIBLINGS;
							} else {
								return result;
							}
						} catch (NumberFormatException e) {
							if (logger.isEnabledFor(Priority.WARN)) {
								String typeName = aforeborn.getType() != null && aforeborn.getType().getName() != null
										? aforeborn.getType().getName() : "cross-reference";
								logger.warn("Cannot determine position to place " + typeName
										+ " correctly because the sorting criterion of one of its siblings is \""
										+ metadataElement.getValue() + "\", but must be numeric.");
							}
						}
					}
				}
			}
			return null;
		}
		return result;
	}

	/**
	 * The function insertReferencesToOtherIssuesInThisYear() inserts METS
	 * pointer references to other issues which have been published in the same
	 * year as this process contains data for, but which are contained in other
	 * processes, into the logical document hierarchy of this process.
	 * 
	 * @param issues
	 *            the root of the logical document hierarchy to modify
	 * @param ownYear
	 *            a map of all issue dates along with their pointer URLs
	 * @param ownMetsPointerURL
	 *            the current year—issues of other years are skipped
	 * @param caudexDigitalis
	 *            my own METS pointer URL—issues that share the same URL are
	 *            also skipped
	 * @param ruleSet
	 *            rule set the process is based on
	 * @throws TypeNotAllowedForParentException
	 *             is thrown, if this DocStruct is not allowed for a parent
	 * @throws TypeNotAllowedAsChildException
	 *             if a child should be added, but it's DocStruct type isn't
	 *             member of this instance's DocStruct type
	 * @throws MetadataTypeNotAllowedException
	 *             if the DocStructType of this DocStruct instance does not
	 *             allow the MetadataType or if the maximum number of Metadata
	 *             (of this type) is already available
	 */
	private void insertReferencesToOtherIssuesInThisYear(ArrayListMap<LocalDate, String> issues,
			int currentYear, String ownMetsPointerURL, DigitalDocument act, Prefs ruleSet)
			throws TypeNotAllowedForParentException, TypeNotAllowedAsChildException, MetadataTypeNotAllowedException {
		for (int i = 0; i < issues.size(); i++) {
			if ((issues.getKey(i).getYear() == currentYear) && !issues.getValue(i).equals(ownMetsPointerURL)) {
				insertIssueReference(act, ruleSet, issues.getKey(i), issues.getValue(i));
			}
		}
		return;
	}

	/**
	 * Inserts a reference (METS pointer (mptr) URL) for an issue on a given
	 * date into an act.
	 * 
	 * @param act
	 *            act in whose logical structure the pointer is to create
	 * @param ruleset
	 *            rule set the act is based on
	 * @param date
	 *            date of the issue to create a pointer to
	 * @param metsPointerURL
	 *            URL of the issue
	 * @throws TypeNotAllowedForParentException
	 *             is thrown, if this DocStruct is not allowed for a parent
	 * @throws MetadataTypeNotAllowedException
	 *             if the DocStructType of this DocStruct instance does not
	 *             allow the MetadataType or if the maximum number of Metadata
	 *             (of this type) is already available
	 * @throws TypeNotAllowedAsChildException
	 *             if a child should be added, but it's DocStruct type isn't
	 *             member of this instance's DocStruct type
	 */
	private void insertIssueReference(DigitalDocument act, Prefs ruleset, LocalDate date, String metsPointerURL)
			throws TypeNotAllowedForParentException, TypeNotAllowedAsChildException, MetadataTypeNotAllowedException {
		DocStruct year = getOrCreateChild(act.getLogicalDocStruct(), yearLevelName,
				MetsModsImportExport.CREATE_LABEL_ATTRIBUTE_TYPE, Integer.toString(date.getYear()),
				MetsModsImportExport.CREATE_ORDERLABEL_ATTRIBUTE_TYPE, act, ruleset);
		DocStruct month = getOrCreateChild(year, monthLevelName,
				MetsModsImportExport.CREATE_ORDERLABEL_ATTRIBUTE_TYPE, Integer.toString(date.getMonthOfYear()),
				MetsModsImportExport.CREATE_LABEL_ATTRIBUTE_TYPE, act, ruleset);
		DocStruct day = getOrCreateChild(month, dayLevelName,
				MetsModsImportExport.CREATE_ORDERLABEL_ATTRIBUTE_TYPE, Integer.toString(date.getDayOfMonth()),
				MetsModsImportExport.CREATE_LABEL_ATTRIBUTE_TYPE, act, ruleset);
		DocStruct issue = day.createChild(issueLevelName, act, ruleset);
		issue.addMetadata(MetsModsImportExport.CREATE_MPTR_ELEMENT_TYPE, metsPointerURL);
	}

	/**
	 * Calls the clone constructor to create a not yet executed instance of this
	 * thread object. This is necessary for threads that have terminated in
	 * order to render possible to restart them.
	 * 
	 * @return a not-yet-executed replacement of this thread
	 * @see de.sub.goobi.helper.tasks.EmptyTask#replace()
	 */
	@Override
	public ExportNewspaperBatchTask replace() {
		return new ExportNewspaperBatchTask(this);
	}
}
