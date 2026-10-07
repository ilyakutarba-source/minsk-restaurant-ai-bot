package by.ilya.restaurantbot.miniapp;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = MiniAppController.class)
public class MiniAppErrors {
    public record Error(String code, String message) { }

    static Error error(String code) {
        return new Error(code, switch (code) {
            case "AUTH_FAILED" -> "Не удалось подтвердить запуск через Telegram.";
            case "INVALID_INPUT" -> "Проверьте дату, время, количество гостей и бюджет.";
            case "NOT_FOUND" -> "Заведение не найдено.";
            default -> "Сервис временно недоступен. Попробуйте позже.";
        });
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> invalid(Exception ignored) {
        return ResponseEntity.badRequest().body(error("INVALID_INPUT"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Error> missing(ResponseStatusException ignored) {
        return ResponseEntity.status(404).body(error("NOT_FOUND"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> temporary(Exception ignored) {
        return ResponseEntity.status(503).body(error("TEMPORARY_ERROR"));
    }
}
