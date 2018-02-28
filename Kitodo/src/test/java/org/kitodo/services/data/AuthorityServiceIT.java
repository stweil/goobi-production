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

package org.kitodo.services.data;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.json.simple.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.kitodo.MockDatabase;
import org.kitodo.data.database.beans.Authority;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.dto.AuthorityDTO;
import org.kitodo.services.ServiceManager;

public class AuthorityServiceIT {

    private static final AuthorityService authorityService = new ServiceManager().getAuthorityService();

    @BeforeClass
    public static void prepareDatabase() throws Exception {
        MockDatabase.startNode();
        MockDatabase.insertUserGroupsFull();
    }

    @AfterClass
    public static void cleanDatabase() throws Exception {
        MockDatabase.stopNode();
        MockDatabase.cleanDatabase();
    }

    @Before
    public void multipleInit() throws InterruptedException {
        Thread.sleep(500);
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldCountAllAuthorizations() throws Exception {
        Long amount = authorityService.count();
        assertEquals("Authorizations were not counted correctly!", Long.valueOf(3), amount);
    }

    @Test
    public void shouldCountAllDatabaseRowsForAuthorizations() throws Exception {
        Long amount = authorityService.countDatabaseRows();
        assertEquals("Authorizations were not counted correctly!", Long.valueOf(3), amount);
    }

    @Test
    public void shouldFindAllAuthorizations() throws Exception {
        List<AuthorityDTO> authorizations = authorityService.findAll();
        assertEquals("Not all authorizations were found in database!", 3, authorizations.size());
    }

    @Test
    public void shouldFindById() throws Exception {
        AuthorityDTO authorization = authorityService.findById(2);
        String actual = authorization.getTitle();
        String expected = "manager";
        assertEquals("User group was not found in index!", expected, actual);
    }

    @Test
    public void shouldFindByTitle() throws Exception {
        List<JSONObject> authorities = authorityService.findByTitle("user", true);
        Integer actual = authorities.size();
        Integer expected = 1;
        assertEquals("Authority was not found in index!", expected, actual);

        authorities = authorityService.findByTitle("none", true);
        actual = authorities.size();
        expected = 0;
        assertEquals("Authority was found in index!", expected, actual);
    }

    @Test
    public void shouldGetAllAuthorizations() {
        List<Authority> authorities = authorityService.getAll();
        assertEquals("Authorizations were not found databse!", 3, authorities.size());
    }

    @Test
    public void shouldNotSaveAlreadyExistingAuthorization() throws DataException {
        Authority adminAuthority = new Authority();
        adminAuthority.setTitle("admin");
        exception.expect(DataException.class);
        authorityService.save(adminAuthority);
    }
}