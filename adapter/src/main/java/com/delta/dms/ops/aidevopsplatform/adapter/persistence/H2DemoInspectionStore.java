package com.delta.dms.ops.aidevopsplatform.adapter.persistence;

import com.delta.dms.ops.aidevopsplatform.model.event.DomainEvent;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.*;
import com.delta.dms.ops.aidevopsplatform.service.port.out.*;
import org.h2.jdbcx.JdbcDataSource;
import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** H2-only demo adapter. Its SQL and transactions are not a PostgreSQL implementation. */
public final class H2DemoInspectionStore implements InspectionRepository, NotificationOutbox, EventReceiptPort {
    private final DataSource dataSource;
    public H2DemoInspectionStore(String url, String username, String password) {
        if(!url.matches("jdbc:h2:mem:[A-Za-z0-9_-]+;DB_CLOSE_DELAY=-1"))
            throw new IllegalArgumentException("Demo requires an in-memory H2 URL with DB_CLOSE_DELAY=-1");
        var ds=new JdbcDataSource(); ds.setURL(url); ds.setUser(username); ds.setPassword(password); dataSource=ds;
        try(var input=getClass().getResourceAsStream("/demo/inspection-schema.sql"); var c=dataSource.getConnection()) {
            if(input==null) throw new IllegalStateException("Missing demo schema");
            for(var sql:new String(input.readAllBytes(),StandardCharsets.UTF_8).split(";"))
                if(!sql.isBlank()) try(var statement=c.createStatement()) { statement.execute(sql); }
        } catch(Exception e) { throw new IllegalStateException("Demo database initialization failed",e); }
    }
    @Override public void save(InspectionReport r, ServiceInspected event) {
        if(!event.aggregateId().equals(r.id()) || !event.correlationId().equals(r.correlationId()))
            throw new IllegalArgumentException("Event does not belong to report");
        try(var c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(var s=c.prepareStatement("INSERT INTO demo_inspection VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    s.setObject(1,r.id());s.setObject(2,r.correlationId());s.setString(3,r.actorId());s.setString(4,r.serviceId());
                    s.setString(5,r.serviceName());s.setString(6,r.status().name());s.setString(7,r.version());
                    s.setObject(8,r.observedAt().atOffset(ZoneOffset.UTC));s.setString(9,r.summary());s.setString(10,r.modelName());
                    s.setBoolean(11,r.probeMocked());s.setBoolean(12,r.modelMocked());
                    s.executeUpdate();
                }
                try(var s=c.prepareStatement("INSERT INTO demo_notification_outbox(event_id,report_id,correlation_id,occurred_at) VALUES (?,?,?,?)")) {
                    s.setObject(1,event.eventId());s.setObject(2,event.aggregateId());s.setObject(3,event.correlationId());
                    s.setObject(4,event.occurredAt().atOffset(ZoneOffset.UTC));s.executeUpdate();
                }
                c.commit();
            } catch(SQLException|RuntimeException e) { c.rollback();throw e; }
        } catch(SQLException e) { throw new IllegalStateException("Report/event transaction failed",e); }
    }
    @Override public Optional<InspectionReport> findById(UUID id) {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT * FROM demo_inspection WHERE id=?")) {
            s.setObject(1,id);
            try(var r=s.executeQuery()) {
                if(!r.next()) return Optional.empty();
                return Optional.of(new InspectionReport(r.getObject("id",UUID.class),r.getObject("correlation_id",UUID.class),
                        r.getString("actor_id"),r.getString("service_id"),r.getString("service_name"),
                        Observation.Status.valueOf(r.getString("health_status")),r.getString("service_version"),
                        r.getObject("observed_at",OffsetDateTime.class).toInstant(),r.getString("summary"),r.getString("model_name"),
                        r.getBoolean("probe_mocked"),r.getBoolean("model_mocked")));
            }
        } catch(SQLException e) { throw new IllegalStateException("Cannot read report",e); }
    }
    @Override public List<DomainEvent> pending(int limit) {
        if(limit<1 || limit>1000) throw new IllegalArgumentException("Invalid batch size");
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT * FROM demo_notification_outbox WHERE acknowledged=FALSE ORDER BY occurred_at,event_id LIMIT ?")) {
            s.setInt(1,limit);var events=new ArrayList<DomainEvent>();
            try(var r=s.executeQuery()) {while(r.next()) events.add(new ServiceInspected(r.getObject("event_id",UUID.class),
                    r.getObject("report_id",UUID.class),r.getObject("correlation_id",UUID.class),r.getObject("occurred_at",OffsetDateTime.class).toInstant()));}
            return List.copyOf(events);
        } catch(SQLException e) {throw new IllegalStateException("Cannot read notification outbox",e);}
    }
    @Override public void acknowledge(UUID eventId) {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("UPDATE demo_notification_outbox SET acknowledged=TRUE WHERE event_id=? AND EXISTS(SELECT 1 FROM demo_event_receipt WHERE event_id=?)")) {
            s.setObject(1,eventId);s.setObject(2,eventId);
            if(s.executeUpdate()!=1) throw new IllegalStateException("Required consumer has not received event");
        } catch(SQLException e) {throw new IllegalStateException("Cannot acknowledge notification",e);}
    }
    @Override public void recordOnce(UUID eventId) {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("INSERT INTO demo_event_receipt(event_id,consumer_id) VALUES (?,'inspection-receipt-v1')")) {
            s.setObject(1,eventId);s.executeUpdate();
        } catch(SQLException e) {
            if(!"23505".equals(e.getSQLState())) throw new IllegalStateException("Cannot record event receipt",e);
        }
    }
}
