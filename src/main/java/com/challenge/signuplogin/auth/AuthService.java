package com.challenge.signuplogin.auth;


import com.challenge.signuplogin.user.Role;
import com.challenge.signuplogin.user.User;
import com.challenge.signuplogin.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service 

public class AuthService {
   private final UserRepository userRepository;
   private final PasswordEncoder passwordEncoder;

   public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
     this.userRepository = userRepository;
     this.passwordEncoder = passwordEncoder;
   }

   public SigupResponse singup(SignupRequest request) {
     String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
     if(userRepository.existByEmail(mail)) {
      throw new EmailAlreadyUsedException();
     }

     User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
    try {
            User saved = userRepository.save(user);
            return new SignupResponse(saved.getId(), saved.getEmail());
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyUsedException();
        }
   }
}