package com.delta.dms.ops.aidevopsplatform.web.management;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/** demo profile 管理頁面；帳密驗證一律由 Spring Security 表單登入流程處理。 */
@Controller
@Profile("demo")
public class ManagementPageController {
    @GetMapping("/")
    public String home() { return "redirect:/home"; }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/login-failure")
    public String loginFailure() { return "login-failure"; }

    @GetMapping("/home")
    public String home(Model model, Principal principal) {
        model.addAttribute("username", accountName(principal));
        return "home";
    }

    private String accountName(Principal principal) {
        if (principal instanceof UserDetails user) return user.getUsername();
        return principal == null ? "" : principal.getName();
    }
}
