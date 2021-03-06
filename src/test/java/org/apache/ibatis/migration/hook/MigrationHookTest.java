/**
 *    Copyright 2010-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.migration.hook;

import static org.junit.Assert.*;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.SqlRunner;
import org.apache.ibatis.migration.Migrator;
import org.apache.ibatis.migration.utils.TestUtil;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.Assertion;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class MigrationHookTest {

  @Rule
  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

  @Rule
  public final SystemOutRule out = new SystemOutRule().enableLog();

  private static File dir;
  private static Properties env;

  @BeforeClass
  public static void init() throws Exception {
    dir = Resources.getResourceAsFile("org/apache/ibatis/migration/hook/testdir");
    env = Resources
        .getResourceAsProperties("org/apache/ibatis/migration/hook/testdir/environments/development.properties");
  }

  @Test
  public void testHooks() throws Exception {
    exit.expectSystemExit();
    exit.checkAssertionAfterwards(new Assertion() {
      public void checkAssertion() {
        assertEquals("", out.getLog());
      }
    });
    bootstrap();
    up();
    assertChangelogIntact();
    assertWorklogRowCount(3);
    down();
    assertWorklogRowCount(4);

    out.clearLog();
    System.exit(0);
  }

  private void bootstrap() throws Exception {
    out.clearLog();
    // bootstrap creates a table used in a hook script later
    Migrator.main(TestUtil.args("--path=" + dir.getAbsolutePath(), "bootstrap"));
    assertTrue(out.getLog().contains("SUCCESS"));
  }

  private void up() throws Exception {
    out.clearLog();
    Migrator.main(TestUtil.args("--path=" + dir.getAbsolutePath(), "up"));
    String output = out.getLog();
    assertTrue(out.getLog().contains("SUCCESS"));
    // before
    assertEquals(1, TestUtil.countStr(output, "HELLO_1"));
    assertTrue(output.indexOf("HELLO_1") < output.indexOf("Applying: 001_create_changelog.sql"));
    // before each
    assertEquals(3, TestUtil.countStr(output, "FUNCTION_GLOBALVAR_LOCALVAR1_LOCALVAR2_ARG1_ARG2"));
    // after each
    assertEquals(3, TestUtil.countStr(output,
        "insert into worklog (str1, str2, str3) values ('GLOBALVAR', 'LOCALVAR1', 'LOCALVAR2')"));
    // after
    assertEquals(1, TestUtil.countStr(output, "METHOD_GLOBALVAR_LOCALVAR1_LOCALVAR2_ARG1_ARG2"));
    // assert the global variable defined and incremented in scripts
    assertEquals(1, TestUtil.countStr(output, "SCRIPT_VAR=1"));
    assertEquals(1, TestUtil.countStr(output, "SCRIPT_VAR=5"));
    assertEquals(0, TestUtil.countStr(output, "SCRIPT_VAR=6"));
  }

  private void down() throws Exception {
    out.clearLog();
    Migrator.main(TestUtil.args("--path=" + dir.getAbsolutePath(), "down"));
    String output = out.getLog();
    assertTrue(out.getLog().contains("SUCCESS"));
    // before
    assertEquals(1, TestUtil.countStr(output, "insert into worklog (str1) values ('3')"));
  }

  private void assertWorklogRowCount(int expectedRows) throws SQLException, ClassNotFoundException {
    Connection con = TestUtil.getConnection(env);
    SqlRunner sqlRunner = new SqlRunner(con);
    Map<String, Object> result = sqlRunner.selectOne("select count(*) as c from worklog");
    // compare as strings to avoid Long / Integer mismatch
    assertEquals(String.valueOf(expectedRows), result.get("C").toString());
  }

  private void assertChangelogIntact() throws SQLException, ClassNotFoundException {
    Connection con = TestUtil.getConnection(env);
    SqlRunner sqlRunner = new SqlRunner(con);
    Map<String, Object> result = sqlRunner
        .selectOne("select count(*) as c from changes where description = 'bogus description'");
    // compare as strings to avoid Long / Integer mismatch
    assertEquals("0", result.get("C").toString());
  }
}
