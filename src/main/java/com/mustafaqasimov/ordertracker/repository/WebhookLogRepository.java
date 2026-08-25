package com.mustafaqasimov.ordertracker.repository;

import com.mustafaqasimov.ordertracker.entity.WebhookLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long>, JpaSpecificationExecutor<WebhookLog> {

    Page<WebhookLog> findAll(Pageable pageable);
}
