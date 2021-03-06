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
package org.sonar.server.computation.issue;

import com.google.common.collect.Sets;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.api.issue.internal.FieldDiffs;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.Duration;
import org.sonar.api.utils.KeyValueFormat;
import org.sonar.batch.protocol.output.BatchOutput;
import org.sonar.core.rule.RuleDto;
import org.sonar.server.computation.ComputationContext;
import org.sonar.server.util.cache.DiskCache;

import java.util.Date;

public class IssueComputation {

  private final RuleCache ruleCache;
  private final ScmAccountCache scmAccountCache;
  private final SourceLinesCache linesCache;
  private final DiskCache<DefaultIssue>.DiskAppender diskIssuesAppender;

  public IssueComputation(RuleCache ruleCache, SourceLinesCache linesCache, ScmAccountCache scmAccountCache,
    IssueCache issueCache) {
    this.ruleCache = ruleCache;
    this.linesCache = linesCache;
    this.scmAccountCache = scmAccountCache;
    this.diskIssuesAppender = issueCache.newAppender();
  }

  public void processComponentIssues(ComputationContext context, String componentUuid, Iterable<BatchOutput.ReportIssue> issues) {
    linesCache.init(componentUuid);
    for (BatchOutput.ReportIssue reportIssue : issues) {
      DefaultIssue issue = toDefaultIssue(context, componentUuid, reportIssue);
      if (issue.isNew()) {
        guessAuthor(issue);
        autoAssign(issue);
        copyRuleTags(issue);
        // TODO execute extension points
      }
      diskIssuesAppender.append(issue);
    }
    linesCache.clear();
  }

  private DefaultIssue toDefaultIssue(ComputationContext context, String componentUuid, BatchOutput.ReportIssue issue) {
    DefaultIssue target = new DefaultIssue();
    target.setKey(issue.getUuid());
    target.setComponentUuid(componentUuid);
    target.setRuleKey(RuleKey.of(issue.getRuleRepository(), issue.getRuleKey()));
    target.setSeverity(issue.getSeverity().name());
    target.setManualSeverity(issue.getManualSeverity());
    target.setMessage(issue.hasMsg() ? issue.getMsg() : null);
    target.setLine(issue.hasLine() ? issue.getLine() : null);
    target.setProjectUuid(context.getProject().uuid());
    target.setProjectKey(context.getProject().key());
    target.setEffortToFix(issue.hasEffortToFix() ? issue.getEffortToFix() : null);
    target.setDebt(issue.hasDebtInMinutes() ? Duration.create(issue.getDebtInMinutes()) : null);
    if (issue.hasDiffFields()) {
      FieldDiffs fieldDiffs = FieldDiffs.parse(issue.getDiffFields());
      fieldDiffs.setCreationDate(context.getAnalysisDate());
      target.setCurrentChange(fieldDiffs);
    }
    target.setStatus(issue.getStatus());
    target.setTags(issue.getTagsList());
    target.setResolution(issue.hasResolution() ? issue.getResolution() : null);
    target.setReporter(issue.hasReporter() ? issue.getReporter() : null);
    target.setAssignee(issue.hasAssignee() ? issue.getAssignee() : null);
    target.setChecksum(issue.hasChecksum() ? issue.getChecksum() : null);
    target.setAttributes(issue.hasAttributes() ? KeyValueFormat.parse(issue.getAttributes()) : null);
    target.setAuthorLogin(issue.hasAuthorLogin() ? issue.getAuthorLogin() : null);
    target.setActionPlanKey(issue.hasActionPlanKey() ? issue.getActionPlanKey() : null);
    target.setCreationDate(issue.hasCreationDate() ? new Date(issue.getCreationDate()) : null);
    target.setUpdateDate(issue.hasUpdateDate() ? new Date(issue.getUpdateDate()) : null);
    target.setCloseDate(issue.hasCloseDate() ? new Date(issue.getCloseDate()) : null);
    target.setChanged(issue.getIsChanged());
    target.setNew(issue.getIsNew());
    target.setSelectedAt(issue.hasSelectedAt() ? issue.getSelectedAt() : null);
    target.setSendNotifications(issue.getMustSendNotification());
    return target;
  }

  public void afterReportProcessing() {
    diskIssuesAppender.close();
  }

  private void guessAuthor(DefaultIssue issue) {
    // issue.authorLogin() can be not-null when old developer cockpit plugin (or other plugin)
    // is still installed and executed during analysis
    if (issue.authorLogin() == null) {
      issue.setAuthorLogin(linesCache.lineAuthor(issue.line()));
    }
  }

  private void autoAssign(DefaultIssue issue) {
    // issue.assignee() can be not-null if the issue-assign-plugin is
    // still installed and executed during analysis
    if (issue.assignee() == null) {
      String scmAccount = issue.authorLogin();
      if (scmAccount != null) {
        issue.setAssignee(scmAccountCache.getNullable(scmAccount));
      }
    }
  }

  private void copyRuleTags(DefaultIssue issue) {
    RuleDto rule = ruleCache.get(issue.ruleKey());
    issue.setTags(Sets.union(rule.getTags(), rule.getSystemTags()));
  }

}
