package com.delta.dms.ops.aidevopsplatform.service.port.out;

public interface DirectoryAuthenticationPort {
    /** Return canonical identity or throw SecurityException. Never return a password. */
    String authenticate(String username, char[] password);
}
