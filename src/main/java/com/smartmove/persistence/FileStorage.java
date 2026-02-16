package com.smartmove.persistence;

import java.util.List;

public interface FileStorage<T> {
    List<T> loadAll();
    void saveAll(List<T> items);
}
