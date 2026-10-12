package cn.voidhaven.api.web;

import cn.voidhaven.api.service.ProjectJoinService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
public class ProjectJoinController {
    private final ProjectJoinService projectJoin;

    public ProjectJoinController(ProjectJoinService projectJoin) {
        this.projectJoin = projectJoin;
    }

    @GetMapping("/participation/project-join")
    ApiResponse<List<ProjectJoinService.Entry>> entries(
            @RequestHeader(value = "X-Community-Grant", required = false) String grant,
            HttpServletRequest request) {
        return new ApiResponse<>(projectJoin.publicEntries(grant),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
}
