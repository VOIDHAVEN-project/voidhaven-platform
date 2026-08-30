package cn.voidhaven.api.domain;
import jakarta.persistence.*;
@Entity @Table(name = "roles")
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String code;
    protected Role() { }
    public Role(String code) { this.code = code; }
    public String getCode() { return code; }
}
