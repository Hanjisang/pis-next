package com.hanjisang.pis.integration;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.hanjisang.pis.integration.InboundApplicationSource.InboundApplication;

/** Local/test-only source used to exercise the inbox without a hospital interface. */
@Component
@Profile({ "local", "test" })
public class LocalInboundApplicationAdapter implements InboundApplicationSource {

    private final ConcurrentHashMap<UUID, InboundApplication> applications = new ConcurrentHashMap<>();

    public LocalInboundApplicationAdapter() {
        String fixtureRun = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Instant receivedAt = Instant.parse("2026-08-11T08:30:00Z");
        add(new InboundApplication(UUID.randomUUID(),
                "LOCAL-APP-" + fixtureRun + "-001", "LOCAL-PATIENT-" + fixtureRun + "-001",
                "LOCAL-VISIT-" + fixtureRun + "-001", "消化内科", "申请医生A",
                "SYNTH-HISTOLOGY", receivedAt, false, sourceSystemCode(), null, null));
        add(new InboundApplication(UUID.randomUUID(),
                "LOCAL-APP-" + fixtureRun + "-002", "LOCAL-PATIENT-" + fixtureRun + "-002",
                "LOCAL-VISIT-" + fixtureRun + "-002", "消化内科", "申请医生C",
                "SYNTH-HISTOLOGY", receivedAt.plusSeconds(120), false, sourceSystemCode(), null, null));
        add(new InboundApplication(UUID.randomUUID(),
                "LOCAL-APP-" + fixtureRun + "-003", "LOCAL-PATIENT-" + fixtureRun + "-003",
                "LOCAL-VISIT-" + fixtureRun + "-003", "消化内科", "申请医生D",
                "SYNTH-HISTOLOGY", receivedAt.plusSeconds(180), false, sourceSystemCode(), null, null));
        add(new InboundApplication(UUID.randomUUID(),
                "LOCAL-APP-" + fixtureRun + "-004", "LOCAL-PATIENT-" + fixtureRun + "-004",
                "LOCAL-VISIT-" + fixtureRun + "-004", "消化内科", "申请医生E",
                "SYNTH-HISTOLOGY", receivedAt.plusSeconds(240), false, sourceSystemCode(), null, null));
        add(new InboundApplication(UUID.fromString("10000000-0000-0000-0000-000000000002"),
                "LOCAL-APP-CANCELLED", "LOCAL-PATIENT-002", "LOCAL-VISIT-002", "消化内科", "申请医生B",
                "SYNTH-HISTOLOGY", receivedAt.plusSeconds(60), true, sourceSystemCode(), null, null));
    }

    private void add(InboundApplication application) { applications.put(application.applicationId(), application); }

    @Override
    public String sourceSystemCode() { return "LOCAL_FIXTURE"; }

    @Override
    public List<InboundApplication> findApplications() { return applications.values().stream().sorted((a, b) ->
            b.receivedAt().compareTo(a.receivedAt())).toList(); }

    @Override
    public void markRegistered(UUID applicationId, UUID caseId, Instant registeredAt) {
        applications.computeIfPresent(applicationId, (ignored, item) -> new InboundApplication(item.applicationId(),
                item.applicationNo(), item.patientReference(), item.visitReference(), item.department(), item.doctor(),
                item.applicationItemCode(), item.receivedAt(), item.cancelled(), item.sourceSystemCode(), caseId,
                registeredAt));
    }
}
