package vn.nhaminh.api.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Trả index.html cho các đường dẫn của giao diện (không có dấu chấm, không phải /api) để SPA tự định tuyến. */
@Controller
public class SpaController {
    @GetMapping({"/{path:(?!api$)[^\\.]*}", "/{path:(?!api$)[^\\.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
