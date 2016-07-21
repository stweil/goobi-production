/**
 * This file is part of the Goobi Application - a Workflow tool for the support
 * of mass digitization.
 *
 * (c) 2013 Goobi. Digitalisieren im Verein e.V. <contact@goobi.org>
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
package de.sub.goobi.forms;

// import javax.faces.bean.ManagedProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.faces.model.SelectItem;
import javax.xml.transform.TransformerException;

import org.apache.log4j.Logger;
import org.goobi.production.constants.Parameters;
import org.goobi.production.model.bibliography.course.Course;
import org.goobi.production.model.bibliography.course.Granularity;
import org.w3c.dom.Document;

import de.sub.goobi.config.ConfigMain;
import de.sub.goobi.helper.FacesUtils;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.XMLUtils;
import de.sub.goobi.helper.tasks.CreateNewspaperProcessesTask;
import de.sub.goobi.helper.tasks.TaskManager;

/**
 * The class GranularityForm provides the screen logic for a JSF page to choose
 * the granularity to split up the course of appearance of a newspaper into
 * Goobi processes.
 *
 * @author Matthias Ronge &lt;matthias.ronge@zeutschel.de&gt;
 */
public class GranularityForm {
	private static final Logger logger = Logger.getLogger(GranularityForm.class);

	protected Granularity generateBatches;

	/**
	 * The field granularity holds the granularity chosen by the user. It is
	 * null initially indicating that the user didn’t choose anything yet.
	 */
	protected Granularity granularity;

	/**
	 * The field course holds the course of appearance previously created by the
	 * calendar form instance that from the information about the issues that
	 * appeared is taken to be shown. This field is a managed property which is
	 * automatically populated by JSF upon form creation by calling setCourse().
	 * This behaviour is configured in faces-config.xml
	 */
	// @ManagedProperty(value = "#{CalendarForm.course}")
	protected Course course;

	/**
	 * The field numberOfPages holds the total number of pages of the
	 * digitization project guessed by the user. It is null initially indicating
	 * that the user didn’t enter anything yet.
	 */
	protected Long numberOfPages;

	/**
	 * The procedure breakModeClick() is called from the procedures which are
	 * called if the user clicks one of the button to select the granularity
	 * level. It sets the granularity to the given BreakMode and triggers the
	 * recalculation of the processes in the course of appearance data model.
	 */
	private void alterGranularityClick(Granularity granularity) {
		this.granularity = granularity;
		course.splitInto(granularity);
	}

	/**
	 * The procedure daysClick() is called if the user clicks the button to
	 * select the granularity level “days”. It sets the granularity to
	 * BreakMode.DAYS and triggers the recalculation of the breaks in the course
	 * of appearance data model.
	 */
	public void daysClick() {
		alterGranularityClick(Granularity.DAYS);
	}

	/**
	 * The procedure createProcessesClick() is called if the user clicks the
	 * button to create processes for the course of appearance. If the
	 * underlying ProzesskopieForm is incomplete, the user will be redirected
	 * back to the page to fix the problems and nothing more happens. If
	 * everything is fine, the meta data field “PublicationRun”—if available—is
	 * populated with the course of appearance in text form. Then, a long
	 * running task to create processes is prepared and the user will be
	 * redirected to the task manager page where it can observe the task
	 * progressing.
	 *
	 * @return the next page to show as named in a &lt;from-outcome&gt; element
	 *         in faces_config.xml
	 */
	public String createProcessesClick() {
		ProzesskopieForm prozesskopieForm = (ProzesskopieForm) Helper.getManagedBeanValue("#{ProzesskopieForm}");
		if (!prozesskopieForm.isContentValid(false)) {
			return ProzesskopieForm.NAVI_FIRST_PAGE;
		}
		if (course == null || course.getNumberOfProcesses() < 1) {
			Helper.setFehlerMeldung("UnvollstaendigeDaten", "granularity.header");
			return "";
		}
		Helper.removeManagedBean("ProzesskopieForm");
		CreateNewspaperProcessesTask createProcesses = new CreateNewspaperProcessesTask(prozesskopieForm, course, generateBatches);
		TaskManager.addTask(createProcesses);
		return "taskmanager";
	}

