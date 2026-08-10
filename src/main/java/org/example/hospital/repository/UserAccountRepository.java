package org.example.hospital.repository;

import java.util.List;
import java.util.Optional;
import org.example.hospital.domain.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @EntityGraph(attributePaths = {"department", "roles"})
    @Override
    List<UserAccount> findAll();

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);
}
