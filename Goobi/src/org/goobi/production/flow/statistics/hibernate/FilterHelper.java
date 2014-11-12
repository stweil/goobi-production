package org.goobi.production.flow.statistics.hibernate;

/**
 * This file is part of the Goobi Application - a Workflow tool for the support of mass digitization.
 * 
 * Visit the websites for more information. 
 *     		- http://www.goobi.org
 *     		- http://launchpad.net/goobi-production
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.text.StrTokenizer;
import org.apache.log4j.Logger;
import org.goobi.production.flow.IlikeExpression;
import org.goobi.production.flow.statistics.hibernate.UserDefinedFilter.Parameters;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Conjunction;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;


import de.sub.goobi.beans.Benutzer;
import de.sub.goobi.beans.Projekt;
import de.sub.goobi.beans.Prozess;
import de.sub.goobi.beans.Schritt;
import de.sub.goobi.config.ConfigMain;
import de.sub.goobi.forms.LoginForm;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.PaginatingCriteria;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.persistence.BenutzerDAO;

/**
 * class provides methods used by implementations of IEvaluableFilter
 * 
 * @author Wulf Riebensahm
 * 
 */
public class FilterHelper {

	private static final Logger logger = Logger.getLogger(FilterHelper.class);

	/**
	 * limit query to project (formerly part of ProzessverwaltungForm)
	 * 
	 * @param crit
	 */
	protected static void limitToUserAccessRights(Conjunction con) {
		/* restriction to specific projects if not with admin rights */
		LoginForm loginForm = (LoginForm) Helper.getManagedBeanValue("#{LoginForm}");
		Benutzer aktuellerNutzer = null;
		try {
			if (loginForm != null && loginForm.getMyBenutzer() != null) {
				aktuellerNutzer = new BenutzerDAO().get(loginForm.getMyBenutzer().getId());
			}
		} catch (DAOException e) {
			logger.warn("DAOException", e);
		} catch (Exception e) {
			logger.trace("Exception", e);
		}
		if (aktuellerNutzer != null) {
			if (loginForm.getMaximaleBerechtigung() > 1) {
				Disjunction dis = Restrictions.disjunction();
				for (Projekt proj : aktuellerNutzer.getProjekteList()) {
					dis.add(Restrictions.eq("projekt", proj));
				}
				con.add(dis);
			}
		}
	}

	private static void limitToUserAssignedSteps(Conjunction con, Boolean stepOpenOnly, Boolean userAssignedStepsOnly) {
		/* show only open Steps or those in use by current user */

		Session session = Helper.getHibernateSession();
		/* identify current user */
		LoginForm login = (LoginForm) Helper.getManagedBeanValue("#{LoginForm}");
		if (login == null || login.getMyBenutzer() == null) {
			return;
		}
		/* init id-list, preset with item 0 */
		List<Integer> idList = new ArrayList<Integer>();
		idList.add(Integer.valueOf(0));

		/*
		 * -------------------------------- hits by user groups --------------------------------
		 */
		Criteria critGroups = session.createCriteria(Schritt.class);

		if (stepOpenOnly) {
			critGroups.add(Restrictions.eq("bearbeitungsstatus", Integer.valueOf(1)));
		} else if (userAssignedStepsOnly) {
			critGroups.add(Restrictions.eq("bearbeitungsstatus", Integer.valueOf(2)));
			critGroups.add(Restrictions.eq("bearbeitungsbenutzer.id", login.getMyBenutzer().getId()));
		} else {
			critGroups.add(Restrictions.or(Restrictions.eq("bearbeitungsstatus", Integer.valueOf(1)),
					Restrictions.like("bearbeitungsstatus", Integer.valueOf(2))));
		}

		/* only processes which are not templates */
		Criteria temp = critGroups.createCriteria("prozess", "proz");
		critGroups.add(Restrictions.eq("proz.istTemplate", Boolean.valueOf(false)));

		/* only assigned projects */
		temp.createCriteria("projekt", "proj").createCriteria("benutzer", "projektbenutzer");
		critGroups.add(Restrictions.eq("projektbenutzer.id", login.getMyBenutzer().getId()));

		/*
		 * only steps assigned to the user groups the current user is member of
		 */
		critGroups.createCriteria("benutzergruppen", "gruppen").createCriteria("benutzer", "gruppennutzer");
		critGroups.add(Restrictions.eq("gruppennutzer.id", login.getMyBenutzer().getId()));

		/* collecting the hits */
		critGroups.setProjection(Projections.id());
		for (Object o : critGroups.setFirstResult(0).setMaxResults(Integer.MAX_VALUE).list()) {
			idList.add((Integer) o);
		}
	

		/*
		 * -------------------------------- Users only --------------------------------
		 */
		Criteria critUser = session.createCriteria(Schritt.class);

		if (stepOpenOnly) {
			critUser.add(Restrictions.eq("bearbeitungsstatus", Integer.valueOf(1)));
		} else if (userAssignedStepsOnly) {
			critUser.add(Restrictions.eq("bearbeitungsstatus", Integer.valueOf(2)));
			critUser.add(Restrictions.eq("bearbeitungsbenutzer.id", login.getMyBenutzer().getId()));
		} else {
			critUser.add(Restrictions.or(Restrictions.eq("bearbeitungsstatus", Integer.valueOf(1)),
					Restrictions.like("bearbeitungsstatus", Integer.valueOf(2))));
		}

		/* exclude templates */
		Criteria temp2 = critUser.createCriteria("prozess", "proz");
		critUser.add(Restrictions.eq("proz.istTemplate", Boolean.valueOf(false)));

		/* check project assignment */
		temp2.createCriteria("projekt", "proj").createCriteria("benutzer", "projektbenutzer");
		critUser.add(Restrictions.eq("projektbenutzer.id", login.getMyBenutzer().getId()));

		/* only steps where the user is assigned to */
		critUser.createCriteria("benutzer", "nutzer");
		critUser.add(Restrictions.eq("nutzer.id", login.getMyBenutzer().getId()));

		/* collecting the hits */

		critUser.setProjection(Projections.id());
		for (Object o : critUser.setFirstResult(0).setMaxResults(Integer.MAX_VALUE).list()) {
			idList.add((Integer) o);
		}
	

		/*
		 * -------------------------------- only taking the hits by restricting to the ids --------------------------------
		 */
		con.add(Restrictions.in("id", idList));
	}

