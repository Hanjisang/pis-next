package com.hanjisang.pis.security;

public interface EnhancedAuthenticationPort {

    EnhancedAuthenticationProof prove(ActorContext actor, String operationCode);
}
