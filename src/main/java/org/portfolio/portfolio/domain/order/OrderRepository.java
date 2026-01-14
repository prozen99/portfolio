package org.portfolio.portfolio.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o from Order o " +
            "join fetch o.user u " +
            "join fetch o.item i " +
            "left join fetch o.payment p " +
            "where o.id = :id")
    Optional<Order> findByIdWithUserItemPayment(@Param("id") Long id);

    @Query("select distinct o from Order o " +
            "join fetch o.user u " +
            "join fetch o.item i " +
            "left join fetch o.payment p " +
            "where u.id = :userId")
    List<Order> findAllByUserIdWithItemPayment(@Param("userId") Long userId);

    @Query(value = "select o from Order o " +
            "join fetch o.user u " +
            "join fetch o.item i " +
            "left join fetch o.payment p " +
            "where u.id = :userId order by o.id desc",
            countQuery = "select count(o) from Order o join o.user u where u.id = :userId")
    Page<Order> findPageByUserIdWithItemPayment(@Param("userId") Long userId, Pageable pageable);
}
