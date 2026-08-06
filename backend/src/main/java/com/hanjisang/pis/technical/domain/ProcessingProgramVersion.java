package com.hanjisang.pis.technical.domain;

public record ProcessingProgramVersion(String id, String versionLabel, String environmentCode,
        String versionStateCode, String versionDigest, String parameterReference) {

    public boolean active() { return "ACTIVE".equals(versionStateCode); }

    public boolean allowedIn(String runtimeEnvironment) {
        return "FORMAL".equals(environmentCode)
                || ("SYNTHETIC".equals(environmentCode)
                        && ("local".equalsIgnoreCase(runtimeEnvironment) || "test".equalsIgnoreCase(runtimeEnvironment)));
    }
}