	/**
	 * This functions extracts the Integer from the parameters passed with the step filter in first position.
	 * 
	 * @param String
	 *            parameter
	 * @author Wulf Riebensahm
	 * @return Integer
	 ****************************************************************************/
	protected static Integer getStepStart(String parameter) {
		String[] strArray = parameter.split("-");
		return Integer.parseInt(strArray[0]);
	}

	/**
	 * This functions extracts the Integer from the parameters passed with the step filter in last position.
	 * 
	 * @param String
	 *            parameter
	 * @author Wulf Riebensahm
	 * @return Integer
	 ****************************************************************************/
	protected static Integer getStepEnd(String parameter) {
		String[] strArray = parameter.split("-");
		return Integer.parseInt(strArray[1]);
	}

	/**
	 * This function analyzes the parameters on a step filter and returns a StepFilter enum to direct further processing it reduces the necessity to
	 * apply some filter keywords
	 * 
	 * @param String
	 *            parameters
	 * @author Wulf Riebensahm
	 * @return StepFilter
	 ****************************************************************************/
	protected static StepFilter getStepFilter(String parameters) {

		if (parameters.contains("-")) {
			String[] strArray = parameters.split("-");
			if (!(strArray.length < 2)) {
				if (strArray[0].length() == 0) {
					return StepFilter.max;
				} else {
					return StepFilter.range;
				}
			} else {
				return StepFilter.min;
			}
		} else if (!parameters.contains("-")) {
			try {
				// check if parseInt throws an exception
				Integer.parseInt(parameters);
				return StepFilter.exact;
			} catch (NumberFormatException e) {
				return StepFilter.name;
			}
		}
		return StepFilter.unknown;
	}

	/**
	 * This enum represents the result of parsing the step<modifier>: filter Restrictions
	 ****************************************************************************/
	protected static enum StepFilter {
		exact, range, min, max, name, unknown
	}

	/**
	 * Filter processes for done steps range
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 * @param inStatus
	 *            {@link StepStatus} of searched step
	 ****************************************************************************/
	protected static void filterStepRange(Conjunction con, String parameters, StepStatus inStatus, boolean negate, String prefix) {
		if (!negate) {
			con.add(Restrictions.and(
					Restrictions.and(Restrictions.ge(prefix + "reihenfolge", FilterHelper.getStepStart(parameters)),
							Restrictions.le(prefix + "reihenfolge", FilterHelper.getStepEnd(parameters))),
					Restrictions.eq(prefix + "bearbeitungsstatus", inStatus.getValue().intValue())));
		} else {
			con.add(Restrictions.not(Restrictions.and(
					Restrictions.and(Restrictions.ge(prefix + "reihenfolge", FilterHelper.getStepStart(parameters)),
							Restrictions.le(prefix + "reihenfolge", FilterHelper.getStepEnd(parameters))),
					Restrictions.eq(prefix + "bearbeitungsstatus", inStatus.getValue().intValue()))));
		}
	}

