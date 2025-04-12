package com.winworld.coursestools.repository;

import com.winworld.coursestools.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {
}
