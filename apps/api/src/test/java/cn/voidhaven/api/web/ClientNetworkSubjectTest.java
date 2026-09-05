package cn.voidhaven.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientNetworkSubjectTest {
    @Test
    void ignores_forwarded_address_when_direct_peer_is_not_a_trusted_proxy() {
        ClientNetworkSubject subject = new ClientNetworkSubject(true, "10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        assertThat(subject.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void accepts_only_a_single_numeric_address_from_a_trusted_proxy() {
        ClientNetworkSubject subject = new ClientNetworkSubject(true, "172.16.0.0/12");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.20.0.3");
        request.addHeader("X-Forwarded-For", "198.51.100.7");
        assertThat(subject.resolve(request)).isEqualTo("198.51.100.7");

        request.removeHeader("X-Forwarded-For");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.2");
        assertThat(subject.resolve(request)).isEqualTo("172.20.0.3");
    }
}
