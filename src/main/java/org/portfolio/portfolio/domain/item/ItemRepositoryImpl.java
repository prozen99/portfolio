package org.portfolio.portfolio.domain.item;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.portfolio.portfolio.domain.category.QCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.portfolio.portfolio.domain.item.QItem.item;

@Repository
@RequiredArgsConstructor
public class ItemRepositoryImpl implements ItemRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Item> search(ItemSearchCondition condition, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();

        if (condition != null) {
            if (condition.getCategoryIds() != null && !condition.getCategoryIds().isEmpty()) {
                builder.and(item.category.id.in(condition.getCategoryIds()));
            } else if (condition.getCategoryId() != null) {
                builder.and(item.category.id.eq(condition.getCategoryId()));
            }
            if (condition.getMinPrice() != null) {
                builder.and(item.price.goe(condition.getMinPrice()));
            }
            if (condition.getMaxPrice() != null) {
                builder.and(item.price.loe(condition.getMaxPrice()));
            }
            if (condition.getStatus() != null) {
                builder.and(item.status.eq(condition.getStatus()));
            }
        }

        List<Item> contents = queryFactory
                .selectFrom(item)
                .leftJoin(item.category, QCategory.category).fetchJoin()
                .where(builder)
                .orderBy(item.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(item.count())
                .from(item)
                .where(builder)
                .fetchOne();

        long totalCount = total != null ? total : 0L;
        return new PageImpl<>(contents, pageable, totalCount);
    }
}
