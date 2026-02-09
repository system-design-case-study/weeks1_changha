package com.systemdesigncasestudy.weeks1changha.business.repository;

import com.systemdesigncasestudy.weeks1changha.business.domain.Business;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryBusinessRepository implements BusinessRepository {

    private final AtomicLong sequence = new AtomicLong(1L);
    private final ConcurrentHashMap<Long, Business> store = new ConcurrentHashMap<>();

    @Override
    public long nextId() {
        return sequence.getAndIncrement();
    }

    @Override
    public Business save(Business business) {
        store.put(business.id(), business);
        return business;
    }

    @Override
    public Optional<Business> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Business> findAllByIds(Collection<Long> ids) {
        List<Business> result = new ArrayList<>();
        for (Long id : ids) {
            Business business = store.get(id);
            if (business != null) {
                result.add(business);
            }
        }
        return result;
    }
}
