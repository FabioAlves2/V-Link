package com.vlink.backend.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import com.vlink.backend.validation.ValidEventDates;

@Entity @Table(name = "events")
@ValidEventDates
@Getter @Setter @NoArgsConstructor
public class Event {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O título não pode estar vazio.")
    @Size(max = 30, message = "O título não pode ter mais de 30 caracteres.")
    @Column(nullable = false, length = 30)
    private String title;

    @Size(max = 300, message = "A descrição não pode ter mais de 300 caracteres.")
    @Column(length = 300)
    private String description;

    @NotBlank(message = "A localização não pode estar vazia.")
    @Size(max = 100, message = "A localização não pode ter mais de 100 caracteres.")
    @Column(nullable = false, length = 100)
    private String location;

    @Min(value = 1, message = "A capacidade tem de ser pelo menos 1.")
    @Column(nullable = false)
    private int capacity = 1;

    @NotNull(message = "A data de início é obrigatória.")
    @Column(nullable = false)
    private LocalDateTime startDate;

    @NotNull(message = "A data de fim é obrigatória.")
    @Column(nullable = false)
    private LocalDateTime endDate;

    @Size(max = 500, message = "O URL da imagem não pode ter mais de 500 caracteres.")
    @Column(length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Type type = Type.OUTRO;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    // Não persistido — preenchido pelo EventController a partir das subscrições reais
    @Transient
    private int subscriberCount = 0;

    public enum Status { DRAFT, PUBLISHED, CLOSED }

    public enum Type { LIMPEZA, DOACAO, EDUCACAO, AMBIENTE, SOCIAL, OUTRO }
}
