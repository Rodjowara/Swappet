package PROGI.Swappet.repository;

import PROGI.Swappet.model.Transakcija;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransakcijaRepository extends JpaRepository<Transakcija, Integer> {
}
