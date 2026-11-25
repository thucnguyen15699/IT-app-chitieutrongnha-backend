package vn.nhaminh.api.planning;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.nhaminh.api.planning.PlanningDtos.ContributionRequest;
import vn.nhaminh.api.planning.PlanningDtos.PlannedExpenseRequest;
import vn.nhaminh.api.planning.PlanningDtos.PlannedExpenseView;
import vn.nhaminh.api.planning.PlanningDtos.PlanningSummary;
import vn.nhaminh.api.planning.PlanningDtos.SavingsGoalRequest;
import vn.nhaminh.api.planning.PlanningDtos.SavingsGoalView;
import vn.nhaminh.api.transaction.TransactionDtos.TransactionView;

@RestController
@RequestMapping("/api")
public class PlanningController {
    private final PlanningService planningService;

    public PlanningController(PlanningService planningService) {
        this.planningService = planningService;
    }

    @GetMapping("/planning/summary")
    public PlanningSummary summary(Authentication authentication, @RequestParam(required = false) String month) {
        return planningService.summary(authentication.getName(), month);
    }

    // ----- Khoản sắp phải chi -----

    @GetMapping("/planned-expenses")
    public List<PlannedExpenseView> listPlanned(Authentication authentication) {
        return planningService.listPlanned(authentication.getName());
    }

    @PostMapping("/planned-expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public PlannedExpenseView createPlanned(
            Authentication authentication, @Valid @RequestBody PlannedExpenseRequest request) {
        return planningService.createPlanned(authentication.getName(), request);
    }

    @PutMapping("/planned-expenses/{id}")
    public PlannedExpenseView updatePlanned(
            Authentication authentication, @PathVariable Long id, @Valid @RequestBody PlannedExpenseRequest request) {
        return planningService.updatePlanned(authentication.getName(), id, request);
    }

    @DeleteMapping("/planned-expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlanned(Authentication authentication, @PathVariable Long id) {
        planningService.deletePlanned(authentication.getName(), id);
    }

    @PostMapping("/planned-expenses/{id}/pay")
    public TransactionView payPlanned(Authentication authentication, @PathVariable Long id) {
        return planningService.payPlanned(authentication.getName(), id);
    }

    // ----- Tiết kiệm -----

    @GetMapping("/savings-goals")
    public List<SavingsGoalView> listGoals(Authentication authentication) {
        return planningService.listGoals(authentication.getName());
    }

    @PostMapping("/savings-goals")
    @ResponseStatus(HttpStatus.CREATED)
    public SavingsGoalView createGoal(Authentication authentication, @Valid @RequestBody SavingsGoalRequest request) {
        return planningService.createGoal(authentication.getName(), request);
    }

    @PutMapping("/savings-goals/{id}")
    public SavingsGoalView updateGoal(
            Authentication authentication, @PathVariable Long id, @Valid @RequestBody SavingsGoalRequest request) {
        return planningService.updateGoal(authentication.getName(), id, request);
    }

    @DeleteMapping("/savings-goals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGoal(Authentication authentication, @PathVariable Long id) {
        planningService.deleteGoal(authentication.getName(), id);
    }

    @PostMapping("/savings-goals/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public SavingsGoalView addContribution(
            Authentication authentication, @PathVariable Long id, @Valid @RequestBody ContributionRequest request) {
        return planningService.addContribution(authentication.getName(), id, request);
    }
}
