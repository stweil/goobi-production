/*
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
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
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

import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.joda.time.Duration;

import de.sub.goobi.config.ConfigMain;
import de.sub.goobi.helper.tasks.EmptyTask.Behaviour;

/**
 * The class TaskSitter takes care of the tasks in the task manager. While the
 * application is working, a scheduler on the TaskManager will call the run()
 * method of the TaskSitter every some seconds to delete threads that have died,
 * replace threads that are to be restarted by new copies of themselves (a
 * Thread can never be started twice) and finally starts some new threads if
 * there aren’t too many working any more. Several limits are configurable for
 * the {@link #run()} method.
 * 
 * On shutdown of the servlet container, the TaskSitter will try to shut down
 * all threads that are still running. Because the TaskManager is singleton (its
 * constructor is private) a caring class is needed which will be available for
 * instantiation to the servlet container.
 * 
 * @author Matthias Ronge &lt;matthias.ronge@zeutschel.de&gt;
 */
public class TaskSitter implements Runnable, ServletContextListener {
	private static final int KEEP_FAILED = 10;
	private static final long KEEP_FAILED_MINS = 250;
	private static final int KEEP_SUCCESSFUL = 3;
	private static final long KEEP_SUCCESSFUL_MINS = 20;

	/**
	 * The field autoRunLimit holds the number of threads which at most are
	 * allowed to be started automatically. It is by default initialised by the
	 * number of available processors of the runtime and set to 0 while the
	 * feature is disabled.
	 */
	private static int autoRunLimit;
	{
		setAutoRunningThreads(true);
	}

