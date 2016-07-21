package org.goobi.production.flow.statistics.hibernate;

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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.goobi.production.flow.statistics.IDataSource;
import org.goobi.production.flow.statistics.IStatisticalQuestionLimitedTimeframe;
import org.goobi.production.flow.statistics.enums.CalculationUnit;
import org.goobi.production.flow.statistics.enums.StatisticsMode;
import org.goobi.production.flow.statistics.enums.TimeUnit;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.type.StandardBasicTypes;

import de.intranda.commons.chart.renderer.ChartRenderer;
import de.intranda.commons.chart.renderer.IRenderer;
import de.intranda.commons.chart.results.DataRow;
import de.intranda.commons.chart.results.DataTable;
import de.sub.goobi.beans.Prozess;
import de.sub.goobi.helper.Helper;

/**
 * This class is an implementation of {@link IStatisticalQuestionLimitedTimeframe} and retrieves statistical Data about the productivity of the
 * selected processes, which are passed into this class via implemetations of the IDataSource interface.
 * 
 * According to {@link IStatisticalQuestionLimitedTimeframe} other parameters can be set before the productivity of the selected {@link Prozess}es is
 * evaluated.
 * 
 * @author Wulf Riebensahm
 * @author Robert Sehr
 */
public class StatQuestProduction implements IStatisticalQuestionLimitedTimeframe {

	// default value time filter is open
	Date timeFilterFrom;
	Date timeFilterTo;

	// default values set to days and volumesAndPages
	TimeUnit timeGrouping = TimeUnit.days;
	private CalculationUnit cu = CalculationUnit.volumesAndPages;

