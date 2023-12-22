package org.studyeasy.SpringBlog.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.studyeasy.SpringBlog.Util.email.EmailDetails;
import org.studyeasy.SpringBlog.models.Account;
import org.studyeasy.SpringBlog.services.AccountService;
import org.studyeasy.SpringBlog.services.EmailService;

@Controller
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private EmailService emailService;

    private int password_token_timeout = 600;

    private String site_domain = "http://localhost:8080/";

    @GetMapping("/register")
    public String register(Model model) {
        Account account = new Account();
        model.addAttribute("account", account);
        return "account_views/register";
    }

    @PostMapping("/register")
    public String register_user(@Valid @ModelAttribute Account account, BindingResult result) {
        if (result.hasErrors()) {
            return "account_views/register";
        }
        if (account.getEmail() == null || account.getEmail().isEmpty() ||
                account.getPassword() == null || account.getPassword().isEmpty()) {
            result.rejectValue("email", "error.account", "Email and password are required");
            return "account_views/register";
        }
        accountService.save(account);
        return "redirect:/";
    }

    @GetMapping("/login")
    public String login(Model model) {
        return "account_views/login";
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public String profile(Model model, Principal principal) {
        String authUser = "email";
        if (principal != null) {
            authUser = principal.getName();
        }
        Optional<Account> optionalAccount = accountService.findOneByEmail(authUser);
        if (optionalAccount.isPresent()) {
            Account account = optionalAccount.get();
            model.addAttribute("account", account);
            return "account_views/profile";
        } else {
            return "redirect:/?error";
        }
    }

    @GetMapping("/test")
    public String test(Model model) {
        return "test";
    }

    @PostMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public String post_profile(@Valid @ModelAttribute Account account, BindingResult bindingResult,
            Principal principal) {
        String authUser = "email";

        if (bindingResult.hasErrors()) {
            return "account_views/profile";
        }
        if (principal != null) {
            authUser = principal.getName();
        }
        Optional<Account> optionalAccount = accountService.findOneByEmail(authUser);
        if (optionalAccount.isPresent()) {
            Account account_by_id = accountService.findById(account.getId()).get();
            account_by_id.setAge(account.getAge());
            account_by_id.setDate_of_birth(account.getDate_of_birth());
            account_by_id.setFirstname(account.getFirstname());
            account_by_id.setGender(account.getGender());
            account_by_id.setLastname(account.getLastname());
            account_by_id.setPassword(account.getPassword());

            accountService.save(account_by_id);
            SecurityContextHolder.clearContext();
            return "redirect:/";
        } else {
            return "redirect:/?error";
        }
    }

    @GetMapping("/forgot-password")
    public String forgot_password(Model model) {
        return "account_views/forgot_password";
    }

    @PostMapping("/reset-password")
    public String reset_password(@RequestParam("email") String _email, RedirectAttributes attributes, Model model) {
        Optional<Account> optional_account = accountService.findOneByEmail(_email);
        if (optional_account.isPresent()) {
            Account account = accountService.findById(optional_account.get().getId()).get();
            String reset_token = UUID.randomUUID().toString();
            account.setToken(reset_token);
            account.setPassword_reset_token_expiry(LocalDateTime.now().plusMinutes(password_token_timeout));
            accountService.save(account);
            String reset_message = "This is the reset password link: "+site_domain+"change-password?token="+reset_token;
            EmailDetails emailDetails = new EmailDetails(account.getEmail(), reset_message,
                    "Reset Password EgeGap demo");
            if (emailService.sendSimpleEmail(emailDetails) == false) {
                attributes.addFlashAttribute("error", "Error while sending email, contact admin");
                return "redirect:/forgot-password";
            }
            attributes.addFlashAttribute("message", "Password reset email sent");
            return "redirect:/login";

        } else {
            attributes.addFlashAttribute("error", "No user found with the email supplied");
            return "redirect:/forgot-password";

        }
    }

    @GetMapping("/change-password")
    public String change_password(Model model, @RequestParam("token") String token, RedirectAttributes attributes) {
        if(token.equals("")){
            attributes.addFlashAttribute("error","Invalid Token");
            return "redirect:/forgot-password";
        }

        Optional<Account> optional_account = accountService.findByToken(token);
        if (optional_account.isPresent()) {
            Account account = accountService.findById(optional_account.get().getId()).get();
            LocalDateTime now = LocalDateTime.now();
            if(now.isAfter(optional_account.get().getPassword_reset_token_expiry())){
                attributes.addFlashAttribute("error","Token Expired");
                return "redirect:/forgot-password";
            }
            model.addAttribute("account", account);
            return "account_views/change_password";
        }
        attributes.addFlashAttribute("error", "Invalid token");
        return "redirect:/forgot-password";
    }

    @PostMapping("/change-password")
    public String post_change_password(@ModelAttribute Account account, RedirectAttributes attributes){
        Account account_by_id = accountService.findById(account.getId()).get();
        account_by_id.setPassword(account.getPassword());
        account_by_id.setToken("");
        accountService.save(account_by_id);
        attributes.addFlashAttribute("message", "Password Updated");
        return "redirect:/login";
    }

}
