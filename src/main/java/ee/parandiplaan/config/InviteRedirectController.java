package ee.parandiplaan.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class InviteRedirectController {

    @GetMapping("/invite/{token}")
    public String redirectInvite(@PathVariable String token) {
        return "redirect:/invite.html?token=" + token;
    }
}
