package com.vlink.backend.dto;

public record FavoriteResponse(boolean favorited) {
    public static FavoriteResponse of(boolean favorited) {
        return new FavoriteResponse(favorited);
    }
}
