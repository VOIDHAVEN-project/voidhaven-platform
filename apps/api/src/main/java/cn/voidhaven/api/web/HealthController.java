package cn.voidhaven.api.web;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController
public class HealthController { @GetMapping("/healthz") Map<String, String> healthz() { return Map.of("status", "UP"); } }
