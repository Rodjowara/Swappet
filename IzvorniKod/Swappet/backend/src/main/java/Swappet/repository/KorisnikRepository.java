package Swappet.repository;

import Swappet.model.Korisnik;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KorisnikRepository extends JpaRepository<Korisnik, String> {

    Korisnik findByEmail(String email);

}
