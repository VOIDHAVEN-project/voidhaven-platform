package cn.voidhaven.api.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {
    @Test
    void mapsAuthorizationFailuresToForbiddenInsteadOfServerErrors() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestIdFilter.ATTRIBUTE)).thenReturn("01JREQUEST0000000000000000");

        var response = new ApiExceptionHandler().handleAccessDenied(new AccessDeniedException("denied"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ACCESS_DENIED");
        assertThat(response.getBody().retryable()).isFalse();
    }
}
