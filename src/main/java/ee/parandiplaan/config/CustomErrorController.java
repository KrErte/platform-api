package ee.parandiplaan.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute("jakarta.servlet.error.status_code");
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            if (statusCode == HttpStatus.FORBIDDEN.value()) {
                return "forward:/error/403.html";
            }
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return "forward:/error/404.html";
            }
            if (statusCode >= 500) {
                return "forward:/error/500.html";
            }
        }
        return "forward:/error/404.html";
    }
}
