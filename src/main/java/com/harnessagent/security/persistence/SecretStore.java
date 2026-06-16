package com.harnessagent.security.persistence;

import java.util.Optional;

public interface SecretStore {

    Optional<String> resolve(String reference);
}
