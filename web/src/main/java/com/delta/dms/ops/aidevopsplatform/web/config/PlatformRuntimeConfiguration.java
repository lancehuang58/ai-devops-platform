package com.delta.dms.ops.aidevopsplatform.web.config;

import com.delta.dms.ops.aidevopsplatform.adapter.access.LocalDirectory;
import com.delta.dms.ops.aidevopsplatform.adapter.alert.AlertIntake;
import com.delta.dms.ops.aidevopsplatform.adapter.execution.RejectingExecution;
import com.delta.dms.ops.aidevopsplatform.adapter.jira.JiraHttpGateway;
import com.delta.dms.ops.aidevopsplatform.adapter.jira.JiraOutboxDispatcher;
import com.delta.dms.ops.aidevopsplatform.adapter.llm.UnavailableLlm;
import com.delta.dms.ops.aidevopsplatform.adapter.notify.LoggingTeamsNotifier;
import com.delta.dms.ops.aidevopsplatform.adapter.persistence.JdbcPlatformStore;
import com.delta.dms.ops.aidevopsplatform.adapter.persistence.JdbcWorkItemStore;
import com.delta.dms.ops.aidevopsplatform.adapter.persistence.jpa.CaseEventJpaRepository;
import com.delta.dms.ops.aidevopsplatform.adapter.persistence.jpa.JpaWorkItemQuery;
import com.delta.dms.ops.aidevopsplatform.adapter.persistence.jpa.WorkItemJpaRepository;
import com.delta.dms.ops.aidevopsplatform.adapter.workflow.JdbcResourceLock;
import com.delta.dms.ops.aidevopsplatform.adapter.workflow.WorkQueueWorker;
import com.delta.dms.ops.aidevopsplatform.service.event.NotificationFanout;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ChangeExecutionPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.DirectoryAuthenticationPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.JiraPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.LlmPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ResourceLockPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.TeamsNotificationPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkItemQuery;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkItemStore;
import com.delta.dms.ops.aidevopsplatform.service.takeover.TakeoverService;
import com.delta.dms.ops.aidevopsplatform.service.workflow.ClosureService;
import com.delta.dms.ops.aidevopsplatform.service.workflow.FixCatalog;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemCoordinator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@Profile("platform")
@EnableScheduling
@EnableConfigurationProperties(PlatformAuthProperties.class)
public class PlatformRuntimeConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean JdbcPlatformStore jdbcPlatformStore(DataSource dataSource) { return new JdbcPlatformStore(dataSource); }
    @Bean WorkItemStore workItemStore(DataSource dataSource) { return new JdbcWorkItemStore(dataSource); }
    @Bean WorkItemQuery workItemQuery(WorkItemJpaRepository items, CaseEventJpaRepository events) {
        return new JpaWorkItemQuery(items, events);
    }
    @Bean DirectoryAuthenticationPort directoryAuthentication(PlatformAuthProperties auth) {
        var passwords = auth.getUsers().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().getPassword()));
        return new LocalDirectory(passwords);
    }
    @Bean LlmPort llmPort() { return new UnavailableLlm(); }
    @Bean ChangeExecutionPort changeExecutionPort() { return new RejectingExecution(); }
    @Bean TeamsNotificationPort teamsNotificationPort() { return new LoggingTeamsNotifier(); }
    @Bean JiraPort jiraPort(@Value("${platform.jira.base-url:http://127.0.0.1:9}") String baseUrl) {
        return new JiraHttpGateway(baseUrl);
    }
    @Bean FixCatalog fixCatalog() { return new FixCatalog(List.of(), List.of(), List.of()); }
    @Bean WorkItemCoordinator workItemCoordinator() { return new WorkItemCoordinator(); }
    @Bean ResourceLockPort resourceLockPort(JdbcPlatformStore store) { return new JdbcResourceLock(store); }
    @Bean AlertIntake alertIntake(JdbcPlatformStore store) { return new AlertIntake(store); }
    @Bean NotificationFanout notificationFanout() { return new NotificationFanout(); }
    @Bean ClosureService closureService(WorkItemStore store) { return new ClosureService(store); }
    @Bean TakeoverService takeoverService(WorkItemStore store) { return new TakeoverService(store); }
    @Bean JiraOutboxDispatcher jiraOutboxDispatcher(JdbcPlatformStore store, JiraPort jira) {
        return new JiraOutboxDispatcher(store, jira);
    }
    @Bean WorkQueueWorker workQueueWorker(JdbcPlatformStore queue, WorkItemStore items, WorkItemCoordinator coordinator,
                                           LlmPort llm, ChangeExecutionPort execution, ResourceLockPort locks, FixCatalog catalog,
                                           @Value("${platform.workflow.workers:0}") int workers) {
        return new WorkQueueWorker(queue, items, coordinator, llm, execution, locks, catalog, Math.max(workers, 0));
    }

    @Bean
    @ConditionalOnExpression("${platform.workflow.workers:0} > 0")
    PlatformMaintenance platformMaintenance(JdbcPlatformStore store, NotificationFanout fanout, TeamsNotificationPort teams,
                                             JiraOutboxDispatcher jira, WorkQueueWorker worker, Clock clock) {
        return new PlatformMaintenance(store, fanout, teams, jira, worker, clock);
    }

    static final class PlatformMaintenance {
        private final JdbcPlatformStore store;
        private final NotificationFanout fanout;
        private final TeamsNotificationPort teams;
        private final JiraOutboxDispatcher jira;
        private final WorkQueueWorker worker;
        private final Clock clock;

        PlatformMaintenance(JdbcPlatformStore store, NotificationFanout fanout, TeamsNotificationPort teams,
                            JiraOutboxDispatcher jira, WorkQueueWorker worker, Clock clock) {
            this.store = store;
            this.fanout = fanout;
            this.teams = teams;
            this.jira = jira;
            this.worker = worker;
            this.clock = clock;
        }

        @Scheduled(fixedDelay = 1000)
        public void tick() {
            var now = Instant.now(clock);
            store.releaseDueHolds(now);
            fanout.deliver(store.pendingNotifications(), teams, key -> store.markNotificationSent(key, now));
            jira.dispatchDue(now, 2);
            worker.tick(now);
        }
    }
}
