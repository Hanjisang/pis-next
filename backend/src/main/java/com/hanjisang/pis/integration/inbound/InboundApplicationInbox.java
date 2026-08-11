package com.hanjisang.pis.integration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.integration.InboundApplicationSource.InboundApplication;

/**
 * Application-side read/write boundary for registration inbox items. The inbox
 * has no dependency on the V2 Case aggregate and can be backed by HIS, EMR or a
 * local fixture without changing registration domain code.
 */
@Service
public class InboundApplicationInbox {

    private final ObjectProvider<InboundApplicationSource> sourceProvider;

    public InboundApplicationInbox(ObjectProvider<InboundApplicationSource> sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    public InboxSnapshot snapshot() {
        InboundApplicationSource source = sourceProvider.getIfAvailable();
        if (source == null) {
            return new InboxSnapshot(false, "当前尚未连接申请来源", List.of(), List.of());
        }
        List<InboundApplication> applications = source.findApplications();
        return new InboxSnapshot(true, null,
                applications.stream().filter(item -> item.registeredCaseId() == null && !item.cancelled()).toList(),
                applications.stream().filter(item -> item.cancelled() && item.registeredCaseId() == null).toList());
    }

    public InboundApplication require(UUID applicationId) {
        InboundApplicationSource source = sourceProvider.getIfAvailable();
        if (source == null) {
            throw new P15BusinessException("V2-INBOUND-SOURCE-NOT-CONNECTED", "当前尚未连接申请来源", 409);
        }
        InboundApplication application = source.find(applicationId)
                .orElseThrow(() -> new P15BusinessException("V2-INBOUND-APPLICATION-NOT-FOUND", "申请已不存在或已被移除", 404));
        if (application.cancelled()) {
            throw new P15BusinessException("V2-INBOUND-APPLICATION-CANCELLED", "已取消申请不能登记", 409);
        }
        if (application.registeredCaseId() != null) {
            throw new P15BusinessException("V2-INBOUND-APPLICATION-REGISTERED", "该申请已经登记", 409);
        }
        return application;
    }

    public void markRegistered(UUID applicationId, UUID caseId, Instant registeredAt) {
        InboundApplicationSource source = sourceProvider.getIfAvailable();
        if (source != null) source.markRegistered(applicationId, caseId, registeredAt);
    }

    public record InboxSnapshot(boolean sourceAvailable, String sourceMessage,
            List<InboundApplication> pendingApplications, List<InboundApplication> cancelledApplications) { }
}
