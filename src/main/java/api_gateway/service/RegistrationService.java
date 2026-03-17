package api_gateway.service;

import api_gateway.dto.request.CreateCredentialsRequest;
import api_gateway.dto.request.CreateUserRequest;
import api_gateway.dto.request.RegistrationRequest;
import api_gateway.dto.response.RegistrationResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final WebClient.Builder webClientBuilder;

    @Value("${service.user}")
    private String userServiceUrl;

    @Value("${service.auth}")
    private String authServiceUrl;

    public Mono<RegistrationResponse> registerUser(RegistrationRequest request) {
        log.info("Starting distributed registration process for user: {}", request.getUsername());

        return registerInUserService(request)
                .flatMap(userResponse -> {
                    log.info("Successfully registered in user service, proceeding to auth service");
                    return registerInAuthService(request, userResponse);
                })
                .retryWhen(
                        Retry.fixedDelay(5, Duration.ofSeconds(2))
                                .filter(ex -> ex instanceof WebClientRequestException)
                                .onRetryExhaustedThrow((spec, signal) ->
                                        new RegistrationException("User service is unavailable after retries"))
                )
                .onErrorResume(throwable -> {
                    log.error("FINAL registration failure for user: {}", request.getUsername(), throwable);
                    return handleRegistrationFailure(throwable);
                });
    }

    private Mono<UserRegistrationResponse> registerInUserService(RegistrationRequest request) {
        CreateUserRequest userRequest = request.toCreateUserRequest();

        return webClientBuilder.build()
                .post()
                .uri(userServiceUrl + "/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(userRequest)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody ->
                                        Mono.error(new RegistrationException("User service registration failed: " + errorBody)))
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new RegistrationException("User service is unavailable")))
                .bodyToMono(UserRegistrationResponse.class)
                .doOnNext(res ->
                        log.info("User created with id: {}", res.getId())
                );
    }

    private Mono<RegistrationResponse> registerInAuthService(RegistrationRequest request,
                                                             UserRegistrationResponse userResponse) {
        CreateCredentialsRequest authRequest = request.toCreateCredentialsRequest();

        return webClientBuilder.build()
                .post()
                .uri(authServiceUrl + "/authorization/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(authRequest)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    log.warn("Auth service failed, rolling back user creation for: {}", request.getUsername());
                    return rollbackUserCreation(userResponse.getId())
                            .then(response.bodyToMono(String.class)
                                    .flatMap(errorBody ->
                                            Mono.error(new RegistrationException("Auth service registration failed: " + errorBody))));
                })
                .onStatus(HttpStatusCode::is5xxServerError, response -> {
                    log.warn("Auth service unavailable, rolling back user creation for: {}", request.getUsername());
                    return rollbackUserCreation(userResponse.getId())
                            .then(Mono.error(new RegistrationException("Auth service is unavailable")));
                })
                .toBodilessEntity()
                .thenReturn(createSuccessResponse(userResponse))
                .doOnNext(res ->
                        log.info("Auth-service OK for user: {}", request.getUsername())
                );
    }

    private Mono<Void> rollbackUserCreation(Long userId) {
        log.info("Rolling back user creation for userId: {}", userId);
        return webClientBuilder.build()
                .delete()
                .uri(userServiceUrl + "/users/{id}", userId)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v ->
                        log.info("Rollback success for user: {}", userId)
                )
                .onErrorResume(error -> {
                    log.error("Rollback FAILED for user: {}", userId, error);
                    return Mono.empty();
                });
    }

    private Mono<RegistrationResponse> handleRegistrationFailure(Throwable throwable) {
        return Mono.just(RegistrationResponse.builder()
                .success(false)
                .message("Registration failed: " + throwable.getMessage())
                .build());
    }

    private RegistrationResponse createSuccessResponse(UserRegistrationResponse userResponse) {
        return RegistrationResponse.builder()
                .success(true)
                .userId(String.valueOf(userResponse.getId()))
                .username(userResponse.getName())
                .email(userResponse.getEmail())
                .message("User registered successfully in both services")
                .build();
    }

    public static class RegistrationException extends RuntimeException {
        public RegistrationException(String message) {
            super(message);
        }
    }

    @Setter
    @Getter
    public static class UserRegistrationResponse {
        private Long id;
        private String username;
        private String email;
        private String name;
        private String surname;
    }
}