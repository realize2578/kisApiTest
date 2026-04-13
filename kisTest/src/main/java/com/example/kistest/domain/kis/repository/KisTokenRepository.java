package com.example.kistest.domain.kis.repository;

import com.example.kistest.domain.kis.entity.KisToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KisTokenRepository extends JpaRepository<KisToken, String> {
}
