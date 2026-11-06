# Plan: Signup and Login Service with JWT

Legend: **📚 Learn** marks the concepts you need to pick up at each step.

## What you have now

| Item | State |
|---|---|
| Spring Boot `4.2.0-M2`, Java 17, Maven | Works, but it's a **milestone release**, which matters for library compatibility (see Step 0). |
| `spring-boot-starter-security` | Already in `pom.xml`. Spring Security's default config locks **every endpoint** behind a generated password, so even `GET /` is protected. |
| `spring-boot-starter-data-jpa` + PostgreSQL | Configured in `application.properties` (`ddl-auto=update`). No entities yet. |
| `spring-boot-starter-mail` | Present. Can be used later for email verification or password reset. |
| Lombok | Present. |
| Code | Only `HelloController` (`GET /`) and an empty `contextLoads` test. |
| Missing | A JWT library, the validation starter, a User entity, and any auth code. |

## Target architecture

```
POST /api/auth/signup  → validate → hash password → save User → 201
POST /api/auth/login   → check credentials → return signed JWT
GET  /api/me           → JwtAuthFilter reads "Authorization: Bearer <token>"
                         → verifies it → sets SecurityContext → controller runs
```

Package layout under `com.challenge.signuplogin`:

```
user/      User, Role, UserRepository
auth/      AuthController, AuthService, SignupRequest, LoginRequest, AuthResponse
security/  SecurityConfig, JwtService, JwtAuthenticationFilter, CustomUserDetailsService
error/     GlobalExceptionHandler
```

---

## Step 0: Prep and foundations

1. Decide whether to stay on `4.2.0-M2`. A stable Boot 3.x or 4.x release is safer while learning, because milestone versions can break third-party libraries like the JWT one.
2. Add dependencies to `pom.xml`:
   - `spring-boot-starter-validation`
   - JJWT: `jjwt-api`, plus `jjwt-impl` and `jjwt-jackson` at runtime
3. Move secrets out of `application.properties`. Use environment variables for the DB password and the JWT secret, for example `${JWT_SECRET}`.

**📚 Learn:**
- **What a JWT is.** Three parts: header, payload (claims), signature. It is **signed, not encrypted**: anyone can read it, nobody can modify it. Paste one into jwt.io to see this.
- **Stateless vs session auth.** The server stores no session; the token itself is the proof of login.
- **Why not hardcode secrets.** Your DB password is currently committed to git.

## Step 1: User model and database

1. Create a `User` entity with `id`, `email` (unique), `passwordHash`, `role`, `createdAt`.
2. Create `UserRepository extends JpaRepository<User, Long>` with `findByEmail` and `existsByEmail`.
3. Run the app against Postgres and check that the table is created.

**📚 Learn:**
- **JPA basics:** `@Entity`, `@Id`, `@GeneratedValue`, `@Column(unique = true)`, and Spring Data derived queries.
- **Why `ddl-auto=update` is fine for learning but not for production.** The production alternative is Flyway or Liquibase migrations.
- **Never name a table `user` in Postgres.** It's a reserved word, so use `@Table(name = "users")`.

## Step 2: Signup (no JWT yet)

1. Create a `SignupRequest` DTO with `@Email`, `@NotBlank`, `@Size(min = 8)`.
2. Define a `PasswordEncoder` bean (`BCryptPasswordEncoder`).
3. Write `AuthService.signup()`: reject duplicate emails, hash the password, save the user.
4. Write `AuthController` with `POST /api/auth/signup`, returning `201` and **no password in the response**.
5. Add a `GlobalExceptionHandler` (`@RestControllerAdvice`) so validation errors return `400` and a duplicate email returns `409`.

**📚 Learn:**
- **Password hashing vs encryption.** BCrypt is one-way and salted. Never store plain text.
- **DTOs vs entities.** Don't expose or accept your entity directly.
- **Bean validation:** `@Valid` and `MethodArgumentNotValidException`.
- **HTTP status codes** for each failure case.

## Step 3: Open the doors in Spring Security

Right now Security blocks everything, so signup will return `401`. Define a `SecurityFilterChain` bean:

- `permitAll()` for `/api/auth/**`
- `authenticated()` for everything else
- Disable CSRF (acceptable for stateless APIs using bearer tokens)
- `SessionCreationPolicy.STATELESS`

**📚 Learn (this is the hardest conceptual part):**
- **The Security filter chain.** Requests pass through an ordered list of filters before reaching your controller.
- **`SecurityFilterChain` bean style.** The older `WebSecurityConfigurerAdapter` is removed, so ignore old tutorials that use it.
- **What CSRF is,** and why it's safe to disable when you don't use cookies.
- **Authentication vs authorization.**

## Step 4: Login and issuing a JWT