	/**
	 * IDataSource needs here to be an implementation of hibernate.IEvaluableFilter, which is a hibernate based extension of IDataSource
	 * 
	 * (non-Javadoc)
	 * 
	 * @see org.goobi.production.flow.statistics.IStatisticalQuestion#getDataTables(org.goobi.production.flow.statistics.IDataSource)
	 ****************************************************************************/
	@Override
	public List<DataTable> getDataTables(IDataSource dataSource) {

		// contains an intger representing "reihenfolge" in schritte, as defined for this request
		// if not defined it will trigger a fall back on a different way of retrieving the statistical data
		Integer exactStepDone = null;
		String stepname = null;
		List<DataTable> allTables = new ArrayList<DataTable>();

		IEvaluableFilter originalFilter;

		if (dataSource instanceof IEvaluableFilter) {
			originalFilter = (IEvaluableFilter) dataSource;
		} else {
			throw new UnsupportedOperationException("This implementation of IStatisticalQuestion needs an IDataSource for method getDataSets()");
		}

		// gathering some information from the filter passed by dataSource
		// exactStepDone is very important ...

		try {
			exactStepDone = originalFilter.stepDone();
		} catch (UnsupportedOperationException e1) {
		}
		try {
			stepname = originalFilter.stepDoneName();
		} catch (UnsupportedOperationException e1) {
		}

		// we have to build a query from scratch by reading the ID's
		List<Integer> IDlist = null;
		try {
			IDlist = originalFilter.getIDList();
		} catch (UnsupportedOperationException e) {
		}
		if (IDlist == null || IDlist.size() == 0) {
			return null;
		}
		String natSQL = "";
		// adding time restrictions
		if (stepname == null) {
			natSQL = new ImprovedSQLProduction(this.timeFilterFrom, this.timeFilterTo, this.timeGrouping, IDlist).getSQL(exactStepDone);
		} else {
			natSQL = new ImprovedSQLProduction(this.timeFilterFrom, this.timeFilterTo, this.timeGrouping, IDlist).getSQL(stepname);
		}
		Session session = Helper.getHibernateSession();

		SQLQuery query = session.createSQLQuery(natSQL);

		// needs to be there otherwise an exception is thrown
		query.addScalar("volumes", StandardBasicTypes.INTEGER);
		query.addScalar("pages", StandardBasicTypes.INTEGER);
		query.addScalar("intervall", StandardBasicTypes.STRING);

		@SuppressWarnings("rawtypes")
		List list = query.list();

		StringBuilder title = new StringBuilder(StatisticsMode.PRODUCTION.getTitle());
		 title.append(" (");
		 title.append(this.cu.getTitle());
		 if (stepname == null || stepname.equals("")) {
		 title.append(")");
		 } else {
			 title.append(", " + stepname + " )");
		 }

		// building table for the Table
		DataTable dtbl = new DataTable(title.toString());
		// building a second table for the chart
		DataTable dtblChart = new DataTable(title.toString());
		// 
		DataRow dataRowChart;
		DataRow dataRow;

		// each data row comes out as an Array of Objects
		// the only way to extract the data is by knowing
		// in which order they come out
		for (Object obj : list) {
			dataRowChart = new DataRow(null);
			dataRow = new DataRow(null);
			Object[] objArr = (Object[]) obj;
			try {

				// getting localized time group unit

				// String identifier = timeGrouping.getTitle();
				// setting row name with localized time group and the date/time extraction based on the group

				dataRowChart.setName(new Converter(objArr[2]).getString() + "");
				dataRow.setName(new Converter(objArr[2]).getString() + "");
				// dataRow.setName(new Converter(objArr[2]).getString());

				// building up row depending on requested output having different fields
				switch (this.cu) {

				case volumesAndPages: {
					dataRowChart.addValue(CalculationUnit.volumes.getTitle(), (new Converter(objArr[0]).getDouble()));
					dataRowChart.addValue(CalculationUnit.pages.getTitle() + " (*100)", (new Converter(objArr[1]).getDouble()) / 100);

					dataRow.addValue(CalculationUnit.volumes.getTitle(), (new Converter(objArr[0]).getDouble()));
					dataRow.addValue(CalculationUnit.pages.getTitle(), (new Converter(objArr[1]).getDouble()));

				}
					break;

				case volumes: {
					dataRowChart.addValue(CalculationUnit.volumes.getTitle(), (new Converter(objArr[0]).getDouble()));
					dataRow.addValue(CalculationUnit.volumes.getTitle(), (new Converter(objArr[0]).getDouble()));

				}
					break;

				case pages: {

					dataRowChart.addValue(CalculationUnit.pages.getTitle(), (new Converter(objArr[1]).getDouble()));
					dataRow.addValue(CalculationUnit.pages.getTitle(), (new Converter(objArr[1]).getDouble()));

				}
					break;

				}

				// fall back, if conversion triggers an exception
			} catch (Exception e) {
				dataRowChart.addValue(e.getMessage(), new Double(0));
				dataRow.addValue(e.getMessage(), new Double(0));
			}

			// finally adding dataRow to DataTable and fetching next row
			// adding the extra table
			dtblChart.addDataRow(dataRowChart);
			dtbl.addDataRow(dataRow);
		}

		// a list of DataTables is expected as return Object, even if there is only one
		// Data Table as it is here in this implementation
		dtblChart.setUnitLabel(Helper.getTranslation(this.timeGrouping.getSingularTitle()));
		dtbl.setUnitLabel(Helper.getTranslation(this.timeGrouping.getSingularTitle()));

		dtblChart.setShowableInTable(false);
		dtbl.setShowableInChart(false);

		allTables.add(dtblChart);
		allTables.add(dtbl);
		return allTables;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.goobi.production.flow.statistics.IStatisticalQuestionLimitedTimeframe#setTimeFrame(java.util.Date, java.util.Date)
	 */
	@Override
	public void setTimeFrame(Date timeFrom, Date timeTo) {
		this.timeFilterFrom = timeFrom;
		this.timeFilterTo = timeTo;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.goobi.production.flow.statistics.IStatisticalQuestion#setTimeUnit(org.goobi.production.flow.statistics.enums.TimeUnit)
	 */
	@Override
	public void setTimeUnit(TimeUnit timeGrouping) {
		this.timeGrouping = timeGrouping;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.goobi.production.flow.statistics.IStatisticalQuestion#setCalculationUnit(org.goobi.production.flow.statistics.enums.CalculationUnit)
	 */
	@Override
	public void setCalculationUnit(CalculationUnit cu) {
		this.cu = cu;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.goobi.production.flow.statistics.IStatisticalQuestion#isRendererInverted(de.intranda.commons.chart.renderer.IRenderer)
	 */
	@Override
	public Boolean isRendererInverted(IRenderer inRenderer) {
		return inRenderer instanceof ChartRenderer;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.goobi.production.flow.statistics.IStatisticalQuestion#getNumberFormatPattern()
	 */
	@Override
	public String getNumberFormatPattern() {
		return "#";
	}
}
