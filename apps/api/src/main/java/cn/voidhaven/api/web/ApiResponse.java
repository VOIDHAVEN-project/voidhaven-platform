package cn.voidhaven.api.web;
public record ApiResponse<T>(T data, String requestId) { }