1. Implement `CustomUserDetailsService implements UserDetailsService`, which loads a user by email and maps it to Spring's `UserDetails`.
2. Expose `AuthenticationManager` as a bean.
3. Write `JwtService` with:
   - `generateToken(user)`: subject = email, `iat`, `exp` (for example 15–60 minutes), and a `role` claim
   - `extractUsername(token)`
   - `isTokenValid(token, userDetails)`
4. Add `POST /api/auth/login`. Call `authenticationManager.authenticate(...)`, then return `{ "accessToken": "...", "expiresIn": ... }`.
5. Return a **generic** `401` for both "unknown email" and "wrong password".

**📚 Learn:**
- **`UserDetails`, `UserDetailsService`, `AuthenticationManager` and `AuthenticationProvider`.** How they connect is the core of Spring Security.
- **JWT claims:** `sub`, `iat`, `exp`, and custom claims.
- **HMAC signing (HS256).** The secret must be long enough (≥256 bits) and Base64-encoded in config.
- **Why error messages must be generic.** Specific ones allow user enumeration.

## Step 5: Protecting endpoints with a JWT filter

1. Write `JwtAuthenticationFilter extends OncePerRequestFilter`:
   - Read the `Authorization` header and check for the `Bearer ` prefix
   - Extract and validate the token
   - Build a `UsernamePasswordAuthenticationToken` and put it in `SecurityContextHolder`
   - Always continue the chain with `filterChain.doFilter(...)`
2. Register it in the chain with `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`.
3. Add a protected `GET /api/me` that returns the current user's email and role.
4. Handle an invalid or expired token with `401` and a JSON body, using a custom `AuthenticationEntryPoint`.

**📚 Learn:**
- **`OncePerRequestFilter`** and filter ordering.
- **`SecurityContextHolder`** and how `@AuthenticationPrincipal` gets its value.
- **Exceptions from JJWT:** `ExpiredJwtException`, `SignatureException`, `MalformedJwtException`.
- **401 vs 403.** Unauthenticated and unauthorized are different.

## Step 6: Roles (optional but useful)

- Add `USER` and `ADMIN` roles.
- Protect an endpoint with `.requestMatchers("/api/admin/**").hasRole("ADMIN")` or `@PreAuthorize`, which needs `@EnableMethodSecurity`.

**📚 Learn:** `GrantedAuthority` and the `ROLE_` prefix convention.

## Step 7: Testing

1. **Unit tests** for `JwtService`: generated tokens are valid, expired tokens are rejected, tampered tokens are rejected.
2. **Integration tests** using `MockMvc` (the `webmvc-test` and `security-test` starters are already included):
   - signup succeeds, then a duplicate returns `409`
   - login with the right password returns a token, and the wrong password returns `401`
   - `/api/me` without a token returns `401`, and with a token returns `200`
3. Use a test database, either H2 or **Testcontainers** with Postgres, so tests don't touch your real DB.
4. Try the flow manually with `curl` or Postman.

**📚 Learn:** `@SpringBootTest` + `@AutoConfigureMockMvc`, `@WithMockUser`, and Testcontainers.

## Step 8: Hardening and extensions (later)

| Feature | What to learn |
|---|---|
| **Refresh tokens** (short-lived access token, long-lived refresh token) | Token rotation, storing and revoking refresh tokens, and why you can't revoke a plain JWT |
| **Email verification and password reset** (you already have `starter-mail`) | Single-use tokens, expiry, and testing with MailHog or Mailpit |
| **Rate limiting on `/login`** | Brute-force protection |
| **CORS** if a frontend will call this | Preflight requests and allowed origins |
| **Where to store the token on the client** | `localStorage` (XSS risk) vs `HttpOnly` cookie (CSRF risk) |
| **Logout** | A stateless JWT can't be "deleted" server-side, so you'd need a denylist or short expiry |

---

## Suggested order and checkpoints

1. Steps 0–2: you can register users, and the DB has hashed passwords.
2. Steps 3–4: you can log in and get a token, and you can decode it on jwt.io.
3. Step 5: `/api/me` works only with a valid token.
4. Step 7: tests pass.
5. Steps 6 and 8: pick whatever interests you.

## Biggest gotchas to watch for

- **Everything returns 401 or 403 after adding Security.** This is usually a missing `permitAll()` on the auth routes.
- **Old tutorials use deprecated APIs.** Check that they use `SecurityFilterChain` and lambda DSL, and JJWT 0.12+ (`Jwts.builder().subject(...)` and `Jwts.parser().verifyWith(...)`).
- **The `4.2.0-M2` milestone** can cause odd dependency errors, so switch versions first if you hit one.
- **Never log tokens or passwords.**

The YouTube link in `doc.md` may follow an older Spring Security style, so cross-check it against the points above.
