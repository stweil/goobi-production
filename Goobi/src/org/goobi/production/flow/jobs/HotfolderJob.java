package org.goobi.production.flow.jobs;

/**
 * This file is part of the Goobi Application - a Workflow tool for the support of mass digitization.
 * 
 * Visit the websites for more information. 
 *     		- http://www.goobi.org
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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.goobi.production.cli.helper.CopyProcess;
import org.goobi.production.flow.helper.JobCreation;
import org.goobi.production.importer.GoobiHotfolder;
import org.goobi.production.importer.ImportObject;

import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;
import de.sub.goobi.beans.Prozess;
import de.sub.goobi.config.ConfigMain;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.ScriptThreadWithoutHibernate;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.ProzessDAO;
import de.sub.goobi.persistence.apache.StepManager;
import de.sub.goobi.persistence.apache.StepObject;

/**
 * 
 * @author Robert Sehr
 * 
 */

@Deprecated
public class HotfolderJob extends AbstractGoobiJob {
	private static final Logger logger = Logger.getLogger(HotfolderJob.class);

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.goobi.production.flow.jobs.SimpleGoobiJob#initialize()
	 */
	@Override
	public String getJobName() {
		return "HotfolderJob";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.goobi.production.flow.jobs.SimpleGoobiJob#execute()
	 */
	@Override
	public void execute() {
		// logger.error("TEST123");
		if (ConfigMain.getBooleanParameter("runHotfolder", false)) {
			logger.trace("1");
			List<GoobiHotfolder> hotlist = GoobiHotfolder.getInstances();
			logger.trace("2");
			for (GoobiHotfolder hotfolder : hotlist) {
				logger.trace("3");
				List<File> list = hotfolder.getCurrentFiles();
				logger.trace("4");
				long size = getSize(list);
				logger.trace("5");
				try {
					if (size > 0) {
						if (!hotfolder.isLocked()) {

							logger.trace("6");
							Thread.sleep(10000);
							logger.trace("7");
							list = hotfolder.getCurrentFiles();
							logger.trace("8");
							if (size == getSize(list)) {
								hotfolder.lock();
								logger.trace("9");
								ProzessDAO dao = new ProzessDAO();
								Prozess template = dao.get(hotfolder.getTemplate());
								dao.refresh(template);
								logger.trace("10");
								List<String> metsfiles = hotfolder.getFileNamesByFilter(GoobiHotfolder.filter);
								logger.trace("11");
								HashMap<String, Integer> failedData = new HashMap<String, Integer>();
								logger.trace("12");

								for (String filename : metsfiles) {
									logger.debug("found file: " + filename);
									logger.trace("13");

									int returnValue = generateProcess(filename, template, hotfolder.getFolderAsFile(), hotfolder.getCollection(),
											hotfolder.getUpdateStrategy());
									logger.trace("14");
									if (returnValue != 0) {
										logger.trace("15");
										failedData.put(filename, returnValue);
										logger.trace("16");
									} else {
										logger.debug("finished file: " + filename);
									}
								}
								if (!failedData.isEmpty()) {
									// // TODO Errorhandling
									logger.trace("17");
									for (String filename : failedData.keySet()) {
										File oldFile = new File(hotfolder.getFolderAsFile(), filename);
										if (oldFile.exists()) {
											File newFile = new File(oldFile.getAbsolutePath() + "_");
											oldFile.renameTo(newFile);
										}
										logger.error("error while importing file: " + filename + " with error code " + failedData.get(filename));
									}
								}
								hotfolder.unlock();
							}
						} else {
							logger.trace("18");
							return;
						}
						logger.trace("19");
					}

				} catch (InterruptedException e) {
					logger.error(e);
					logger.trace("20");
				} catch (DAOException e) {
					logger.error(e);
					logger.trace("21");
				} catch (Exception e) {
					logger.error(e);
				}
			}

		}
	}

	private long getSize(List<File> list) {
		long size = 0;
		for (File f : list) {
			if (f.isDirectory()) {
				File[] subdir = f.listFiles();
				for (File sub : subdir) {
					size += sub.length();
				}
			} else {
				size += f.length();
			}
		}
		return size;
	}

	public static int generateProcess(String processTitle, Prozess vorlage, File dir, String digitalCollection, String updateStrategy) {
		// wenn keine anchor Datei, dann Vorgang anlegen
		if (!processTitle.contains("anchor") && processTitle.endsWith("xml")) {
			if (!updateStrategy.equals("ignore")) {
				boolean test = testTitle(processTitle.substring(0, processTitle.length() - 4));
				if (!test && updateStrategy.equals("error")) {
					File images = new File(dir.getAbsoluteFile() + File.separator + processTitle.substring(0, processTitle.length() - 4)
							+ File.separator);
					List<String> imageDir = new ArrayList<String>();
					if (images.isDirectory()) {
						String[] files = images.list();
						for (int i = 0; i < files.length; i++) {
							imageDir.add(files[i]);
						}
						try {
							FileUtils.deleteDirectory(images);
						} catch (IOException e) {
						}
					}
					try {
						FileUtils.forceDelete(new File(dir.getAbsolutePath() + File.separator + processTitle));
					} catch (Exception e) {
						logger.error("Can not delete file " + processTitle, e);
						return 30;
					}
					File anchor = new File(dir.getAbsolutePath() + File.separator + processTitle.substring(0, processTitle.length() - 4)
							+ "_anchor.xml");
					if (anchor.exists()) {
						FileUtils.deleteQuietly(anchor);
					}
					return 27;
				} else if (!test && updateStrategy.equals("update")) {
					// TODO UPDATE mets data
					File images = new File(dir.getAbsoluteFile() + File.separator + processTitle.substring(0, processTitle.length() - 4)
							+ File.separator);
					List<String> imageDir = new ArrayList<String>();
					if (images.isDirectory()) {
						String[] files = images.list();
						for (int i = 0; i < files.length; i++) {
							imageDir.add(files[i]);
						}
						try {
							FileUtils.deleteDirectory(images);
						} catch (IOException e) {
						}
					}
					try {
						FileUtils.forceDelete(new File(dir.getAbsolutePath() + File.separator + processTitle));
					} catch (Exception e) {
						logger.error("Can not delete file " + processTitle, e);
						return 30;
					}
					File anchor = new File(dir.getAbsolutePath() + File.separator + processTitle.substring(0, processTitle.length() - 4)
							+ "_anchor.xml");
					if (anchor.exists()) {
						FileUtils.deleteQuietly(anchor);
					}
					return 28;
				}
			}
			CopyProcess form = new CopyProcess();
			form.setProzessVorlage(vorlage);
			form.metadataFile = dir.getAbsolutePath() + File.separator + processTitle;
			form.Prepare();
			form.getProzessKopie().setTitel(processTitle.substring(0, processTitle.length() - 4));
			if (form.testTitle()) {
				if (digitalCollection == null) {
					List<String> collections = new ArrayList<String>();
					form.setDigitalCollections(collections);
				} else {
					List<String> col = new ArrayList<String>();
					col.add(digitalCollection);
					form.setDigitalCollections(col);
				}
				form.OpacAuswerten();

				try {
					Prozess p = form.NeuenProzessAnlegen2();
					if (p.getId() != null) {

						// copy image files to new directory
						File images = new File(dir.getAbsoluteFile() + File.separator + processTitle.substring(0, processTitle.length() - 4)
								+ File.separator);
						List<String> imageDir = new ArrayList<String>();
						if (images.isDirectory()) {
							String[] files = images.list();
							for (int i = 0; i < files.length; i++) {
								imageDir.add(files[i]);
							}
							for (String file : imageDir) {
								File image = new File(images, file);
								File dest = new File(p.getImagesOrigDirectory(false) + image.getName());
								FileUtils.moveFile(image, dest);
							}
							FileUtils.deleteDirectory(images);
						}

						// copy fulltext files

						File fulltext = new File(dir.getAbsoluteFile() + File.separator + processTitle.substring(0, processTitle.length() - 4)
								+ "_txt" + File.separator);
						if (fulltext.isDirectory()) {
					
							FileUtils.moveDirectory(fulltext, new File(p.getTxtDirectory()));
						}

						// copy source files

						File sourceDir = new File(dir.getAbsoluteFile() + File.separator + processTitle.substring(0, processTitle.length() - 4)
								+ "_src" + File.separator);
						if (sourceDir.isDirectory()) {
							FileUtils.moveDirectory(sourceDir, new File(p.getImportDirectory()));
						}

						try {
							FileUtils.forceDelete(new File(dir.getAbsolutePath() + File.separator + processTitle));
						} catch (Exception e) {
							logger.error("Can not delete file " + processTitle + " after importing " + p.getTitel() + " into goobi", e);
							return 30;
						}
						File anchor = new File(dir.getAbsolutePath() + File.separator + processTitle.substring(0, processTitle.length() - 4)
								+ "_anchor.xml");
						if (anchor.exists()) {
							FileUtils.deleteQuietly(anchor);
						}
						List<StepObject> steps = StepManager.getStepsForProcess(p.getId());
						for (StepObject s : steps) {
							if (s.getBearbeitungsstatus() == 1 && s.isTypAutomatisch()) {
								ScriptThreadWithoutHibernate myThread = new ScriptThreadWithoutHibernate(s);
								myThread.start();
							}
						}
					}
				} catch (ReadException e) {
					logger.error(e);
					return 20;
				} catch (PreferencesException e) {
					logger.error(e);
					return 21;
				} catch (SwapException e) {
					logger.error(e);
					return 22;
				} catch (DAOException e) {
					logger.error(e);
					return 22;
				} catch (WriteException e) {
					logger.error(e);
					return 23;
				} catch (IOException e) {
					logger.error(e);
					return 24;
				} catch (InterruptedException e) {
					logger.error(e);
					return 25;
				}
			}
			// TODO updateImagePath aufrufen

			return 0;
		} else {
			return 26;
		}
	}

	public static boolean testTitle(String titel) {
		if (titel != null) {
			long anzahl = 0;
			try {
				anzahl = new ProzessDAO().count("from Prozess where titel='" + titel + "'");
			} catch (DAOException e) {
				return false;
			}
			if (anzahl > 0) {
				Helper.setFehlerMeldung("processTitleAllreadyInUse");
				return false;
			}
		} else {
			return false;
		}
		return true;
	}

	@SuppressWarnings("static-access")
	public static Prozess generateProcess(ImportObject io, Prozess vorlage) {
		String processTitle = io.getProcessTitle();
		logger.trace("processtitle is " + processTitle);
		String metsfilename = io.getMetsFilename();
		logger.trace("mets filename is " + metsfilename);
		String basepath = metsfilename.substring(0, metsfilename.length() - 4);
		logger.trace("basepath is " + basepath);
		File metsfile = new File(metsfilename);
		Prozess p = null;
		if (!testTitle(processTitle)) {
			logger.trace("wrong title");
			// removing all data
			File imagesFolder = new File(basepath);
			if (imagesFolder.exists() && imagesFolder.isDirectory()) {
				deleteDirectory(imagesFolder);
			} else {
				imagesFolder = new File(basepath + "_" + vorlage.DIRECTORY_SUFFIX);
				if (imagesFolder.exists() && imagesFolder.isDirectory()) {
					deleteDirectory(imagesFolder);
				}
			}
			try {
				FileUtils.forceDelete(metsfile);
			} catch (Exception e) {
				logger.error("Can not delete file " + processTitle, e);
				return null;
			}
			File anchor = new File(basepath + "_anchor.xml");
			if (anchor.exists()) {
				FileUtils.deleteQuietly(anchor);
			}
			return null;
		}

		CopyProcess cp = new CopyProcess();
		cp.setProzessVorlage(vorlage);
		cp.metadataFile = metsfilename;
		cp.Prepare(io);
		cp.getProzessKopie().setTitel(processTitle);
		logger.trace("testing title");
		if (cp.testTitle()) {
			logger.trace("title is valid");
			cp.OpacAuswerten();
			try {
				p = cp.createProcess(io);
				JobCreation.moveFiles(metsfile, basepath, p);

			} catch (ReadException e) {
				Helper.setFehlerMeldung(e);
				logger.error(e);
			} catch (PreferencesException e) {
				Helper.setFehlerMeldung(e);
				logger.error(e);
			} catch (SwapException e) {
				Helper.setFehlerMeldung(e);
				logger.error(e);
			} catch (DAOException e) {
				Helper.setFehlerMeldung(e);
				logger.error(e);
			} catch (WriteException e) {
				Helper.setFehlerMeldung(e);
				logger.error(e);
			} catch (IOException e) {
				Helper.setFehlerMeldung(e);
				logger.error(e);
			} catch (InterruptedException e) {
				Helper.setFehlerMeldung(e);
				logger.error(e);
			}
		} else {
			logger.trace("title is invalid");
		}
		return p;

	}

	private static void deleteDirectory(File directory) {
		try {
			FileUtils.deleteDirectory(directory);
		} catch (IOException e) {
			logger.error(e);
		}
	}
}
