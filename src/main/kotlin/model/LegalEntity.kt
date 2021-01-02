package model

import javax.persistence.*

@Entity
@Table(name = "legal_entity")
class LegalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "s_legal_entity")
    @Column(name = "le_id")
    var id = 0

    @Column
    var code: String = ""

    @Column
    var rating: String = ""

}