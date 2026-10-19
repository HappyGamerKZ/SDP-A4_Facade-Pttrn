package org.aitu.se.backhouse;

import org.aitu.se.facade.ICookingStationAllocator;

import java.util.UUID;

public class CookingStationAllocator implements ICookingStationAllocator {
    private final KitchenRepository repository;
    private final QualityChecker qualityChecker;
    private int activeStations = 0;
    private final int MAX_STATIONS = 3; // Максимум 3 заказа одновременно

    public CookingStationAllocator(String kitchen_db_url) {
        this.repository = new KitchenRepository(kitchen_db_url);
        this.qualityChecker = new QualityChecker();
    }

    // НОРМАЛЬНЫЙ ПОТОК (Шаг B)
    // При любом сбое тикет переводится в WASTED: незавершенный шаг не оставляет за собой активных записей
    @Override
    public String startCooking(String orderId, boolean forceStationFail) {
        String ticketId = UUID.randomUUID().toString();
        KitchenTicket ticket = new KitchenTicket(ticketId, orderId);
        repository.save(ticket);

        System.out.println("[Backhouse] Получен заказ " + orderId + ". Ищем свободную плиту...");

        try {
            // Искусственная логика для демонстрации падения Шага B (Требование 3.4)
            if (forceStationFail || activeStations >= MAX_STATIONS) {
                throw new NoAvailableStationsException("Нет свободных кухонных зон для заказа " + orderId);
            }

            // Выделяем станцию и начинаем готовить
            activeStations++;
            try {
                ticket.setStationId(activeStations);
                ticket.transitionTo(KitchenStatus.PREPARING);
                repository.save(ticket);
                System.out.println("[Backhouse] Блюдо готовится на станции №" + ticket.getStationId());

                // Проверяем качество
                qualityChecker.verifyMeal(ticket, forceStationFail);

                // Завершаем готовку
                ticket.transitionTo(KitchenStatus.QUALITY_PASSED);
                repository.save(ticket);
                System.out.println("[Backhouse] Тикет " + ticketId + " готов к выдаче!");
            } finally {
                activeStations--; // Освобождаем станцию даже при сбое
            }
            return ticketId;
        } catch (RuntimeException e) {
            ticket.transitionTo(KitchenStatus.WASTED);
            repository.save(ticket);
            System.out.println("[Backhouse] Тикет " + ticketId + " не выполнен и помечен как WASTED.");
            throw e;
        }
    }

    // КОМПЕНСАЦИЯ: Откат на кухне (когда упал Шаг C - Оплата)
    @Override
    public void compensateTicket(String ticketId) {
        KitchenTicket ticket = repository.findById(ticketId);
        if (ticket != null && ticket.getStatus() != KitchenStatus.WASTED) {
            ticket.transitionTo(KitchenStatus.WASTED);
            repository.save(ticket);
            System.out.println("[Backhouse - Компенсация] Тикет " + ticketId + " помечен как WASTED (в мусорку).");
        }
    }
}