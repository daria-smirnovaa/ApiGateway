package api_gateway.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCredentialsRequest {
    private String username;
    private String email;
    private String password;
    private String confirmPassword;
}