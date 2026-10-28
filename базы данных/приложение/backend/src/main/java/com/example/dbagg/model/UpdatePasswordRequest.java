package com.example.dbagg.model;

import jakarta.validation.constraints.NotBlank;

/** Запрос на обновление пароля инстанса. */
public record UpdatePasswordRequest(@NotBlank String password) {}
