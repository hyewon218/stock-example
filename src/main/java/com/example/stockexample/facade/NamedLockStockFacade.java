package com.example.stockexample.facade;

import com.example.stockexample.repository.LockRepository;
import com.example.stockexample.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NamedLockStockFacade {

    private final LockRepository lockRepository;

    private final StockService stockService;

    @Transactional
    public void decrease(Long id, Long quantity) {

        try {
            lockRepository.getLock(id.toString());
            stockService.decrease(id, quantity);
        }finally {
            lockRepository.releaseLock(id.toString());

        }
    }
}