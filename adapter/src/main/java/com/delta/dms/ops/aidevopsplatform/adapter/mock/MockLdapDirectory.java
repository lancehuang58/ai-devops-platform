package com.delta.dms.ops.aidevopsplatform.adapter.mock;

import com.delta.dms.ops.aidevopsplatform.service.port.out.DirectoryAuthenticationPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Explicit demo identity provider. No LDAP network connection is made. */
public final class MockLdapDirectory implements DirectoryAuthenticationPort {
    private final Map<String,byte[]> passwords;
    public MockLdapDirectory(Map<String,String> users) {
        var copy=new HashMap<String,byte[]>();
        users.forEach((username,password) -> {
            if(username.isBlank() || password == null || password.isBlank()) throw new IllegalArgumentException("Mock user credentials required");
            copy.put(username, password.getBytes(StandardCharsets.UTF_8));
        });
        if(copy.isEmpty()) throw new IllegalArgumentException("Mock users required");
        passwords=Map.copyOf(copy);
    }
    @Override public String authenticate(String username, char[] password) {
        byte[] supplied=new String(password).getBytes(StandardCharsets.UTF_8);
        try {
            var expected=passwords.get(username);
            if(expected == null || !MessageDigest.isEqual(expected,supplied)) throw new SecurityException("Invalid credentials");
            return username;
        } finally { Arrays.fill(supplied,(byte)0); }
    }
}
