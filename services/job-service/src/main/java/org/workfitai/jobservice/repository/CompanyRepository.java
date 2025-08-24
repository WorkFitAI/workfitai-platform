package org.workfitai.jobservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.jobservice.domain.Company;

@Repository
public interface CompanyRepository extends JpaRepository<Company, String> {
}
