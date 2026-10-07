package by.ilya.restaurantbot.miniapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MiniAppWebConfiguration implements WebMvcConfigurer {
    private final TelegramMiniAppInitDataVerifier verifier;
    private final ObjectMapper mapper;
    private final boolean devMode;

    public MiniAppWebConfiguration(TelegramMiniAppInitDataVerifier verifier, ObjectMapper mapper,
            Environment environment, @Value("${restaurant-bot.miniapp.dev-mode:false}") boolean devMode,
            @Value("${server.address:127.0.0.1}") String address) {
        if (devMode && (!environment.matchesProfiles("dev", "test")
                || environment.matchesProfiles("prod", "production")
                || !(address.equals("127.0.0.1") || address.equals("::1")))) {
            throw new IllegalStateException("Mini App dev mode requires dev/test profile and loopback binding");
        }
        this.verifier = verifier;
        this.mapper = mapper;
        this.devMode = devMode;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/miniapp/").setViewName("forward:/miniapp/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws java.io.IOException {
                response.setHeader("Cache-Control", "no-store");
                String raw = request.getHeader("X-Telegram-Init-Data");
                if (devMode && (raw == null || raw.isBlank()) && isLoopback(request.getRemoteAddr())) return true;
                try {
                    request.setAttribute("miniappContext", verifier.verify(raw));
                    return true;
                } catch (TelegramMiniAppInitDataVerifier.AuthFailed e) {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    mapper.writeValue(response.getWriter(), MiniAppErrors.error("AUTH_FAILED"));
                    return false;
                }
            }
        }).addPathPatterns("/api/miniapp/**");
    }

    private static boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address);
    }
}
