package com.systemdesigncasestudy.weeks1changha.business.repository;

import com.systemdesigncasestudy.weeks1changha.business.domain.Business;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BusinessRepository {

    long nextId();

    Business save(Business business);

    Optional<Business> findById(long id);

    List<Business> findAllByIds(Collection<Long> ids);
}
