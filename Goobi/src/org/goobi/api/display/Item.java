package org.goobi.api.display;

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
public class Item {
	private String myLabel;
	private String myValue;
	private Boolean isSelected;
	
	/**
	 * Creates a new item with given params
	 * 
	 * @param label label of the item
	 * @param value value of the item
	 * @param selected indicates whether an item is preselected or not
	 */
	
	public Item(String label, String value, Boolean selected ){
		setLabel(label);
		setValue(value);
		setIsSelected(selected);
		
	}
	
	/**
	 * 
	 * @param myLabel sets label for the item
	 */

	public void setLabel(String myLabel) {
		this.myLabel = myLabel;
	}

	/**
	 * 
	 * @return label of the item
	 */
	public String getLabel() {
		return myLabel;
	}

	/**
	 * 
	 * @param myValue sets value for the item
	 */
	
	public void setValue(String myValue) {
		this.myValue = myValue;
	}

	/**
	 * 
	 * @return value of the item
	 */
	public String getValue() {
		return myValue;
	}

	/**
	 * 
	 * @param isSelected sets Boolean that indicates whether item is preselected or not
	 */
	
	public void setIsSelected(Boolean isSelected) {
		this.isSelected = isSelected;
	}

	/**
	 * 
	 * @return Boolean: is preselected or not
	 */
	public Boolean getIsSelected() {
		return isSelected;
	}
	
}
