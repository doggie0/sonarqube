/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
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
package org.sonar.server.computation.step;

import com.google.common.collect.ImmutableSet;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.core.component.ComponentDto;
import org.sonar.server.computation.ComputationContext;
import org.sonar.server.computation.issue.IssueCache;
import org.sonar.server.computation.issue.RuleCache;
import org.sonar.server.issue.notification.IssueChangeNotification;
import org.sonar.server.issue.notification.NewIssuesNotification;
import org.sonar.server.notifications.NotificationService;
import org.sonar.server.util.CloseableIterator;

import java.util.Set;

/**
 * Reads issues from disk cache and send related notifications. For performance reasons,
 * the standard notification DB queue is not used as a temporary storage. Notifications
 * are directly processed by {@link org.sonar.server.notifications.NotificationService}.
 */
public class SendIssueNotificationsStep implements ComputationStep {
  /**
   * Types of the notifications sent by this step
   */
  static final Set<String> NOTIF_TYPES = ImmutableSet.of(IssueChangeNotification.TYPE, NewIssuesNotification.TYPE);

  private final IssueCache issueCache;
  private final RuleCache rules;
  private final NotificationService service;

  public SendIssueNotificationsStep(IssueCache issueCache, RuleCache rules, NotificationService service) {
    this.issueCache = issueCache;
    this.rules = rules;
    this.service = service;
  }

  @Override
  public void execute(ComputationContext context) {
    if (service.hasProjectSubscribersForTypes(context.getProject().uuid(), NOTIF_TYPES)) {
      doExecute(context);
    }
  }

  private void doExecute(ComputationContext context) {
    NewIssuesNotification.Stats newIssueStats = new NewIssuesNotification.Stats();
    CloseableIterator<DefaultIssue> issues = issueCache.traverse();
    try {
      while (issues.hasNext()) {
        DefaultIssue issue = issues.next();
        if (issue.isNew() && issue.resolution() == null) {
          newIssueStats.add(issue);
        } else if (issue.isChanged() && issue.mustSendNotifications()) {
          IssueChangeNotification changeNotification = new IssueChangeNotification();
          changeNotification.setRuleName(rules.ruleName(issue.ruleKey()));
          changeNotification.setIssue(issue);
          changeNotification.setProject(context.getProject());
          service.deliver(changeNotification);
        }
      }

    } finally {
      issues.close();
    }
    sendNewIssuesStatistics(context, newIssueStats);
  }

  private void sendNewIssuesStatistics(ComputationContext context, NewIssuesNotification.Stats stats) {
    if (stats.size() > 0) {
      ComponentDto project = context.getProject();
      NewIssuesNotification notification = new NewIssuesNotification();
      notification.setProject(project);
      notification.setAnalysisDate(context.getAnalysisDate());
      notification.setStatistics(project, stats);
      service.deliver(notification);
    }
  }

  @Override
  public String getDescription() {
    return "Send issue notifications";
  }

}
