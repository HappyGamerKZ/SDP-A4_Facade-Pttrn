package org.aitu.se.backhouse;

public class QualityChecker {

    public void verifyMeal(KitchenTicket ticket, boolean forceBurn) {
        System.out.println("[QualityChecker] Проверка качества для тикета " + ticket.getTicketId() + "...");

        // Специальный триггер для демонстрации отката на защите
        if (forceBurn) {
            throw new QualityCheckFailedException("Блюдо сгорело или не соответствует стандартам ресторана!");
        }

        System.out.println("[QualityChecker] Блюдо выглядит отлично. Контроль пройден.");
    }
}