	/**
	 * Filter processes for steps name with given status
	 * 
	 * @param inStatus
	 *            {@link StepStatus} of searched step
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param parameters
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterStepName(Conjunction con, String parameters, StepStatus inStatus, boolean negate, String prefix) {
		if (con == null) {
			con = Restrictions.conjunction();
		}
		if (!negate) {
			con.add(Restrictions.and(Restrictions.like(prefix + "titel", "%" + parameters + "%"),
					Restrictions.eq(prefix + "bearbeitungsstatus", inStatus.getValue().intValue())));
		} else {
			con.add(Restrictions.not(Restrictions.and(Restrictions.like(prefix + "titel", "%" + parameters + "%"),
					Restrictions.eq(prefix + "bearbeitungsstatus", inStatus.getValue().intValue()))));
		}
	}

	/**
	 * Filter processes for steps name with given status
	 * 
	 * @param inStatus
	 *            {@link StepStatus} of searched step
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param parameters
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterAutomaticSteps(Conjunction con, String tok, boolean flagSteps) {
		if (con == null) {
			con = Restrictions.conjunction();
		}
		if (!flagSteps) {
			if (tok.substring(tok.indexOf(":") + 1).equalsIgnoreCase("true")) {
				con.add(Restrictions.eq("steps.typAutomatisch", true));
			} else {
				con.add(Restrictions.eq("steps.typAutomatisch", false));
			}
		} else {
			if (tok.substring(tok.indexOf(":") + 1).equalsIgnoreCase("true")) {
				con.add(Restrictions.eq("typAutomatisch", true));
			} else {
				con.add(Restrictions.eq("typAutomatisch", false));
			}
		}
	}

	/**
	 * Filter processes for done steps min
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param parameters
	 *            part of filter string to use
	 * @param inStatus
	 *            {@link StepStatus} of searched step
	 ****************************************************************************/
	protected static void filterStepMin(Conjunction con, String parameters, StepStatus inStatus, boolean negate, String prefix) {
		if (con == null) {
			con = Restrictions.conjunction();
		}
		if (!negate) {
			con.add(Restrictions.and(Restrictions.ge(prefix + "reihenfolge", FilterHelper.getStepStart(parameters)),
					Restrictions.eq(prefix + "bearbeitungsstatus", inStatus.getValue().intValue())));
		} else {
			con.add(Restrictions.not(Restrictions.and(Restrictions.ge(prefix + "reihenfolge", FilterHelper.getStepStart(parameters)),
					Restrictions.eq(prefix + "bearbeitungsstatus", inStatus.getValue().intValue()))));
		}
	}

	/**
	 * Filter processes for done steps max
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param parameters
	 *            part of filter string to use
	 * @param inStatus
	 *            {@link StepStatus} of searched step
	 ****************************************************************************/
	protected static void filterStepMax(Conjunction con, String parameters, StepStatus inStatus, boolean negate, String prefix) {
		if (con == null) {
			con = Restrictions.conjunction();
		}
		if (!negate) {
			con.add(Restrictions.and(Restrictions.le(prefix + "reihenfolge", FilterHelper.getStepEnd(parameters)),
					Restrictions.eq(prefix + "bearbeitungsstatus", inStatus.getValue().intValue())));
		} else {
			con.add(Restrictions.not(Restrictions.and(Restrictions.le(prefix + "reihenfolge", FilterHelper.getStepEnd(parameters)),
					Restrictions.eq(prefix + "bearbeitungsstatus", inStatus.getValue().intValue()))));
		}
	}

	/**
	 * Filter processes for done steps exact
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param parameters
	 *            part of filter string to use
	 * @param inStatus
	 *            {@link StepStatus} of searched step
	 ****************************************************************************/
	protected static void filterStepExact(Conjunction con, String parameters, StepStatus inStatus, boolean negate, String prefix) {
		if (!negate) {
			con.add(Restrictions.and(Restrictions.eq(prefix + "reihenfolge", FilterHelper.getStepStart(parameters)),
					Restrictions.eq(prefix + "bearbeitungsstatus", inStatus.getValue().intValue())));
		} else {
			con.add(Restrictions.not(Restrictions.and(Restrictions.eq(prefix + "reihenfolge", FilterHelper.getStepStart(parameters)),
					Restrictions.eq(prefix + "bearbeitungsstatus", inStatus.getValue().intValue()))));
		}
	}

