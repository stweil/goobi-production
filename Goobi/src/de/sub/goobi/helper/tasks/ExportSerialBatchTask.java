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
package de.sub.goobi.helper.tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.goobi.production.constants.Parameters;
import org.hibernate.Hibernate;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.fileformats.mets.MetsModsImportExport;
import de.sub.goobi.beans.Batch;
import de.sub.goobi.beans.Prozess;
import de.sub.goobi.config.ConfigMain;
import de.sub.goobi.export.dms.ExportDms;
import de.sub.goobi.forms.LoginForm;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;

/**
 * Thread implementation to export a batch holding a serial publication as set,
 * cross-over inserting METS pointer references to the respective other volumes
 * in the anchor file.
 * 
 * Requires the {@code MetsModsImportExport.CREATE_MPTR_ELEMENT_TYPE} metadata
 * type ("MetsPointerURL") to be available for adding to the first level child
 * of the logical document structure hierarchy (typically "Volume").
 * 
 * @author Matthias Ronge &lt;matthias.ronge@zeutschel.de&gt;
 */
public class ExportSerialBatchTask extends EmptyTask implements INameableTask {

	/**
	 * The batch to export
	 */
	private final Batch batch;

	/**
	 * The METS pointers of all volumes belonging to this serial publication
	 */
	private final ArrayList<String> pointers;

	/**
	 * Counter used for incrementing the progress bar, starts from 0 and ends
	 * with “maxsize”.
	 */
	private int stepcounter;

	/**
	 * Iterator along the processes of the batch during export
	 */
	private Iterator<Prozess> processesIterator;

	/**
	 * Value indicating 100% on the progress bar
	 */
	private final int maxsize;

	/**
	 * Creates a new ExportSerialBatchTask from a batch of processes belonging
	 * to a serial publication.
	 * 
	 * @param batch
	 *            batch holding a serial publication
	 */
	public ExportSerialBatchTask(Batch batch) {
		super(batch.getLabel());
		this.batch = batch;
		int batchSize = batch.getProcesses().size();
		pointers = new ArrayList<String>(batchSize);
		stepcounter = 0;
		processesIterator = null;
		maxsize = batchSize + 1;
		initialiseRuleSets(batch.getProcesses());
	}

	/**
	 * Returns the display name of the task to show to the user.
	 * 
	 * @see de.sub.goobi.helper.tasks.INameableTask#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return Helper.getTranslation("ExportSerialBatchTask");
	}

	/**
	 * Initialises the the rule sets of the processes to export that export
	 * depends on. This cannot be done later because the therad doesn’t have
	 * access to the hibernate session any more.
	 * 
	 * @param processes
	 *            collection of processes whose rulesets are to be initialised
	 */
	private static final void initialiseRuleSets(Iterable<Prozess> processes) {
		for (Prozess process : processes) {
			Hibernate.initialize(process.getRegelsatz());
		}
	}

	/**
	 * Clone constructor. Creates a new ExportSerialBatchTask from another one.
	 * This is used for restarting the thread as a Java thread cannot be run
	 * twice.
	 * 
	 * @param master
	 *            copy master
	 */
	public ExportSerialBatchTask(ExportSerialBatchTask master) {
		super(master);
		batch = master.batch;
		pointers = master.pointers;
		stepcounter = master.stepcounter;
		processesIterator = master.processesIterator;
		maxsize = master.maxsize;
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
			if (stepcounter == 0) {
				pointers.clear();
				for (Prozess process1 : batch.getProcesses()) {
					process = process1;
					pointers.add(ExportNewspaperBatchTask.getMetsPointerURL(process));
				}
				processesIterator = batch.getProcesses().iterator();
				stepcounter++;
				setProgress(100 * stepcounter / maxsize);
			}
			if (stepcounter > 0) {
				while (processesIterator.hasNext()) {
					if (isInterrupted()) {
						return;
					}
					process = processesIterator.next();
					DigitalDocument out = buildExportDocument(process, pointers);
					ExportDms exporter = new ExportDms(ConfigMain.getBooleanParameter(Parameters.EXPORT_WITH_IMAGES,
							true));
					exporter.setExportDmsTask(this);
					exporter.startExport(process, LoginForm.getCurrentUserHomeDir(), out);
					stepcounter++;
					setProgress(100 * stepcounter / maxsize);
				}
			}
		} catch (Exception e) { // PreferencesException, ReadException, SwapException, DAOException, IOException, InterruptedException and some runtime exceptions
			String message = e.getClass().getSimpleName() + " while "
					+ (stepcounter == 0 ? "examining " : "exporting ") + (process != null ? process.getTitel() : "")
					+ ": " + e.getMessage();
			setException(new RuntimeException(message, e));
			return;
		}
	}

	/**
	 * The function buildExportableMetsMods() returns a DigitalDocument object
	 * whose logical document structure tree has been enriched with all nodes
	 * that have to be exported along with the data to make cross-volume
	 * referencing work.
	 * 
	 * @param process
	 *            process to get the METS/MODS data from
	 * @param allPointers
	 *            all the METS pointers from all volumes
	 * @return an enriched DigitalDocument
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
	private static DigitalDocument buildExportDocument(Prozess process, Iterable<String> allPointers)
			throws PreferencesException, ReadException, SwapException, DAOException, IOException, InterruptedException,
			MetadataTypeNotAllowedException, TypeNotAllowedForParentException, TypeNotAllowedAsChildException {

		DigitalDocument result = process.readMetadataFile().getDigitalDocument();
		DocStruct root = result.getLogicalDocStruct();
		String type = "Volume";
		try {
			type = root.getAllChildren().get(0).getType().getName();
		} catch (NullPointerException e) {
		}
		String ownPointer = ExportNewspaperBatchTask.getMetsPointerURL(process);
		Prefs ruleset = process.getRegelsatz().getPreferences();
		for (String pointer : allPointers) {
			if (!pointer.equals(ownPointer)) {
				root.createChild(type, result, ruleset).addMetadata(MetsModsImportExport.CREATE_MPTR_ELEMENT_TYPE,
						pointer);
			}
		}
		return result;
	}
}