	/**
	 * The procedure downloadClick() is called if the user clicks the button to
	 * download the course of appearance in XML format.
	 *
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws TransformerException
	 *             when it is not possible to create a Transformer instance or
	 *             if an unrecoverable error occurs during the course of the
	 *             transformation
	 */
	public void downloadClick() {
		try {
			if (course == null || course.getNumberOfProcesses() < 1) {
				Helper.setFehlerMeldung("UnvollstaendigeDaten", "granularity.header");
				return;
			}
			course.recalculateRegularityOfIssues();
			Document courseXML = course.toXML();
			byte[] data = XMLUtils.documentToByteArray(courseXML, 4);
			FacesUtils.sendDownload(data, "course.xml");
		} catch (TransformerException e) {
			Helper.setFehlerMeldung("granularity.download.error", "error.TransformerException");
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			Helper.setFehlerMeldung("granularity.download.error", "error.IOException");
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * The function getBatchOptions() returns the granularity levels available
	 * to summarize the processes to create in batches along with their verbal
	 * description to be shown to the user. The available values depend on the
	 * granularity value of the global variable &ldquo;granularity&rdquo;: Only
	 * granularity values <i>broader than</i> the chosen option are available.
	 * If the year level is chosen, or if &ldquo;granularity&rdquo; isn&rsquo;t
	 * set, only the null value, indicating the function is in &ldquo;off&rdquo;
	 * state, is returned along with a verbal description of the cause.
	 *
	 * @return the granularity level chosen by the user
	 */
	@SuppressWarnings("incomplete-switch")
	public List<SelectItem> getBatchOptions() {
		List<SelectItem> result = new ArrayList<SelectItem>();
		if (granularity == null) {
			result.add(new SelectItem("null", Helper.getTranslation("granularity.batches.noData")));
		} else if (granularity == Granularity.YEARS) {
			result.add(new SelectItem("null", Helper.getTranslation("granularity.batches.notAvailable")));
		} else {
			result.add(new SelectItem("null", Helper.getTranslation("granularity.null")));
			switch (granularity) {
			case ISSUES:
				result.add(new SelectItem("issues", Helper.getTranslation("granularity.issues")));
			case DAYS:
				result.add(new SelectItem("weeks", Helper.getTranslation("granularity.weeks")));
			case WEEKS:
				result.add(new SelectItem("months", Helper.getTranslation("granularity.months")));
			case MONTHS:
				result.add(new SelectItem("quarters", Helper.getTranslation("granularity.quarters")));
			case QUARTERS:
				result.add(new SelectItem("years", Helper.getTranslation("granularity.years")));
				break;
			}
		}
		return result;
	}

	/**
	 * The function getGranularity() returns the granularity level chosen by the
	 * user in lower case as read-only property “granularity”. If there are no
	 * processes—indicating that the user didn’t choose anything yet or didn’t
	 * choose anything again after clicking back in the bread crumbs and
	 * altering the course of appearance in a way that the processes need to be
	 * recalculated—it literally returns “null” as String. If there are
	 * processes loaded from a foreign source, it returns “foreign”.
	 *
	 * @return the granularity level chosen by the user
	 */
	public String getGranularity() {
		if (granularity == null) {
			return course.getNumberOfProcesses() == 0 ? "null" : "foreign";
		}
		return granularity.toString().toLowerCase();
	}

	/**
	 * The function getIssueCount() returns the number of issues that physically
	 * appeared as to the underlying course of appearance data model as
	 * read-only property “issueCount”.
	 *
	 * @return the number of issues physically appeared
	 */
	public long getIssueCount() {
		return course.countIndividualIssues();
	}

	/**
	 * The function getLockMessage() returns an empty string if either no limit
	 * for the minimal number of pages per process has been configured, or the
	 * limit has been reached, which will allow normal processing. Otherwise, an
	 * error message string is returned, explaining the user how to fix the
	 * problem. Quotes are replaced for not to break the Javascript in action
	 * here.
	 *
	 * @return an error message, or the empty string if everything is okay.
	 */
	public String getLockMessage() {
		long perProcess = ConfigMain.getLongParameter(Parameters.MINIMAL_NUMBER_OF_PAGES, -1);
		if (getNumberOfProcesses() < 1 || perProcess < 1
				|| (numberOfPages != null && numberOfPages / getNumberOfProcesses() >= perProcess)) {
			return "";
		}
		double perIssue = (double) perProcess * getNumberOfProcesses() / course.countIndividualIssues();
		List<String> args = Arrays.asList(new String[] { Long.toString(perProcess),
				Long.toString((long) Math.ceil(perIssue)) });
		return Helper.getTranslation("granularity.numberOfPages.tooSmall", args).replaceAll("\"", "″")
				.replaceAll("'", "′");
	}

	/**
	 * The function getNumberOfPages returns the total number of pages of the
	 * digitization project guessed and entered by the user—or null indicating
	 * that the user didn’t enter anything yet—as read-write property
	 * “numberOfPages”
	 *
	 * @return the total number of pages of the digitization project
	 */
	public Long getNumberOfPages() {
		return numberOfPages;
	}

	/**
	 * The function getNumberOfPages returns the number of pages per issue
	 * guessed and entered by the user—or null indicating that the user didn’t
	 * enter anything yet—as read-write property “numberOfPagesPerIssue”
	 *
	 * @return the total number of pages of the digitization project
	 */
	public Long getNumberOfPagesPerIssue() {
		return numberOfPages != null ? numberOfPages / course.countIndividualIssues() : null;
	}

	/**
	 * The function getNumberOfPages returns the total number of pages of the
	 * digitization project entered by the user or a guessed value as read-only
	 * property “numberOfPagesOptionallyGuessed”
	 *
	 * @return an (optionally guessed) total number of pages
	 */
	public Long getNumberOfPagesOptionallyGuessed() {
		if (numberOfPages == null) {
			return course.guessTotalNumberOfPages();
		} else {
			return numberOfPages;
		}
	}

	/**
	 * The function getNumberOfProcesses() returns the number of processes that
	 * will be created. If the course had to condemn its processes because the
	 * user changed the course, the recalculation of the processes will be
	 * re-initiated here.
	 *
	 * @return the number of processes that will be created
	 */
	public int getNumberOfProcesses() {
		if (course.getNumberOfProcesses() == 0 && granularity != null) {
			course.splitInto(granularity);
		}
		return course.getNumberOfProcesses();
	}

	/**
	 * The function getPagesPerProcessRounded() returns the pages per process as
	 * a rounded string.
	 *
	 * This should be done in JSF using “convertNumber”, but it doesn’t show any
	 * effect and the number still prints with a decimal fractions part that is
	 * not desired. This JSF code should put the number rounded into the request
	 * scope, but rounding doesn’t work for some unknown reason.
	 *
	 * <pre>
	 * &lt;h:outputText binding=&quot;#{requestScope.pagesPerProcess}&quot; rendered=&quot;false&quot;&gt;
	 *         value=&quot;#{GranularityForm.numberOfPagesOptionallyGuessed / GranularityForm.numberOfProcesses}&quot;
	 *     &lt;f:convertNumber maxFractionDigits=&quot;0&quot; /&gt;
	 * &lt;/h:outputText&gt;
	 * </pre>
	 *
	 * @return the pages per process as a rounded string
	 */
	public String getPagesPerProcessRounded() {
		double pagesPerProcess = (double) getNumberOfPagesOptionallyGuessed() / getNumberOfProcesses();
		return Long.toString(Math.round(pagesPerProcess));
	}

	/**
	 * The function getSelectedBatchOption() returns the level for which batches
	 * will be created as read-write property “numberOfPagesOptionallyGuessed”
	 *
	 * @return an (optionally guessed) total number of pages
	 */
	public String getSelectedBatchOption() {
		return String.valueOf(generateBatches).toLowerCase();
	}

	/**
	 * The procedure issuesClick() is called if the user clicks the button to
	 * select the granularity level “issues”. It sets the granularity to
	 * BreakMode.ISSUES and triggers the recalculation of the breaks in the
	 * course of appearance data model.
	 */
	public void issuesClick() {
		alterGranularityClick(Granularity.ISSUES);
	}

	/**
	 * The procedure monthsClick() is called if the user clicks the button to
	 * select the granularity level “months”. It sets the granularity to
	 * BreakMode.MONTHS and triggers the recalculation of the breaks in the
	 * course of appearance data model.
	 */
	public void monthsClick() {
		alterGranularityClick(Granularity.MONTHS);
	}

	/**
	 * The procedure monthsClick() is called if the user clicks the button to
	 * select the granularity level “quarters”. It sets the granularity to
	 * BreakMode.MONTHS and triggers the recalculation of the breaks in the
	 * course of appearance data model.
	 */
	public void quartersClick() {
		alterGranularityClick(Granularity.QUARTERS);
	}

	/**
	 * The method setCourse() is called by JSF to inject the course data model
	 * into the form. This behaviour is configured in faces-config.xml
	 *
	 * @param course
	 *            Course of appearance data model to be used
	 */
	public void setCourse(Course course) {
		this.course = course;
	}

	/**
	 * The procedure setNumberOfPagesPerIssue() is called by Faces on postbacks
	 * to save the received value of the read-write property
	 * “numberOfPagesPerIssue”.
	 *
	 * @param value
	 *            new value to be stored
	 */
	public void setNumberOfPagesPerIssue(Long value) {
		numberOfPages = value == null ? null : value * course.countIndividualIssues();
	}

	/**
	 * The procedure setSelectedBatchOption() is called by Faces on postbacks to
	 * save the received value of the read-write property “selectedBatchOption”
	 *
	 * @param option
	 *            Granularity level for which batches will be created
	 */
	public void setSelectedBatchOption(String option) {
		try {
			generateBatches = Granularity.valueOf(option.toUpperCase());
		} catch (IllegalArgumentException e) {
			generateBatches = null;
		}
	}

	/**
	 * The procedure weeksClick() is called if the user clicks the button to
	 * select the granularity level “weeks”. It sets the granularity to
	 * BreakMode.WEEKS and triggers the recalculation of the breaks in the
	 * course of appearance data model.
	 */
	public void weeksClick() {
		alterGranularityClick(Granularity.WEEKS);
	}

	/**
	 * The procedure yearsClick() is called if the user clicks the button to
	 * select the granularity level “years”. It sets the granularity to
	 * BreakMode.YEARS and triggers the recalculation of the breaks in the
	 * course of appearance data model.
	 */
	public void yearsClick() {
		alterGranularityClick(Granularity.YEARS);
	}
}
