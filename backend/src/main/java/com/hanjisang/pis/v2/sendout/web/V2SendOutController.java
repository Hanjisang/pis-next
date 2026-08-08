package com.hanjisang.pis.v2.sendout.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.sendout.application.V2SendOutApplicationService;
import com.hanjisang.pis.v2.sendout.application.V2SendOutApplicationService.ReceiveResultCommand;
import com.hanjisang.pis.v2.sendout.application.V2SendOutApplicationService.RequestCommand;

@RestController
@RequestMapping("/api/v2/send-outs")
public class V2SendOutController {

    private final V2SendOutApplicationService service;

    public V2SendOutController(V2SendOutApplicationService service) {
        this.service = service;
    }

    @PostMapping("/cases/{caseId}")
    public V2SendOutApplicationService.SendOutResult request(@PathVariable UUID caseId,
            @RequestBody Request request) {
        return service.request(caseId, new RequestCommand(request.externalReference(), request.destinationName(),
                request.idempotencyKey()));
    }

    @PostMapping("/{sendOutId}/result")
    public V2SendOutApplicationService.SendOutResult receive(@PathVariable UUID sendOutId,
            @RequestBody ReceiveResultRequest request) {
        return service.receiveResult(sendOutId, new ReceiveResultCommand(request.resultData()));
    }

    @GetMapping("/{sendOutId}")
    public V2SendOutApplicationService.SendOutResult get(@PathVariable UUID sendOutId) {
        return service.get(sendOutId);
    }

    public record Request(String externalReference, String destinationName, String idempotencyKey) { }
    public record ReceiveResultRequest(String resultData) { }
}
