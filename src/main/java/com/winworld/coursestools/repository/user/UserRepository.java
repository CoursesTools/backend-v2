package com.winworld.coursestools.repository.user;

import com.winworld.coursestools.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findUserByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.partnerCode.code = :partnerCode")
    Optional<User> findUserByPartnerCode(String partnerCode);
}
