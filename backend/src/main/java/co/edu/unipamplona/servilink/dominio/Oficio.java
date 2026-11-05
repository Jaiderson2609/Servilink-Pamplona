package co.edu.unipamplona.servilink.dominio;

import jakarta.persistence.*;

@Entity
@Table(name = "oficios")
public class Oficio {
    @Id
    private Integer id;
    private String nombre;

    protected Oficio() {}

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
}
