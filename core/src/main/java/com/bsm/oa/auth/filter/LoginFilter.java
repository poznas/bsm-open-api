package com.bsm.oa.auth.filter;

import static com.bsm.oa.auth.Headers.HEADER_AUTHORIZATION;
import static com.bsm.oa.auth.Headers.HEADER_AWS_IDENTITY;
import static com.bsm.oa.auth.Headers.HEADER_AWS_TOKEN;
import static com.bsm.oa.auth.Headers.HEADER_ID_TOKEN;
import static com.bsm.oa.auth.Headers.HEADER_REFRESH_TOKEN;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;

import com.bsm.oa.auth.TokenProvider;
import com.bsm.oa.auth.service.TokenVerifier;
import com.bsm.oa.common.model.User;
import com.bsm.oa.user.service.UserService;
import java.io.IOException;
import java.util.function.Supplier;
import javax.servlet.FilterChain;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RequiredArgsConstructor
@WebFilter(urlPatterns = "/login")
public class LoginFilter extends OncePerRequestFilter {

  private static final Supplier<ResponseStatusException> EMPTY_ID_TOKEN_HEADER =
    () -> new ResponseStatusException(BAD_REQUEST, HEADER_ID_TOKEN + " cannot be empty");

  private final TokenProvider tokenProvider;
  private final TokenVerifier tokenVerifier;
  private final UserService userService;

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
                                  @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain)
    throws IOException {

    try {
      var idToken = ofNullable(request.getHeader(HEADER_ID_TOKEN))
        .orElseThrow(EMPTY_ID_TOKEN_HEADER);

      User user = tokenVerifier.verifyTokenId(idToken);
      Authentication authentication = userService.getUserAuthentication(user);

      String accessToken = tokenProvider.createAccessToken(authentication);
      String refreshToken = tokenProvider.createRefreshToken(authentication);
      response.addHeader(HEADER_AUTHORIZATION, accessToken);
      response.addHeader(HEADER_REFRESH_TOKEN, refreshToken);

      var awsAccessToken = userService.getOpenIdAccessToken(user.getUserId());
      response.addHeader(HEADER_AWS_IDENTITY, awsAccessToken.getIdentityId());
      response.addHeader(HEADER_AWS_TOKEN, awsAccessToken.getToken());

      response.setStatus(OK.value());

    } catch (ResponseStatusException ex) {
      response.sendError(ex.getStatus().value(), ex.getReason());
    }
  }
}
