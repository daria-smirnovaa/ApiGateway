package api_gateway.controller;

import api_gateway.dto.request.RegistrationRequest;
import api_gateway.dto.response.RegistrationResponse;
import api_gateway.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/authentication")
@RequiredArgsConstructor
public class RegistrationController {
    private final RegistrationService registrationService;

    @PostMapping("/register")
    public Mono<RegistrationResponse> register(@RequestBody RegistrationRequest request) {
        return registrationService.registerUser(request);
    }
}
