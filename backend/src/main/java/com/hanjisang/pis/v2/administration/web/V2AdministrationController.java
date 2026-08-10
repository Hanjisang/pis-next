package com.hanjisang.pis.v2.administration.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.administration.application.V2AdministrationApplicationService;
import com.hanjisang.pis.v2.administration.application.V2AdministrationApplicationService.UpdateUser;

@RestController
@RequestMapping("/api/v2/administration")
public class V2AdministrationController {

    private final V2AdministrationApplicationService service;

    public V2AdministrationController(V2AdministrationApplicationService service) { this.service = service; }

    @GetMapping
    public V2AdministrationApplicationService.AdministrationSnapshot snapshot() { return service.snapshot(); }

    @PutMapping("/users/{id}")
    public V2AdministrationApplicationService.AdministrationSnapshot updateUser(@PathVariable UUID id,
            @RequestBody UpdateUser request) { return service.updateUser(id, request); }
}
