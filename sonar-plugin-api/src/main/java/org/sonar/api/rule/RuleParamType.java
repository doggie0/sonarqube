/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.api.rule;

/**
 * @since 4.2
 */
public final class RuleParamType {

  public static final RuleParamType STRING = new RuleParamType("STRING");
  public static final RuleParamType TEXT = new RuleParamType("TEXT");
  public static final RuleParamType BOOLEAN = new RuleParamType("BOOLEAN");
  public static final RuleParamType INTEGER = new RuleParamType("INTEGER");
  public static final RuleParamType REGULAR_EXPRESSION = new RuleParamType("REGULAR_EXPRESSION");

  private final String key;

  private RuleParamType(String key) {
    this.key = key;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    RuleParamType that = (RuleParamType) o;
    return key.equals(that.key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @Override
  public String toString() {
    return key;
  }
}
