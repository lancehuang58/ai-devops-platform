package com.delta.dms.ops.aidevopsplatform.adapter.access;

import com.delta.dms.ops.aidevopsplatform.service.port.out.DirectoryAuthenticationPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;

/** Configured local accounts for the platform profile. This is not LDAP. */
public final class LocalDirectory implements DirectoryAuthenticationPort {
    private final Map<String, String> passwords;

    public LocalDirectory(Map<String, String> passwords) { this.passwords = Map.copyOf(passwords); }

    @Override
    public String authenticate(String username, char[] password) {
        var expected = passwords.get(username);
        if (expected == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                new String(password).getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid credentials");
        }
        Arrays.fill(password, '\0');
        return username;
    }
}
