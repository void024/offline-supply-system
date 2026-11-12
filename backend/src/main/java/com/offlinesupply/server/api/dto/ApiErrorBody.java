package com.offlinesupply.server.api.dto;

/** Uniform error body; the frontend's error normalizer reads `message`. */
public record ApiErrorBody(int status, String error, String message) {
}
