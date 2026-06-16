package com.harnessagent.production.infrastructure;

import com.harnessagent.persistence.DurableStoreCapability;
import com.harnessagent.persistence.DurableBackendType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.harnessagent.production.snapshot.Snapshot;
import com.harnessagent.production.snapshot.SnapshotMetadata;
import com.harnessagent.production.snapshot.SnapshotStore;
import com.harnessagent.production.snapshot.SnapshotStoreType;

@Repository
@Profile("production")
public class JdbcSnapshotStore implements SnapshotStore, DurableStoreCapability {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSnapshotStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DurableBackendType durableBackendType() {
        return DurableBackendType.MYSQL;
    }

    @Override
    public SnapshotMetadata save(SnapshotMetadata metadata, byte[] content) {
        SnapshotMetadata stored = metadata.withLocation(SnapshotStoreType.JDBC, "jdbc://snapshot/" + metadata.id());
        MapSqlParameterSource metadataParams = metadataParams(stored);
        int updated = jdbc.update("""
                update ha_snapshot_metadata
                set tenant_id = :tenantId,
                    agent_id = :agentId,
                    session_id = :sessionId,
                    task_id = :taskId,
                    created_at = :createdAt,
                    backend_type = :backendType,
                    location = :location
                where id = :id
                """, metadataParams);
        if (updated == 0) {
            jdbc.update("""
                    insert into ha_snapshot_metadata (
                        id, tenant_id, agent_id, session_id, task_id, created_at, backend_type, location
                    ) values (
                        :id, :tenantId, :agentId, :sessionId, :taskId, :createdAt, :backendType, :location
                    )
                    """, metadataParams);
        }
        int contentUpdated = jdbc.update("""
                update ha_snapshot_content
                set content = :content
                where snapshot_id = :id
                """, new MapSqlParameterSource()
                .addValue("id", stored.id())
                .addValue("content", content == null ? new byte[0] : content));
        if (contentUpdated == 0) {
            jdbc.update("""
                    insert into ha_snapshot_content (snapshot_id, content)
                    values (:id, :content)
                    """, new MapSqlParameterSource()
                    .addValue("id", stored.id())
                    .addValue("content", content == null ? new byte[0] : content));
        }
        return stored;
    }

    @Override
    public Optional<Snapshot> load(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select m.id, m.tenant_id, m.agent_id, m.session_id, m.task_id, m.created_at,
                           m.backend_type, m.location, c.content
                    from ha_snapshot_metadata m
                    join ha_snapshot_content c on c.snapshot_id = m.id
                    where m.id = :id
                    """, Map.of("id", snapshotId.trim()), snapshotMapper()));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public List<SnapshotMetadata> list(String tenantId, String agentId, String sessionId) {
        return jdbc.query("""
                select id, tenant_id, agent_id, session_id, task_id, created_at, backend_type, location
                from ha_snapshot_metadata
                where tenant_id = :tenantId
                  and agent_id = :agentId
                  and session_id = :sessionId
                order by created_at asc, id asc
                """, Map.of(
                        "tenantId", tenantId,
                        "agentId", agentId,
                        "sessionId", sessionId),
                metadataMapper());
    }

    @Override
    public boolean delete(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return false;
        }
        return jdbc.update("""
                delete from ha_snapshot_metadata
                where id = :id
                """, Map.of("id", snapshotId.trim())) > 0;
    }

    private static MapSqlParameterSource metadataParams(SnapshotMetadata metadata) {
        return new MapSqlParameterSource()
                .addValue("id", metadata.id())
                .addValue("tenantId", metadata.tenantId())
                .addValue("agentId", metadata.agentId())
                .addValue("sessionId", metadata.sessionId())
                .addValue("taskId", metadata.taskId())
                .addValue("createdAt", Timestamp.from(metadata.createdAt()))
                .addValue("backendType", metadata.backendType().name())
                .addValue("location", metadata.location());
    }

    private static RowMapper<SnapshotMetadata> metadataMapper() {
        return (rs, rowNum) -> metadata(rs);
    }

    private static RowMapper<Snapshot> snapshotMapper() {
        return (rs, rowNum) -> new Snapshot(metadata(rs), rs.getBytes("content"));
    }

    private static SnapshotMetadata metadata(ResultSet rs) throws SQLException {
        return new SnapshotMetadata(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("agent_id"),
                rs.getString("session_id"),
                rs.getString("task_id"),
                instant(rs, "created_at"),
                SnapshotStoreType.valueOf(rs.getString("backend_type")),
                rs.getString("location"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }
}
