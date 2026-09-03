package cn.voidhaven.api.service;

public interface MediaStorage {
    void put(String key, String mediaType, byte[] bytes);
    StoredObject get(String key);
    void delete(String key);
    record StoredObject(byte[] bytes, String mediaType) { }
}
