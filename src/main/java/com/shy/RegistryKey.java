package com.shy;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 注册表项类
 * 包含子项和键值对
 */
public class RegistryKey implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String name;
    private Map<String, RegistryKey> subKeys;  // 子健集合
    private Map<String, RegistryValue> values;  // 键的值集合
    
    public RegistryKey(String name) {
        this.name = name;
        this.subKeys = new HashMap<>();
        this.values = new HashMap<>();
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    // 添加子健，
    public void addSubKey(RegistryKey key) {
        subKeys.put(key.getName(), key);
    }
    // 根据子健名获取子健
    public RegistryKey getSubKey(String name) {
        return subKeys.get(name);
    }
    // 根据子健名删除子健
    public boolean removeSubKey(String name) {
        return subKeys.remove(name) != null;
    }
    // 添加键值
    public void addValue(RegistryValue value) {
        values.put(value.getName(), value);
    }
    // 根据键值的名字获取键值
    public RegistryValue getValue(String name) {
        return values.get(name);
    }
    // 根据键值的名字删除键值
    public boolean removeValue(String name) {
        return values.remove(name) != null;
    }
    // 获取键值集合,
    public Map<String, RegistryValue> getValues() { return values;}
    // 获取子健集合
    public Map<String, RegistryKey> getSubKeys() { return subKeys;}
}
