package vn.nhaminh.api.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Phục vụ giao diện React (SPA) từ classpath:/static.
 * File có thật (js, css, icon...) thì trả đúng file; đường dẫn giao diện như /login, /transactions/5
 * thì trả index.html để React tự định tuyến. /api/** và đường dẫn có đuôi file không tồn tại → 404.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {
    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        if (path.isEmpty()) {
                            return INDEX;
                        }
                        Resource requested = location.createRelative(path);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
                        if (path.startsWith("api/") || lastSegment.contains(".")) {
                            return null;
                        }
                        return INDEX;
                    }
                });
    }
}
