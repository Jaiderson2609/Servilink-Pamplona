package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String rol;
    private String nombre;
    private String correo;
    private String telefono;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "token_push")
    private String tokenPush;

    protected Usuario() {}

    public Usuario(String rol, String nombre, String correo, String telefono, String passwordHash) {
        this.rol = rol;
        this.nombre = nombre;
        this.correo = correo;
        this.telefono = telefono;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getRol() { return rol; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public String getTelefono() { return telefono; }
    public String getPasswordHash() { return passwordHash; }
    public String getTokenPush() { return tokenPush; }
    public void setTokenPush(String tokenPush) { this.tokenPush = tokenPush; }
}
