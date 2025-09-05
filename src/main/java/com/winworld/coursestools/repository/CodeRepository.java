package com.winworld.coursestools.repository;

import com.winworld.coursestools.entity.Code;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeRepository extends JpaRepository<Code, String> {

    @Query(value = """
            SELECT EXISTS (
                    SELECT 1 FROM codes_usages
                    WHERE code_id = :codeId
                    AND user_id = :userId
                )
            """, nativeQuery = true)
    boolean existsUsageCodeByUser(int userId, int codeId);

    @Query(value =
            """
            SELECT COUNT(*) FROM codes_usages
            WHERE code_id = :codeId
            """, nativeQuery = true)
    int countCodeUsages(int codeId);

    @Query(value = """
            INSERT INTO codes_usages(code_id, user_id)
            VALUES (:codeId, :userId)
            """, nativeQuery = true)
    @Modifying
    void useCode(int codeId, int userId);

    boolean existsByCode(String code);

    Optional<Code>  findByCodeIgnoreCase(String code);

    List<Code> findAllByOwner_IdNull();
}
