package cn.voidhaven.api.web;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AdministrativeRouteCoverageTest {
    @Test void every_existing_admin_controller_operation_has_an_explicit_policy() {
        var mapping = new org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping();
        mapping.setPatternParser(new org.springframework.web.util.pattern.PathPatternParser());
        var controllers = List.of(AdminController.class, OrganizationAdministrationController.class, AdminAuthorizationController.class, ContentVerificationAdminController.class,
                ResourceConfigController.class, HomepageAdminController.class, CatalogOrderAdminController.class, AdminTaskController.class,
                PasswordAdminController.class, CommunityAdminController.class, ProjectJoinAdminController.class, ManualDeliveryAdminController.class);
        for (Class<?> controller : controllers) {
            var prefix = controller.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class).value()[0];
            for (var method : controller.getDeclaredMethods()) {
                var annotation = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(method, org.springframework.web.bind.annotation.RequestMapping.class);
                if (annotation == null) continue;
                String suffix = annotation.value().length == 0 ? "" : annotation.value()[0];
                for (RequestMethod verb : annotation.method()) assertThat(AdminAuthorizationInterceptor.rule(verb.name(), prefix + suffix)).as(controller.getSimpleName() + "." + method.getName()).isNotNull();
            }
        }
        assertThat(AdminAuthorizationInterceptor.rule("POST", "/api/v1/admin/unknown-new-operation")).isNull();
    }
}
