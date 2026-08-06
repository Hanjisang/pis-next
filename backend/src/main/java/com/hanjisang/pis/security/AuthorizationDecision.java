package com.hanjisang.pis.security;

public record AuthorizationDecision(boolean allowed, String permissionCode, String reason, ActorContext actor) {
}
