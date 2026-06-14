package com.harnessagent.security;

import java.util.Optional;

public interface SecretStore {

    Optional<String> resolve(String reference);
}
