package com.delta.dms.ops.aidevopsplatform.service.port.out;

/** Outbound Jira. v1 tests use an HTTP fake; the platform does not embed Jira. */
public interface JiraPort {
    boolean createIssue(String itemNo, String summary);
}
