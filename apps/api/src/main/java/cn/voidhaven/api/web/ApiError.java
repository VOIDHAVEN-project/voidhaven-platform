package cn.voidhaven.api.web;
public record ApiError(String code, String message, String requestId) { }
