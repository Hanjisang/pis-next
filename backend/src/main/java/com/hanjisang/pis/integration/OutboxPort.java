package com.hanjisang.pis.integration;

import java.util.UUID;

public interface OutboxPort {

    void append(String eventTypeCode, UUID subjectId, String subjectKindCode, long aggregateVersion,
            String correlationId, String payloadDigest, String actorRef);
}
