/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2013, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.util;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.rolling.helper.FileNamePattern;
import ch.qos.logback.core.spi.ContextAwareBase;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import static ch.qos.logback.core.CoreConstants.FA_FILENAME_COLLISION_MAP;
import static ch.qos.logback.core.CoreConstants.RFA_FILENAME_PATTERN_COLLISION_MAP;

public class ContextUtil extends ContextAwareBase {

  public ContextUtil(Context context) {
    setContext(context);
  }

  /**
   * Add the local host's name as a property
   */
  public void addHostNameAsProperty() {
    context.putProperty(CoreConstants.HOSTNAME_KEY, "localhost");
  }

  public void addProperties(Properties props) {
    if (props == null) {
      return;
    }
    Iterator i = props.keySet().iterator();
    while (i.hasNext()) {
      String key = (String) i.next();
      context.putProperty(key, props.getProperty(key));
    }
  }

  public static Map<String, String> getFilenameCollisionMap(Context context) {
    if (context == null) {
      return null;
    }
    @SuppressWarnings("unchecked")
    Map<String, String> map = (Map<String, String>) context.getObject(FA_FILENAME_COLLISION_MAP);
    return map;
  }

  public static Map<String, FileNamePattern> getFilenamePatternCollisionMap(Context context) {
    if (context == null) {
      return null;
    }
    @SuppressWarnings("unchecked")
    Map<String, FileNamePattern> map = (Map<String, FileNamePattern>) context.getObject(RFA_FILENAME_PATTERN_COLLISION_MAP);
    return map;
  }
}
