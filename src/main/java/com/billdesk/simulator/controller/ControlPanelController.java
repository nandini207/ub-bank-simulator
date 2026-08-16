package com.billdesk.simulator.controller;

import com.billdesk.simulator.model.SimulatorOutcome;
import com.billdesk.simulator.model.SimulatorSettings;
import com.billdesk.simulator.repository.TransactionRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


@Controller
@RequestMapping("/control")
public class ControlPanelController {

    private final SimulatorSettings simulatorSettings;

    private final TransactionRepository transactionRepository;

    public ControlPanelController(SimulatorSettings simulatorSettings,
                                   TransactionRepository transactionRepository) {
        this.simulatorSettings = simulatorSettings;
        this.transactionRepository = transactionRepository;
    }

    @GetMapping
    public String showControlPanel(Model model) {
    	
        model.addAttribute("settings", simulatorSettings);

        model.addAttribute("allOutcomes", SimulatorOutcome.values());

        model.addAttribute("transactions", transactionRepository.findAll());
        model.addAttribute("transactionCount", transactionRepository.count());

        return "control";
    }

    @PostMapping
    public String saveSettings(
            @RequestParam(value = "defaultOutcome",          defaultValue = "SUCCESS") String outcome,
            @RequestParam(value = "callbackDelaySeconds",    defaultValue = "0")       int delaySeconds,
            @RequestParam(value = "dropCallback",            defaultValue = "false")   boolean dropCallback,
            @RequestParam(value = "duplicateCallback",       defaultValue = "false")   boolean duplicateCallback,
            @RequestParam(value = "pendingCheckerDelay",     defaultValue = "10")      int pendingDelay,
            @RequestParam(value = "pendingFinalOutcome",     defaultValue = "SUCCESS") String pendingOutcome) {

        simulatorSettings.setDefaultOutcome(SimulatorOutcome.valueOf(outcome));
        simulatorSettings.setCallbackDelaySeconds(delaySeconds);
        simulatorSettings.setDropCallback(dropCallback);
        simulatorSettings.setDuplicateCallback(duplicateCallback);
        simulatorSettings.setPendingCheckerDelaySeconds(pendingDelay);
        simulatorSettings.setPendingFinalOutcome(SimulatorOutcome.valueOf(pendingOutcome));

        return "redirect:/control";
    }
}
