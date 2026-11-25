package vn.nhaminh.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.nhaminh.api.user.User;
import vn.nhaminh.api.user.UserRepository;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                String username = jwtService.extractUsername(token);
                userRepository.findByUsernameIgnoreCase(username)
                        .filter(User::isEnabled)
                        .ifPresent(user -> {
                            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                            user.getRoles().forEach(role -> {
                                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
                                role.getPermissions().forEach(permission -> authorities.add(
                                        new SimpleGrantedAuthority("PERM_" + permission.getCode())));
                            });
                            var authentication = new UsernamePasswordAuthenticationToken(
                                    user.getUsername(), null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
            }
        }
        filterChain.doFilter(request, response);
    }
}
