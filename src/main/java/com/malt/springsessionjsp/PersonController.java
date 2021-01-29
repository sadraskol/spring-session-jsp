package com.malt.springsessionjsp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class PersonController {

    @GetMapping({"/", "/create-new"})
    public String personList(
            Model model
    ) {
        model.addAttribute("persons", List.of(
                Person.of("Daenerys", "Stormwind"),
                Person.of("Jon", "Snow")
        ));
        return "personList";
    }
}