	/**
	 * When the servlet is unloaded, i.e. on container shutdown, the TaskManager
	 * shall be shut down gracefully.
	 * 
	 * @see javax.servlet.ServletContextListener#contextDestroyed(javax.servlet.ServletContextEvent)
	 */
	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		TaskManager.shutdownNow();
	}

	/**
	 * Currently, there is nothing to do when the servlet is loading.
	 * 
	 * @see javax.servlet.ServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)
	 */
	@Override
	public void contextInitialized(ServletContextEvent arg0) {
	}

	/**
	 * The function isAutoRunningThreads() returns whether the TaskManager’s
	 * autorun mode is on or not.
	 * 
	 * @return whether the TaskManager is auto-running threads or not
	 */
	public static boolean isAutoRunningThreads() {
		return autoRunLimit > 0;
	}

	/**
	 * The function run() examines the task list, deletes threads that have
	 * died, replaces threads that are to be restarted by new copies of
	 * themselves and finally starts new threads up to the given limit.
	 * 
	 * Several limits are configurable: There are both limits in number and in
	 * time for successfully finished or erroneous threads which can be set in
	 * the configuration. There are internal default values for these settings
	 * too, which will be applied in case of missing configuration entries.
	 * Since zombie processes will still occupy all their resources and aren’t
	 * available for garbage collection, these values have been chosen rather
	 * restrictive. For the limit for auto starting threads, see
	 * {@link #setAutoRunningThreads(boolean)}.
	 * 
	 * If the task list is empty, the method will exit without further delay,
	 * otherwise it will initialise its variables and read the configuration.
	 * Reading the configuration is done again in each iteration so
	 * configuration changes will propagate here.
	 * 
	 * Then the function iterates along the task list and takes care for each
	 * task. To be able to modify the list in passing, we need a
	 * {@link java.util.ListIterator} here.
	 * 
	 * Running tasks reduce the clearance to run new tasks. (However, the
	 * clearance must not become negative.) New tasks will be added to the
	 * launch list, except if they have already been marked for removal, of
	 * course. If a task has terminated, it is handled as specified by its
	 * behaviour variable: All tasks that are marked DELETE_IMMEDIATELY will
	 * instantly be disposed of; otherwise, they will be kept as long as
	 * configured and only be removed if their dead body has become older. Tasks
	 * marked PREPARE_FOR_RESTART will be replaced (because a
	 * {@link java.lang.Thread} cannot be started a second time) by a copy of
	 * them.
	 * 
	 * If a ConcurrentModificationException arises during list examination, the
	 * method will behave like a polite servant and retire silently until the
	 * lordship has scarpered. This is not a pity because it will be started
	 * every some seconds.
	 * 
	 * After having finished iterating, the method will reduce the absolute
	 * number of expired threads as configured. (Since new threads will be added
	 * to the bottom of the list and we therefore want to remove older ones
	 * top-down we cannot do this before we know their count, thus we cannot do
	 * this while iterating.) Last, new threads will be started up to the
	 * remaining available clearance.
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		TaskManager taskManager = TaskManager.singleton();
		if (taskManager.taskList.size() == 0) {
			return;
		}

		LinkedList<EmptyTask> launchableThreads = new LinkedList<EmptyTask>();
		LinkedList<EmptyTask> finishedThreads = new LinkedList<EmptyTask>();
		LinkedList<EmptyTask> failedThreads = new LinkedList<EmptyTask>();
		int availableClearance = autoRunLimit;

		int successfulMaxCount = ConfigMain
				.getIntParameter("taskManager.keepThreads.successful.count", KEEP_SUCCESSFUL);
		int failedMaxCount = ConfigMain.getIntParameter("taskManager.keepThreads.failed.count", KEEP_FAILED);
		Duration successfulMaxAge = ConfigMain.getDurationParameter("taskManager.keepThreads.successful.minutes",
				TimeUnit.MINUTES, KEEP_SUCCESSFUL_MINS);
		Duration failedMaxAge = ConfigMain.getDurationParameter("taskManager.keepThreads.failed.minutes",
				TimeUnit.MINUTES, KEEP_FAILED_MINS);

		ListIterator<EmptyTask> position = taskManager.taskList.listIterator();
		EmptyTask task;
		try {
			while (position.hasNext()) {
				task = position.next();
				switch (task.getTaskState()) {
				case WORKING:
				case STOPPING:
					availableClearance = Math.max(availableClearance - 1, 0);
					break;
				case NEW:
					if (Behaviour.DELETE_IMMEDIATELY.equals(task.getBehaviour())) {
						position.remove();
					} else {
						launchableThreads.addLast(task);
					}
					break;
				default: // cases STOPPED, FINISHED, CRASHED
					switch (task.getBehaviour()) {
					case DELETE_IMMEDIATELY:
						position.remove();
						break;
					default: // case KEEP_FOR_A_WHILE 
						boolean taskFinishedSuccessfully = task.getException() == null;
						Duration durationDead = task.getDurationDead();
						if (durationDead == null) {
							task.setTimeOfDeath();
						} else if (durationDead
								.isLongerThan(taskFinishedSuccessfully ? successfulMaxAge : failedMaxAge)) {
							position.remove();
							break;
						}
						if (taskFinishedSuccessfully) {
							finishedThreads.add(task);
						} else {
							failedThreads.add(task);
						}
						break;
					case PREPARE_FOR_RESTART:
						EmptyTask replacement = task.replace();
						if (replacement != null) {
							position.set(replacement);
							launchableThreads.addLast(replacement);
						}
						break;
					}
				}
			}
		} catch (ConcurrentModificationException e) {
			return;
		}

		while (finishedThreads.size() > successfulMaxCount && (task = finishedThreads.pollFirst()) != null) {
			taskManager.taskList.remove(task);
		}

		while (failedThreads.size() > failedMaxCount && (task = failedThreads.pollFirst()) != null) {
			taskManager.taskList.remove(task);
		}

		while (launchableThreads.size() > availableClearance) {
			launchableThreads.removeLast();
		}
		while ((task = launchableThreads.pollFirst()) != null) {
			task.start();
		}
	}

	/**
	 * The function setAutoRunningThreads() turns the feature to auto-run tasks
	 * on or off. To enable, it will set the limit of auto running threads to
	 * the number of available cores of the runtime or to the value set in the
	 * global configuration file, if any. To disable auto-running it will set
	 * the number to 0.
	 * 
	 * @param on
	 *            whether the TaskManager shall auto-run threads
	 */
	public static void setAutoRunningThreads(boolean on) {
		if (on) {
			int cores = Runtime.getRuntime().availableProcessors();
			autoRunLimit = ConfigMain.getIntParameter("taskManager.autoRunLimit", cores);
		} else {
			autoRunLimit = 0;
		}
	}
}