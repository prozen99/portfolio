package org.portfolio.portfolio.application.category;

import lombok.RequiredArgsConstructor;
import org.portfolio.portfolio.domain.category.Category;
import org.portfolio.portfolio.domain.category.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<Long> collectDescendantIds(Long categoryId) {
        Set<Long> result = new HashSet<>();
        collect(categoryId, result);
        return new ArrayList<>(result);
    }

    private void collect(Long id, Set<Long> acc) {
        if (id == null || acc.contains(id)) return;
        acc.add(id);
        List<Category> children = categoryRepository.findByParent_Id(id);
        if (children == null || children.isEmpty()) return;
        for (Category c : children) {
            collect(c.getId(), acc);
        }
    }
}