	/**
	 * Filter processes for done steps by user
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterStepDoneUser(Conjunction con, String tok) {
		/*
		 * filtering by a certain done step, which the current user finished
		 */
		String login = tok.substring(tok.indexOf(":") + 1);
		con.add(Restrictions.eq("user.login", login));
	}

	/**
	 * Filter processes by project
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterProject(Conjunction con, String tok, boolean negate) {
		/* filter according to linked project */
		if (!negate) {
			con.add(Restrictions.like("proj.titel", "%" + tok.substring(tok.indexOf(":") + 1) + "%"));
		} else {
			con.add(Restrictions.not(Restrictions.like("proj.titel", "%" + tok.substring(tok.indexOf(":") + 1) + "%")));
		}
	}

	/**
	 * Filter processes by scan template
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterScanTemplate(Conjunction con, String tok, boolean negate) {
		/* Filtering by signature */
		String[] ts = tok.substring(tok.indexOf(":") + 1).split(":");
		if (!negate) {
			if (ts.length > 1) {
				con.add(Restrictions.and(Restrictions.like("vorleig.wert", "%" + ts[1] + "%"), Restrictions.like("vorleig.titel", "%" + ts[0] + "%")));
			} else {
				con.add(Restrictions.like("vorleig.wert", "%" + ts[0] + "%"));
			}
		} else {
			if (ts.length > 1) {
				con.add(Restrictions.not(Restrictions.and(Restrictions.like("vorleig.wert", "%" + ts[1] + "%"),
						Restrictions.like("vorleig.titel", "%" + ts[0] + "%"))));
			} else {
				con.add(Restrictions.not(Restrictions.like("vorleig.wert", "%" + ts[0] + "%")));
			}
		}
	}

	protected static void filterStepProperty(Conjunction con, String tok, boolean negate) {
		/* Filtering by signature */
		String[] ts = tok.substring(tok.indexOf(":") + 1).split(":");
		if (!negate) {
			if (ts.length > 1) {
				con.add(Restrictions.and(Restrictions.like("schritteig.wert", "%" + ts[1] + "%"),
						Restrictions.like("schritteig.titel", "%" + ts[0] + "%")));
			} else {
				con.add(Restrictions.like("schritteig.wert", "%" + ts[0] + "%"));
			}
		} else {
			if (ts.length > 1) {
				con.add(Restrictions.not(Restrictions.and(Restrictions.like("schritteig.wert", "%" + ts[1] + "%"),
						Restrictions.like("schritteig.titel", "%" + ts[0] + "%"))));
			} else {
				con.add(Restrictions.not(Restrictions.like("schritteig.wert", "%" + ts[0] + "%")));
			}
		}
	}

	protected static void filterProcessProperty(Conjunction con, String tok, boolean negate) {
		/* Filtering by signature */
		/* Filtering by signature */
		String[] ts = tok.substring(tok.indexOf(":") + 1).split(":");
		if (!negate) {
			if (ts.length > 1) {
				con.add(Restrictions.and(Restrictions.like("prozesseig.wert", "%" + ts[1] + "%"),
						Restrictions.like("prozesseig.titel", "%" + ts[0] + "%")));
			} else {
				con.add(Restrictions.like("prozesseig.wert", "%" + ts[0] + "%"));
			}
		} else {
			if (ts.length > 1) {
				con.add(Restrictions.not(Restrictions.and(Restrictions.like("prozesseig.wert", "%" + ts[1] + "%"),
						Restrictions.like("prozesseig.titel", "%" + ts[0] + "%"))));
			} else {
				con.add(Restrictions.not(Restrictions.like("prozesseig.wert", "%" + ts[0] + "%")));
			}
		}
	}

	/**
	 * Filter processes by Ids
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterIds(Conjunction con, String tok) {
		/* filtering by ids */
		List<Integer> listIds = new ArrayList<Integer>();
		if (tok.substring(tok.indexOf(":") + 1).length() > 0) {
			String[] tempids = tok.substring(tok.indexOf(":") + 1).split(" ");
			for (int i = 0; i < tempids.length; i++) {
				try {
					int tempid = Integer.parseInt(tempids[i]);
					listIds.add(tempid);
				} catch (NumberFormatException e) {
					Helper.setFehlerMeldung(tempids[i] + Helper.getTranslation("NumberFormatError"));
				}
			}
		}
		if (listIds.size() > 0) {
			con.add(Restrictions.in("id", listIds));
		}
	}

	/**
	 * Filter processes by workpiece
	 * 
	 * @param crit
	 *            {@link Criteria} to extend
	 * @param tok
	 *            part of filter string to use
	 ****************************************************************************/
	protected static void filterWorkpiece(Conjunction con, String tok, boolean negate) {
		/* filter according signature */
		String[] ts = tok.substring(tok.indexOf(":") + 1).split(":");
		if (!negate) {
			if (ts.length > 1) {
				con.add(Restrictions.and(Restrictions.like("werkeig.wert", "%" + ts[1] + "%"), Restrictions.like("werkeig.titel", "%" + ts[0] + "%")));
			} else {

				con.add(Restrictions.like("werkeig.wert", "%" + ts[0] + "%"));
			}
		} else {
			if (ts.length > 1) {
				con.add(Restrictions.not(Restrictions.and(Restrictions.like("werkeig.wert", "%" + ts[1] + "%"),
						Restrictions.like("werkeig.titel", "%" + ts[0] + "%"))));
			} else {

				con.add(Restrictions.not(Restrictions.like("werkeig.wert", "%" + ts[0] + "%")));
			}
		}
	}

	/**
	 * This method builds a criteria depending on a filter string and some other parameters passed on along the initial criteria. The filter is parsed
	 * and depending on which data structures are used for applying filtering restrictions conjunctions are formed and collect the restrictions and
	 * then will be applied on the corresponding criteria. A criteria is only added if needed for the presence of filters applying to it.
	 * 
	 * 
	 * @param inFilter
	 * @param crit
	 * @param isTemplate
	 * @param returnParameters
	 *            Object containing values which need to be set and returned to UserDefinedFilter
	 * @param userAssignedStepsOnly
	 * @param stepOpenOnly
	 * @return String used to pass on error messages about errors in the filter expression
	 */
	public static String criteriaBuilder(Session session, String inFilter, PaginatingCriteria crit, Boolean isTemplate,
			Parameters returnParameters, Boolean stepOpenOnly, Boolean userAssignedStepsOnly, boolean clearSession) {

		if (ConfigMain.getBooleanParameter("DatabaseAutomaticRefreshList", true) && clearSession) {
			session.clear();
		}
		// for ordering the lists there are some
		// criteria, which needs to be added even no
		// restrictions apply, to avoid multiple analysis
		// of the criteria it is only done here once and
		// to set flags which are subsequently used
		Boolean flagSteps = false;
		Boolean flagProcesses = false;
		@SuppressWarnings("unused")
		Boolean flagSetCritProjects = false;
		String filterPrefix = "";
		if (crit.getClassName().equals(Prozess.class.getName())) {
			flagProcesses = true;
			filterPrefix = "steps.";
		}

		if (crit.getClassName().equals(Schritt.class.getName())) {
			flagSteps = true;
		}

		// keeping a reference to the passed criteria
		Criteria inCrit = crit;
		@SuppressWarnings("unused")
		Criteria critProject = null;
		Criteria critProcess = null;

		// to collect and return feedback about erroneous use of filter
		// expressions
		String message = new String("");

		StrTokenizer tokenizer = new StrTokenizer(inFilter, ' ', '\"');

		// conjunctions collecting conditions
		Conjunction conjWorkPiece = null;
		Conjunction conjProjects = null;
		Conjunction conjSteps = null;
		Conjunction conjProcesses = null;
		Conjunction conjTemplates = null;
		Conjunction conjUsers = null;
		Conjunction conjStepProperties = null;
		Conjunction conjProcessProperties = null;

		// this is needed if we filter processes
		if (flagProcesses) {
			conjProjects = Restrictions.conjunction();
			limitToUserAccessRights(conjProjects);
			// in case nothing is set here it needs to be removed again
			// happens if user has admin rights
			if (conjProjects.toString().equals("()")) {
				conjProjects = null;
				flagSetCritProjects = true;
			}
		}

		// this is needed if we filter steps
		if (flagSteps) {
			conjSteps = Restrictions.conjunction();
			limitToUserAssignedSteps(conjSteps, stepOpenOnly, userAssignedStepsOnly);
			// in case nothing is set here conjunction needs to be set to null
			// again
			if (conjSteps.toString().equals("()")) {
				conjSteps = null;
			}
		}

		// this is needed for the template filter (true) and the undefined
		// processes filter (false) in any other case it needs to be null
		if (isTemplate != null) {
			conjProcesses = Restrictions.conjunction();
			if (!isTemplate) {
				conjProcesses.add(Restrictions.eq("istTemplate", Boolean.valueOf(false)));
			} else {
				conjProcesses.add(Restrictions.eq("istTemplate", Boolean.valueOf(true)));
			}
		}
		
		// this is needed for evaluating a filter string
		while (tokenizer.hasNext()) {
			String tok = tokenizer.nextToken().trim();

			if (tok.toLowerCase().startsWith(FilterString.PROCESSPROPERTY) || tok.toLowerCase().startsWith(FilterString.PROZESSEIGENSCHAFT)) {
				if (conjProcessProperties == null) {
					conjProcessProperties = Restrictions.conjunction();
				}
				FilterHelper.filterProcessProperty(conjProcessProperties, tok, false);
			} else if (tok.toLowerCase().startsWith(FilterString.STEPPROPERTY) || tok.toLowerCase().startsWith(FilterString.SCHRITTEIGENSCHAFT)) {
				if (conjStepProperties == null) {
					conjStepProperties = Restrictions.conjunction();
				}
				FilterHelper.filterStepProperty(conjStepProperties, tok, false);
			}

			// search over steps
			// original filter, is left here for compatibility reason
			// doesn't fit into new keyword scheme
			else if (tok.toLowerCase().startsWith(FilterString.STEP) || tok.toLowerCase().startsWith(FilterString.SCHRITT)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + createHistoricFilter(conjSteps, tok, flagSteps);

			} else if (tok.toLowerCase().startsWith(FilterString.STEPINWORK) || tok.toLowerCase().startsWith(FilterString.SCHRITTINARBEIT)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(returnParameters, conjSteps, tok, StepStatus.INWORK, false, filterPrefix));

				// new keyword stepLocked implemented
			} else if (tok.toLowerCase().startsWith(FilterString.STEPLOCKED) || tok.toLowerCase().startsWith(FilterString.SCHRITTINARBEIT)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(returnParameters, conjSteps, tok, StepStatus.LOCKED, false, filterPrefix));

				// new keyword stepOpen implemented
			} else if (tok.toLowerCase().startsWith(FilterString.STEPOPEN) || tok.toLowerCase().startsWith(FilterString.SCHRITTOFFEN)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(returnParameters, conjSteps, tok, StepStatus.OPEN, false, filterPrefix));

				// new keyword stepDone implemented
			} else if (tok.toLowerCase().startsWith(FilterString.STEPDONE) || tok.toLowerCase().startsWith(FilterString.SCHRITTABGESCHLOSSEN)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(returnParameters, conjSteps, tok, StepStatus.DONE, false, filterPrefix));

				// new keyword stepDoneTitle implemented, replacing so far
				// undocumented
			} else if (tok.toLowerCase().startsWith(FilterString.STEPDONETITLE)
					|| tok.toLowerCase().startsWith(FilterString.ABGESCHLOSSENERSCHRITTTITEL)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				String stepTitel = tok.substring(tok.indexOf(":") + 1);
				FilterHelper.filterStepName(conjSteps, stepTitel, StepStatus.DONE, false, filterPrefix);

			} else if (tok.toLowerCase().startsWith(FilterString.STEPDONEUSER)
					|| tok.toLowerCase().startsWith(FilterString.ABGESCHLOSSENERSCHRITTBENUTZER)) {
				if (conjUsers == null) {
					conjUsers = Restrictions.conjunction();
				}
				FilterHelper.filterStepDoneUser(conjUsers, tok);
			} else if (tok.toLowerCase().startsWith(FilterString.STEPAUTOMATIC) || tok.toLowerCase().startsWith(FilterString.SCHRITTAUTOMATISCH)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				FilterHelper.filterAutomaticSteps(conjSteps, tok, flagSteps);
			} else if (tok.toLowerCase().startsWith(FilterString.PROJECT) || tok.toLowerCase().startsWith(FilterString.PROJEKT)) {
				if (conjProjects == null) {
					conjProjects = Restrictions.conjunction();
				}
				FilterHelper.filterProject(conjProjects, tok, false);

			} else if (tok.toLowerCase().startsWith(FilterString.TEMPLATE) || tok.toLowerCase().startsWith(FilterString.VORLAGE)) {
				if (conjTemplates == null) {
					conjTemplates = Restrictions.conjunction();
				}
				FilterHelper.filterScanTemplate(conjTemplates, tok, false);

			} else if (tok.toLowerCase().startsWith(FilterString.ID)) {
				if (conjProcesses == null) {
					conjProcesses = Restrictions.conjunction();
				}
				FilterHelper.filterIds(conjProcesses, tok);

			} else if (tok.toLowerCase().startsWith(FilterString.PROCESS) || tok.toLowerCase().startsWith(FilterString.PROZESS)) {
				if (conjProcesses == null) {
					conjProcesses = Restrictions.conjunction();
				}
				conjProcesses.add(Restrictions.like("titel", "%" + "proc:" + tok.substring(tok.indexOf(":") + 1) + "%"));
			} else if (tok.toLowerCase().startsWith(FilterString.BATCH) || tok.toLowerCase().startsWith(FilterString.GRUPPE)) {
				if (conjProcesses == null) {
					conjProcesses = Restrictions.conjunction();
				}
				int value = Integer.valueOf(tok.substring(tok.indexOf(":") + 1));
				conjProcesses.add(Restrictions.eq("batchID", value));
			} else if (tok.toLowerCase().startsWith(FilterString.WORKPIECE) || tok.toLowerCase().startsWith(FilterString.WERKSTUECK)) {
				if (conjWorkPiece == null) {
					conjWorkPiece = Restrictions.conjunction();
				}
				FilterHelper.filterWorkpiece(conjWorkPiece, tok, false);

			} else if (tok.toLowerCase().startsWith("-" + FilterString.PROCESSPROPERTY)
					|| tok.toLowerCase().startsWith("-" + FilterString.PROZESSEIGENSCHAFT)) {
				if (conjProcessProperties == null) {
					conjProcessProperties = Restrictions.conjunction();
				}
				FilterHelper.filterProcessProperty(conjProcessProperties, tok, true);
			} else if (tok.toLowerCase().startsWith("-" + FilterString.STEPPROPERTY)
					|| tok.toLowerCase().startsWith("-" + FilterString.SCHRITTEIGENSCHAFT)) {
				if (conjStepProperties == null) {
					conjStepProperties = Restrictions.conjunction();
				}
				FilterHelper.filterStepProperty(conjStepProperties, tok, true);
			}

			else if (tok.toLowerCase().startsWith("-" + FilterString.STEPINWORK) || tok.toLowerCase().startsWith("-" + FilterString.SCHRITTINARBEIT)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(returnParameters, conjSteps, tok, StepStatus.INWORK, true, filterPrefix));

				// new keyword stepLocked implemented
			} else if (tok.toLowerCase().startsWith("-" + FilterString.STEPLOCKED)
					|| tok.toLowerCase().startsWith("-" + FilterString.SCHRITTGESPERRT)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(returnParameters, conjSteps, tok, StepStatus.LOCKED, true, filterPrefix));

				// new keyword stepOpen implemented
			} else if (tok.toLowerCase().startsWith("-" + FilterString.STEPOPEN) || tok.toLowerCase().startsWith("-" + FilterString.SCHRITTOFFEN)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(returnParameters, conjSteps, tok, StepStatus.OPEN, true, filterPrefix));

				// new keyword stepDone implemented
			} else if (tok.toLowerCase().startsWith("-" + FilterString.STEPDONE)
					|| tok.toLowerCase().startsWith("-" + FilterString.SCHRITTABGESCHLOSSEN)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				message = message + (createStepFilters(returnParameters, conjSteps, tok, StepStatus.DONE, true, filterPrefix));

				// new keyword stepDoneTitle implemented, replacing so far
				// undocumented
			} else if (tok.toLowerCase().startsWith("-" + FilterString.STEPDONETITLE)
					|| tok.toLowerCase().startsWith("-" + FilterString.ABGESCHLOSSENERSCHRITTTITEL)) {
				if (conjSteps == null) {
					conjSteps = Restrictions.conjunction();
				}
				String stepTitel = tok.substring(tok.indexOf(":") + 1);
				FilterHelper.filterStepName(conjSteps, stepTitel, StepStatus.DONE, true, filterPrefix);

			} else if (tok.toLowerCase().startsWith("-" + FilterString.PROJECT) || tok.toLowerCase().startsWith("-" + FilterString.PROJEKT)) {
				if (conjProjects == null) {
					conjProjects = Restrictions.conjunction();
				}
				FilterHelper.filterProject(conjProjects, tok, true);

			} else if (tok.toLowerCase().startsWith("-" + FilterString.TEMPLATE) || tok.toLowerCase().startsWith("-" + FilterString.VORLAGE)) {
				if (conjTemplates == null) {
					conjTemplates = Restrictions.conjunction();
				}
				FilterHelper.filterScanTemplate(conjTemplates, tok, true);

			} else if (tok.toLowerCase().startsWith("-" + FilterString.WORKPIECE) || tok.toLowerCase().startsWith("-" + FilterString.WERKSTUECK)) {
				if (conjWorkPiece == null) {
					conjWorkPiece = Restrictions.conjunction();
				}
				FilterHelper.filterWorkpiece(conjWorkPiece, tok, true);

			} else if (tok.toLowerCase().startsWith("-")) {

				if (conjProcesses == null) {
					conjProcesses = Restrictions.conjunction();
				}

				conjProcesses.add(Restrictions.not(Restrictions.like("titel", "%" + tok.substring(1) + "%")));

			} else {

				/* standard-search parameter */
				if (conjProcesses == null) {
					conjProcesses = Restrictions.conjunction();
				}

				conjProcesses.add(IlikeExpression.ilike("titel", "*" + tok + "*", '!'));
			}
		}

		if (conjProcesses != null || flagSteps) {
			if (!flagProcesses) {

				critProcess = crit.createCriteria("prozess", "proc");

				if (conjProcesses != null) {
					critProcess.add(conjProcesses);
				}
			} else {
				if (conjProcesses != null) {
					inCrit.add(conjProcesses);
				}
			}
		}

		if (flagSteps) {

			critProject = critProcess.createCriteria("projekt", "proj");

			if (conjProjects != null) {
				inCrit.add(conjProjects);
			}
		} else {
			inCrit.createCriteria("projekt", "proj");
			if (conjProjects != null) {
				inCrit.add(conjProjects);
			}
		}

		if (conjSteps != null) {
			if (!flagSteps) {
				crit.createCriteria("schritte", "steps");
				crit.add(conjSteps);
			} else {
				inCrit.add(conjSteps);
			}
		}

		if (conjTemplates != null) {
			if (flagSteps) {
				critProcess.createCriteria("vorlagen", "vorl");
				critProcess.createAlias("vorl.eigenschaften", "vorleig");
				critProcess.add(conjTemplates);
			} else {
				crit.createCriteria("vorlagen", "vorl");
				crit.createAlias("vorl.eigenschaften", "vorleig");
				inCrit.add(conjTemplates);
			}
		}

		if (conjProcessProperties != null) {
			if (flagSteps) {
				critProcess.createAlias("proc.eigenschaften", "prozesseig");
				critProcess.add(conjProcessProperties);
			} else {

				inCrit.createAlias("eigenschaften", "prozesseig");
				inCrit.add(conjProcessProperties);
			}
		}

		if (conjStepProperties != null) {
			if (!flagSteps) {
				Criteria stepCrit = session.createCriteria(Prozess.class);
				stepCrit.createCriteria("schritte", "steps");
				stepCrit.createAlias("steps.eigenschaften", "schritteig");
				stepCrit.add(conjStepProperties);
				stepCrit.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
				List<Integer> myIds = new ArrayList<Integer>();

				for (@SuppressWarnings("unchecked")
				Iterator<Prozess> it = stepCrit.setFirstResult(0).setMaxResults(Integer.MAX_VALUE).list().iterator(); it.hasNext();) {
					Prozess p = it.next();
					myIds.add(p.getId());
				}
				crit.add(Restrictions.in("id", myIds));
			} else {
				critProcess.createAlias("steps.eigenschaften", "schritteig");
				inCrit.add(conjStepProperties);
			}
		}

		if (conjWorkPiece != null) {
			if (flagSteps) {
				critProcess.createCriteria("werkstuecke", "werk");
				critProcess.createAlias("werk.eigenschaften", "werkeig");
				critProcess.add(conjWorkPiece);
			} else {
				inCrit.createCriteria("werkstuecke", "werk");
				inCrit.createAlias("werk.eigenschaften", "werkeig");
				inCrit.add(conjWorkPiece);
			}
		}
		if (conjUsers != null) {
			if (flagSteps) {
				critProcess.createCriteria("bearbeitungsbenutzer", "user");
				critProcess.add(conjUsers);
			} else {
				inCrit.createAlias("steps.bearbeitungsbenutzer", "user");
				inCrit.add(conjUsers);
			}
		}
		return message;
	}

	/**
	 * 
	 * @param conjSteps
	 * @param filterPart
	 * @return
	 */
	private static String createHistoricFilter(Conjunction conjSteps, String filterPart, Boolean stepCriteria) {
		/* filtering by a certain minimal status */
		Integer stepReihenfolge;

		String stepTitle = filterPart.substring(filterPart.indexOf(":") + 1);
		// if the criteria is build on steps the table need not be identified
		String tableIdentifier;
		if (stepCriteria) {
			tableIdentifier = "";
		} else {
			tableIdentifier = "steps.";
		}
		try {
			stepReihenfolge = Integer.parseInt(stepTitle);
		} catch (NumberFormatException e) {
			stepTitle = filterPart.substring(filterPart.indexOf(":") + 1);
			if (stepTitle.startsWith("-")) {
				stepTitle = stepTitle.substring(1);
				conjSteps.add(Restrictions.and(Restrictions.not(Restrictions.like(tableIdentifier + "titel", "%" + stepTitle + "%")),
						Restrictions.ge(tableIdentifier + "bearbeitungsstatus", StepStatus.OPEN.getValue())));
				return "";
			} else {
				conjSteps.add(Restrictions.and(Restrictions.like(tableIdentifier + "titel", "%" + stepTitle + "%"),
						Restrictions.ge(tableIdentifier + "bearbeitungsstatus", StepStatus.OPEN.getValue())));
				return "";
			}
		}
		conjSteps.add(Restrictions.and(Restrictions.eq(tableIdentifier + "reihenfolge", stepReihenfolge),
				Restrictions.ge(tableIdentifier + "bearbeitungsstatus", StepStatus.OPEN.getValue())));
		return "";
	}

	/************************************************************************************
	 * @param flagCriticalQuery
	 * @param crit
	 * @param parameters
	 * @return
	 ************************************************************************************/
	private static String createStepFilters(Parameters returnParameters, Conjunction con, String filterPart, StepStatus inStatus, boolean negate,
			String filterPrefix) {
		// extracting the substring into parameter (filter parameters e.g. 5,
		// -5,
		// 5-10, 5- or "Qualitätssicherung")

		String parameters = filterPart.substring(filterPart.indexOf(":") + 1);
		String message = "";
		/*
		 * -------------------------------- Analyzing the parameters and what user intended (5->exact, -5 ->max, 5-10 ->range, 5- ->min.,
		 * Qualitätssicherung ->name) handling the filter according to the parameters --------------------------------
		 */

		switch (FilterHelper.getStepFilter(parameters)) {

		case exact:
			try {
				FilterHelper.filterStepExact(con, parameters, inStatus, negate, filterPrefix);
				returnParameters.setStepDone(FilterHelper.getStepStart(parameters));
			} catch (NullPointerException e) {
				message = "stepdone is preset, don't use 'step' filters";
			} catch (Exception e) {
				logger.error(e);
				message = "filterpart '" + filterPart.substring(filterPart.indexOf(":") + 1) + "' in '" + filterPart + "' caused an error\n";
			}
			break;

		case max:
			try {
				FilterHelper.filterStepMax(con, parameters, inStatus, negate, filterPrefix);
				returnParameters.setCriticalQuery();
			} catch (NullPointerException e) {
				message = "stepdone is preset, don't use 'step' filters";
			} catch (Exception e) {
				message = "filterpart '" + filterPart.substring(filterPart.indexOf(":") + 1) + "' in '" + filterPart + "' caused an error\n";
			}
			break;

		case min:
			try {
				FilterHelper.filterStepMin(con, parameters, inStatus, negate, filterPrefix);
				returnParameters.setCriticalQuery();
			} catch (NullPointerException e) {
				message = "stepdone is preset, don't use 'step' filters";
			} catch (Exception e) {
				message = "filterpart '" + filterPart.substring(filterPart.indexOf(":") + 1) + "' in '" + filterPart + "' caused an error\n";
			}
			break;

		case name:
			/* filter for a specific done step by it's name (Titel) */
			// myObservable.setMessage("Filter 'stepDone:" + parameters
			// + "' is not yet implemented and will be ignored!");
			try {
				FilterHelper.filterStepName(con, parameters, inStatus, negate, filterPrefix);
			} catch (NullPointerException e) {
				message = "stepdone is preset, don't use 'step' filters";
			} catch (Exception e) {
				message = "filterpart '" + filterPart.substring(filterPart.indexOf(":") + 1) + "' in '" + filterPart + "' caused an error\n";
			}
			break;

		case range:
			try {
				FilterHelper.filterStepRange(con, parameters, inStatus, negate, filterPrefix);
				returnParameters.setCriticalQuery();
			} catch (NullPointerException e) {
				message = "stepdone is preset, don't use 'step' filters";
			} catch (NumberFormatException e) {
				try {
					FilterHelper.filterStepName(con, parameters, inStatus, negate, filterPrefix);
				} catch (NullPointerException e1) {
					message = "stepdone is preset, don't use 'step' filters";
				} catch (Exception e1) {
					message = "filterpart '" + filterPart.substring(filterPart.indexOf(":") + 1) + "' in '" + filterPart + "' caused an error\n";
				}
			} catch (Exception e) {
				message = "filterpart '" + filterPart.substring(filterPart.indexOf(":") + 1) + "' in '" + filterPart + "' caused an error\n";
			}
			break;

		case unknown:
			message = message + ("Filter '" + filterPart + "' is not known!\n");
		}
		return message;
	}

}
