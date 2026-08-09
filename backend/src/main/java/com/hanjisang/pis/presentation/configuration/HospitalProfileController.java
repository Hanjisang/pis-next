package com.hanjisang.pis.presentation.configuration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.security.P15AuthorizationService;

@RestController
@RequestMapping("/api/v2/site/hospital-profiles")
public class HospitalProfileController {

    private final HospitalProfileApplicationService profiles;
    private final P15AuthorizationService authorization;

    public HospitalProfileController(HospitalProfileApplicationService profiles,
            P15AuthorizationService authorization) {
        this.profiles = profiles;
        this.authorization = authorization;
    }

    @GetMapping("/{profileCode}")
    public HospitalProfileSnapshot get(@PathVariable String profileCode) {
        authorization.require("P14-PERM-055");
        return profiles.requireProfile(profileCode);
    }

    @GetMapping("/{profileCode}/business-types/{businessTypeCode}/registration-configuration")
    public HospitalProfileApplicationService.RegistrationConfiguration registrationConfiguration(
            @PathVariable String profileCode, @PathVariable String businessTypeCode) {
        authorization.require("P14-PERM-055");
        return profiles.registrationConfiguration(profileCode, businessTypeCode);
    }
}
