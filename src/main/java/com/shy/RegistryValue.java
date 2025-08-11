package com.shy;

import java.io.Serializable;

/**
 * 注册表键值对类
 * 包含名称、类型和值
 */
public class RegistryValue implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String type;
    private String value;
    
    public RegistryValue(String name, String type, String value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }
    
    public String getName() {
        return name;
    }
    
    public String getType() {
        return type;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
}
