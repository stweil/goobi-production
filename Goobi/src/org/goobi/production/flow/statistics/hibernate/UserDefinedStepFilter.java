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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

import de.sub.goobi.beans.Schritt;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.PaginatingCriteria;

/**
 * This filter replaces the filter, which was integrated in class
 * AktuelleSchritteForm ... the purpose of refactoring was the goal to access
 * filter functions on the level of processes, which were already implemented in
 * UserDefinedFilter and combine them for the step filter.
 * 
 * 
 * @author Wulf Riebensahm
 * 
 */
public class UserDefinedStepFilter implements IEvaluableFilter, Cloneable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7134772860962768932L;
	private String myFilter = null;
	private WeakReference<Criteria> myCriteria = null;
	private ArrayList<Integer> myIds = null;
	private Dispatcher myObservable;
	private Boolean stepOpenOnly = false;
	private boolean userAssignedStepsOnly = false;
	private boolean clearSession = false;
	
	public UserDefinedStepFilter(boolean clearSession) {
		this.clearSession = clearSession;
	}
	
	
	/*
	 * setting basic filter modes
	 */
	public void setFilterModes(Boolean stepOpenOnly, boolean userAssignedStepsOnly) {
		myCriteria = null;
		this.stepOpenOnly = stepOpenOnly;
		this.userAssignedStepsOnly = userAssignedStepsOnly;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.goobi.production.flow.statistics.hibernate.IEvaluableFilter#getCriteria
	 * ()
	 */
	@Override
	public Criteria getCriteria() {
		// myCriteria is a WeakReference ... both cases needs to be evaluated,
		// after gc the WeakReference
		// object is still referenced but not the object referenced by it
		if (myCriteria == null || myCriteria.get() == null) {
			if (myIds == null) {
				if (myFilter != null) {
					myCriteria = new WeakReference<Criteria>(createCriteriaFromFilterString(myFilter));
				}
			} else {
				myCriteria = new WeakReference<Criteria>(createCriteriaFromIDList());
			}
		}

		return myCriteria.get();
	}

	private Criteria createCriteriaFromIDList() {
		Session session = Helper.getHibernateSession();
		Criteria crit = new PaginatingCriteria(Schritt.class, session);
		crit.add(Restrictions.in("id", myIds));
		return crit;
	}

	private Criteria createCriteriaFromFilterString(String filter) {
		Session session = Helper.getHibernateSession();

		PaginatingCriteria crit = new PaginatingCriteria(Schritt.class, session);

		/*
		 * -------------------------------- combine all parameters together this
		 * part was exported to FilterHelper so that other Filters could access
		 * it --------------------------------
		 */

		// following was moved to Filter Helper
		// limitToUserAssignedSteps(crit);

		String message = FilterHelper.criteriaBuilder(session, myFilter, crit, null, null, stepOpenOnly, userAssignedStepsOnly, clearSession);

		if (message.length() > 0) {
			myObservable.setMessage(message);
		}

		return crit;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.goobi.production.flow.statistics.hibernate.IEvaluableFilter#getIDList
	 * ()
	 */
	@Override
	public List<Integer> getIDList() {
		throw new UnsupportedOperationException("The class " + this.getClass().getName() + " does not implement getIDList() ");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.goobi.production.flow.statistics.hibernate.IEvaluableFilter#getName()
	 */
	@Override
	public String getName() {
		throw new UnsupportedOperationException("The class " + this.getClass().getName() + " does not implement getName() ");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.goobi.production.flow.statistics.hibernate.IEvaluableFilter#getObservable
	 * ()
	 */
	@Override
	public Observable getObservable() {

		if (myObservable == null) {
			myObservable = new Dispatcher();
		}
		return myObservable;
	}

	/*
	 * this internal class is extending the Observable Class and dispatches a
	 * message to the Observers
	 */
	private static class Dispatcher extends Observable {

		private void setMessage(String message) {
			super.setChanged();
			super.notifyObservers(message);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.goobi.production.flow.statistics.hibernate.IEvaluableFilter#setFilter
	 * (java.lang.String)
	 */
	@Override
	public void setFilter(String filter) {
		myCriteria = null;
		myFilter = filter;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.goobi.production.flow.statistics.hibernate.IEvaluableFilter#setName
	 * (java.lang.String)
	 */
	@Override
	public void setName(String name) {
		throw new UnsupportedOperationException("The class " + this.getClass().getName() + " does not implement setName() ");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.goobi.production.flow.statistics.hibernate.IEvaluableFilter#setSQL
	 * (java.lang.String)
	 */
	@Override
	public void setSQL(String sqlString) {
		throw new UnsupportedOperationException("The class " + this.getClass().getName() + " does not implement setSQL() ");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.goobi.production.flow.statistics.hibernate.IEvaluableFilter#stepDone
	 * ()
	 */
	@Override
	public Integer stepDone() {
		throw new UnsupportedOperationException("The class " + this.getClass().getName() + " does not implement stepDone() ");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.goobi.production.flow.statistics.IDataSource#getSourceData()
	 */
	@Override
	public List<Object> getSourceData() {
		throw new UnsupportedOperationException("The class " + this.getClass().getName() + " does not implement getSourceData() ");
	}

	@Override
	public UserDefinedStepFilter clone() {
		throw new UnsupportedOperationException("The class " + this.getClass().getName() + " does not implement clone() ");

	}

	@Override
	public String stepDoneName() {
		throw new UnsupportedOperationException("The class " + this.getClass().getName() + " does not implement stepDoneName() ");
	}

	

}
