# Plan: Signup and Login with Server Sessions

Legend: **📚 Learn** marks the concepts you need to pick up at each step.

This version keeps signup, the user table, and Spring Security. After login the server stores the login in an HTTP session and sends back a cookie. Later requests send that cookie, and logout deletes the session.

JJWT and `app.jwt.*` in `application.properties` can sit unused until you return to tokens.

## What you have now

| Item | State |
|---|---|
| Spring Boot `4.2.0-M2`, Java 17, Maven | Works. Session login uses only Spring Security, so the JWT-library risk from the token plan does not apply here. |
| `spring-boot-starter-security` | On the classpath. The default config locks every endpoint behind a generated password, so even `GET /` is protected. |
| `spring-boot-starter-data-jpa` + PostgreSQL | Configured in `application.properties` (`ddl-auto=update`). |
| `spring-boot-starter-validation` | Already in `pom.xml`. |
| `spring-boot-starter-mail` | Present. Leave it for later. |
| Lombok | Present. |
| User model | `User`, `Role`, and `UserRepository` exist. `@Table` still needs the name `users`. |
| Auth code | None yet. No `SecurityFilterChain`, signup, or login. |

## Target architecture

```
POST /api/auth/signup  → validate → hash password → save User → 201
GET  /api/auth/csrf    → issue a CSRF token (cookie + body)
POST /api/auth/login   → check credentials → create session → Set-Cookie
GET  /api/me           → cookie identifies the session → SecurityContext is restored
POST /api/auth/logout  → invalidate the session → browser drops the cookie
```

The cookie is only an id. The email, role, and authentication live in the session on the server. Restarting the app clears in-memory sessions, which is fine while learning.

Package layout under `com.challenge.signuplogin`:

```
user/      User, Role, UserRepository          (already there)
auth/      AuthController, AuthService, SignupRequest, LoginRequest
security/  SecurityConfig, CustomUserDetailsService
error/     GlobalExceptionHandler
```

Spring Security's session filters reload the `SecurityContext` from the session. This plan has no `JwtService` and no authentication filter of your own.

---

## Step 0: Prep

1. Keep Boot `4.2.0-M2` unless a dependency fails.
2. Confirm `spring-boot-starter-validation` is on the classpath. It already is.
3. Keep the database password in `${DB_PASSWORD}`. There is no signing secret for this version.
4. Add one session setting when you want an explicit lifetime, for example `server.servlet.session.timeout=30m`. The default cookie name is `JSESSIONID`, and it is `HttpOnly`.

**📚 Learn:**
- **Session vs token.** The browser stores an opaque id. The server stores who is logged in. Logout works, because the server can delete that record.
- **Why the cookie is enough to impersonate someone.** Browsers attach it to matching requests automatically, which is why CSRF matters in the next steps.
- **Where the session lives.** The default is memory inside this JVM. A second instance, or a restart, does not see those logins. A shared store (JDBC or Redis) is a later step.

## Step 1: Finish the user model

Most of this is done. Two checks before signup:

1. Set `@Table(name = "users")` on `User`. The name `user` is reserved in Postgres.
2. Start the app and confirm Postgres created `users` with `email`, `password_hash`, `role`, and `created_at`.

**📚 Learn:**
- **JPA mapping** and derived queries (`findByEmail`, `existsByEmail`).
- **`ddl-auto=update` is fine for learning.** Flyway or Liquibase is the production replacement.

## Step 2: Signup

Still no login machinery:

1. `SignupRequest` with `@Email`, `@NotBlank`, and `@Size(min = 8)` on the password.
2. A `PasswordEncoder` bean (`BCryptPasswordEncoder`).
3. `AuthService.signup()`: reject a duplicate email, hash the password, save the user with `Role.USER`.
4. `POST /api/auth/signup` returns `201` and a body with id and email only.
5. `GlobalExceptionHandler`: validation failures are `400`, duplicate email is `409`.

**📚 Learn:**
- **Password hashing vs encryption.** BCrypt is one-way and salted. Never store plain text.
- **DTOs vs entities.** Don't expose or accept your entity directly.
- **Bean validation:** `@Valid` and `MethodArgumentNotValidException`.
- **HTTP status codes** for each failure case.

Signup has to be reachable before a session exists, so this endpoint is public in Step 3. It still needs a CSRF token once CSRF is on. Step 3 includes the token endpoint so you can call signup with `curl`.

## Step 3: Security filter chain for sessions

Replace the default "lock everything" setup with a `SecurityFilterChain`:

