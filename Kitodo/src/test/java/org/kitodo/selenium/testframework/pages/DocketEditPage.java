/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.selenium.testframework.pages;

import org.kitodo.data.database.beans.Docket;
import org.kitodo.selenium.testframework.Browser;
import org.kitodo.selenium.testframework.Pages;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class DocketEditPage extends Page<DocketEditPage> {

    @SuppressWarnings("unused")
    @FindBy(id = "editForm:save")
    private WebElement saveDocketButton;

    @SuppressWarnings("unused")
    @FindBy(id = "editForm:docketTabView:title")
    private WebElement titleInput;

    @SuppressWarnings("unused")
    @FindBy(className = "ui-selectonemenu-trigger")
    private WebElement selectTrigger;

    @SuppressWarnings("unused")
    @FindBy(className = "ui-messages-error-summary")
    private WebElement errorMessage;

    public DocketEditPage() {
        super("pages/docketEdit.jsf");
    }

    @Override
    public DocketEditPage goTo() {
        return null;
    }

    public DocketEditPage insertDocketData(Docket docket) {
        titleInput.sendKeys(docket.getTitle());
        selectTrigger.click();
        Browser.getDriver().findElement(By.id("editForm:docketTabView:file_0")).click();
        return this;
    }

    public ProjectsPage save() throws IllegalAccessException, InstantiationException {
        Browser.clickAjaxSaveButton(saveDocketButton);
        WebDriverWait wait = new WebDriverWait(Browser.getDriver(), 30); //seconds
        wait.until(ExpectedConditions.urlContains(Pages.getProjectsPage().getUrl()));
        return Pages.getProjectsPage();
    }

    public String saveWithError() {
        return saveWithError(saveDocketButton);
    }
}
