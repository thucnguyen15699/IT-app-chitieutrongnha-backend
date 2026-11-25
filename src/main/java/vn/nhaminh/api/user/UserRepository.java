package vn.nhaminh.api.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    java.util.List<User> findAllByOrderByIdAsc();
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);
}
