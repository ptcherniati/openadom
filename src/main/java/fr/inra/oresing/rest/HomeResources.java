package fr.inra.oresing.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class HomeResources {

    @GetMapping(value = "/")
    public RedirectView home() {
        RedirectView result = new RedirectView();
        result.setContextRelative(true);
        result.setUrl("/swagger-ui.html");
        return result;
    }
}
