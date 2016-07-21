package de.sub.goobi.helper;

/**
 * This file is part of the Goobi Application - a Workflow tool for the support of mass digitization.
 * 
 * Visit the websites for more information. 
 *     		- http://www.kitodo.org
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
import org.apache.log4j.Logger;
import org.hibernate.Session;

import de.sub.goobi.beans.Prozess;
import de.sub.goobi.beans.Schritt;
import de.sub.goobi.persistence.HibernateUtilOld;

public class RefreshObject {
	private static final Logger logger = Logger.getLogger(RefreshObject.class);

	public static void refreshProcess(int processID) {
		if(logger.isDebugEnabled()){
			logger.debug("refreshing process with id " + processID);
		}
		try {
			Session session = HibernateUtilOld.getSessionFactory().openSession();
			if (session != null) {
				if(logger.isDebugEnabled()){
					logger.debug("session is connected: " + session.isConnected());
					logger.debug("session is open: " + session.isOpen());
				}
			} else {
				logger.debug("session is null");
			}
			if ((session == null) || (!session.isOpen()) || (!session.isConnected())) {
				logger.debug("found no open session, don't refresh the process");
				if (session != null) {
					session.close();
					logger.debug("closed session");
				}
				return;
			}

			logger.debug("created a new session");
			Prozess o = (Prozess) session.get(Prozess.class, Integer.valueOf(processID));
			logger.debug("loaded process");
			session.refresh(o);
			logger.debug("refreshed process");
			session.close();
			logger.debug("closed session");
		} catch (Throwable e) {
			logger.error("cannot refresh process with id " + processID);
		}
	}

	public static void refreshProcess_GUI(int processID) {
		if(logger.isDebugEnabled()){
			logger.debug("refreshing process with id " + processID);
		}
		try {
			Session session = Helper.getHibernateSession();
			if (session == null || !session.isOpen() || !session.isConnected()) {
				logger.debug("session is closed, creating a new session");
				HibernateUtilOld.rebuildSessionFactory();
				session = HibernateUtilOld.getSessionFactory().openSession();
			}
			Prozess o = (Prozess) session.get(Prozess.class, processID);
			logger.debug("loaded process");
			session.refresh(o);
			logger.debug("refreshed process");
			// session.close();
			// logger.debug("closed session");
		} catch (Throwable e) {
			logger.error("cannot refresh process with id " + processID);
		}
	}

	public static void refreshStep(int stepID) {
		try {

			Session session = HibernateUtilOld.getSessionFactory().openSession();
			Schritt o = (Schritt) session.get(Schritt.class, stepID);
			session.refresh(o);
			session.close();
		} catch (Exception e) {
			logger.error("cannot refresh step with id " + stepID);
		}

	}

}
