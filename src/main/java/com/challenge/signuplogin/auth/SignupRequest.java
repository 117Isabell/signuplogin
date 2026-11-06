package com.challenge.signuplogin.auth;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequest {
    
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min =8)
    private String password;
}