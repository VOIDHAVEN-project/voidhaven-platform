package cn.voidhaven.api.web;

import cn.voidhaven.api.service.OperationalTaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tasks")
public class AdminTaskController {
    private final OperationalTaskService tasks;
    public AdminTaskController(OperationalTaskService tasks) { this.tasks = tasks; }
    @GetMapping ApiResponse<List<OperationalTaskService.Task>> tasks(HttpServletRequest request) {
        return new ApiResponse<>(tasks.tasks(), (String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
}