- `permitAll()` for `POST /api/auth/signup`, `POST /api/auth/login`, and `GET /api/auth/csrf`.
- `authenticated()` for everything else, including `GET /api/me`.
- Leave session creation at `SessionCreationPolicy.IF_REQUIRED` (the default). A session is created when login succeeds.
- Leave CSRF enabled.
- Serve the CSRF token in a cookie the client can read, via `CookieCsrfTokenRepository.withHttpOnlyFalse()`, and add `GET /api/auth/csrf` that returns the `CsrfToken`.
- `POST /api/auth/logout` can stay authenticated. It needs a live session to invalidate, plus the CSRF header.

**📚 Learn:**
- **The Security filter chain.** Requests pass through an ordered list of filters before your controller. For sessions, the important ones are the security-context filter (loads the session), the CSRF filter, and the authorization filter.
- **`SecurityFilterChain` bean style.** The older `WebSecurityConfigurerAdapter` is removed, so ignore old tutorials that use it.
- **CSRF here is required.** A malicious site can trigger a browser request that includes `JSESSIONID`. The CSRF token is a second value that site cannot read.
- **Authentication vs authorization.** Login proves who you are. `authenticated()` and `hasRole` decide what you may do.
- Spring Security changes the session id on a successful login (`migrateSession`) so a pre-login session id cannot be reused.

## Step 4: Login

1. `CustomUserDetailsService` loads by email and maps to `UserDetails`, with the password hash and a `ROLE_` authority from `Role`.
2. Expose `AuthenticationManager` if auto-configuration does not already provide one once `UserDetailsService` and `PasswordEncoder` exist. On current Boot, defining those two beans is often enough, and you inject `AuthenticationManager` into the controller.
3. `POST /api/auth/login` accepts email and password, then:

```java
Authentication authentication = authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(email, password));
SecurityContext context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(authentication);
SecurityContextHolder.setContext(context);
securityContextRepository.saveContext(context, request, response);
```

Use `HttpSessionSecurityContextRepository`. Since Spring Security 6 the context is saved only when you call `saveContext`. Putting it in `SecurityContextHolder` alone does not write the session.

4. Return `200` with a small body such as `{ "email": "...", "role": "USER" }`. The credential is the `Set-Cookie` header.
5. On bad credentials, return one generic `401` for an unknown email and a wrong password.

**📚 Learn:**
- **`UserDetails`, `UserDetailsService`, and `AuthenticationManager`.** `UserDetailsService` supplies the user. `AuthenticationManager` checks the password through `DaoAuthenticationProvider` and your `PasswordEncoder`.
- A session cookie is a capability. Anyone who has the value is that user until the session ends.
- **Why error messages must be generic.** Specific ones allow user enumeration.
- Call the endpoint with a cookie jar. `curl -c cookies -b cookies` stores `JSESSIONID` and sends it on the next call. Also send the CSRF header from Step 3, usually `X-XSRF-TOKEN`.

## Step 5: A protected endpoint and logout

1. `GET /api/me` returns the email and role from `SecurityContextHolder` or `@AuthenticationPrincipal`.
2. Call it with the login cookie and expect `200`. Call it with no cookie and expect `401` and a JSON body from a custom `AuthenticationEntryPoint`.
3. `POST /api/auth/logout` (with the CSRF header) runs `SecurityContextLogoutHandler`, which clears the context and invalidates the session. The next `GET /api/me` with the old cookie is `401`.

**📚 Learn:**
- **`SecurityContextHolder`** and how `@AuthenticationPrincipal` gets its value.
- **401 vs 403.** A missing or dead session is 401. A logged-in user who lacks a role is 403.
- Logout is real. The server forgets the session, so a copied cookie stops working.
- Session timeout (`server.servlet.session.timeout`) is the idle expiry.

---

## Order for these steps

1. Step 1: `users` exists in Postgres.
2. Steps 2–3: you can register, and a missing CSRF token on `POST` returns `403`.
3. Step 4: login responds with `Set-Cookie`, and a second request with that cookie is authenticated.
4. Step 5: `/api/me` follows the session, and logout ends it.

## Gotchas

- **401 or 403 on signup.** The route is missing from `permitAll()`, or the POST has no CSRF token. A CSRF failure is `403`.
- **Login returns 200 but the next call is anonymous.** The context was set on `SecurityContextHolder` and `saveContext` was never called, or the client dropped the cookie.
- **`curl` without `-b` and `-c`.** Each invocation is a new client, so the session never comes back.
- **CSRF cookie and session cookie are different.** `XSRF-TOKEN` is the value you copy into a header. `JSESSIONID` is the login.
- **Session fixation.** Leave the default `migrateSession` in place so the id changes at login.
- **`@Table` without `users`.** Postgres will reject a table named `user`.
- **Never log passwords, session ids, or CSRF tokens.**
