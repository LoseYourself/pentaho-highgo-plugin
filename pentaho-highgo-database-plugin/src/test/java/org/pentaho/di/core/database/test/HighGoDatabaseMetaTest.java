/*******************************************************************************
 *
 * 
 *
 * Copyright (C) 2011-2019 by Sun : http://www.kingbase.com.cn
 *
 *******************************************************************************
 *
 *
 *    Email : snj1314@163.com
 *
 *
 ******************************************************************************/

package org.pentaho.di.core.database.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.pentaho.di.core.database.HighGoDatabaseMeta;

/**
 * 
 * 
 * @author Sun
 * @since 2019年9月6日
 * @version
 * 
 */
public class HighGoDatabaseMetaTest {

  @Test
  public void testDriverClass() {
    HighGoDatabaseMeta dbMeta = new HighGoDatabaseMeta();
    assertEquals("com.highgo.jdbc.Driver", dbMeta.getDriverClass());
  }

  @Test
  public void testDefaultDatabasePort() {
    HighGoDatabaseMeta dbMeta = new HighGoDatabaseMeta();
    assertEquals(5866, dbMeta.getDefaultDatabasePort());
  }

}
