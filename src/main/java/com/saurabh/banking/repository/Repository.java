package com.saurabh.banking.repository;

import java.util.List;

public interface Repository<ID, T> {
    T findById(ID id);
    List<T> findAll();
    void save(T entity);
    void delete(ID id);
}
