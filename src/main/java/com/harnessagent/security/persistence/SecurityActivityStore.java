package com.harnessagent.security.persistence;

import java.time.Instant;
import java.util.List;

public interface SecurityActivityStore {

    SecurityActivityRecord save(SecurityActivityRecord record);

    List<SecurityActivityRecord> search(String ownerScopeId, Instant occurredAtFromInclusive);
}
